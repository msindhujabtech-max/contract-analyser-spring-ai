package com.contract.analyser.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * WebClient-based audit service with Circuit Breaker pattern.
 *
 * Circuit Breaker states:
 * - CLOSED: Normal operation, all calls go through
 * - OPEN: Too many failures (>50%), calls are rejected immediately for 30s
 * - HALF_OPEN: After 30s, allows 3 test calls to check if service is back
 *
 * This prevents cascading failures — if the audit service is down,
 * we stop hammering it and return the fallback instantly.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final WebClient webClient;

    public AuditService(@Value("${app.audit.base-url}") String auditBaseUrl) {
        this.webClient = WebClient.builder().baseUrl(auditBaseUrl).build();
    }

    @CircuitBreaker(name = "auditService", fallbackMethod = "auditFallback")
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
                .doOnError(err -> log.warn("Audit service call failed: {}", err.getMessage()));
    }

    /**
     * Fallback method — called when circuit is OPEN or the call fails.
     * Must have the same signature + Throwable parameter.
     */
    private Mono<String> auditFallback(String contractName, String status, int wordCount, Throwable t) {
        log.warn("Circuit breaker fallback for audit service. Reason: {}", t.getMessage());
        return Mono.just("Audit service unavailable (circuit breaker active)");
    }
}
