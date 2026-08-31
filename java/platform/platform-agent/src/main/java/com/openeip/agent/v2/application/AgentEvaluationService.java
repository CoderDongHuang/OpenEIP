package com.openeip.agent.v2.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.v2.domain.AgentPlatformModels.EvaluationDataset;
import com.openeip.agent.v2.domain.AgentPlatformModels.EvaluationRun;
import com.openeip.agent.v2.domain.AgentPlatformModels.EvaluationSuite;
import com.openeip.agent.v2.infrastructure.AgentEvaluationGateway;
import com.openeip.agent.v2.infrastructure.AgentPlatformStore;
import com.openeip.agent.v2.shared.AgentPlatformException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentEvaluationService {
  private final AgentPlatformStore store;
  private final AgentEvaluationGateway gateway;
  private final ObjectMapper mapper;
  private final Clock clock;

  @Autowired
  public AgentEvaluationService(
      AgentPlatformStore store, AgentEvaluationGateway gateway, ObjectMapper mapper) {
    this(store, gateway, mapper, Clock.systemUTC());
  }

  AgentEvaluationService(
      AgentPlatformStore store, AgentEvaluationGateway gateway, ObjectMapper mapper, Clock clock) {
    this.store = store;
    this.gateway = gateway;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional
  public EvaluationDataset createDataset(
      String actorId, String idempotencyKey, String name, String description) {
    AgentPlatformSupport.uuid(actorId);
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    Instant now = clock.instant();
    EvaluationDataset value =
        new EvaluationDataset(
            UUID.randomUUID().toString(),
            actorId,
            AgentPlatformSupport.requiredText(name, "Evaluation dataset name", 120),
            AgentPlatformSupport.optionalText(description, "description", 1000),
            "DRAFT",
            0,
            now,
            now);
    store.insertDataset(value);
    return value;
  }

  @Transactional(readOnly = true)
  public List<EvaluationDataset> datasets(String actorId, int limit) {
    AgentPlatformSupport.uuid(actorId);
    return store.datasets(actorId, AgentPlatformSupport.limit(limit));
  }

  @Transactional
  public EvaluationSuite createSuite(
      String actorId,
      String idempotencyKey,
      String name,
      JsonNode datasetVersionIds,
      JsonNode gatePolicy) {
    AgentPlatformSupport.uuid(actorId);
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    List<String> datasets =
        AgentPlatformSupport.stringList(datasetVersionIds, 32, "dataset versions");
    for (String id : datasets) {
      if (!store.datasetVersionExists(AgentPlatformSupport.uuid(id))) {
        throw AgentPlatformException.notFound();
      }
    }
    JsonNode policy = gatePolicy == null ? mapper.createObjectNode() : gatePolicy;
    if (!policy.isObject() || policy.size() > 32) {
      throw AgentPlatformException.invalid("Invalid Evaluation gate policy");
    }
    String datasetJson = AgentPlatformSupport.canonical(mapper, datasetVersionIds);
    String gateJson = AgentPlatformSupport.canonical(mapper, policy);
    Instant now = clock.instant();
    EvaluationSuite value =
        new EvaluationSuite(
            UUID.randomUUID().toString(),
            actorId,
            AgentPlatformSupport.requiredText(name, "Evaluation suite name", 120),
            "PUBLISHED",
            0,
            now,
            UUID.randomUUID().toString(),
            datasetJson,
            gateJson,
            AgentPlatformSupport.sha256(datasetJson + gateJson));
    store.insertSuite(value);
    return value;
  }

  @Transactional(readOnly = true)
  public List<EvaluationSuite> suites(String actorId, int limit) {
    AgentPlatformSupport.uuid(actorId);
    return store.suites(actorId, AgentPlatformSupport.limit(limit));
  }

  @Transactional
  public EvaluationRun run(
      String actorId,
      String idempotencyKey,
      String suiteVersionId,
      String candidateVersionId,
      String baselineVersionId,
      int repeatCount) {
    AgentPlatformSupport.uuid(actorId);
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    if (repeatCount < 1 || repeatCount > 10) {
      throw AgentPlatformException.invalid("Repeat count must be between 1 and 10");
    }
    EvaluationSuite suite =
        store
            .suiteVersion(AgentPlatformSupport.uuid(suiteVersionId), actorId)
            .orElseGet(
                () ->
                    store.suites("system", 100).stream()
                        .filter(value -> value.versionId().equals(suiteVersionId))
                        .findFirst()
                        .orElseThrow(AgentPlatformException::notFound));
    var candidate =
        store
            .versionById(AgentPlatformSupport.uuid(candidateVersionId))
            .orElseThrow(AgentPlatformException::notFound);
    var baseline =
        store
            .versionById(AgentPlatformSupport.uuid(baselineVersionId))
            .orElseThrow(AgentPlatformException::notFound);
    if (store.definition(candidate.agentId(), actorId).isEmpty()
        || store.definition(baseline.agentId(), actorId).isEmpty()) {
      throw AgentPlatformException.notFound();
    }
    if (!("CANDIDATE".equals(candidate.status()) || "PUBLISHED".equals(candidate.status()))
        || !"PUBLISHED".equals(baseline.status())) {
      throw AgentPlatformException.conflict(
          "Evaluation requires a candidate snapshot and a published baseline");
    }
    Instant now = clock.instant();
    String runId = UUID.randomUUID().toString();
    EvaluationRun pending =
        new EvaluationRun(
            runId,
            suite.versionId(),
            candidate.id(),
            baseline.id(),
            repeatCount,
            "RUNNING",
            null,
            AgentPlatformSupport.sha256("openeip-v0.6-deterministic"),
            0,
            actorId,
            now,
            null,
            List.of(),
            List.of());
    store.insertEvaluationRun(pending);
    List<String> datasetVersionIds =
        AgentPlatformSupport.stringList(
            AgentPlatformSupport.read(mapper, suite.datasetVersionsJson()), 32, "dataset versions");
    var cases = store.evaluationCases(datasetVersionIds);
    if (cases.isEmpty() || cases.size() > 5000) {
      throw AgentPlatformException.conflict("Evaluation suite has no executable cases");
    }
    var result =
        gateway.run(
            suite.digest(),
            candidate.digest(),
            baseline.digest(),
            repeatCount,
            cases,
            AgentPlatformSupport.read(mapper, suite.gatePolicyJson()));
    result
        .metrics()
        .forEach(
            value ->
                store.insertMetric(UUID.randomUUID().toString(), runId, value, clock.instant()));
    result
        .gates()
        .forEach(
            value -> store.insertGate(UUID.randomUUID().toString(), runId, value, clock.instant()));
    if (!store.completeEvaluation(runId, 0, "COMPLETED", result.gateStatus(), clock.instant())) {
      throw AgentPlatformException.precondition("Evaluation run revision is stale");
    }
    return get(actorId, runId);
  }

  @Transactional(readOnly = true)
  public EvaluationRun get(String actorId, String id) {
    return store
        .evaluationRun(AgentPlatformSupport.uuid(id), AgentPlatformSupport.uuid(actorId))
        .orElseThrow(AgentPlatformException::notFound);
  }

  @Transactional
  public EvaluationRun cancel(String actorId, String id, String ifMatch, String idempotencyKey) {
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    EvaluationRun current = get(actorId, id);
    if (!"RUNNING".equals(current.status())) {
      throw AgentPlatformException.conflict("Only a running Evaluation can be cancelled");
    }
    if (!store.completeEvaluation(
        id, AgentPlatformSupport.revision(ifMatch), "CANCELLED", "FAIL", clock.instant())) {
      throw AgentPlatformException.precondition("Evaluation revision is stale");
    }
    return get(actorId, id);
  }
}
