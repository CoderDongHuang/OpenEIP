package com.openeip.auth.infrastructure.security;

import com.openeip.auth.infrastructure.web.ApiErrorWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** Returns the documented JSON envelope for missing authentication. */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ApiErrorWriter errorWriter;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Writer is an application-scoped Spring dependency.")
  public JsonAuthenticationEntryPoint(ApiErrorWriter errorWriter) {
    this.errorWriter = errorWriter;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authenticationException)
      throws IOException, ServletException {
    errorWriter.write(request, response, 401, "AUTH-E-003", "Authentication is required");
  }
}
