package com.openeip.connector.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.connector.api.ConnectorDtos.CreateRequest;
import com.openeip.connector.api.ConnectorDtos.PageResponse;
import com.openeip.connector.api.ConnectorDtos.Response;
import com.openeip.connector.api.ConnectorDtos.StatusRequest;
import com.openeip.connector.api.ConnectorDtos.UpdateRequest;
import com.openeip.connector.application.ConnectorService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/connectors")
public class ConnectorController {
  private final ConnectorService service;
  private final ObjectMapper mapper;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected services are application scoped.")
  public ConnectorController(ConnectorService service, ObjectMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @PostMapping
  public ResponseEntity<ApiEnvelope<Response>> create(
      @Valid @RequestBody CreateRequest body, Authentication auth, HttpServletRequest request) {
    Response data =
        Response.from(
            service.create(
                auth.getName(), body.name(), body.type(), body.config(), body.credentialRef()),
            mapper);
    return ResponseEntity.status(201).body(ApiEnvelope.success(data, RequestIdFilter.get(request)));
  }

  @GetMapping
  public ApiEnvelope<PageResponse> list(
      @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication auth,
      HttpServletRequest request) {
    var result =
        service.list(auth.getName(), page, size).map(value -> Response.from(value, mapper));
    return ApiEnvelope.success(PageResponse.from(result), RequestIdFilter.get(request));
  }

  @GetMapping("/{id}")
  public ApiEnvelope<Response> get(
      @PathVariable("id") String id, Authentication auth, HttpServletRequest request) {
    return ApiEnvelope.success(
        Response.from(service.get(auth.getName(), id), mapper), RequestIdFilter.get(request));
  }

  @PatchMapping("/{id}")
  public ApiEnvelope<Response> update(
      @PathVariable("id") String id,
      @Valid @RequestBody UpdateRequest body,
      Authentication auth,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        Response.from(
            service.update(auth.getName(), id, body.name(), body.config(), body.credentialRef()),
            mapper),
        RequestIdFilter.get(request));
  }

  @PostMapping("/{id}/status")
  public ApiEnvelope<Response> status(
      @PathVariable("id") String id,
      @Valid @RequestBody StatusRequest body,
      Authentication auth,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        Response.from(service.setStatus(auth.getName(), id, body.status()), mapper),
        RequestIdFilter.get(request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable("id") String id, Authentication auth) {
    service.delete(auth.getName(), id);
    return ResponseEntity.noContent().build();
  }
}
