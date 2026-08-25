package com.contract.analyser.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final WebClient webClient;

    public AuditService(@Value("${app.audit.base-url}") String auditBaseUrl) {
        this.webClient = WebClient.builder().baseUrl(auditBaseUrl).build();
    }

    public Mono<String> logAudit(String contractName, String status, int wordCount) {
        Map<String, Object> body = Map.of(
                "contractName", contractName,
                "status", status,
                "wordCount", wordCount
        );

        return webClient.post()
                .uri("/api/audit/log")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(resp -> log.info("Audit logged: {}", resp))
                .doOnError(err -> log.warn("Audit service unavailable: {}", err.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }
}
