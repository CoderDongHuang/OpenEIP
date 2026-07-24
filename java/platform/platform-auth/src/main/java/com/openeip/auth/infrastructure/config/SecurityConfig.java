package com.openeip.auth.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.auth.application.service.AuthService;
import com.openeip.auth.application.service.JwtService;
import com.openeip.auth.infrastructure.security.AuthRateLimitFilter;
import com.openeip.auth.infrastructure.security.FixedWindowRateLimiter;
import com.openeip.auth.infrastructure.security.JsonAccessDeniedHandler;
import com.openeip.auth.infrastructure.security.JsonAuthenticationEntryPoint;
import com.openeip.auth.infrastructure.security.JwtAuthenticationFilter;
import com.openeip.auth.infrastructure.web.ApiErrorWriter;
import com.openeip.common.web.RequestIdFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Stateless Spring Security configuration for the Auth service. */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      RequestIdFilter requestIdFilter,
      AuthRateLimitFilter rateLimitFilter,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      JsonAuthenticationEntryPoint authenticationEntryPoint,
      JsonAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(
            requests ->
                requests
                    .dispatcherTypeMatchers(DispatcherType.ASYNC)
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/workflow-hooks/*",
                        "/actuator/health",
                        "/actuator/info")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/roles")
                    .hasAuthority("ROLE_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/roles")
                    .hasAuthority("ROLE_ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/auth/users/*/roles")
                    .hasAuthority("ROLE_ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, RequestIdFilter.class)
        .addFilterAfter(jwtAuthenticationFilter, AuthRateLimitFilter.class);
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public ApiErrorWriter apiErrorWriter(ObjectMapper objectMapper) {
    return new ApiErrorWriter(objectMapper);
  }

  @Bean
  public RequestIdFilter requestIdFilter() {
    return new RequestIdFilter();
  }

  @Bean
  public FixedWindowRateLimiter fixedWindowRateLimiter(
      @Value("${openeip.auth.rate-limit.requests:20}") int requests,
      @Value("${openeip.auth.rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${openeip.auth.rate-limit.max-keys:10000}") int maxKeys) {
    return new FixedWindowRateLimiter(requests, windowSeconds, maxKeys);
  }

  @Bean
  public AuthRateLimitFilter authRateLimitFilter(
      FixedWindowRateLimiter limiter, ApiErrorWriter errorWriter) {
    return new AuthRateLimitFilter(limiter, errorWriter);
  }

  @Bean
  public JwtAuthenticationFilter jwtAuthenticationFilter(
      JwtService jwtService, AuthService authService, ApiErrorWriter errorWriter) {
    return new JwtAuthenticationFilter(jwtService, authService, errorWriter);
  }

  @Bean
  public JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint(ApiErrorWriter errorWriter) {
    return new JsonAuthenticationEntryPoint(errorWriter);
  }

  @Bean
  public JsonAccessDeniedHandler jsonAccessDeniedHandler(ApiErrorWriter errorWriter) {
    return new JsonAccessDeniedHandler(errorWriter);
  }

  @Bean
  public FilterRegistrationBean<RequestIdFilter> requestIdRegistration(RequestIdFilter filter) {
    return disabledRegistration(filter);
  }

  @Bean
  public FilterRegistrationBean<AuthRateLimitFilter> rateLimitRegistration(
      AuthRateLimitFilter filter) {
    return disabledRegistration(filter);
  }

  @Bean
  public FilterRegistrationBean<JwtAuthenticationFilter> jwtRegistration(
      JwtAuthenticationFilter filter) {
    return disabledRegistration(filter);
  }

  private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabledRegistration(
      T filter) {
    FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
