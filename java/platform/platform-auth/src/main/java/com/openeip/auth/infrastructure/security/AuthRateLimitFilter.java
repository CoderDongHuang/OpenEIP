package com.openeip.auth.infrastructure.security;

import com.openeip.auth.infrastructure.web.ApiErrorWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limits public credential endpoints by container-observed remote address and path. */
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private static final Set<String> LIMITED_PATHS =
      Set.of("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh");

  private final FixedWindowRateLimiter limiter;
  private final ApiErrorWriter errorWriter;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Limiter and writer are application-scoped Spring dependencies.")
  public AuthRateLimitFilter(FixedWindowRateLimiter limiter, ApiErrorWriter errorWriter) {
    this.limiter = limiter;
    this.errorWriter = errorWriter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String key = request.getRemoteAddr() + ':' + request.getRequestURI();
    FixedWindowRateLimiter.Decision decision = limiter.acquire(key);
    if (!decision.allowed()) {
      response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
      errorWriter.write(request, response, 429, "AUTH-R-001", "Too many requests");
      return;
    }
    chain.doFilter(request, response);
  }
}
