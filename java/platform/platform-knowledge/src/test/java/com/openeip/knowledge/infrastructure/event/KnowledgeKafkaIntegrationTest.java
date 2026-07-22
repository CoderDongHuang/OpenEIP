package com.openeip.knowledge.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.openeip.knowledge.KnowledgeTestApplication;
import com.openeip.knowledge.domain.ProcessingStatus;
import com.openeip.knowledge.domain.entity.KnowledgeDocument;
import com.openeip.knowledge.domain.repository.KnowledgeDocumentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@SpringBootTest(
    classes = KnowledgeTestApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:knowledge_kafka;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
      "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
      "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "openeip.knowledge.kafka.enabled=true",
      "openeip.knowledge.kafka.external-tenant-id=11111111-1111-4111-8111-111111111111",
      "openeip.knowledge.kafka.parsed-topic=knowledge-parsed-test",
      "openeip.knowledge.kafka.embedding-topic=knowledge-embedding-test",
      "openeip.knowledge.kafka.group-id=knowledge-test-group"
    })
@EmbeddedKafka(
    partitions = 1,
    topics = {
      "knowledge-parsed-test",
      "knowledge-parsed-test.DLQ",
      "knowledge-embedding-test",
      "knowledge-embedding-test.DLQ"
    })
class KnowledgeKafkaIntegrationTest {
  private static final String DOCUMENT = "33333333-3333-4333-8333-333333333333";
  private static final String BASE = "44444444-4444-4444-8444-444444444444";

  @Autowired KafkaTemplate<String, String> template;
  @Autowired KnowledgeDocumentRepository documents;
  @Autowired EmbeddedKafkaBroker broker;

  @Test
  void consumesValidEventAndRoutesInvalidEnvelopeToDlqAfterRetries() throws Exception {
    documents.saveAndFlush(
        new KnowledgeDocument(
            "55555555-5555-4555-8555-555555555555",
            "default",
            BASE,
            DOCUMENT,
            Instant.parse("2026-07-22T00:00:00Z")));

    template.send("knowledge-parsed-test", parsed()).get();
    waitUntilParsed();

    Map<String, Object> properties = KafkaTestUtils.consumerProps("dlq-reader", "true", broker);
    try (Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<>(
                properties, new StringDeserializer(), new StringDeserializer())
            .createConsumer()) {
      broker.consumeFromAnEmbeddedTopic(consumer, "knowledge-parsed-test.DLQ");
      template.send("knowledge-parsed-test", "{\"invalid\":true}").get();
      ConsumerRecord<String, String> record =
          KafkaTestUtils.getSingleRecord(
              consumer, "knowledge-parsed-test.DLQ", Duration.ofSeconds(10));
      assertThat(record.value()).isEqualTo("{\"invalid\":true}");
      assertThat(record.headers().lastHeader("kafka_dlt-exception-message")).isNotNull();
    }
  }

  private void waitUntilParsed() throws InterruptedException {
    Instant deadline = Instant.now().plusSeconds(10);
    while (Instant.now().isBefore(deadline)) {
      ProcessingStatus status =
          documents
              .findByTenantIdAndKnowledgeBaseIdAndDocumentId("default", BASE, DOCUMENT)
              .orElseThrow()
              .getStatus();
      if (status == ProcessingStatus.PARSED) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("Parsed event was not consumed within 10 seconds");
  }

  private static String parsed() {
    return """
        {"eventId":"22222222-2222-4222-8222-222222222222",
         "eventType":"document.lifecycle.parsed","eventVersion":1,
         "timestamp":"2026-07-22T00:00:00Z","source":"python-engine-document",
         "tenantId":"11111111-1111-4111-8111-111111111111",
         "userId":"66666666-6666-4666-8666-666666666666","traceId":"trace-1",
         "idempotencyKey":"parse_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
         "payload":{"documentId":"__DOCUMENT__","sourceType":"TEXT","sourceSha256":"__SOURCE_HASH__",
         "normalizedTextSha256":"__TEXT_HASH__","chunkCount":1,"parserVersion":"1.0"}}
        """
        .replace("__DOCUMENT__", DOCUMENT)
        .replace("__SOURCE_HASH__", "a".repeat(64))
        .replace("__TEXT_HASH__", "b".repeat(64));
  }
}
