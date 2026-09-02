package com.openeip.governance.application.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openeip.governance.shared.exception.GovernanceAuthorizationException;
import org.junit.jupiter.api.Test;

class TraceContextTest {
  @Test
  void createsTraceIdWhenNoParentWasSupplied() {
    String traceId = TraceContext.resolveTraceId(null);

    assertThat(traceId).matches("[0-9a-f]{32}");
  }

  @Test
  void extractsAndValidatesW3cTraceId() {
    assertThat(
            TraceContext.resolveTraceId("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"))
        .isEqualTo("0123456789abcdef0123456789abcdef");
    assertThatThrownBy(
            () ->
                TraceContext.resolveTraceId(
                    "00-00000000000000000000000000000000-0123456789abcdef-01"))
        .isInstanceOf(GovernanceAuthorizationException.class)
        .hasMessage("Invalid traceparent");
  }
}
