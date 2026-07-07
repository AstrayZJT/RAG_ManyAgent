package com.astray.insightflow.knowledge.service;

import com.astray.insightflow.common.exception.NotFoundException;
import com.astray.insightflow.config.RagProperties;
import com.astray.insightflow.knowledge.domain.DocumentChunk;
import com.astray.insightflow.knowledge.domain.KnowledgeDocument;
import com.astray.insightflow.knowledge.domain.KnowledgeDocumentStatus;
import com.astray.insightflow.knowledge.persistence.DocumentChunkRepository;
import com.astray.insightflow.knowledge.persistence.KnowledgeDocumentRepository;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeDocumentService {

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final Path storageRoot;

    public KnowledgeDocumentService(KnowledgeDocumentRepository knowledgeDocumentRepository,
                                    DocumentChunkRepository documentChunkRepository,
                                    RagProperties ragProperties) throws IOException {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.storageRoot = Path.of(ragProperties.knowledgePath()).toAbsolutePath();
        Files.createDirectories(this.storageRoot.resolve("raw"));
    }

    @Transactional
    public KnowledgeDocument upload(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String documentId = UUID.randomUUID().toString();
        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        String storedName = extension.isBlank() ? documentId : documentId + "." + extension;
        Path target = storageRoot.resolve("raw").resolve(storedName);
        Files.copy(file.getInputStream(), target);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(documentId);
        document.setOriginalFilename(StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : storedName);
        document.setStoragePath(target.toString());
        document.setMediaType(file.getContentType());
        document.setStatus(KnowledgeDocumentStatus.UPLOADED);
        document.setUploadedAt(Instant.now());
        return knowledgeDocumentRepository.save(document);
    }

    @Transactional
    public KnowledgeDocument index(String documentId) throws IOException {
        KnowledgeDocument document = knowledgeDocumentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        document.setStatus(KnowledgeDocumentStatus.INDEXING);
        knowledgeDocumentRepository.save(document);

        try {
            documentChunkRepository.deleteByDocumentId(documentId);
            String content = readDocumentContent(Path.of(document.getStoragePath()));
            List<String> chunks = splitIntoChunks(content, 800);
            List<DocumentChunk> entities = new ArrayList<>();
            for (int index = 0; index < chunks.size(); index++) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setId(UUID.randomUUID().toString());
                chunk.setDocumentId(documentId);
                chunk.setChunkIndex(index);
                chunk.setContent(chunks.get(index));
                chunk.setTokenCount(chunks.get(index).length());
                chunk.setCreatedAt(Instant.now());
                entities.add(chunk);
            }
            documentChunkRepository.saveAll(entities);
            document.setStatus(KnowledgeDocumentStatus.INDEXED);
            document.setIndexedAt(Instant.now());
            document.setErrorMessage(null);
            return knowledgeDocumentRepository.save(document);
        } catch (Exception exception) {
            document.setStatus(KnowledgeDocumentStatus.FAILED);
            document.setErrorMessage(exception.getMessage());
            knowledgeDocumentRepository.save(document);
            throw exception;
        }
    }

    public KnowledgeDocument getDocument(String documentId) {
        return knowledgeDocumentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
    }

    public List<KnowledgeDocument> listDocuments() {
        return knowledgeDocumentRepository.findAllByOrderByUploadedAtDesc();
    }

    String readDocumentContent(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        List<Charset> candidates = List.of(
                StandardCharsets.UTF_8,
                Charset.forName("GB18030"),
                Charset.forName("GBK")
        );
        for (Charset charset : candidates) {
            try {
                return charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (CharacterCodingException ignored) {
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private List<String> splitIntoChunks(String content, int maxLength) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String normalized = content.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + maxLength, normalized.length());
            chunks.add(normalized.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
