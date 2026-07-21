package com.openeip.knowledge.api;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.knowledge.api.dto.AttachDocumentRequest;
import com.openeip.knowledge.api.dto.KnowledgeBasePageResponse;
import com.openeip.knowledge.api.dto.KnowledgeBaseRequest;
import com.openeip.knowledge.api.dto.KnowledgeBaseResponse;
import com.openeip.knowledge.api.dto.KnowledgeDocumentResponse;
import com.openeip.knowledge.application.KnowledgeBaseService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/knowledge/bases")
public class KnowledgeBaseController {
  private final KnowledgeBaseService service;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected service is application scoped.")
  public KnowledgeBaseController(KnowledgeBaseService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiEnvelope<KnowledgeBaseResponse>> create(
      @Valid @RequestBody KnowledgeBaseRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var data =
        KnowledgeBaseResponse.from(
            service.create(authentication.getName(), body.name(), body.description()));
    return ResponseEntity.status(201).body(ApiEnvelope.success(data, RequestIdFilter.get(request)));
  }

  @GetMapping
  public ApiEnvelope<KnowledgeBasePageResponse> list(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
      Authentication authentication,
      HttpServletRequest request) {
    var data =
        service.list(authentication.getName(), page, pageSize).map(KnowledgeBaseResponse::from);
    return ApiEnvelope.success(KnowledgeBasePageResponse.from(data), RequestIdFilter.get(request));
  }

  @GetMapping("/{baseId}")
  public ApiEnvelope<KnowledgeBaseResponse> get(
      @PathVariable String baseId, Authentication authentication, HttpServletRequest request) {
    return ApiEnvelope.success(
        KnowledgeBaseResponse.from(service.get(authentication.getName(), baseId)),
        RequestIdFilter.get(request));
  }

  @PatchMapping("/{baseId}")
  public ApiEnvelope<KnowledgeBaseResponse> update(
      @PathVariable String baseId,
      @Valid @RequestBody KnowledgeBaseRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        KnowledgeBaseResponse.from(
            service.update(authentication.getName(), baseId, body.name(), body.description())),
        RequestIdFilter.get(request));
  }

  @DeleteMapping("/{baseId}")
  public ResponseEntity<Void> delete(@PathVariable String baseId, Authentication authentication) {
    service.delete(authentication.getName(), baseId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{baseId}/documents")
  public ResponseEntity<ApiEnvelope<KnowledgeDocumentResponse>> attach(
      @PathVariable String baseId,
      @Valid @RequestBody AttachDocumentRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var document =
        service.attach(
            authentication.getName(), isAdmin(authentication), baseId, body.documentId());
    return ResponseEntity.status(201)
        .body(
            ApiEnvelope.success(
                KnowledgeDocumentResponse.from(document), RequestIdFilter.get(request)));
  }

  @GetMapping("/{baseId}/documents")
  public ApiEnvelope<List<KnowledgeDocumentResponse>> documents(
      @PathVariable String baseId, Authentication authentication, HttpServletRequest request) {
    var data =
        service.listDocuments(authentication.getName(), baseId).stream()
            .map(KnowledgeDocumentResponse::from)
            .toList();
    return ApiEnvelope.success(data, RequestIdFilter.get(request));
  }

  @GetMapping("/{baseId}/documents/{documentId}")
  public ApiEnvelope<KnowledgeDocumentResponse> document(
      @PathVariable String baseId,
      @PathVariable String documentId,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        KnowledgeDocumentResponse.from(
            service.getDocument(authentication.getName(), baseId, documentId)),
        RequestIdFilter.get(request));
  }

  @DeleteMapping("/{baseId}/documents/{documentId}")
  public ResponseEntity<Void> detach(
      @PathVariable String baseId, @PathVariable String documentId, Authentication authentication) {
    service.detach(authentication.getName(), baseId, documentId);
    return ResponseEntity.noContent().build();
  }

  private static boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }
}
