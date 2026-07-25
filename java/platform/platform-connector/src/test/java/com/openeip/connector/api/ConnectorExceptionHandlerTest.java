package com.openeip.connector.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.openeip.connector.shared.ConnectorException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ConnectorExceptionHandlerTest {
  @Test
  void mapsDomainErrorsToDocumentedEnvelope() {
    var response =
        new ConnectorExceptionHandler()
            .connector(ConnectorException.invalid("bad"), new MockHttpServletRequest());
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody().code()).isEqualTo("CONN-V-001");
  }
}
