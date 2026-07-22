package com.openeip.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiEnvelopeTest {

  @Test
  void createsSuccessAndErrorEnvelopes() {
    ApiEnvelope<String> success = ApiEnvelope.success("value", "request-1");
    ApiEnvelope<Void> error = ApiEnvelope.error("TEST-E-001", "failed", "request-2");

    assertThat(success.code()).isEqualTo(0);
    assertThat(success.message()).isEqualTo("success");
    assertThat(success.data()).isEqualTo("value");
    assertThat(success.requestId()).isEqualTo("request-1");
    assertThat(success.timestamp()).isNotNull();
    assertThat(error.code()).isEqualTo("TEST-E-001");
    assertThat(error.message()).isEqualTo("failed");
    assertThat(error.data()).isNull();
    assertThat(error.requestId()).isEqualTo("request-2");
  }
}
