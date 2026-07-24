package com.openeip.workflow.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.WorkflowTestApplication;
import com.openeip.workflow.application.WorkflowExecutionService;
import com.openeip.workflow.application.WorkflowService;
import com.openeip.workflow.domain.ExecutionStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("benchmark")
@SpringBootTest(
    classes = WorkflowTestApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:workflow_benchmark;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "openeip.workflow.scheduler-enabled=false"
    })
class WorkflowTransitionBenchmarkTest {
  private static final String USER = "11111111-1111-1111-1111-111111111111";
  @Autowired WorkflowService workflows;
  @Autowired WorkflowExecutionService executions;
  @Autowired ObjectMapper mapper;

  @Test
  void measuresOneThousandTransitionsAndOneHundredParallelExecutions() throws Exception {
    var workflow = workflows.create(USER, "Benchmark " + UUID.randomUUID(), "").workflow();
    workflows.publish(USER, workflow.getId(), 0);

    List<Long> latencies = new ArrayList<>();
    for (int index = 0; index < 500; index++) {
      long started = System.nanoTime();
      var execution =
          executions.trigger(
              USER, workflow.getId(), "sequential-" + index, mapper.createObjectNode());
      latencies.add(System.nanoTime() - started);
      assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    }
    latencies.sort(Long::compareTo);

    var pool = Executors.newFixedThreadPool(8);
    List<Callable<ExecutionStatus>> work = new ArrayList<>();
    for (int index = 0; index < 100; index++) {
      int item = index;
      work.add(
          () ->
              executions
                  .trigger(USER, workflow.getId(), "parallel-" + item, mapper.createObjectNode())
                  .getStatus());
    }
    long parallelStarted = System.nanoTime();
    var results = pool.invokeAll(work);
    pool.shutdown();
    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    long parallelNanos = System.nanoTime() - parallelStarted;
    for (var result : results) {
      assertThat(result.get()).isEqualTo(ExecutionStatus.SUCCEEDED);
    }

    double p99Millis = latencies.get((int) Math.ceil(latencies.size() * 0.99) - 1) / 1_000_000.0;
    double parallelMillis = parallelNanos / 1_000_000.0;
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("schemaVersion", 1);
    report.put("generatedAt", Instant.now().toString());
    report.put("engine", "platform-workflow-h2-deterministic-nodes");
    report.put("nodeTransitions", 1000);
    report.put("transitionPairP99Ms", rounded(p99Millis));
    report.put("parallelExecutions", 100);
    report.put("parallelWallTimeMs", rounded(parallelMillis));
    report.put("parallelThroughputPerSecond", rounded(100_000.0 / parallelMillis));
    report.put("allTerminalSucceeded", true);
    write(report);
  }

  private void write(Map<String, Object> report) throws Exception {
    Path output = Path.of(System.getProperty("workflowBenchmarkOutput"));
    Path parent = output.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
  }

  private static double rounded(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
