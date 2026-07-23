package com.openeip.auth.api.dto.request;

import jakarta.validation.constraints.NotNull;

/** Administrative user activation command. */
public record UpdateUserStatusRequest(@NotNull Boolean active) {}
