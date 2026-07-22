package com.openeip.document.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openeip.document.domain.entity.DocumentFile;
import com.openeip.document.domain.repository.DocumentFileRepository;
import com.openeip.document.domain.storage.ObjectStorage;
import com.openeip.document.shared.exception.DocumentException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentFileServiceTest {

  private static final String OWNER = "11111111-1111-1111-1111-111111111111";
  private static final String FILE_ID = "22222222-2222-2222-2222-222222222222";

  @Mock DocumentFileRepository repository;
  @Mock ObjectStorage storage;
  private DocumentFileService service;

  @BeforeEach
  void setUp() {
    service = new DocumentFileService(repository, storage, new FileUploadPolicy(), 10);
  }

  @Test
  void uploadsObjectThenFlushesMetadata() throws Exception {
    when(storage.put(any(), any(), eq(10L)))
        .thenReturn(new ObjectStorage.StoredObject(5, "a".repeat(64)));
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    DocumentFile result = service.upload(OWNER, textFile("hello"));

    assertThat(result.getOwnerId()).isEqualTo(OWNER);
    assertThat(result.getTenantId()).isEqualTo(DocumentFileService.MVP_TENANT);
    assertThat(result.getOriginalName()).isEqualTo("notes.txt");
    assertThat(result.getContentType()).isEqualTo("text/plain");
    assertThat(result.getSizeBytes()).isEqualTo(5);
    assertThat(result.getSha256()).isEqualTo("a".repeat(64));
    assertThat(result.getStatus()).isEqualTo("READY");
    assertThat(result.getCreatedAt()).isNotNull();
    assertThat(result.getUpdatedAt()).isNotNull();
    assertThat(result.getDeletedAt()).isNull();
    assertThat(result.getObjectKey()).endsWith(result.getId());
  }

  @Test
  void mapsContentReadAndStorageFailures() throws Exception {
    MockMultipartFile unreadable =
        new MockMultipartFile(
            "file", "notes.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8)) {
          @Override
          public java.io.InputStream getInputStream() throws IOException {
            throw new IOException("failed");
          }
        };
    assertThatThrownBy(() -> service.upload(OWNER, unreadable))
        .isInstanceOf(DocumentException.class)
        .extracting("errorCode")
        .isEqualTo("DOC-S-001");

    when(storage.put(any(), any(), eq(10L))).thenThrow(DocumentException.tooLarge(10));
    assertThatThrownBy(() -> service.upload(OWNER, textFile("hello")))
        .isInstanceOf(DocumentException.class)
        .extracting("errorCode")
        .isEqualTo("DOC-V-003");
  }

  @Test
  void compensatesObjectWhenMetadataFlushFails() throws Exception {
    when(storage.put(any(), any(), eq(10L)))
        .thenReturn(new ObjectStorage.StoredObject(5, "a".repeat(64)));
    when(repository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(() -> service.upload(OWNER, textFile("hello")))
        .isInstanceOf(DataIntegrityViolationException.class);
    verify(storage).delete(any());
  }

  @Test
  void preservesOriginalFailureWhenCompensationAlsoFails() throws Exception {
    when(storage.put(any(), any(), eq(10L)))
        .thenReturn(new ObjectStorage.StoredObject(5, "a".repeat(64)));
    DataIntegrityViolationException failure = new DataIntegrityViolationException("duplicate");
    when(repository.saveAndFlush(any())).thenThrow(failure);
    doThrow(new IOException("cleanup failed")).when(storage).delete(any());

    assertThatThrownBy(() -> service.upload(OWNER, textFile("hello")))
        .isSameAs(failure)
        .satisfies(exception -> assertThat(exception.getSuppressed()).hasSize(1));
  }

  @Test
  void resolvesOwnerAndAdminAccessWithoutPostFiltering() {
    DocumentFile file = file();
    when(repository.findByIdAndTenantIdAndOwnerIdAndDeletedAtIsNull(
            FILE_ID, DocumentFileService.MVP_TENANT, OWNER))
        .thenReturn(Optional.of(file));
    when(repository.findByIdAndTenantIdAndDeletedAtIsNull(FILE_ID, DocumentFileService.MVP_TENANT))
        .thenReturn(Optional.of(file));

    assertThat(service.get(OWNER, false, FILE_ID)).isSameAs(file);
    assertThat(service.get("admin", true, FILE_ID)).isSameAs(file);
    verify(repository)
        .findByIdAndTenantIdAndOwnerIdAndDeletedAtIsNull(
            FILE_ID, DocumentFileService.MVP_TENANT, OWNER);
  }

  @Test
  void hidesInaccessibleFile() {
    when(repository.findByIdAndTenantIdAndOwnerIdAndDeletedAtIsNull(
            FILE_ID, DocumentFileService.MVP_TENANT, OWNER))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(OWNER, false, FILE_ID))
        .isInstanceOf(DocumentException.class)
        .extracting("errorCode")
        .isEqualTo("DOC-E-001");
  }

  @Test
  void listsOwnerAndAdminPages() {
    var page = new PageImpl<>(java.util.List.of(file()));
    when(repository.findAllByTenantIdAndOwnerIdAndDeletedAtIsNull(
            eq(DocumentFileService.MVP_TENANT), eq(OWNER), any(Pageable.class)))
        .thenReturn(page);
    when(repository.findAllByTenantIdAndDeletedAtIsNull(
            eq(DocumentFileService.MVP_TENANT), any(Pageable.class)))
        .thenReturn(page);

    assertThat(service.list(OWNER, false, 1, 20)).hasSize(1);
    assertThat(service.list("admin", true, 1, 20)).hasSize(1);
  }

  @Test
  void opensAndDeletesAccessibleObject() throws Exception {
    DocumentFile file = file();
    when(repository.findByIdAndTenantIdAndOwnerIdAndDeletedAtIsNull(
            FILE_ID, DocumentFileService.MVP_TENANT, OWNER))
        .thenReturn(Optional.of(file));
    when(storage.open(file.getObjectKey()))
        .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

    try (var content = service.open(OWNER, false, FILE_ID).content()) {
      assertThat(content.readAllBytes()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    }
    service.delete(OWNER, false, FILE_ID);
    verify(storage).delete(file.getObjectKey());
    verify(repository).delete(file);
  }

  @Test
  void missingDeleteIsIdempotentAndStorageErrorsAreMapped() throws Exception {
    when(repository.findByIdAndTenantIdAndOwnerIdAndDeletedAtIsNull(
            FILE_ID, DocumentFileService.MVP_TENANT, OWNER))
        .thenReturn(Optional.empty());
    service.delete(OWNER, false, FILE_ID);
    verify(storage, never()).delete(any());

    DocumentFile file = file();
    when(repository.findByIdAndTenantIdAndOwnerIdAndDeletedAtIsNull(
            FILE_ID, DocumentFileService.MVP_TENANT, OWNER))
        .thenReturn(Optional.of(file));
    when(storage.open(file.getObjectKey())).thenThrow(new IOException("offline"));
    assertThatThrownBy(() -> service.open(OWNER, false, FILE_ID))
        .isInstanceOf(DocumentException.class)
        .extracting("errorCode")
        .isEqualTo("DOC-S-001");
    doThrow(new IOException("offline")).when(storage).delete(file.getObjectKey());
    assertThatThrownBy(() -> service.delete(OWNER, false, FILE_ID))
        .isInstanceOf(DocumentException.class)
        .extracting("errorCode")
        .isEqualTo("DOC-S-001");
  }

  @Test
  void rejectsInvalidConfiguredLimit() {
    assertThatThrownBy(
            () -> new DocumentFileService(repository, storage, new FileUploadPolicy(), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static MockMultipartFile textFile(String content) {
    return new MockMultipartFile(
        "file",
        "notes.txt",
        "text/plain",
        content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static DocumentFile file() {
    return new DocumentFile(
        FILE_ID,
        DocumentFileService.MVP_TENANT,
        OWNER,
        "notes.txt",
        "22/" + FILE_ID,
        "text/plain",
        5,
        "a".repeat(64),
        Instant.parse("2026-07-22T00:00:00Z"));
  }
}
