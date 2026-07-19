package com.astray.insightflow.knowledge.service;

import com.astray.insightflow.common.exception.NotFoundException;
import com.astray.insightflow.config.RagProperties;
import com.astray.insightflow.knowledge.domain.DocumentChunk;
import com.astray.insightflow.knowledge.domain.KnowledgeDocument;
import com.astray.insightflow.knowledge.domain.KnowledgeDocumentStatus;
import com.astray.insightflow.knowledge.persistence.DocumentChunkRepository;
import com.astray.insightflow.knowledge.persistence.KnowledgeDocumentRepository;
import com.astray.insightflow.retrieval.vector.VectorSearchService;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeDocumentService {

    public static final String DEFAULT_COLLECTION = "default";
    private static final String SOFT_BOUNDARY_CHARS = "\n.!?;,:)]\u3002\uff01\uff1f\uff1b\uff0c\uff1a\uff09\uff3d";

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final VectorSearchService vectorSearchService;
    private final Path storageRoot;
    private final int chunkMaxLength;
    private final int chunkOverlap;

    public KnowledgeDocumentService(KnowledgeDocumentRepository knowledgeDocumentRepository,
                                    DocumentChunkRepository documentChunkRepository,
                                    RagProperties ragProperties,
                                    VectorSearchService vectorSearchService) throws IOException {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.vectorSearchService = vectorSearchService;
        this.storageRoot = Path.of(ragProperties.knowledgePath()).toAbsolutePath();
        this.chunkMaxLength = ragProperties.chunking().maxLength();
        this.chunkOverlap = ragProperties.chunking().overlap();
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
        document.setContentHash(sha256Hex(target));
        document.setCollectionName(DEFAULT_COLLECTION);
        document.setMediaType(file.getContentType());
        document.setStatus(KnowledgeDocumentStatus.UPLOADED);
        document.setUploadedAt(Instant.now());
        return knowledgeDocumentRepository.save(document);
    }

    @Transactional
    public KnowledgeDocument index(String documentId) throws IOException {
        KnowledgeDocument document = knowledgeDocumentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        Path sourcePath = Path.of(document.getStoragePath());
        if (!StringUtils.hasText(document.getContentHash())) {
            document.setContentHash(sha256Hex(sourcePath));
        }
        document.setStatus(KnowledgeDocumentStatus.INDEXING);
        knowledgeDocumentRepository.save(document);

        try {
            List<String> staleChunkIds = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream()
                    .map(DocumentChunk::getId)
                    .toList();
            documentChunkRepository.deleteByDocumentId(documentId);
            String content = readDocumentContent(sourcePath);
            List<ChunkSlice> chunks = splitIntoChunks(content, chunkMaxLength, chunkOverlap);
            List<DocumentChunk> entities = new ArrayList<>();
            for (int index = 0; index < chunks.size(); index++) {
                ChunkSlice slice = chunks.get(index);
                DocumentChunk chunk = new DocumentChunk();
                chunk.setId(UUID.randomUUID().toString());
                chunk.setDocumentId(documentId);
                chunk.setChunkIndex(index);
                chunk.setStartOffset(slice.startOffset());
                chunk.setEndOffset(slice.endOffset());
                chunk.setContentHash(sha256Hex(slice.content()));
                chunk.setContent(slice.content());
                chunk.setTokenCount(slice.content().length());
                chunk.setCreatedAt(Instant.now());
                entities.add(chunk);
            }
            documentChunkRepository.saveAll(entities);
            vectorSearchService.replaceDocumentChunks(staleChunkIds, entities, document.getCollectionName());
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

    @Transactional
    public KnowledgeDocument importText(String filename,
                                        String content,
                                        String collectionName,
                                        boolean forceReindex) throws IOException {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("Imported document content is empty");
        }
        String normalizedCollection = StringUtils.hasText(collectionName)
                ? collectionName.trim()
                : DEFAULT_COLLECTION;
        String contentHash = sha256Hex(content);
        KnowledgeDocument existing = knowledgeDocumentRepository
                .findFirstByCollectionNameAndContentHash(normalizedCollection, contentHash)
                .orElse(null);
        if (existing != null) {
            if (forceReindex || existing.getStatus() != KnowledgeDocumentStatus.INDEXED) {
                return index(existing.getId());
            }
            return existing;
        }

        String documentId = UUID.randomUUID().toString();
        Path target = storageRoot.resolve("raw").resolve(documentId + ".txt");
        Files.writeString(target, content, StandardCharsets.UTF_8);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(documentId);
        document.setOriginalFilename(StringUtils.hasText(filename) ? filename.trim() : documentId + ".txt");
        document.setStoragePath(target.toString());
        document.setContentHash(contentHash);
        document.setCollectionName(normalizedCollection);
        document.setMediaType("text/plain");
        document.setStatus(KnowledgeDocumentStatus.UPLOADED);
        document.setUploadedAt(Instant.now());
        knowledgeDocumentRepository.save(document);
        return index(documentId);
    }

    public List<DocumentChunk> listChunks(String documentId) {
        if (!knowledgeDocumentRepository.existsById(documentId)) {
            throw new NotFoundException("Document not found: " + documentId);
        }
        return documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
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

    List<ChunkSlice> splitIntoChunks(String content, int maxLength, int overlap) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<ChunkSlice> chunks = new ArrayList<>();
        int contentEnd = trimTrailingWhitespace(content, 0, content.length());
        int start = skipLeadingWhitespace(content, 0, contentEnd);
        while (start < contentEnd) {
            int end = findChunkEnd(content, start, contentEnd, maxLength);
            int sliceStart = skipLeadingWhitespace(content, start, end);
            int sliceEnd = trimTrailingWhitespace(content, sliceStart, end);
            if (sliceStart < sliceEnd) {
                chunks.add(new ChunkSlice(content.substring(sliceStart, sliceEnd), sliceStart, sliceEnd));
            }
            if (end >= contentEnd) {
                break;
            }
            start = Math.max(end - Math.max(0, overlap), start + 1);
            start = skipLeadingWhitespace(content, start, contentEnd);
        }
        return chunks;
    }

    private int findChunkEnd(String content, int start, int contentEnd, int maxLength) {
        int hardEnd = Math.min(start + maxLength, contentEnd);
        if (hardEnd >= contentEnd) {
            return contentEnd;
        }
        int softFloor = start + Math.max(120, (int) (maxLength * 0.55D));
        for (int index = hardEnd - 1; index >= softFloor; index--) {
            char current = content.charAt(index);
            if (SOFT_BOUNDARY_CHARS.indexOf(current) >= 0) {
                return index + 1;
            }
        }
        return hardEnd;
    }

    private int skipLeadingWhitespace(String value, int start, int end) {
        int index = Math.max(0, start);
        while (index < end && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private int trimTrailingWhitespace(String value, int start, int end) {
        int index = Math.min(value.length(), end);
        while (index > start && Character.isWhitespace(value.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private String sha256Hex(Path path) throws IOException {
        return sha256Hex(Files.readAllBytes(path));
    }

    private String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    record ChunkSlice(String content, int startOffset, int endOffset) {
    }
}
