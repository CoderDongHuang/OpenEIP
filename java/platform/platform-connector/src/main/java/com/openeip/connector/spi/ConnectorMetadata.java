package com.openeip.connector.spi;

import com.openeip.connector.domain.ConnectorType;

public record ConnectorMetadata(
    ConnectorType type,
    String name,
    String version,
    String description,
    boolean readable,
    boolean writable) {}
