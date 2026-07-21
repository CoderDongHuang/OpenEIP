package com.openeip.auth.infrastructure.security;

import com.openeip.auth.infrastructure.web.ApiErrorWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Returns the documented JSON envelope for authorization failures. */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

  private final ApiErrorWriter errorWriter;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Writer is an application-scoped Spring dependency.")
  public JsonAccessDeniedHandler(ApiErrorWriter errorWriter) {
    this.errorWriter = errorWriter;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    errorWriter.write(request, response, 403, "AUTH-P-001", "Insufficient permission");
  }
}
