package com.openeip.knowledge.api.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record KnowledgeBasePageResponse(
    List<KnowledgeBaseResponse> items, int page, int pageSize, long total, int totalPages) {
  public static KnowledgeBasePageResponse from(Page<KnowledgeBaseResponse> page) {
    return new KnowledgeBasePageResponse(
        page.getContent(),
        page.getNumber() + 1,
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
