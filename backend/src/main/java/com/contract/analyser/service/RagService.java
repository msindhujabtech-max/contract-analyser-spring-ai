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
    private final CacheService cacheService;
    private final ChatHistoryService chatHistoryService;
    private final RateLimiterService rateLimiterService;

    public RagService(ChatClient chatClient, VectorStore vectorStore,
                      CacheService cacheService, ChatHistoryService chatHistoryService,
                      RateLimiterService rateLimiterService) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.cacheService = cacheService;
        this.chatHistoryService = chatHistoryService;
        this.rateLimiterService = rateLimiterService;
    }

    public Flux<String> streamResponse(ChatRequest request) {
        // Step 1: Check rate limit
        return rateLimiterService.isAllowed(request.userId())
                .flatMapMany(allowed -> {
                    if (!allowed) {
                        return Flux.just("Rate limit exceeded. Please wait a minute before asking again.");
                    }

                    // Step 2: Check cache for existing response
                    String cacheKey = cacheService.generateCacheKey(
                            request.contractId(), request.userId(), request.question());

                    return cacheService.getCachedResponse(cacheKey)
                            .flatMapMany(cachedResponse -> {
                                // Cache HIT — return cached response immediately
                                // Also save to chat history
                                return chatHistoryService.saveMessage(
                                        request.contractId(), request.userId(), "user", request.question())
                                        .then(chatHistoryService.saveMessage(
                                                request.contractId(), request.userId(), "assistant", cachedResponse))
                                        .thenMany(Flux.just(cachedResponse));
                            })
                            .switchIfEmpty(
                                    // Cache MISS — run full RAG pipeline
                                    executeRagPipeline(request, cacheKey)
                            );
                });
    }

    /**
     * Executes the full RAG pipeline: vector search → LLM streaming → cache result
     */
    private Flux<String> executeRagPipeline(ChatRequest request, String cacheKey) {
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
                String fallback = "I cannot find that information in the contract.";
                return cacheService.cacheResponse(cacheKey, fallback)
                        .thenMany(Flux.just(fallback));
            }

            // Build the system prompt with context
            String systemPrompt = SYSTEM_PROMPT_TEMPLATE.replace("{context}", context);

            // Stream response and collect for caching
            StringBuilder responseBuilder = new StringBuilder();

            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(request.question())
                    .stream()
                    .content()
                    .doOnNext(responseBuilder::append)
                    .doOnComplete(() -> {
                        String fullResponse = responseBuilder.toString();
                        // Cache the complete response
                        cacheService.cacheResponse(cacheKey, fullResponse).subscribe();
                        // Save to chat history
                        chatHistoryService.saveMessage(
                                request.contractId(), request.userId(), "user", request.question()).subscribe();
                        chatHistoryService.saveMessage(
                                request.contractId(), request.userId(), "assistant", fullResponse).subscribe();
                    });
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
