package com.openeip.knowledge.application;

import com.openeip.document.application.service.DocumentFileService;
import com.openeip.document.domain.entity.DocumentFile;
import com.openeip.knowledge.domain.ProcessingStatus;
import com.openeip.knowledge.domain.entity.KnowledgeDocument;
import com.openeip.knowledge.infrastructure.ingestion.KnowledgeIngestionGateway;
import com.openeip.knowledge.infrastructure.ingestion.KnowledgeIngestionGateway.ParsedDocument;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import com.openeip.knowledge.shared.exception.KnowledgeIngestionException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Authorizes and coordinates one bounded stored-file to searchable-vector operation. */
@Service
public class KnowledgeIngestionService {
  private final KnowledgeBaseService bases;
  private final KnowledgeEventService events;
  private final Optional<DocumentFileService> files;
  private final KnowledgeIngestionGateway gateway;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected collaborators are application-scoped Spring services.")
  public KnowledgeIngestionService(
      KnowledgeBaseService bases,
      KnowledgeEventService events,
      Optional<DocumentFileService> files,
      KnowledgeIngestionGateway gateway) {
    this.bases = bases;
    this.events = events;
    this.files = files;
    this.gateway = gateway;
  }

  public IngestionResult process(
      String userId, boolean administrator, String knowledgeBaseId, String documentId) {
    KnowledgeDocument document = bases.getDocument(userId, knowledgeBaseId, documentId);
    if (document.getStatus() == ProcessingStatus.READY) {
      return new IngestionResult(document, "ready", 0, 0);
    }
    if (document.getStatus() == ProcessingStatus.FAILED) {
      throw KnowledgeException.conflict("Failed document must be detached before retry");
    }

    try {
      DocumentContent content = content(userId, administrator, documentId);
      ParsedDocument parsed =
          gateway.parse(userId, documentId, content.contentType(), content.bytes());
      if (document.getStatus() == ProcessingStatus.PENDING_PARSE) {
        events.processParsed(
            UUID.randomUUID().toString(),
            KnowledgeBaseService.MVP_TENANT,
            documentId,
            parsed.fingerprint());
      }
      if (document.getStatus() == ProcessingStatus.PENDING_PARSE
          || document.getStatus() == ProcessingStatus.PARSED) {
        events.scheduleEmbedding(KnowledgeBaseService.MVP_TENANT, knowledgeBaseId, documentId);
      }
      int vectors = gateway.embed(userId, knowledgeBaseId, documentId, parsed.chunks());
      String embeddingFingerprint =
          sha256(knowledgeBaseId + ":" + documentId + ":" + parsed.fingerprint() + ":" + vectors);
      events.processEmbeddingCompleted(
          UUID.randomUUID().toString(),
          KnowledgeBaseService.MVP_TENANT,
          knowledgeBaseId,
          documentId,
          embeddingFingerprint);
      KnowledgeDocument ready = bases.getDocument(userId, knowledgeBaseId, documentId);
      return new IngestionResult(ready, parsed.sourceType(), parsed.chunks().size(), vectors);
    } catch (KnowledgeIngestionException exception) {
      fail(knowledgeBaseId, documentId, exception.getFailureCode());
      throw exception;
    }
  }

  private DocumentContent content(String userId, boolean administrator, String documentId) {
    DocumentFileService.Download download =
        files
            .orElseThrow(KnowledgeIngestionException::upstream)
            .open(userId, administrator, documentId);
    DocumentFile metadata = download.metadata();
    if (metadata.getSizeBytes() > Integer.MAX_VALUE) {
      throw KnowledgeIngestionException.corrupted();
    }
    try (InputStream input = download.content()) {
      byte[] bytes = input.readNBytes((int) metadata.getSizeBytes() + 1);
      if (bytes.length != metadata.getSizeBytes() || !metadata.getSha256().equals(sha256(bytes))) {
        throw KnowledgeIngestionException.corrupted();
      }
      return new DocumentContent(metadata.getContentType(), bytes);
    } catch (IOException exception) {
      throw KnowledgeIngestionException.corrupted();
    }
  }

  private void fail(String knowledgeBaseId, String documentId, String failureCode) {
    try {
      events.markFailed(KnowledgeBaseService.MVP_TENANT, knowledgeBaseId, documentId, failureCode);
    } catch (RuntimeException ignored) {
      // Preserve the stable processing failure if the state was concurrently finalized.
    }
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String sha256(String value) {
    return sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private record DocumentContent(String contentType, byte[] bytes) {
    private DocumentContent {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }

  public record IngestionResult(
      KnowledgeDocument document, String sourceType, int chunkCount, int vectorCount) {}
}
