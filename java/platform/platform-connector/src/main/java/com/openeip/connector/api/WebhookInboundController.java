package com.openeip.connector.api;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.connector.application.WebhookInboundService;
import com.openeip.connector.application.WebhookInboundService.ReceiveResult;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/connector-hooks")
public class WebhookInboundController {
  private final WebhookInboundService service;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected service is application scoped.")
  public WebhookInboundController(WebhookInboundService service) {
    this.service = service;
  }

  @PostMapping("/{connectorId}")
  public ResponseEntity<ApiEnvelope<ReceiveResult>> receive(
      @PathVariable("connectorId") String connectorId,
      @RequestHeader Map<String, String> headers,
      @RequestBody String payload,
      HttpServletRequest request) {
    String signature = header(headers, "x-openeip-signature");
    String timestamp = header(headers, "x-openeip-timestamp");
    String eventId = header(headers, "x-event-id");
    ReceiveResult result =
        service.receive(connectorId, eventId, timestamp, signature, headers, payload);
    return ResponseEntity.accepted()
        .body(ApiEnvelope.success(result, RequestIdFilter.get(request)));
  }

  private static String header(Map<String, String> headers, String expected) {
    return headers.entrySet().stream()
        .filter(entry -> expected.equals(entry.getKey().toLowerCase(Locale.ROOT)))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }
}
