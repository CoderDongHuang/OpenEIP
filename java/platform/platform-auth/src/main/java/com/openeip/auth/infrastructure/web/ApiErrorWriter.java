package com.openeip.auth.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.auth.api.dto.response.ApiEnvelope;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

/** Writes the same JSON error contract from filters and Spring Security handlers. */
public class ApiErrorWriter {

  private final ObjectMapper objectMapper;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "ObjectMapper is an application-scoped Spring dependency.")
  public ApiErrorWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(), ApiEnvelope.error(code, message, RequestIdFilter.get(request)));
  }
}
