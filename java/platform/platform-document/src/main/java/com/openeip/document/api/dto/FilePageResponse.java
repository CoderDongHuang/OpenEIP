package com.openeip.document.api.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable one-based page representation. */
public record FilePageResponse(
    List<FileResponse> items, int page, int pageSize, long total, int totalPages) {

  public FilePageResponse {
    items = List.copyOf(items);
  }

  @Override
  public List<FileResponse> items() {
    return List.copyOf(items);
  }

  public static FilePageResponse from(Page<FileResponse> result) {
    return new FilePageResponse(
        result.getContent(),
        result.getNumber() + 1,
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }
}
