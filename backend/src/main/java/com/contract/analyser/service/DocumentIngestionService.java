package com.contract.analyser.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class DocumentIngestionService {

    private static final Long DEFAULT_CONTRACT_ID = 1L;
    private static final Long DEFAULT_USER_ID = 101L;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final CacheService cacheService;
    private final ChatHistoryService chatHistoryService;
    private final AuditService auditService;

    public DocumentIngestionService(VectorStore vectorStore, JdbcTemplate jdbcTemplate,
                                    CacheService cacheService, ChatHistoryService chatHistoryService,
                                    AuditService auditService) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.cacheService = cacheService;
        this.chatHistoryService = chatHistoryService;
        this.auditService = auditService;
    }

    public Map<String, Object> ingestDocument(FilePart filePart) {
        Path tempFile = null;
        try {
            // Save uploaded file to temp location
            tempFile = Files.createTempFile("upload-", "-" + filePart.filename());
            File file = tempFile.toFile();
            filePart.transferTo(file).block();

            // Read PDF using Tika
            TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(file));
            List<Document> documents = reader.get();

            // Chunk documents using TokenTextSplitter
            TokenTextSplitter splitter = new TokenTextSplitter(1000, 200, 5, 10000, true);
            List<Document> chunks = splitter.apply(documents);

            // Enrich metadata with contract_id and user_id
            for (Document chunk : chunks) {
                chunk.getMetadata().put("contract_id", DEFAULT_CONTRACT_ID);
                chunk.getMetadata().put("user_id", DEFAULT_USER_ID);
            }

            // Delete previous vectors for this contract
            jdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'contract_id' = ?",
                    String.valueOf(DEFAULT_CONTRACT_ID)
            );

            // Save chunks to vector store (triggers embedding via Ollama)
            vectorStore.add(chunks);

            // Invalidate Redis cache for this contract (old answers are now stale)
            cacheService.invalidateContractCache(DEFAULT_CONTRACT_ID).subscribe();

            // Clear chat history (new document means fresh conversation)
            chatHistoryService.clearHistory(DEFAULT_CONTRACT_ID, DEFAULT_USER_ID).subscribe();

            // Fire audit event to contract-audit-service (blocking to capture response)
            String auditResult = auditService.logAudit(filePart.filename(), "INGESTED", chunks.size())
                    .block(java.time.Duration.ofSeconds(3));

            return Map.of(
                    "status", "success",
                    "filename", filePart.filename(),
                    "chunks", chunks.size(),
                    "message", "Document processed and indexed successfully",
                    "auditStatus", auditResult != null ? auditResult : "Audit service unavailable"
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
        }
    }
}
