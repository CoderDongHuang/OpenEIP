package com.openeip.document.api.dto;

import com.openeip.document.domain.entity.DocumentFile;
import java.time.Instant;

/** Public file metadata excluding owner and object-store internals. */
public record FileResponse(
    String id,
    String originalName,
    String contentType,
    long sizeBytes,
    String sha256,
    String status,
    Instant createdAt) {

  public static FileResponse from(DocumentFile file) {
    return new FileResponse(
        file.getId(),
        file.getOriginalName(),
        file.getContentType(),
        file.getSizeBytes(),
        file.getSha256(),
        file.getStatus(),
        file.getCreatedAt());
  }
}
