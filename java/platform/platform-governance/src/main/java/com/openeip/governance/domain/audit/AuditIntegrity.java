package com.openeip.governance.domain.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Canonical SHA-256 calculation for the tenant-scoped audit chain. */
public final class AuditIntegrity {
  private AuditIntegrity() {}

  public static String recordHash(
      AuditAppendCommand command, String previousHash, String summaryJson) {
    String canonical =
        fields(
            command.eventId().toString(),
            command.tenantId().toString(),
            command.principalId().toString(),
            command.action(),
            command.resourceType(),
            command.resourceId(),
            command.outcome().name(),
            command.requestId(),
            command.traceId(),
            command.policyVersion(),
            command.schemaVersion(),
            command.occurredAt().toString(),
            previousHash,
            summaryJson);
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      var result = new StringBuilder(64);
      for (byte value : digest) {
        result.append(String.format("%02x", value));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }

  private static String fields(String... values) {
    var result = new StringBuilder();
    for (String value : values) {
      String safe = value == null ? "" : value;
      result.append(safe.length()).append(':').append(safe).append('|');
    }
    return result.toString();
  }
}
