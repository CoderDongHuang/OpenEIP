package com.openeip.knowledge.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openeip.knowledge.KnowledgeTestApplication;
import com.openeip.knowledge.application.KnowledgeEventService;
import com.openeip.knowledge.domain.entity.KnowledgeDocument;
import com.openeip.knowledge.domain.repository.KnowledgeDocumentRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("benchmark")
@SpringBootTest(
    classes = KnowledgeTestApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:knowledge_benchmark;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "logging.level.org.hibernate=WARN"
    })
class KnowledgeTransitionBenchmarkTest {
  private static final int WARMUPS = 10;
  private static final int SAMPLES = 1000;

  @Autowired KnowledgeDocumentRepository documents;
  @Autowired KnowledgeEventService events;

  @Test
  void persistedParsedTransitionsStayWithinLatencyBudget() throws Exception {
    List<KnowledgeDocument> rows = new ArrayList<>();
    for (int index = 0; index < WARMUPS + SAMPLES; index++) {
      String documentId = UUID.randomUUID().toString();
      rows.add(
          new KnowledgeDocument(
              UUID.randomUUID().toString(),
              "default",
              "22222222-2222-2222-2222-222222222222",
              documentId,
              Instant.parse("2026-07-22T00:00:00Z")));
    }
    documents.saveAllAndFlush(rows);

    for (int index = 0; index < WARMUPS; index++) {
      events.processParsed(
          UUID.randomUUID().toString(), "default", rows.get(index).getDocumentId(), "a".repeat(64));
    }
    List<Double> milliseconds = new ArrayList<>();
    for (int index = WARMUPS; index < rows.size(); index++) {
      long started = System.nanoTime();
      events.processParsed(
          UUID.randomUUID().toString(), "default", rows.get(index).getDocumentId(), "a".repeat(64));
      milliseconds.add((System.nanoTime() - started) / 1_000_000.0);
    }
    Collections.sort(milliseconds);
    double p50 = percentile(milliseconds, 0.50);
    double p95 = percentile(milliseconds, 0.95);
    double p99 = percentile(milliseconds, 0.99);
    assertThat(p99).isLessThan(50.0);

    String output = System.getProperty("knowledgeBenchmarkOutput");
    if (output != null) {
      Path path = Path.of(output);
      Path parent = path.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("module", "knowledge-base");
      evidence.put("operation", "transactional parsed event transition");
      evidence.put("warmups", WARMUPS);
      evidence.put("samples", SAMPLES);
      evidence.put("p50Ms", rounded(p50));
      evidence.put("p95Ms", rounded(p95));
      evidence.put("p99Ms", rounded(p99));
      evidence.put("thresholdP99Ms", 50);
      evidence.put("result", "PASS");
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .writeValue(path.toFile(), evidence);
    }
  }

  private static double percentile(List<Double> values, double percentile) {
    return values.get((int) Math.ceil(percentile * values.size()) - 1);
  }

  private static double rounded(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
