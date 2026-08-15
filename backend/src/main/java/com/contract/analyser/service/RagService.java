package com.contract.analyser.service;

import com.contract.analyser.dto.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a helpful AI contract analyst. Your role is to answer questions about contract documents accurately.
            
            STRICT RULES:
            1. Only answer based on the provided context below.
            2. If the context does not contain enough information to answer the question, respond EXACTLY with: "I cannot find that information in the contract."
            3. Do not make assumptions or infer information not present in the context.
            4. Be precise and cite specific clauses or sections when possible.
            
            CONTEXT FROM CONTRACT:
            {context}
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagService(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    public Flux<String> streamResponse(ChatRequest request) {
        return Flux.defer(() -> {
            // Build metadata filter for multi-tenant isolation
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            var filterExpression = builder.and(
                    builder.eq("contract_id", request.contractId()),
                    builder.eq("user_id", request.userId())
            ).build();

            // Perform similarity search with metadata filtering
            SearchRequest searchRequest = SearchRequest.query(request.question())
                    .withTopK(3)
                    .withFilterExpression(filterExpression);

            List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

            // Build context from retrieved documents
            String context = relevantDocs.stream()
                    .map(Document::getContent)
                    .collect(Collectors.joining("\n\n---\n\n"));

            if (context.isBlank()) {
                return Flux.just("I cannot find that information in the contract.");
            }

            // Build the system prompt with context
            String systemPrompt = SYSTEM_PROMPT_TEMPLATE.replace("{context}", context);

            // Stream response using ChatClient fluent API
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(request.question())
                    .stream()
                    .content();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
