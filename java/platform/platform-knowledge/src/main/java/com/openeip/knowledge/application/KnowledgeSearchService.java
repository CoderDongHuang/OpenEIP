package com.openeip.knowledge.application;

import com.openeip.knowledge.infrastructure.ingestion.KnowledgeIngestionGateway;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Service;

/** Authorizes one base before dispatching a bounded internal retrieval request. */
@Service
public class KnowledgeSearchService {
  private final KnowledgeBaseService bases;
  private final KnowledgeIngestionGateway gateway;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected collaborators are application-scoped Spring services.")
  public KnowledgeSearchService(KnowledgeBaseService bases, KnowledgeIngestionGateway gateway) {
    this.bases = bases;
    this.gateway = gateway;
  }

  public KnowledgeIngestionGateway.SearchResult search(
      String userId, String baseId, String query, String mode, int topK) {
    bases.get(userId, baseId);
    String normalized = query == null ? "" : query.trim();
    if (normalized.isEmpty()
        || normalized.length() > 2000
        || topK < 1
        || topK > 50
        || !("FULL_TEXT".equals(mode) || "VECTOR".equals(mode) || "HYBRID".equals(mode))) {
      throw KnowledgeException.invalid("Invalid knowledge search request");
    }
    return gateway.search(userId, baseId, normalized, mode, topK);
  }
}
