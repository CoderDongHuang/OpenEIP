package com.openeip.connector.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.shared.ConnectorException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectorRegistryTest {
  @Test
  void registersAndRequiresImplementations() {
    ConnectorSpi mysql = connector(ConnectorType.MYSQL);
    ConnectorRegistry registry = new ConnectorRegistry(List.of(mysql));
    assertThat(registry.require(ConnectorType.MYSQL)).isSameAs(mysql);
    assertThat(registry.metadata())
        .extracting(ConnectorMetadata::type)
        .containsExactly(ConnectorType.MYSQL);
    assertThat(registry.installedTypes()).containsExactly(ConnectorType.MYSQL);
    assertThatThrownBy(() -> registry.require(ConnectorType.KAFKA))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void rejectsDuplicateTypes() {
    assertThatThrownBy(
            () ->
                new ConnectorRegistry(
                    List.of(connector(ConnectorType.MYSQL), connector(ConnectorType.MYSQL))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate");
  }

  private static ConnectorSpi connector(ConnectorType type) {
    ConnectorSpi connector = mock(ConnectorSpi.class);
    when(connector.getMetadata())
        .thenReturn(new ConnectorMetadata(type, type.name(), "1", "", true, true));
    return connector;
  }
}
