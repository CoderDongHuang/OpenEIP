package com.openeip.knowledge.application;

import com.openeip.knowledge.domain.ProcessingStatus;
import com.openeip.knowledge.domain.entity.KnowledgeDocument;
import com.openeip.knowledge.domain.entity.ProcessedKnowledgeEvent;
import com.openeip.knowledge.domain.repository.KnowledgeDocumentRepository;
import com.openeip.knowledge.domain.repository.ProcessedKnowledgeEventRepository;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional core used by future at-least-once broker adapters. */
@Service
public class KnowledgeEventService {
  public static final String PARSED = "document.lifecycle.parsed";
  public static final String EMBEDDED = "knowledge.embedding.completed";

  private final KnowledgeDocumentRepository documents;
  private final ProcessedKnowledgeEventRepository events;
  private final Clock clock;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring collaborators are shared services.")
  public KnowledgeEventService(
      KnowledgeDocumentRepository documents, ProcessedKnowledgeEventRepository events) {
    this(documents, events, Clock.systemUTC());
  }

  KnowledgeEventService(
      KnowledgeDocumentRepository documents,
      ProcessedKnowledgeEventRepository events,
      Clock clock) {
    this.documents = documents;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public EventOutcome processParsed(
      String eventId, String tenantId, String documentId, String payloadFingerprint) {
    String resource = valid(tenantId, eventId, documentId, payloadFingerprint, documentId);
    EventOutcome duplicate = duplicate(tenantId, eventId, PARSED, resource, payloadFingerprint);
    if (duplicate != null) return duplicate;
    List<KnowledgeDocument> matches =
        documents.findAllByTenantIdAndDocumentId(tenantId, documentId);
    if (matches.isEmpty()) throw KnowledgeException.notFound();
    Instant now = clock.instant();
    boolean advanced = false;
    for (KnowledgeDocument document : matches) {
      if (document.getStatus() == ProcessingStatus.PENDING_PARSE) {
        advanced |= document.transitionTo(ProcessingStatus.PARSED, now);
      } else if (!document.isAtOrBeyondParsed()) {
        throw KnowledgeException.invalidTransition(document.getStatus(), ProcessingStatus.PARSED);
      }
    }
    String outcome = advanced ? "ADVANCED" : "NOOP";
    save(tenantId, eventId, PARSED, resource, payloadFingerprint, outcome, now);
    return new EventOutcome(outcome, false);
  }

  @Transactional
  public boolean scheduleEmbedding(String tenantId, String baseId, String documentId) {
    KnowledgeDocument document = document(tenantId, baseId, documentId);
    return document.transitionTo(ProcessingStatus.PENDING_EMBEDDING, clock.instant());
  }

  @Transactional
  public EventOutcome processEmbeddingCompleted(
      String eventId,
      String tenantId,
      String baseId,
      String documentId,
      String payloadFingerprint) {
    String resource = baseId + ":" + documentId;
    valid(tenantId, eventId, documentId, payloadFingerprint, resource);
    KnowledgeBaseService.validUuid(baseId);
    EventOutcome duplicate = duplicate(tenantId, eventId, EMBEDDED, resource, payloadFingerprint);
    if (duplicate != null) return duplicate;
    Instant now = clock.instant();
    boolean advanced =
        document(tenantId, baseId, documentId).transitionTo(ProcessingStatus.READY, now);
    String outcome = advanced ? "ADVANCED" : "NOOP";
    save(tenantId, eventId, EMBEDDED, resource, payloadFingerprint, outcome, now);
    return new EventOutcome(outcome, false);
  }

  @Transactional
  public void markFailed(String tenantId, String baseId, String documentId, String failureCode) {
    document(tenantId, baseId, documentId).fail(failureCode, clock.instant());
  }

  private KnowledgeDocument document(String tenantId, String baseId, String documentId) {
    return documents
        .findByTenantIdAndKnowledgeBaseIdAndDocumentId(tenantId, baseId, documentId)
        .orElseThrow(KnowledgeException::notFound);
  }

  private EventOutcome duplicate(
      String tenantId, String eventId, String type, String resource, String fingerprint) {
    return events
        .findByTenantIdAndEventId(tenantId, eventId)
        .map(
            event -> {
              if (!event.matches(type, resource, fingerprint)) {
                throw KnowledgeException.conflict("Event identifier collision");
              }
              return new EventOutcome(event.getOutcome(), true);
            })
        .orElse(null);
  }

  private void save(
      String tenant,
      String eventId,
      String type,
      String resource,
      String fingerprint,
      String outcome,
      Instant now) {
    events.save(
        new ProcessedKnowledgeEvent(
            UUID.randomUUID().toString(),
            tenant,
            eventId,
            type,
            resource,
            fingerprint,
            outcome,
            now));
  }

  private static String valid(
      String tenant, String eventId, String documentId, String fingerprint, String resource) {
    if (tenant == null || tenant.isBlank() || tenant.length() > 64) {
      throw KnowledgeException.invalid("Invalid tenant identifier");
    }
    KnowledgeBaseService.validUuid(eventId);
    KnowledgeBaseService.validUuid(documentId);
    if (fingerprint == null || !fingerprint.matches("[a-f0-9]{64}")) {
      throw KnowledgeException.invalid("Invalid event fingerprint");
    }
    return resource;
  }

  public record EventOutcome(String outcome, boolean duplicate) {}
}
