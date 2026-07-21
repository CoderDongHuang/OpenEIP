package com.openeip.knowledge.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AttachDocumentRequest(@NotBlank String documentId) {}
