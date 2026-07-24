package com.openeip.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.domain.entity.WorkflowEvent;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class WorkflowEventStream {
  private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
  private final ObjectMapper mapper;
  private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters =
      new ConcurrentHashMap<>();

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "ObjectMapper is application scoped.")
  public WorkflowEventStream(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public SseEmitter open(String executionId, List<WorkflowEvent> backlog) {
    SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
    emitters.computeIfAbsent(executionId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
    Runnable cleanup = () -> remove(executionId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(ignored -> cleanup.run());
    try {
      for (WorkflowEvent event : backlog) {
        send(emitter, event);
      }
      emitter.send(SseEmitter.event().comment("connected"));
    } catch (IOException exception) {
      cleanup.run();
      emitter.completeWithError(exception);
    }
    return emitter;
  }

  public void publish(WorkflowEvent event) {
    for (SseEmitter emitter :
        emitters.getOrDefault(event.getExecutionId(), new CopyOnWriteArrayList<>())) {
      try {
        send(emitter, event);
      } catch (IOException exception) {
        remove(event.getExecutionId(), emitter);
        emitter.complete();
      }
    }
  }

  private void send(SseEmitter emitter, WorkflowEvent event) throws IOException {
    emitter.send(
        SseEmitter.event()
            .id(Long.toString(event.getSequence()))
            .name(event.getType())
            .data(StreamEvent.from(event, mapper)));
  }

  private void remove(String executionId, SseEmitter emitter) {
    List<SseEmitter> values = emitters.get(executionId);
    if (values != null) {
      values.remove(emitter);
      if (values.isEmpty()) {
        emitters.remove(executionId);
      }
    }
  }

  public record StreamEvent(
      String executionId,
      long sequence,
      String type,
      String nodeId,
      Instant occurredAt,
      JsonNode data) {
    public static StreamEvent from(WorkflowEvent event, ObjectMapper mapper) {
      try {
        return new StreamEvent(
            event.getExecutionId(),
            event.getSequence(),
            event.getType(),
            event.getNodeId(),
            event.getOccurredAt(),
            mapper.readTree(event.getDataJson()));
      } catch (IOException exception) {
        throw new IllegalStateException("Stored workflow event JSON is invalid", exception);
      }
    }
  }
}
