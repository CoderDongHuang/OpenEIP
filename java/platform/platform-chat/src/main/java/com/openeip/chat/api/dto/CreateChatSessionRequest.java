package com.openeip.chat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChatSessionRequest(
    @NotBlank String knowledgeBaseId, @Size(max = 120) String title) {}
