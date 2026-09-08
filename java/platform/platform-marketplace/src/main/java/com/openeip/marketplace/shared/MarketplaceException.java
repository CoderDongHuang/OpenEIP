package com.openeip.marketplace.shared;

public class MarketplaceException extends RuntimeException {
  private final String code;

  private MarketplaceException(String code, String message) {
    super(message);
    this.code = code;
  }

  public static MarketplaceException invalid(String message) {
    return new MarketplaceException("MKT-V-001", message);
  }

  public static MarketplaceException conflict(String message) {
    return new MarketplaceException("MKT-C-001", message);
  }

  public static MarketplaceException transition(String message) {
    return new MarketplaceException("MKT-C-002", message);
  }

  public static MarketplaceException forbidden(String message) {
    return new MarketplaceException("MKT-A-001", message);
  }

  public String code() {
    return code;
  }
}
