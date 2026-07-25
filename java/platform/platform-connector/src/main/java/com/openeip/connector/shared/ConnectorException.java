package com.openeip.connector.shared;

import org.springframework.http.HttpStatus;

public class ConnectorException extends RuntimeException {
  private final String code;
  private final HttpStatus status;

  private ConnectorException(String code, HttpStatus status, String message) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public static ConnectorException invalid(String message) {
    return new ConnectorException("CONN-V-001", HttpStatus.BAD_REQUEST, message);
  }

  public static ConnectorException notFound() {
    return new ConnectorException("CONN-E-001", HttpStatus.NOT_FOUND, "Connector not found");
  }

  public static ConnectorException forbidden() {
    return new ConnectorException(
        "CONN-E-002", HttpStatus.FORBIDDEN, "Connector operation forbidden");
  }

  public static ConnectorException conflict(String message) {
    return new ConnectorException("CONN-E-003", HttpStatus.CONFLICT, message);
  }

  public static ConnectorException unauthorized() {
    return new ConnectorException(
        "CONN-A-001", HttpStatus.UNAUTHORIZED, "Webhook signature is invalid");
  }

  public String getCode() {
    return code;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
