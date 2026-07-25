package com.openeip.connector.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openeip.connector.application.WebhookInboundService;
import com.openeip.connector.application.WebhookInboundService.ReceiveResult;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class WebhookInboundControllerTest {
  @Mock private WebhookInboundService service;
  private WebhookInboundController controller;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    controller = new WebhookInboundController(service);
    request = new MockHttpServletRequest();
  }

  @Test
  void normalizesHeadersAndReturnsAccepted() {
    Map<String, String> headers =
        Map.of("X-OpenEIP-Signature", "v1=sig", "X-OpenEIP-Timestamp", "1", "X-Event-Id", "evt");
    when(service.receive("connector", "evt", "1", "v1=sig", headers, "{}"))
        .thenReturn(new ReceiveResult("delivery", false));

    var response = controller.receive("connector", headers, "{}", request);

    assertThat(response.getStatusCode().value()).isEqualTo(202);
    assertThat(response.getBody().data().deliveryId()).isEqualTo("delivery");
    verify(service).receive("connector", "evt", "1", "v1=sig", headers, "{}");
  }
}
