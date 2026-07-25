package com.openeip.connector.spi;

import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.shared.ConnectorException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
    value = "CT_CONSTRUCTOR_THROW",
    justification = "Duplicate SPI registrations must abort application startup.")
public class ConnectorRegistry {
  private final Map<ConnectorType, ConnectorSpi> connectors;

  public ConnectorRegistry(List<ConnectorSpi> implementations) {
    EnumMap<ConnectorType, ConnectorSpi> registered = new EnumMap<>(ConnectorType.class);
    for (ConnectorSpi connector : implementations) {
      ConnectorType type = connector.getMetadata().type();
      if (registered.putIfAbsent(type, connector) != null) {
        throw new IllegalStateException("Duplicate connector SPI: " + type);
      }
    }
    connectors = Map.copyOf(registered);
  }

  public ConnectorSpi require(ConnectorType type) {
    ConnectorSpi connector = connectors.get(type);
    if (connector == null) {
      throw ConnectorException.conflict("Connector adapter is not installed: " + type);
    }
    return connector;
  }

  public List<ConnectorMetadata> metadata() {
    return connectors.values().stream().map(ConnectorSpi::getMetadata).toList();
  }

  public Set<ConnectorType> installedTypes() {
    return connectors.keySet();
  }
}
