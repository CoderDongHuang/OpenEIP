package com.openeip.governance.application.catalog;

import com.openeip.governance.domain.catalog.Prompt;
import com.openeip.governance.domain.catalog.PromptPublication;
import com.openeip.governance.domain.catalog.PromptRegistration;
import com.openeip.governance.domain.catalog.PromptReview;
import com.openeip.governance.domain.catalog.PromptVersion;
import com.openeip.governance.domain.catalog.PromptVersionRegistration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for Prompt definitions, evidence, and publication references. */
public interface PromptCatalogPort {
  Prompt createPrompt(
      PromptRegistration registration, String contentCiphertext, String contentDigest);

  Optional<Prompt> prompt(UUID tenantId, UUID promptId);

  List<Prompt> prompts(UUID tenantId, String state, int limit);

  PromptVersion createVersion(
      PromptVersionRegistration registration,
      String contentCiphertext,
      String contentDigest,
      int versionNumber);

  Optional<PromptVersion> version(UUID tenantId, UUID promptId, UUID versionId);

  Optional<PromptVersion> latestVersion(UUID tenantId, UUID promptId);

  PromptReview addReview(
      UUID tenantId,
      UUID promptVersionId,
      UUID reviewerId,
      String decision,
      String reason,
      String policyVersion,
      UUID evaluationRunId,
      java.time.Instant now);

  boolean hasApprovedReview(UUID tenantId, UUID promptVersionId);

  boolean hasEvaluation(UUID tenantId, UUID promptVersionId, UUID evaluationRunId);

  PromptPublication publish(
      UUID tenantId,
      UUID promptId,
      UUID promptVersionId,
      String contentDigest,
      String reason,
      String policyVersion,
      UUID createdBy,
      java.time.Instant now,
      long expectedRevision);

  Optional<PromptPublication> publication(UUID tenantId, UUID publicationId);
}
