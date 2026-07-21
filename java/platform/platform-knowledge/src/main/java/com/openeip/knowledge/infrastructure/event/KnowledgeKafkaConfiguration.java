package com.openeip.knowledge.infrastructure.event;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Three-attempt Kafka failure policy with a same-partition dead-letter topic. */
@Configuration
@ConditionalOnProperty(prefix = "openeip.knowledge.kafka", name = "enabled", havingValue = "true")
public class KnowledgeKafkaConfiguration {
  public static final long RETRY_INTERVAL_MILLIS = 1000L;
  public static final long RETRY_COUNT = 2L;

  @Bean
  public DefaultErrorHandler knowledgeKafkaErrorHandler(KafkaTemplate<Object, Object> template) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, exception) -> new TopicPartition(record.topic() + ".DLQ", record.partition()));
    return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MILLIS, RETRY_COUNT));
  }
}
