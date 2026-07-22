package com.openeip.document.api.controller;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.document.api.dto.FilePageResponse;
import com.openeip.document.api.dto.FileResponse;
import com.openeip.document.application.service.DocumentFileService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Authenticated raw-file upload and retrieval API. */
@Validated
@RestController
@RequestMapping("/api/v1/documents/files")
public class DocumentFileController {

  private final DocumentFileService service;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The injected service is an application-scoped Spring singleton.")
  public DocumentFileController(DocumentFileService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiEnvelope<FileResponse>> upload(
      @RequestPart("file") MultipartFile file,
      Authentication authentication,
      HttpServletRequest request) {
    FileResponse result = FileResponse.from(service.upload(authentication.getName(), file));
    return ResponseEntity.status(201)
        .body(ApiEnvelope.success(result, RequestIdFilter.get(request)));
  }

  @GetMapping
  public ApiEnvelope<FilePageResponse> list(
      @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
      @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize,
      Authentication authentication,
      HttpServletRequest request) {
    var result =
        service
            .list(authentication.getName(), isAdmin(authentication), page, pageSize)
            .map(FileResponse::from);
    return ApiEnvelope.success(FilePageResponse.from(result), RequestIdFilter.get(request));
  }

  @GetMapping("/{fileId}")
  public ApiEnvelope<FileResponse> get(
      @PathVariable("fileId") String fileId,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        FileResponse.from(service.get(authentication.getName(), isAdmin(authentication), fileId)),
        RequestIdFilter.get(request));
  }

  @GetMapping("/{fileId}/content")
  public ResponseEntity<InputStreamResource> download(
      @PathVariable("fileId") String fileId, Authentication authentication) {
    DocumentFileService.Download download =
        service.open(authentication.getName(), isAdmin(authentication), fileId);
    ContentDisposition disposition =
        ContentDisposition.attachment()
            .filename(download.metadata().getOriginalName(), StandardCharsets.UTF_8)
            .build();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(download.metadata().getContentType()))
        .contentLength(download.metadata().getSizeBytes())
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(new InputStreamResource(download.content()));
  }

  @DeleteMapping("/{fileId}")
  public ResponseEntity<Void> delete(
      @PathVariable("fileId") String fileId, Authentication authentication) {
    service.delete(authentication.getName(), isAdmin(authentication), fileId);
    return ResponseEntity.noContent().build();
  }

  private static boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }
}
