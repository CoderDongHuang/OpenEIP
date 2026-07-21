package com.openeip.auth.infrastructure.security;

import com.openeip.auth.application.service.AuthService;
import com.openeip.auth.application.service.JwtService;
import com.openeip.auth.domain.entity.Permission;
import com.openeip.auth.domain.entity.Role;
import com.openeip.auth.domain.entity.User;
import com.openeip.auth.infrastructure.web.ApiErrorWriter;
import com.openeip.auth.shared.exception.AuthException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Verifies access tokens and resolves current authorities from the database. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final AuthService authService;
  private final ApiErrorWriter errorWriter;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Collaborators are application-scoped Spring dependencies.")
  public JwtAuthenticationFilter(
      JwtService jwtService, AuthService authService, ApiErrorWriter errorWriter) {
    this.jwtService = jwtService;
    this.authService = authService;
    this.errorWriter = errorWriter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = extractToken(request);
    if (token == null) {
      chain.doFilter(request, response);
      return;
    }

    try {
      Claims claims = jwtService.parseAccessToken(token);
      User user = authService.getActiveUser(claims.getSubject());
      List<SimpleGrantedAuthority> authorities =
          Stream.concat(
                  user.getRoles().stream().map(Role::getName),
                  user.getRoles().stream()
                      .flatMap(role -> role.getPermissions().stream())
                      .map(Permission::getCode))
              .distinct()
              .map(SimpleGrantedAuthority::new)
              .toList();
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(user.getId(), null, authorities);
      authentication.setDetails(user.getUsername());
      SecurityContextHolder.getContext().setAuthentication(authentication);
      chain.doFilter(request, response);
    } catch (AuthException exception) {
      SecurityContextHolder.clearContext();
      errorWriter.write(
          request,
          response,
          exception.getHttpStatus(),
          exception.getErrorCode(),
          exception.getMessage());
    }
  }

  private String extractToken(HttpServletRequest request) {
    String bearer = request.getHeader("Authorization");
    if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
      String token = bearer.substring(7);
      return StringUtils.hasText(token) ? token : null;
    }
    return null;
  }
}
