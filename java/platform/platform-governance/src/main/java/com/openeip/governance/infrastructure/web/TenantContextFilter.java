package com.openeip.governance.infrastructure.web;

import com.openeip.common.web.RequestIdFilter;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.application.context.TenantContextResolver;
import com.openeip.governance.application.context.TraceContext;
import com.openeip.governance.shared.exception.GovernanceAuthorizationException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Establishes a server-derived Governance context at the HTTP boundary. */
public class TenantContextFilter extends OncePerRequestFilter {
  private static final String GOVERNANCE_PATH = "/api/v2/governance/";
  private static final String TRACEPARENT_HEADER = "traceparent";

  private final TenantContextResolver resolver;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The resolver is an application-scoped collaborator.")
  public TenantContextFilter(TenantContextResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(GOVERNANCE_PATH);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    UUID principalId = principalId(authentication);
    String traceId = TraceContext.resolveTraceId(request.getHeader(TRACEPARENT_HEADER));
    var context = resolver.resolve(principalId, RequestIdFilter.get(request), traceId);
    TenantContextHolder.bind(context);
    try {
      chain.doFilter(request, response);
    } finally {
      TenantContextHolder.clear();
    }
  }

  private static UUID principalId(Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      throw GovernanceAuthorizationException.invalidContext("Authenticated principal is required");
    }
    try {
      return UUID.fromString(authentication.getName());
    } catch (IllegalArgumentException exception) {
      throw GovernanceAuthorizationException.invalidContext("Authenticated principal is invalid");
    }
  }
}
