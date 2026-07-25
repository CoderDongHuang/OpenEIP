package com.openeip.connector.adapter.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.shared.ConnectorAdapterException;
import com.openeip.connector.spi.ConfigField;
import com.openeip.connector.spi.ConfigField.FieldType;
import com.openeip.connector.spi.ConnectionTestResult;
import com.openeip.connector.spi.ConnectorConfig;
import com.openeip.connector.spi.ConnectorMetadata;
import com.openeip.connector.spi.ConnectorSpi;
import com.openeip.connector.spi.DataReader;
import com.openeip.connector.spi.DataWriter;
import com.openeip.connector.spi.MetadataSchema;
import com.openeip.connector.spi.MetadataSchema.ResourceSchema;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

@Component
public class KafkaConnector implements ConnectorSpi {
  private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(10);
  private final ObjectMapper mapper;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The mapper is an application-scoped collaborator.")
  public KafkaConnector(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public ConnectorMetadata getMetadata() {
    return new ConnectorMetadata(
        ConnectorType.KAFKA, "Kafka", "1.0.0", "Kafka topic connector", true, true);
  }

  @Override
  public List<ConfigField> getConfigSchema() {
    return List.of(
        field("bootstrapServers", "Bootstrap servers", FieldType.TEXT, true, false, null),
        field("securityProtocol", "Security protocol", FieldType.SELECT, false, false, "PLAINTEXT"),
        field("groupId", "Consumer group", FieldType.TEXT, false, false, "openeip-connector"),
        field("username", "SASL username", FieldType.TEXT, false, true, null),
        field("password", "SASL password", FieldType.TEXT, false, true, null));
  }

  @Override
  @SuppressFBWarnings(
      value = "REC_CATCH_EXCEPTION",
      justification =
          "Kafka client operations expose several checked and runtime transport failures.")
  public ConnectionTestResult testConnection(ConnectorConfig config) {
    long started = System.nanoTime();
    try (AdminClient admin =
        AdminClient.create(properties(config, AdminClientConfig.configNames()))) {
      admin.describeCluster().clusterId().get(OPERATION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      return ConnectionTestResult.success(elapsed(started));
    } catch (Exception exception) {
      return ConnectionTestResult.failure(
          elapsed(started), "CONN-KAFKA-CONNECTION", "Kafka connection failed");
    }
  }

  @Override
  public MetadataSchema extractMetadata(ConnectorConfig config) {
    try (AdminClient admin =
        AdminClient.create(properties(config, AdminClientConfig.configNames()))) {
      Map<String, org.apache.kafka.clients.admin.TopicDescription> descriptions =
          admin
              .describeTopics(
                  admin.listTopics().names().get(OPERATION_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
              .allTopicNames()
              .get(OPERATION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      List<ResourceSchema> resources = new ArrayList<>();
      descriptions.entrySet().stream()
          .limit(200)
          .forEach(
              entry ->
                  resources.add(
                      new ResourceSchema(
                          entry.getKey(),
                          "topic",
                          entry.getValue().partitions().stream()
                              .map(partition -> "partition-" + partition.partition())
                              .toList())));
      return new MetadataSchema(resources);
    } catch (Exception exception) {
      throw adapter("CONN-KAFKA-METADATA", "Kafka metadata extraction failed", exception);
    }
  }

  @Override
  public DataReader createReader(ConnectorConfig config) {
    return request -> {
      String topic = topic(request.resource());
      Properties properties = properties(config, ConsumerConfig.configNames());
      properties.put(
          ConsumerConfig.GROUP_ID_CONFIG,
          config.values().path("groupId").asText("openeip-connector"));
      properties.put(
          ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
          "org.apache.kafka.common.serialization.StringDeserializer");
      properties.put(
          ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
          "org.apache.kafka.common.serialization.StringDeserializer");
      properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
      properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
      try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
        List<PartitionInfo> partitions = consumer.partitionsFor(topic, OPERATION_TIMEOUT);
        if (partitions == null || partitions.isEmpty()) {
          throw new ConnectorAdapterException(
              "CONN-KAFKA-RESOURCE", "Kafka topic not found", false);
        }
        List<TopicPartition> assigned =
            partitions.stream()
                .map(partition -> new TopicPartition(topic, partition.partition()))
                .toList();
        consumer.assign(assigned);
        consumer.seekToEnd(assigned);
        List<JsonNode> items = new ArrayList<>();
        long deadline = System.nanoTime() + OPERATION_TIMEOUT.toNanos();
        while (items.size() < request.limit() && System.nanoTime() < deadline) {
          for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
            ObjectNode item = mapper.createObjectNode();
            item.put("topic", record.topic());
            item.put("partition", record.partition());
            item.put("offset", record.offset());
            item.put("key", record.key());
            item.put("value", record.value());
            items.add(item);
            if (items.size() >= request.limit()) {
              break;
            }
          }
        }
        return new DataReader.ReadResult(items, null);
      } catch (ConnectorAdapterException exception) {
        throw exception;
      } catch (Exception exception) {
        throw adapter("CONN-KAFKA-READ", "Kafka read failed", exception);
      }
    };
  }

  @Override
  public Optional<DataWriter> createWriter(ConnectorConfig config) {
    return Optional.of(
        request -> {
          if (!"PUBLISH".equalsIgnoreCase(request.operation())) {
            throw new ConnectorAdapterException(
                "CONN-KAFKA-WRITE", "Only PUBLISH is allowed", false);
          }
          String topic = topic(request.resource());
          String value = request.data().path("value").asText("");
          String key =
              request.data().path("key").isMissingNode()
                  ? request.idempotencyKey()
                  : request.data().path("key").asText();
          try (KafkaProducer<String, String> producer =
              new KafkaProducer<>(producerProperties(config))) {
            var metadata =
                producer
                    .send(new ProducerRecord<>(topic, key, value))
                    .get(OPERATION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            return new DataWriter.WriteResult(
                topic + ":" + metadata.partition() + ":" + metadata.offset(), "PUBLISHED");
          } catch (Exception exception) {
            throw adapter("CONN-KAFKA-WRITE", "Kafka publish failed", exception);
          }
        });
  }

  private Properties producerProperties(ConnectorConfig config) {
    Properties properties = properties(config, ProducerConfig.configNames());
    properties.put(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");
    properties.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");
    properties.put(ProducerConfig.ACKS_CONFIG, "all");
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    return properties;
  }

  private static Properties properties(ConnectorConfig config, java.util.Set<String> ignored) {
    String servers = config.values().path("bootstrapServers").asText("").trim();
    if (servers.isBlank() || servers.length() > 2048) {
      throw new ConnectorAdapterException("CONN-CONFIG", "Invalid Kafka bootstrapServers", false);
    }
    Properties properties = new Properties();
    properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
    String protocol = config.values().path("securityProtocol").asText("PLAINTEXT");
    if (!List.of("PLAINTEXT", "SSL", "SASL_SSL", "SASL_PLAINTEXT").contains(protocol)) {
      throw new ConnectorAdapterException("CONN-CONFIG", "Invalid Kafka security protocol", false);
    }
    properties.put("security.protocol", protocol);
    String username = config.credentials().get("username");
    String password = config.credentials().get("password");
    if (username != null || password != null) {
      if (username == null || password == null) {
        throw new ConnectorAdapterException(
            "CONN-CREDENTIAL", "Kafka SASL credentials are incomplete", false);
      }
      properties.put("sasl.mechanism", "PLAIN");
      properties.put(
          "sasl.jaas.config",
          "org.apache.kafka.common.security.plain.PlainLoginModule required username=\""
              + escape(username)
              + "\" password=\""
              + escape(password)
              + "\";");
    }
    return properties;
  }

  private static String topic(String resource) {
    if (resource == null || !resource.matches("[A-Za-z0-9._-]{1,249}")) {
      throw new ConnectorAdapterException("CONN-KAFKA-RESOURCE", "Invalid Kafka topic", false);
    }
    return resource;
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static ConfigField field(
      String name,
      String label,
      FieldType type,
      boolean required,
      boolean secret,
      String defaultValue) {
    return new ConfigField(name, label, type, required, secret, defaultValue, List.of());
  }

  private static long elapsed(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }

  private static ConnectorAdapterException adapter(String code, String message, Exception cause) {
    ConnectorAdapterException result = new ConnectorAdapterException(code, message, true);
    result.initCause(cause);
    return result;
  }
}
