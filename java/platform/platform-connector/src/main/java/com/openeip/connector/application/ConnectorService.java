package com.openeip.connector.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorStatus;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.domain.entity.ConnectorInstance;
import com.openeip.connector.domain.repository.ConnectorInstanceRepository;
import com.openeip.connector.shared.ConnectorException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectorService {
  public static final String TENANT = "default";
  private final ConnectorInstanceRepository connectors;
  private final ObjectMapper mapper;
  private final Clock clock;

  @Autowired
  public ConnectorService(ConnectorInstanceRepository connectors, ObjectMapper mapper) {
    this(connectors, mapper, Clock.systemUTC());
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected collaborators are application scoped.")
  ConnectorService(ConnectorInstanceRepository connectors, ObjectMapper mapper, Clock clock) {
    this.connectors = connectors;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional
  public ConnectorInstance create(
      String ownerId, String name, ConnectorType type, JsonNode config, String credentialRef) {
    String validName = name(name);
    if (connectors.existsByTenantIdAndOwnerIdAndNameAndDeletedAtIsNull(
        TENANT, ownerId, validName)) {
      throw ConnectorException.conflict("Connector name already exists");
    }
    String canonical = canonicalConfig(type, config);
    return connectors.save(
        new ConnectorInstance(
            UUID.randomUUID().toString(),
            TENANT,
            ownerId,
            validName,
            type,
            canonical,
            credential(credentialRef),
            clock.instant()));
  }

  @Transactional(readOnly = true)
  public Page<ConnectorInstance> list(String ownerId, int page, int size) {
    if (page < 1 || size < 1 || size > 100) {
      throw ConnectorException.invalid("Invalid page");
    }
    return connectors.findOwned(
        TENANT, ownerId, PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
  }

  @Transactional(readOnly = true)
  public ConnectorInstance get(String ownerId, String id) {
    ConnectorInstance connector = find(id);
    if (!connector.getOwnerId().equals(ownerId)) {
      throw ConnectorException.forbidden();
    }
    return connector;
  }

  @Transactional
  public ConnectorInstance update(
      String ownerId, String id, String name, JsonNode config, String credentialRef) {
    ConnectorInstance connector = get(ownerId, id);
    String validName = name(name);
    if (connectors.existsByTenantIdAndOwnerIdAndNameAndIdNotAndDeletedAtIsNull(
        TENANT, ownerId, validName, id)) {
      throw ConnectorException.conflict("Connector name already exists");
    }
    connector.update(
        validName,
        canonicalConfig(connector.getType(), config),
        credential(credentialRef),
        clock.instant());
    return connector;
  }

  @Transactional
  public ConnectorInstance setStatus(String ownerId, String id, ConnectorStatus status) {
    ConnectorInstance connector = get(ownerId, id);
    if (status == ConnectorStatus.ACTIVE) {
      connector.activate(clock.instant());
    } else if (status == ConnectorStatus.PAUSED) {
      connector.pause(clock.instant());
    } else {
      throw ConnectorException.invalid("ERROR is assigned by a health check");
    }
    return connector;
  }

  @Transactional
  public void delete(String ownerId, String id) {
    get(ownerId, id).delete(clock.instant());
  }

  ConnectorInstance find(String id) {
    try {
      UUID.fromString(id);
    } catch (Exception exception) {
      throw ConnectorException.invalid("Invalid connector identifier");
    }
    return connectors
        .findByIdAndTenantIdAndDeletedAtIsNull(id, TENANT)
        .orElseThrow(ConnectorException::notFound);
  }

  private String canonicalConfig(ConnectorType type, JsonNode config) {
    if (type == null || config == null || !config.isObject()) {
      throw ConnectorException.invalid("Connector type and object config are required");
    }
    if (containsSecret(config)) {
      throw ConnectorException.invalid("Secrets must be stored in a credential reference");
    }
    String required =
        switch (type) {
          case MYSQL, POSTGRESQL, ORACLE, SAP, REDIS, EMAIL -> "host";
          case KAFKA -> "bootstrapServers";
          case GITHUB, GITLAB, JIRA, CONFLUENCE -> "url";
          case FEISHU -> "appId";
          case WECOM -> "corpId";
          case MINIO, OSS, WEBHOOK -> "endpoint";
        };
    if (!config.hasNonNull(required) || config.path(required).asText().isBlank()) {
      throw ConnectorException.invalid("Missing connector config: " + required);
    }
    try {
      return mapper.writeValueAsString(config);
    } catch (Exception exception) {
      throw ConnectorException.invalid("Connector config is not serializable");
    }
  }

  private static boolean containsSecret(JsonNode node) {
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        String key = field.getKey().toLowerCase(Locale.ROOT);
        if (key.contains("password") || key.contains("secret") || key.contains("token")) {
          return true;
        }
        if (containsSecret(field.getValue())) {
          return true;
        }
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        if (containsSecret(child)) {
          return true;
        }
      }
    }
    return false;
  }

  private static String name(String value) {
    if (value == null || value.trim().isEmpty() || value.trim().length() > 120) {
      throw ConnectorException.invalid("Connector name must contain 1 to 120 characters");
    }
    return value.trim();
  }

  private static String credential(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    if (!value.startsWith("secret://") || value.length() > 255) {
      throw ConnectorException.invalid("credentialRef must use the secret:// scheme");
    }
    return value;
  }
}
