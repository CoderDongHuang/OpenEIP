package com.openeip.connector.shared;

public class ConnectorAdapterException extends RuntimeException {
  private final String code;
  private final boolean retryable;

  public ConnectorAdapterException(String code, String message, boolean retryable) {
    super(message);
    this.code = code;
    this.retryable = retryable;
  }

  public String getCode() {
    return code;
  }

  public boolean isRetryable() {
    return retryable;
  }
}
