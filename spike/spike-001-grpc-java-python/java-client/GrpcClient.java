package com.openeip.spike;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.v1.AgentProto;
import com.openeip.agent.v1.AgentServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Executes and records the Spike-001 cross-runtime verification. */
public final class GrpcClient {
  private static final int WARMUP_CALLS = 20;
  private static final int MEASURED_CALLS = 200;
  private static final int BIDI_ROUNDS = 3;

  private final ManagedChannel channel;
  private final AgentServiceGrpc.AgentServiceBlockingStub blockingStub;
  private final AgentServiceGrpc.AgentServiceStub asyncStub;

  private GrpcClient(String host, int port) {
    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    blockingStub = AgentServiceGrpc.newBlockingStub(channel);
    asyncStub = AgentServiceGrpc.newStub(channel);
  }

  private AgentProto.ChatRequest request(String sessionId, String message) {
    return AgentProto.ChatRequest.newBuilder().setSessionId(sessionId).setMessage(message).build();
  }

  private void waitUntilReady() throws InterruptedException {
    for (int attempt = 1; attempt <= 60; attempt++) {
      try {
        blockingStub.withDeadlineAfter(1, TimeUnit.SECONDS).chat(request("readiness", "ready"));
        return;
      } catch (StatusRuntimeException ignored) {
        Thread.sleep(1000);
      }
    }
    throw new IllegalStateException("Python gRPC server was not ready within 60 seconds");
  }

  private Map<String, Object> benchmarkUnary() {
    for (int index = 0; index < WARMUP_CALLS; index++) {
      blockingStub.chat(request("warmup", "warmup"));
    }

    List<Double> samplesMs = new ArrayList<>();
    long started = System.nanoTime();
    for (int index = 0; index < MEASURED_CALLS; index++) {
      long callStarted = System.nanoTime();
      AgentProto.ChatResponse response = blockingStub.chat(request("unary", "message-" + index));
      if (!response.getAnswer().equals("received:message-" + index)) {
        throw new IllegalStateException("Unary response content did not match request");
      }
      samplesMs.add((System.nanoTime() - callStarted) / 1_000_000.0);
    }
    double elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
    samplesMs.sort(Double::compareTo);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("samples", MEASURED_CALLS);
    result.put("p50_ms", percentile(samplesMs, 0.50));
    result.put("p95_ms", percentile(samplesMs, 0.95));
    result.put("p99_ms", percentile(samplesMs, 0.99));
    result.put("throughput_rps", MEASURED_CALLS / elapsedSeconds);
    result.put("threshold_p99_ms", 50);
    result.put("passed", percentile(samplesMs, 0.99) < 50);
    return result;
  }

  private Map<String, Object> verifyServerStreaming() throws InterruptedException {
    CountDownLatch completed = new CountDownLatch(1);
    List<Double> firstEvent = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    long started = System.nanoTime();

    asyncStub.chatStream(
        request("server-stream", "stream"),
        new StreamObserver<>() {
          @Override
          public void onNext(AgentProto.ChatStreamResponse response) {
            if (firstEvent.isEmpty()) {
              firstEvent.add((System.nanoTime() - started) / 1_000_000.0);
            }
          }

          @Override
          public void onError(Throwable error) {
            errors.add(error.toString());
            completed.countDown();
          }

          @Override
          public void onCompleted() {
            completed.countDown();
          }
        });

    boolean finished = completed.await(10, TimeUnit.SECONDS);
    double latency = firstEvent.isEmpty() ? Double.POSITIVE_INFINITY : firstEvent.getFirst();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("first_event_ms", latency);
    result.put("threshold_ms", 100);
    result.put("completed", finished);
    result.put("errors", errors);
    result.put("passed", finished && errors.isEmpty() && latency < 100);
    return result;
  }

  private Map<String, Object> verifyBidirectionalStreaming() throws InterruptedException {
    CountDownLatch completed = new CountDownLatch(1);
    List<String> finishReasons = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    StreamObserver<AgentProto.ChatRequest> requests =
        asyncStub.chatBidi(
            new StreamObserver<>() {
              @Override
              public void onNext(AgentProto.ChatStreamResponse response) {
                if (response.hasDone()) {
                  finishReasons.add(response.getDone().getFinishReason());
                }
              }

              @Override
              public void onError(Throwable error) {
                errors.add(error.toString());
                completed.countDown();
              }

              @Override
              public void onCompleted() {
                completed.countDown();
              }
            });

    for (int round = 1; round <= BIDI_ROUNDS; round++) {
      requests.onNext(request("bidi", "round-" + round));
    }
    requests.onCompleted();
    boolean finished = completed.await(10, TimeUnit.SECONDS);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("rounds_sent", BIDI_ROUNDS);
    result.put("rounds_received", finishReasons.size());
    result.put("finish_reasons", finishReasons);
    result.put("errors", errors);
    result.put("passed", finished && errors.isEmpty() && finishReasons.size() == BIDI_ROUNDS);
    return result;
  }

  private Map<String, Object> verifyErrorPropagation() {
    String observedCode = "NONE";
    try {
      blockingStub.chat(request("error", "trigger-error"));
    } catch (StatusRuntimeException error) {
      observedCode = error.getStatus().getCode().name();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("expected_code", Status.INVALID_ARGUMENT.getCode().name());
    result.put("observed_code", observedCode);
    result.put("passed", observedCode.equals(Status.INVALID_ARGUMENT.getCode().name()));
    return result;
  }

  private static double percentile(List<Double> samples, double quantile) {
    int index = (int) Math.ceil(quantile * samples.size()) - 1;
    return samples.get(Math.max(0, index));
  }

  private void close() throws InterruptedException {
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  public static void main(String[] args) throws Exception {
    GrpcClient client = new GrpcClient("python-server", 50051);
    Map<String, Object> evidence = new LinkedHashMap<>();
    try {
      client.waitUntilReady();
      evidence.put("spike", "spike-001");
      evidence.put("executed_at", Instant.now().toString());
      Map<String, Object> unary = client.benchmarkUnary();
      Map<String, Object> serverStreaming = client.verifyServerStreaming();
      Map<String, Object> bidi = client.verifyBidirectionalStreaming();
      Map<String, Object> error = client.verifyErrorPropagation();
      evidence.put("unary", unary);
      evidence.put("server_streaming", serverStreaming);
      evidence.put("bidirectional_streaming", bidi);
      evidence.put("error_propagation", error);
      boolean passed =
          Boolean.TRUE.equals(unary.get("passed"))
              && Boolean.TRUE.equals(serverStreaming.get("passed"))
              && Boolean.TRUE.equals(bidi.get("passed"))
              && Boolean.TRUE.equals(error.get("passed"));
      evidence.put("passed", passed);
      Files.createDirectories(Path.of("/results"));
      new ObjectMapper()
          .writerWithDefaultPrettyPrinter()
          .writeValue(Path.of("/results/result.json").toFile(), evidence);
      if (!passed) {
        throw new IllegalStateException("Spike-001 acceptance criteria failed");
      }
    } finally {
      client.close();
    }
  }
}
