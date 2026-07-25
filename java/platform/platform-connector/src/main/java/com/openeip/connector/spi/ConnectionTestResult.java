package com.openeip.connector.spi;

public record ConnectionTestResult(
    boolean success, long latencyMillis, String code, String message) {
  public static ConnectionTestResult success(long latencyMillis) {
    return new ConnectionTestResult(true, latencyMillis, "OK", "Connection succeeded");
  }

  public static ConnectionTestResult failure(long latencyMillis, String code, String message) {
    return new ConnectionTestResult(false, latencyMillis, code, message);
  }
}
