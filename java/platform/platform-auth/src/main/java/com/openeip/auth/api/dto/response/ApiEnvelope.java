package com.openeip.auth.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Stable response envelope for Auth APIs and security filters. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(
    Object code, String message, T data, String requestId, Instant timestamp) {

  public static <T> ApiEnvelope<T> success(T data, String requestId) {
    return new ApiEnvelope<>(0, "success", data, requestId, Instant.now());
  }

  public static ApiEnvelope<Void> error(String code, String message, String requestId) {
    return new ApiEnvelope<>(code, message, null, requestId, Instant.now());
  }
}
