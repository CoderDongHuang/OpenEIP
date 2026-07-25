package com.openeip.connector.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.adapter.email.EmailConnector;
import com.openeip.connector.adapter.http.ConfluenceConnector;
import com.openeip.connector.adapter.http.FeishuConnector;
import com.openeip.connector.adapter.http.GitHubConnector;
import com.openeip.connector.adapter.http.GitLabConnector;
import com.openeip.connector.adapter.http.JiraConnector;
import com.openeip.connector.adapter.http.WecomConnector;
import com.openeip.connector.adapter.jdbc.MysqlConnector;
import com.openeip.connector.adapter.jdbc.OracleConnector;
import com.openeip.connector.adapter.jdbc.PostgresqlConnector;
import com.openeip.connector.adapter.jdbc.SapHanaConnector;
import com.openeip.connector.adapter.kafka.KafkaConnector;
import com.openeip.connector.adapter.objectstore.MinioConnector;
import com.openeip.connector.adapter.objectstore.OssConnector;
import com.openeip.connector.adapter.redis.RedisConnector;
import com.openeip.connector.adapter.webhook.WebhookConnector;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.spi.ConnectorRegistry;
import com.openeip.connector.spi.ConnectorSpi;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectorAdapterCatalogTest {
  @Test
  void registersEveryReleaseConnectorWithAConfigSchema() {
    ObjectMapper mapper = new ObjectMapper();
    List<ConnectorSpi> adapters =
        List.of(
            new MysqlConnector(mapper),
            new PostgresqlConnector(mapper),
            new OracleConnector(mapper),
            new SapHanaConnector(mapper),
            new RedisConnector(mapper),
            new KafkaConnector(mapper),
            new GitHubConnector(mapper),
            new GitLabConnector(mapper),
            new FeishuConnector(mapper),
            new WecomConnector(mapper),
            new JiraConnector(mapper),
            new ConfluenceConnector(mapper),
            new MinioConnector(mapper),
            new OssConnector(mapper),
            new EmailConnector(mapper),
            new WebhookConnector(mapper));
    ConnectorRegistry registry = new ConnectorRegistry(adapters);
    assertThat(registry.installedTypes()).containsExactlyInAnyOrder(ConnectorType.values());
    assertThat(registry.metadata()).hasSize(16);
    adapters.forEach(adapter -> assertThat(adapter.getConfigSchema()).isNotEmpty());
  }

  @Test
  void rejectsUnsafeHttpAndObjectEndpointsByDefault() {
    ObjectMapper mapper = new ObjectMapper();
    var config =
        new com.openeip.connector.spi.ConnectorConfig(
            mapper.createObjectNode().put("endpoint", "http://127.0.0.1:8080"),
            java.util.Map.of("token", "token"));
    assertThat(new GitHubConnector(mapper).testConnection(config).success()).isFalse();
    assertThat(new MinioConnector(mapper).testConnection(config).success()).isFalse();
  }
}
