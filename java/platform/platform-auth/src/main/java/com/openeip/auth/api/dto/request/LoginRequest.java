package com.openeip.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Login credentials. */
public record LoginRequest(
    @NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 72) String password) {}
