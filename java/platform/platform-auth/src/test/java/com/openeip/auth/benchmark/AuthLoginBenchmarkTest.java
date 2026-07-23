package com.openeip.auth.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openeip.auth.application.service.AuthService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@Tag("benchmark")
class AuthLoginBenchmarkTest {

  private static final int WARMUPS = 5;
  private static final int MEASUREMENTS = 30;

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(
              DockerImageName.parse(
                      "mysql:8.4.10@sha256:5700b0892591a760c4caef7a0024c887afd46317d73dd420801706e661c4db56")
                  .asCompatibleSubstituteFor("mysql"))
          .withDatabaseName("openeip_auth_benchmark")
          .withUsername("openeip")
          .withPassword("benchmark-password");

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("openeip.jwt.allow-ephemeral-key", () -> "true");
  }

  @Autowired private AuthService authService;

  @Test
  void loginP99RemainsBelowBaseline() throws Exception {
    authService.register("benchmark", "benchmark@openeip.org", "password123");
    for (int index = 0; index < WARMUPS; index++) {
      authService.login("benchmark", "password123");
    }

    double[] milliseconds = new double[MEASUREMENTS];
    long totalStarted = System.nanoTime();
    for (int index = 0; index < MEASUREMENTS; index++) {
      long started = System.nanoTime();
      authService.login("benchmark", "password123");
      milliseconds[index] = (System.nanoTime() - started) / 1_000_000.0;
    }
    double totalMilliseconds = (System.nanoTime() - totalStarted) / 1_000_000.0;
    Arrays.sort(milliseconds);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("recordedAt", Instant.now().toString());
    result.put("runtime", System.getProperty("java.runtime.version"));
    result.put("database", "MySQL " + MYSQL.getDockerImageName());
    result.put("bcryptStrength", 12);
    result.put("warmups", WARMUPS);
    result.put("measurements", MEASUREMENTS);
    result.put("p50Ms", percentile(milliseconds, 0.50));
    result.put("p95Ms", percentile(milliseconds, 0.95));
    result.put("p99Ms", percentile(milliseconds, 0.99));
    result.put("totalMs", round(totalMilliseconds));
    result.put("thresholdP99Ms", 500);
    result.put("passed", percentile(milliseconds, 0.99) < 500);

    String outputProperty =
        Objects.requireNonNull(
            System.getProperty("authBenchmarkOutput"), "authBenchmarkOutput is required");
    Path output = Path.of(outputProperty).toAbsolutePath();
    Files.createDirectories(Objects.requireNonNull(output.getParent()));
    new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .writeValue(output.toFile(), result);
    assertThat(percentile(milliseconds, 0.99)).isLessThan(500.0);
  }

  private static double percentile(double[] sorted, double percentile) {
    int index = (int) Math.ceil(percentile * sorted.length) - 1;
    return round(sorted[Math.max(0, index)]);
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
