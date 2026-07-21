package com.openeip.document.domain.repository;

import com.openeip.document.domain.entity.DocumentFile;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Owner-scoped persistence contract for document metadata. */
public interface DocumentFileRepository extends JpaRepository<DocumentFile, String> {

  Optional<DocumentFile> findByIdAndTenantIdAndOwnerIdAndDeletedAtIsNull(
      String id, String tenantId, String ownerId);

  Optional<DocumentFile> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);

  Page<DocumentFile> findAllByTenantIdAndOwnerIdAndDeletedAtIsNull(
      String tenantId, String ownerId, Pageable pageable);

  Page<DocumentFile> findAllByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);
}
