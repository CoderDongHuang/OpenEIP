package com.openeip.connector.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConnectorOperationTest {
  @Test
  void recordsSuccessAndTruncatesFailureMessages() {
    Instant started = Instant.parse("2026-07-25T00:00:00Z");
    ConnectorOperation operation =
        new ConnectorOperation(
            "operation",
            "tenant",
            "connector",
            "actor",
            "WRITE",
            "correlation",
            "idempotency",
            "fingerprint",
            started);
    assertThat(operation.getStatus()).isEqualTo("RUNNING");
    operation.succeed("{\"status\":\"ok\"}", started.plusSeconds(1));
    assertThat(operation.getStatus()).isEqualTo("SUCCEEDED");
    assertThat(operation.getResultJson()).contains("ok");
    String message = "x".repeat(600);
    operation.fail("CONN-ERROR", message, started.plusSeconds(2));
    assertThat(operation.getStatus()).isEqualTo("FAILED");
    assertThat(operation.getErrorCode()).isEqualTo("CONN-ERROR");
    assertThat(operation.getErrorMessage()).hasSize(500);
    assertThat(operation.getCompletedAt()).isEqualTo(started.plusSeconds(2));
  }
}
