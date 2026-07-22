package com.openeip.knowledge.api.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.springframework.data.domain.Page;

@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "The constructor stores an immutable copy and the accessor returns that copy.")
public record KnowledgeBasePageResponse(
    List<KnowledgeBaseResponse> items, int page, int pageSize, long total, int totalPages) {
  public KnowledgeBasePageResponse {
    items = List.copyOf(items);
  }

  public static KnowledgeBasePageResponse from(Page<KnowledgeBaseResponse> page) {
    return new KnowledgeBasePageResponse(
        page.getContent(),
        page.getNumber() + 1,
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
