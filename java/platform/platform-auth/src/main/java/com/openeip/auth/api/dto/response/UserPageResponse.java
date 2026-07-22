package com.openeip.auth.api.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable one-based administrative user page. */
public record UserPageResponse(
    List<UserAdminResponse> items, int page, int pageSize, long total, int totalPages) {
  public UserPageResponse {
    items = List.copyOf(items);
  }

  public static UserPageResponse from(Page<UserAdminResponse> users) {
    return new UserPageResponse(
        users.getContent(),
        users.getNumber() + 1,
        users.getSize(),
        users.getTotalElements(),
        users.getTotalPages());
  }
}
