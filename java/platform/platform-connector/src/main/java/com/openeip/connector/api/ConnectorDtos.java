package com.openeip.connector.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorStatus;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.domain.entity.ConnectorInstance;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class ConnectorDtos {
  private ConnectorDtos() {}

  public record CreateRequest(
      String name, ConnectorType type, JsonNode config, String credentialRef) {}

  public record UpdateRequest(String name, JsonNode config, String credentialRef) {}

  public record StatusRequest(ConnectorStatus status) {}

  public record Response(
      String id,
      String name,
      ConnectorType type,
      ConnectorStatus status,
      JsonNode config,
      String credentialRef,
      Instant lastHealthAt,
      String lastError,
      Instant createdAt,
      Instant updatedAt) {
    public static Response from(ConnectorInstance value, ObjectMapper mapper) {
      try {
        return new Response(
            value.getId(),
            value.getName(),
            value.getType(),
            value.getStatus(),
            mapper.readTree(value.getConfigJson()),
            value.getCredentialRef(),
            value.getLastHealthAt(),
            value.getLastError(),
            value.getCreatedAt(),
            value.getUpdatedAt());
      } catch (Exception exception) {
        throw new IllegalStateException("Stored connector config is invalid", exception);
      }
    }
  }

  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "The response takes an immutable snapshot of page contents.")
  public record PageResponse(List<Response> items, int page, int size, long total, int totalPages) {
    public PageResponse {
      items = List.copyOf(items);
    }

    public static PageResponse from(Page<Response> page) {
      return new PageResponse(
          page.getContent(),
          page.getNumber() + 1,
          page.getSize(),
          page.getTotalElements(),
          page.getTotalPages());
    }
  }
}
