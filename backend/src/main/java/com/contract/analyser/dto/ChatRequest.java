package com.contract.analyser.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatRequest(
        @JsonProperty("contract_id") Long contractId,
        @JsonProperty("user_id") Long userId,
        String question
) {}
