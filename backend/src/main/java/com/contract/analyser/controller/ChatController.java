package com.contract.analyser.controller;

import com.contract.analyser.dto.ChatRequest;
import com.contract.analyser.service.ChatHistoryService;
import com.contract.analyser.service.RagService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final RagService ragService;
    private final ChatHistoryService chatHistoryService;

    public ChatController(RagService ragService, ChatHistoryService chatHistoryService) {
        this.ragService = ragService;
        this.chatHistoryService = chatHistoryService;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        return ragService.streamResponse(request);
    }

    /**
     * GET /api/chat/history?contract_id=1&user_id=101
     * Returns chat history from Redis (survives browser refresh)
     */
    @GetMapping("/chat/history")
    public Mono<List<String>> getChatHistory(
            @RequestParam("contract_id") Long contractId,
            @RequestParam("user_id") Long userId) {
        return chatHistoryService.getHistory(contractId, userId);
    }
}
