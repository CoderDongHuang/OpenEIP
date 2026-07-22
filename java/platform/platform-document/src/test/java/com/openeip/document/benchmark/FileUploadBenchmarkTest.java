package com.openeip.document.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.document.infrastructure.storage.LocalObjectStorage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("benchmark")
class FileUploadBenchmarkTest {

  private static final int WARMUPS = 5;
  private static final int MEASUREMENTS = 30;
  private static final int PAYLOAD_BYTES = 1024 * 1024;

  @TempDir Path storageRoot;

  @Test
  void oneMiBUploadP99StaysBelowLocalBaseline() throws Exception {
    LocalObjectStorage storage = new LocalObjectStorage(storageRoot.toString());
    byte[] payload = new byte[PAYLOAD_BYTES];
    Arrays.fill(payload, (byte) 'a');

    for (int index = 0; index < WARMUPS; index++) {
      writeAndDelete(storage, payload);
    }
    double[] milliseconds = new double[MEASUREMENTS];
    for (int index = 0; index < MEASUREMENTS; index++) {
      long started = System.nanoTime();
      writeAndDelete(storage, payload);
      milliseconds[index] = (System.nanoTime() - started) / 1_000_000.0;
    }
    Arrays.sort(milliseconds);

    double p50 = percentile(milliseconds, 0.50);
    double p95 = percentile(milliseconds, 0.95);
    double p99 = percentile(milliseconds, 0.99);
    writeEvidence(p50, p95, p99);
    assertThat(p99).isLessThan(250.0);
  }

  private static void writeAndDelete(LocalObjectStorage storage, byte[] payload) throws Exception {
    String id = UUID.randomUUID().toString();
    String key = id.substring(0, 2) + "/" + id;
    var result = storage.put(key, new ByteArrayInputStream(payload), PAYLOAD_BYTES);
    assertThat(result.sizeBytes()).isEqualTo(PAYLOAD_BYTES);
    storage.delete(key);
  }

  private static double percentile(double[] values, double percentile) {
    int index = (int) Math.ceil(percentile * values.length) - 1;
    return values[Math.max(0, Math.min(index, values.length - 1))];
  }

  private static void writeEvidence(double p50, double p95, double p99) throws Exception {
    String configured = System.getProperty("documentBenchmarkOutput");
    if (configured == null) {
      return;
    }
    Path output = Path.of(configured);
    Path parent = output.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("benchmark", "file-upload-local-storage");
    result.put("recordedAt", Instant.now().toString());
    result.put("payloadBytes", PAYLOAD_BYTES);
    result.put("warmups", WARMUPS);
    result.put("measurements", MEASUREMENTS);
    result.put("p50Ms", round(p50));
    result.put("p95Ms", round(p95));
    result.put("p99Ms", round(p99));
    result.put("thresholdP99Ms", 250);
    result.put("javaVersion", System.getProperty("java.version"));
    result.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), result);
  }

  private static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
