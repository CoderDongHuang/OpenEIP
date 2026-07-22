package com.openeip.chat.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StreamChatMessageRequest(
    @NotBlank @Size(max = 4000) String message, @Min(1) @Max(20) Integer topK) {
  public int resolvedTopK() {
    return topK == null ? 5 : topK;
  }
}
