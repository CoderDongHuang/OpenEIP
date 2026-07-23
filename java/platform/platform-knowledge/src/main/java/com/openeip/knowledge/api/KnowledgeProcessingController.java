package com.openeip.knowledge.api;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.knowledge.api.dto.KnowledgeProcessingResponse;
import com.openeip.knowledge.application.KnowledgeIngestionService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated user command for the bounded single-node ingestion flow. */
@RestController
@RequestMapping("/api/v1/knowledge/bases")
public class KnowledgeProcessingController {
  private final KnowledgeIngestionService service;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected service is application scoped.")
  public KnowledgeProcessingController(KnowledgeIngestionService service) {
    this.service = service;
  }

  @PostMapping("/{baseId}/documents/{documentId}/processing")
  public ApiEnvelope<KnowledgeProcessingResponse> process(
      @PathVariable("baseId") String baseId,
      @PathVariable("documentId") String documentId,
      Authentication authentication,
      HttpServletRequest request) {
    var result =
        service.process(authentication.getName(), isAdmin(authentication), baseId, documentId);
    return ApiEnvelope.success(
        KnowledgeProcessingResponse.from(result), RequestIdFilter.get(request));
  }

  @PostMapping("/{baseId}/documents/{documentId}/processing/retry")
  public ApiEnvelope<KnowledgeProcessingResponse> retry(
      @PathVariable("baseId") String baseId,
      @PathVariable("documentId") String documentId,
      Authentication authentication,
      HttpServletRequest request) {
    var result =
        service.retry(authentication.getName(), isAdmin(authentication), baseId, documentId);
    return ApiEnvelope.success(
        KnowledgeProcessingResponse.from(result), RequestIdFilter.get(request));
  }

  private static boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }
}
