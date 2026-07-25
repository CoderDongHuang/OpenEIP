package com.openeip.connector.api;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.connector.api.ConnectorRuntimeDtos.ReadRequest;
import com.openeip.connector.api.ConnectorRuntimeDtos.WriteRequest;
import com.openeip.connector.application.ConnectorRuntimeService;
import com.openeip.connector.application.ConnectorRuntimeService.CatalogEntry;
import com.openeip.connector.spi.ConnectionTestResult;
import com.openeip.connector.spi.DataReader.ReadResult;
import com.openeip.connector.spi.DataWriter.WriteResult;
import com.openeip.connector.spi.MetadataSchema;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/connectors")
public class ConnectorRuntimeController {
  private final ConnectorRuntimeService service;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected service is application scoped.")
  public ConnectorRuntimeController(ConnectorRuntimeService service) {
    this.service = service;
  }

  @GetMapping("/catalog")
  public ApiEnvelope<List<CatalogEntry>> catalog(HttpServletRequest request) {
    return ApiEnvelope.success(service.catalog(), RequestIdFilter.get(request));
  }

  @PostMapping("/{id}/test")
  public ApiEnvelope<ConnectionTestResult> test(
      @PathVariable("id") String id,
      @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        service.test(authentication.getName(), id, correlationId), RequestIdFilter.get(request));
  }

  @GetMapping("/{id}/metadata")
  public ApiEnvelope<MetadataSchema> metadata(
      @PathVariable("id") String id,
      @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        service.metadata(authentication.getName(), id, correlationId),
        RequestIdFilter.get(request));
  }

  @PostMapping("/{id}/read")
  public ApiEnvelope<ReadResult> read(
      @PathVariable("id") String id,
      @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
      @Valid @RequestBody ReadRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        service.read(
            authentication.getName(),
            id,
            correlationId,
            body.resource(),
            body.query(),
            body.limit()),
        RequestIdFilter.get(request));
  }

  @PostMapping("/{id}/write")
  public ApiEnvelope<WriteResult> write(
      @PathVariable("id") String id,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
      @Valid @RequestBody WriteRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        service.write(
            authentication.getName(),
            id,
            correlationId,
            idempotencyKey,
            body.resource(),
            body.operation(),
            body.data()),
        RequestIdFilter.get(request));
  }
}
