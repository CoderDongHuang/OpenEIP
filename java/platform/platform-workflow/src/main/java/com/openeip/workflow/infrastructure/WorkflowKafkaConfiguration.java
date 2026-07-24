package com.openeip.workflow.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.application.WorkflowGraphValidator;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(prefix = "openeip.workflow", name = "kafka-enabled", havingValue = "true")
public class WorkflowKafkaConfiguration {
  @Bean("workflowKafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, String>
      workflowKafkaListenerContainerFactory(
          ConsumerFactory<String, String> consumerFactory,
          KafkaTemplate<String, String> kafka,
          ObjectMapper mapper,
          @Value("${openeip.workflow.dlq-topic:workflow.events.dlq.v1}") String dlqTopic) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(
        new DefaultErrorHandler(
            (record, exception) -> recover(kafka, mapper, dlqTopic, record),
            new FixedBackOff(1000, 2)));
    return factory;
  }

  static void recover(
      KafkaTemplate<String, String> kafka,
      ObjectMapper mapper,
      String dlqTopic,
      ConsumerRecord<?, ?> record) {
    String key = record.key() == null ? "" : record.key().toString();
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("eventId", UUID.randomUUID().toString());
    envelope.put("eventType", "workflow.event.rejected");
    envelope.put("eventVersion", 1);
    envelope.put("timestamp", Instant.now().toString());
    envelope.put("source", "platform-workflow");
    envelope.put("topic", record.topic());
    envelope.put("partition", record.partition());
    envelope.put("offset", record.offset());
    envelope.put("keySha256", WorkflowGraphValidator.sha256(key));
    envelope.put("errorCode", "WF-V-001");
    try {
      kafka.send(dlqTopic, WorkflowGraphValidator.sha256(key), mapper.writeValueAsString(envelope));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to encode workflow DLQ envelope", exception);
    }
  }
}
