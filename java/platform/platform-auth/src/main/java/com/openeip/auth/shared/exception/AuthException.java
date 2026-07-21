package com.openeip.auth.shared.exception;

import lombok.Getter;

/** Expected authentication and authorization failure. */
@Getter
public class AuthException extends RuntimeException {

  private final String errorCode;
  private final int httpStatus;

  public AuthException(String errorCode, String message, int httpStatus) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
  }

  public static AuthException validation(String message) {
    return new AuthException("AUTH-V-001", message, 400);
  }

  public static AuthException invalidCredentials() {
    return new AuthException("AUTH-E-001", "Invalid username or password", 401);
  }

  public static AuthException tokenExpired() {
    return new AuthException("AUTH-E-002", "Token has expired", 401);
  }

  public static AuthException tokenInvalid() {
    return new AuthException("AUTH-E-003", "Token is invalid or has already been used", 401);
  }

  public static AuthException duplicateUsername(String username) {
    return new AuthException("AUTH-E-004", "Username already exists: " + username, 409);
  }

  public static AuthException duplicateEmail(String email) {
    return new AuthException("AUTH-E-004", "Email already exists: " + email, 409);
  }

  public static AuthException notFound(String resource) {
    return new AuthException("AUTH-E-005", resource + " was not found", 404);
  }

  public static AuthException duplicateRole(String name) {
    return new AuthException("AUTH-E-006", "Role already exists: " + name, 409);
  }

  public static AuthException forbidden() {
    return new AuthException("AUTH-P-001", "Insufficient permission", 403);
  }

  public static AuthException rateLimited() {
    return new AuthException("AUTH-R-001", "Too many requests", 429);
  }
}
