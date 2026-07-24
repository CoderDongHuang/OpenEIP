package com.openeip.knowledge.api;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.knowledge.api.dto.KnowledgeSearchRequest;
import com.openeip.knowledge.application.KnowledgeSearchService;
import com.openeip.knowledge.infrastructure.ingestion.KnowledgeIngestionGateway.SearchResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public membership-authorized knowledge retrieval endpoint. */
@RestController
@RequestMapping("/api/v1/knowledge/bases")
public class KnowledgeSearchController {
  private final KnowledgeSearchService service;

  public KnowledgeSearchController(KnowledgeSearchService service) {
    this.service = service;
  }

  @PostMapping("/{baseId}/search")
  public ApiEnvelope<SearchResult> search(
      @PathVariable("baseId") String baseId,
      @RequestBody KnowledgeSearchRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    SearchResult result =
        service.search(
            authentication.getName(),
            baseId,
            body.query(),
            body.mode(),
            body.topK() == null ? 10 : body.topK());
    return ApiEnvelope.success(result, RequestIdFilter.get(request));
  }
}
