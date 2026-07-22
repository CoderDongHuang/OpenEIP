package com.openeip.agent.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.openeip.agent.shared.exception.AgentException;
import com.openeip.common.web.RequestIdFilter;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AgentExceptionHandlerTest {
  private static final String REQUEST = "11111111-1111-4111-8111-111111111111";
  private AgentExceptionHandler handler;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    handler = new AgentExceptionHandler();
    request = new MockHttpServletRequest();
    request.setAttribute(RequestIdFilter.ATTRIBUTE, REQUEST);
  }

  @Test
  void mapsExpectedValidationAndUnexpectedErrorsWithoutDetails() {
    var expected = handler.handle(AgentException.notFound(), request);
    assertThat(expected.getStatusCode().value()).isEqualTo(404);
    assertThat(expected.getBody()).isNotNull();
    assertThat(expected.getBody().code()).isEqualTo("AGENT-N-001");
    assertThat(expected.getBody().requestId()).isEqualTo(REQUEST);

    var invalid = handler.invalid(new ConstraintViolationException(Set.of()), request);
    assertThat(invalid.getStatusCode().value()).isEqualTo(400);
    assertThat(invalid.getBody()).isNotNull();
    assertThat(invalid.getBody().code()).isEqualTo("AGENT-V-001");

    var unexpected = handler.unexpected(new RuntimeException("prompt secret traceback"), request);
    assertThat(unexpected.getStatusCode().value()).isEqualTo(500);
    assertThat(unexpected.getBody()).isNotNull();
    assertThat(unexpected.getBody().code()).isEqualTo("AGENT-S-002");
    assertThat(unexpected.getBody().message()).doesNotContain("prompt", "secret", "traceback");
  }
}
