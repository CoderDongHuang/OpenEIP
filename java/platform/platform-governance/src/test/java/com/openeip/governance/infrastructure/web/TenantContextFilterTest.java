package com.openeip.governance.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openeip.common.web.RequestIdFilter;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.application.context.TenantContextResolver;
import com.openeip.governance.domain.context.TenantMembership;
import com.openeip.governance.shared.exception.GovernanceAuthorizationException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantContextFilterTest {
  private static final UUID PRINCIPAL_ID = UUID.randomUUID();

  @AfterEach
  void clearContext() {
    TenantContextHolder.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void bindsGovernanceContextForGovernanceRequestsAndClearsAfterChain() throws Exception {
    var filter = new TenantContextFilter(new TenantContextResolver(id -> membership()));
    var request = request("/api/v2/governance/tenants");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(PRINCIPAL_ID.toString(), null, List.of()));
    AtomicReference<Object> observed = new AtomicReference<>();

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (ignoredRequest, ignoredResponse) -> observed.set(TenantContextHolder.required()));

    assertThat(observed.get()).isNotNull();
    assertThat(TenantContextHolder.current()).isEmpty();
  }

  @Test
  void ignoresNonGovernanceRequests() throws Exception {
    var filter = new TenantContextFilter(new TenantContextResolver(id -> Optional.empty()));
    AtomicReference<Boolean> called = new AtomicReference<>(false);

    filter.doFilter(
        request("/api/v1/chat/sessions"),
        new MockHttpServletResponse(),
        (ignoredRequest, ignoredResponse) -> called.set(true));

    assertThat(called).hasValue(true);
    assertThat(TenantContextHolder.current()).isEmpty();
  }

  @Test
  void rejectsInvalidPrincipalAndTraceparentBeforeCallingMembershipPort() {
    var filter = new TenantContextFilter(new TenantContextResolver(id -> membership()));
    var request = request("/api/v2/governance/tenants");
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("not-a-uuid", null));

    assertThatThrownBy(
            () -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {}))
        .isInstanceOf(GovernanceAuthorizationException.class)
        .hasMessage("Authenticated principal is invalid");

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(PRINCIPAL_ID.toString(), null));
    request.addHeader("traceparent", "invalid");
    assertThatThrownBy(
            () -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {}))
        .isInstanceOf(GovernanceAuthorizationException.class)
        .hasMessage("Invalid traceparent");
  }

  private static MockHttpServletRequest request(String path) {
    var request = new MockHttpServletRequest("GET", path);
    request.setAttribute(RequestIdFilter.ATTRIBUTE, "request-1");
    return request;
  }

  private static Optional<TenantMembership> membership() {
    return Optional.of(
        new TenantMembership(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            PRINCIPAL_ID,
            Set.of("ROLE_OPERATOR"),
            "policy-1"));
  }
}
