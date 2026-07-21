package com.openeip.auth.api.dto.response;

import com.openeip.auth.application.service.AuthService;

/** Access and refresh token pair. */
public record TokenResponse(
    String accessToken, String refreshToken, long expiresIn, String tokenType) {

  public static TokenResponse from(AuthService.TokenPair pair) {
    return new TokenResponse(
        pair.accessToken(), pair.refreshToken(), pair.expiresIn(), pair.tokenType());
  }
}
