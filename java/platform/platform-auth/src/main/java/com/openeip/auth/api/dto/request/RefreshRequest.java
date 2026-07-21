package com.openeip.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Refresh token rotation request. */
public record RefreshRequest(@NotBlank @Size(max = 4096) String refreshToken) {}
