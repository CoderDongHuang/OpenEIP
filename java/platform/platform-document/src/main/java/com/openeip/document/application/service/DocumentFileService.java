package com.openeip.document.application.service;

import com.openeip.document.domain.entity.DocumentFile;
import com.openeip.document.domain.repository.DocumentFileRepository;
import com.openeip.document.domain.storage.ObjectStorage;
import com.openeip.document.shared.exception.DocumentException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Coordinates owner-scoped metadata transactions with non-transactional object storage. */
@Service
public class DocumentFileService {

  public static final String MVP_TENANT = "default";
  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentFileService.class);
  private final DocumentFileRepository repository;
  private final ObjectStorage storage;
  private final FileUploadPolicy policy;
  private final long maxSizeBytes;

  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP2", "CT_CONSTRUCTOR_THROW"},
      justification = "Spring collaborators are application scoped; invalid limits must fail fast.")
  public DocumentFileService(
      DocumentFileRepository repository,
      ObjectStorage storage,
      FileUploadPolicy policy,
      @Value("${openeip.document.max-size-bytes:10485760}") long maxSizeBytes) {
    if (maxSizeBytes <= 0) {
      throw new IllegalArgumentException("Document max size must be positive");
    }
    this.repository = repository;
    this.storage = storage;
    this.policy = policy;
    this.maxSizeBytes = maxSizeBytes;
  }

  @Transactional
  public DocumentFile upload(String ownerId, MultipartFile multipartFile) {
    FileUploadPolicy.UploadDescriptor descriptor =
        policy.validate(
            multipartFile.getOriginalFilename(),
            multipartFile.getContentType(),
            multipartFile.getSize(),
            maxSizeBytes);
    String id = UUID.randomUUID().toString();
    String objectKey = id.substring(0, 2) + "/" + id;
    ObjectStorage.StoredObject stored;
    try (InputStream content = multipartFile.getInputStream()) {
      stored = storage.put(objectKey, content, maxSizeBytes);
    } catch (DocumentException exception) {
      throw exception;
    } catch (IOException exception) {
      throw DocumentException.storageUnavailable();
    }

    DocumentFile entity =
        new DocumentFile(
            id,
            MVP_TENANT,
            ownerId,
            descriptor.originalName(),
            objectKey,
            descriptor.contentType(),
            stored.sizeBytes(),
            stored.sha256(),
            Instant.now());
    try {
      return repository.saveAndFlush(entity);
    } catch (RuntimeException exception) {
      compensate(objectKey, id, exception);
      throw exception;
    }
  }

  @Transactional(readOnly = true)
  public DocumentFile get(String ownerId, boolean admin, String id) {
    return accessible(ownerId, admin, id);
  }

  @Transactional(readOnly = true)
  public Page<DocumentFile> list(String ownerId, boolean admin, int page, int pageSize) {
    PageRequest pageable =
        PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    return admin
        ? repository.findAllByTenantIdAndDeletedAtIsNull(MVP_TENANT, pageable)
        : repository.findAllByTenantIdAndOwnerIdAndDeletedAtIsNull(MVP_TENANT, ownerId, pageable);
  }

  @Transactional(readOnly = true)
  public Download open(String ownerId, boolean admin, String id) {
    DocumentFile file = accessible(ownerId, admin, id);
    try {
      return new Download(file, storage.open(file.getObjectKey()));
    } catch (IOException exception) {
      throw DocumentException.storageUnavailable();
    }
  }

  @Transactional
  public void delete(String ownerId, boolean admin, String id) {
    DocumentFile file = accessibleOrNull(ownerId, admin, id);
    if (file == null) {
      return;
    }
    try {
      storage.delete(file.getObjectKey());
    } catch (IOException exception) {
      throw DocumentException.storageUnavailable();
    }
    repository.delete(file);
  }

  private DocumentFile accessible(String ownerId, boolean admin, String id) {
    DocumentFile file = accessibleOrNull(ownerId, admin, id);
    if (file == null) {
      throw DocumentException.notFound();
    }
    return file;
  }

  private DocumentFile accessibleOrNull(String ownerId, boolean admin, String id) {
    return admin
        ? repository.findByIdAndTenantIdAndDeletedAtIsNull(id, MVP_TENANT).orElse(null)
        : repository
            .findByIdAndTenantIdAndOwnerIdAndDeletedAtIsNull(id, MVP_TENANT, ownerId)
            .orElse(null);
  }

  private void compensate(String objectKey, String fileId, RuntimeException original) {
    try {
      storage.delete(objectKey);
    } catch (IOException cleanupFailure) {
      original.addSuppressed(cleanupFailure);
      LOGGER.error("File metadata failed and object compensation failed fileId={}", fileId);
    }
  }

  public record Download(DocumentFile metadata, InputStream content) {}
}
