package com.openeip.connector.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.shared.ConnectorException;
import com.openeip.connector.spi.SecretResolver;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentSecretResolver implements SecretResolver {
  private static final Pattern REFERENCE = Pattern.compile("secret://env/([A-Z][A-Z0-9_]{0,63})");
  private static final String PREFIX = "OPENEIP_CONNECTOR_SECRET_";
  private final ObjectMapper mapper;
  private final Function<String, String> environment;

  @Autowired
  public EnvironmentSecretResolver(ObjectMapper mapper) {
    this(mapper, System::getenv);
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The mapper and environment accessor are application-scoped collaborators.")
  EnvironmentSecretResolver(ObjectMapper mapper, Function<String, String> environment) {
    this.mapper = mapper;
    this.environment = environment;
  }

  @Override
  public Map<String, String> resolve(String tenantId, String credentialRef) {
    if (credentialRef == null || credentialRef.isBlank()) {
      return Map.of();
    }
    Matcher matcher = REFERENCE.matcher(credentialRef);
    if (!matcher.matches()) {
      throw ConnectorException.invalid("Unsupported credential reference");
    }
    String raw = environment.apply(PREFIX + matcher.group(1));
    if (raw == null || raw.isBlank()) {
      throw ConnectorException.invalid("Connector credential is not configured");
    }
    try {
      JsonNode value = mapper.readTree(raw);
      if (!value.isObject()) {
        throw ConnectorException.invalid("Connector credential must be a JSON object");
      }
      Map<String, String> result = new HashMap<>();
      Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (!field.getValue().isTextual()) {
          throw ConnectorException.invalid("Connector credential values must be strings");
        }
        result.put(field.getKey(), field.getValue().textValue());
      }
      return Map.copyOf(result);
    } catch (ConnectorException exception) {
      throw exception;
    } catch (Exception exception) {
      throw ConnectorException.invalid("Connector credential is invalid JSON");
    }
  }
}
