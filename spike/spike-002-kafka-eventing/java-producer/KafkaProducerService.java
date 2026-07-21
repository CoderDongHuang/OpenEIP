package com.openeip.spike;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/** Produces deterministic events for the Spike-002 integration verification. */
public final class KafkaProducerService {
  private static final String TOPIC = "openeip.spike.events.v1";
  private static final int NORMAL_EVENTS = 2_000;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private KafkaProducerService() {}

  private static Map<String, Object> event(String eventId, String type, int sequence) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("eventId", eventId);
    event.put("eventType", type);
    event.put("eventVersion", "1.0");
    event.put("timestamp", Instant.now().toString());
    event.put("source", Map.of("service", "spike-002-java-producer", "instance", "local"));
    event.put(
        "context",
        Map.of("tenantId", "tenant-spike", "userId", "system", "traceId", "trace-spike-002"));
    event.put("payload", Map.of("sequence", sequence));
    return event;
  }

  public static void main(String[] args) throws Exception {
    Properties properties = new Properties();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    properties.put(ProducerConfig.ACKS_CONFIG, "all");
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

    long started = System.nanoTime();
    try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
      for (int sequence = 0; sequence < NORMAL_EVENTS; sequence++) {
        String eventId = "event-" + sequence;
        producer.send(
            new ProducerRecord<>(
                TOPIC,
                eventId,
                MAPPER.writeValueAsString(event(eventId, "document.created", sequence))));
      }
      Map<String, Object> duplicate = event("event-duplicate", "document.created", NORMAL_EVENTS);
      producer.send(
          new ProducerRecord<>(TOPIC, "event-duplicate", MAPPER.writeValueAsString(duplicate)));
      producer.send(
          new ProducerRecord<>(TOPIC, "event-duplicate", MAPPER.writeValueAsString(duplicate)));
      Map<String, Object> poison = event("event-poison", "validation.poison", NORMAL_EVENTS + 1);
      producer.send(new ProducerRecord<>(TOPIC, "event-poison", MAPPER.writeValueAsString(poison)));
      producer.flush();
    }

    double elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
    int totalRecords = NORMAL_EVENTS + 3;
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("normal_events", NORMAL_EVENTS);
    result.put("records_sent", totalRecords);
    result.put("elapsed_seconds", elapsedSeconds);
    result.put("throughput_rps", totalRecords / elapsedSeconds);
    result.put("duplicate_event_id", "event-duplicate");
    result.put("poison_event_id", "event-poison");
    Files.createDirectories(Path.of("/results"));
    MAPPER
        .writerWithDefaultPrettyPrinter()
        .writeValue(Path.of("/results/producer.json").toFile(), result);

    Path completionMarker = Path.of("/results/consumer.done");
    Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
    while (!Files.exists(completionMarker) && Instant.now().isBefore(deadline)) {
      Thread.sleep(500);
    }
    if (!Files.exists(completionMarker)) {
      throw new IllegalStateException("Consumer did not finish within two minutes");
    }
  }
}
