package com.astray.insightflow.knowledge.service;

import com.astray.insightflow.config.RagProperties;
import com.astray.insightflow.knowledge.domain.KnowledgeDocument;
import com.astray.insightflow.knowledge.domain.KnowledgeDocumentStatus;
import com.astray.insightflow.knowledge.persistence.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Order(100)
public class SampleKnowledgeBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleKnowledgeBootstrapRunner.class);

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final RagProperties ragProperties;

    public SampleKnowledgeBootstrapRunner(KnowledgeDocumentRepository knowledgeDocumentRepository,
                                          KnowledgeDocumentService knowledgeDocumentService,
                                          RagProperties ragProperties) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.ragProperties = ragProperties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (knowledgeDocumentRepository.count() > 0) {
            return;
        }

        Path rawDirectory = Path.of(ragProperties.knowledgePath()).toAbsolutePath().resolve("raw");
        if (!Files.isDirectory(rawDirectory)) {
            log.info("Sample knowledge bootstrap skipped: raw directory not found at {}", rawDirectory);
            return;
        }

        Map<String, Path> uniqueSamples = collectUniqueSamples(rawDirectory);
        if (uniqueSamples.isEmpty()) {
            log.info("Sample knowledge bootstrap skipped: no sample files found under {}", rawDirectory);
            return;
        }

        List<KnowledgeDocument> createdDocuments = new ArrayList<>();
        int index = 1;
        for (Path sample : uniqueSamples.values()) {
            KnowledgeDocument document = new KnowledgeDocument();
            document.setId(UUID.randomUUID().toString());
            document.setOriginalFilename("新能源车竞品样例-" + index + ".txt");
            document.setStoragePath(sample.toAbsolutePath().toString());
            document.setCollectionName(KnowledgeDocumentService.DEFAULT_COLLECTION);
            document.setMediaType("text/plain");
            document.setStatus(KnowledgeDocumentStatus.UPLOADED);
            document.setUploadedAt(Instant.now());
            createdDocuments.add(knowledgeDocumentRepository.save(document));
            index++;
        }

        for (KnowledgeDocument document : createdDocuments) {
            knowledgeDocumentService.index(document.getId());
        }

        log.info("Seeded {} sample knowledge documents from {}", createdDocuments.size(), rawDirectory);
    }

    private Map<String, Path> collectUniqueSamples(Path rawDirectory) throws IOException {
        Map<String, Path> uniqueSamples = new LinkedHashMap<>();
        try (var stream = Files.list(rawDirectory)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isSupportedSampleFile)
                    .sorted()
                    .forEach(path -> {
                        try {
                            String content = knowledgeDocumentService.readDocumentContent(path);
                            String normalized = normalizeContent(content);
                            if (StringUtils.hasText(normalized)) {
                                uniqueSamples.putIfAbsent(normalized, path);
                            }
                        } catch (IOException exception) {
                            log.warn("Skip sample knowledge file {}: {}", path, exception.getMessage());
                        }
                    });
        }
        return uniqueSamples;
    }

    private boolean isSupportedSampleFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".json") || fileName.endsWith(".csv");
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}
