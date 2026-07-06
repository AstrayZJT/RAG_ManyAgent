package com.astray.insightflow.knowledge.api;

import com.astray.insightflow.knowledge.service.KnowledgeDocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    public DocumentController(KnowledgeDocumentService knowledgeDocumentService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @PostMapping("/upload")
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(DocumentResponse.from(knowledgeDocumentService.upload(file)));
    }

    @PostMapping("/{id}/index")
    public ResponseEntity<DocumentResponse> index(@PathVariable("id") String documentId) throws Exception {
        return ResponseEntity.ok(DocumentResponse.from(knowledgeDocumentService.index(documentId)));
    }
}
