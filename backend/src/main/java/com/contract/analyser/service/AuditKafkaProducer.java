package com.contract.analyser.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Kafka Producer — sends audit events asynchronously to the "contract-audit-topic".
 *
 * Unlike the WebClient-based AuditService (which makes a synchronous HTTP call),
 * this producer publishes a message to Kafka and returns immediately.
 * The audit service consumes the message independently.
 *
 * Used for: chat question events (async, fire-and-forget)
 * Compare with: AuditService.java (HTTP, synchronous for upload events)
 */
@Service
public class AuditKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(AuditKafkaProducer.class);
    private static final String TOPIC = "contract-audit-topic";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AuditKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes an audit event to Kafka.
     * The message is a JSON string with contractName, status, wordCount, question, and answer.
     */
    public void sendAuditEvent(String contractName, String status, int wordCount,
                               String question, String answer) {
        try {
            Map<String, Object> event = Map.of(
                    "contractName", contractName,
                    "status", status,
                    "wordCount", wordCount,
                    "question", question,
                    "answer", answer
            );
            String message = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(TOPIC, contractName, message)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Kafka audit event sent: topic={}, key={}, offset={}",
                                    TOPIC, contractName, result.getRecordMetadata().offset());
                        } else {
                            log.warn("Failed to send Kafka audit event: {}", ex.getMessage());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit event: {}", e.getMessage());
        }
    }
}
