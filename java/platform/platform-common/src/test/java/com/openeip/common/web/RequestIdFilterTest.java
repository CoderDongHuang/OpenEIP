package com.openeip.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void assignsServerGeneratedRequestId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.HEADER, "attacker-controlled");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(RequestIdFilter.get(request))
        .isNotEqualTo("attacker-controlled")
        .isEqualTo(response.getHeader(RequestIdFilter.HEADER));
  }

  @Test
  void returnsUnknownWithoutFilter() {
    assertThat(RequestIdFilter.get(new MockHttpServletRequest())).isEqualTo("unknown");
  }
}
