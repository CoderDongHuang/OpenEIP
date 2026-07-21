package com.openeip.auth.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** New user credentials. */
public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "^[A-Za-z0-9_.-]+$") String username,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 72) String password) {}
