package com.openeip.knowledge.api.dto;

public record KnowledgeSearchRequest(String query, String mode, Integer topK) {}
