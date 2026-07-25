package com.openeip.connector.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.openeip.connector.domain.ConnectorStatus;
import com.openeip.connector.domain.ConnectorType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConnectorInstanceTest {
  private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");

  @Test
  void supportsLifecycleAndExposesMetadata() {
    ConnectorInstance value =
        new ConnectorInstance(
            "id",
            "tenant",
            "owner",
            "name",
            ConnectorType.EMAIL,
            "{\"host\":\"smtp\"}",
            "secret://mail",
            NOW);
    assertThat(value.getId()).isEqualTo("id");
    assertThat(value.getTenantId()).isEqualTo("tenant");
    assertThat(value.getOwnerId()).isEqualTo("owner");
    assertThat(value.getName()).isEqualTo("name");
    assertThat(value.getType()).isEqualTo(ConnectorType.EMAIL);
    assertThat(value.getStatus()).isEqualTo(ConnectorStatus.PAUSED);
    assertThat(value.getConfigJson()).contains("smtp");
    assertThat(value.getCredentialRef()).isEqualTo("secret://mail");
    assertThat(value.getCreatedAt()).isEqualTo(NOW);
    value.update("new", "{\"host\":\"smtp2\"}", null, NOW.plusSeconds(1));
    value.activate(NOW.plusSeconds(2));
    assertThat(value.getStatus()).isEqualTo(ConnectorStatus.ACTIVE);
    assertThat(value.getLastError()).isNull();
    value.healthFailure("offline", NOW.plusSeconds(3));
    assertThat(value.getStatus()).isEqualTo(ConnectorStatus.ERROR);
    assertThat(value.getLastError()).isEqualTo("offline");
    assertThat(value.getLastHealthAt()).isEqualTo(NOW.plusSeconds(3));
    value.pause(NOW.plusSeconds(4));
    assertThat(value.getStatus()).isEqualTo(ConnectorStatus.PAUSED);
    value.delete(NOW.plusSeconds(5));
    assertThat(value.getDeletedAt()).isEqualTo(NOW.plusSeconds(5));
    assertThat(value.getUpdatedAt()).isEqualTo(NOW.plusSeconds(5));
  }
}
