package com.openeip.agent.v2.application;

import com.openeip.agent.v2.domain.AgentPlatformModels.McpCapability;
import com.openeip.agent.v2.domain.AgentPlatformModels.McpServer;
import com.openeip.agent.v2.infrastructure.AgentPlatformStore;
import com.openeip.agent.v2.infrastructure.McpGatewayClient;
import com.openeip.agent.v2.shared.AgentPlatformException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class McpGovernanceService {
  private static final Set<String> AUTH = Set.of("NONE", "OAUTH2", "BEARER_REF", "MTLS_REF");
  private final AgentPlatformStore store;
  private final McpGatewayClient gateway;
  private final Clock clock;

  @Autowired
  public McpGovernanceService(AgentPlatformStore store, McpGatewayClient gateway) {
    this(store, gateway, Clock.systemUTC());
  }

  McpGovernanceService(AgentPlatformStore store, McpGatewayClient gateway, Clock clock) {
    this.store = store;
    this.gateway = gateway;
    this.clock = clock;
  }

  @Transactional
  public McpServer create(
      String actorId,
      String idempotencyKey,
      String name,
      String transport,
      String endpoint,
      String authType,
      String credentialRef) {
    AgentPlatformSupport.uuid(actorId);
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    String safeTransport =
        AgentPlatformSupport.requiredText(transport, "MCP transport", 24).toUpperCase();
    String safeAuth =
        AgentPlatformSupport.requiredText(authType, "MCP auth type", 24).toUpperCase();
    if (!Set.of("STDIO", "STREAMABLE_HTTP").contains(safeTransport) || !AUTH.contains(safeAuth)) {
      throw AgentPlatformException.invalid("Unsupported MCP transport or authentication");
    }
    String safeEndpoint = validateEndpoint(safeTransport, endpoint);
    String safeCredential = credential(credentialRef, safeAuth);
    Instant now = clock.instant();
    McpServer value =
        new McpServer(
            UUID.randomUUID().toString(),
            actorId,
            AgentPlatformSupport.requiredText(name, "MCP Server name", 120),
            safeTransport,
            safeEndpoint,
            safeAuth,
            safeCredential,
            "REGISTERED",
            0,
            now,
            now,
            null);
    store.insertMcpServer(value);
    return value;
  }

  @Transactional(readOnly = true)
  public List<McpServer> list(String actorId, int limit) {
    AgentPlatformSupport.uuid(actorId);
    return store.mcpServers(actorId, AgentPlatformSupport.limit(limit));
  }

  @Transactional(readOnly = true)
  public McpServer get(String actorId, String id) {
    return store
        .mcpServer(AgentPlatformSupport.uuid(id), AgentPlatformSupport.uuid(actorId))
        .orElseThrow(AgentPlatformException::notFound);
  }

  @Transactional
  public McpServer update(String actorId, String id, String ifMatch, String name, String endpoint) {
    McpServer current = get(actorId, id);
    long revision = AgentPlatformSupport.revision(ifMatch);
    String safeName =
        name == null
            ? current.name()
            : AgentPlatformSupport.requiredText(name, "MCP Server name", 120);
    String safeEndpoint =
        endpoint == null ? current.endpoint() : validateEndpoint(current.transport(), endpoint);
    if (!store.updateMcpServer(
        id,
        actorId,
        revision,
        safeName,
        safeEndpoint,
        current.status(),
        current.disabledAt(),
        clock.instant())) {
      throw AgentPlatformException.precondition("MCP Server revision is stale");
    }
    return get(actorId, id);
  }

  @Transactional
  public McpServer disable(String actorId, String id, String ifMatch, String idempotencyKey) {
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    McpServer current = get(actorId, id);
    Instant now = clock.instant();
    if (!store.updateMcpServer(
        id,
        actorId,
        AgentPlatformSupport.revision(ifMatch),
        current.name(),
        current.endpoint(),
        "DISABLED",
        now,
        now)) {
      throw AgentPlatformException.precondition("MCP Server revision is stale");
    }
    return get(actorId, id);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> test(
      String actorId, String id, String ifMatch, String idempotencyKey) {
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    McpServer current = get(actorId, id);
    if (current.revision() != AgentPlatformSupport.revision(ifMatch)) {
      throw AgentPlatformException.precondition("MCP Server revision is stale");
    }
    validateEndpoint(current.transport(), current.endpoint());
    return Map.of("serverId", id, "policyStatus", "PASS", "secretResolved", false);
  }

  @Transactional
  public List<McpCapability> discover(
      String actorId, String id, String ifMatch, String idempotencyKey) {
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    McpServer current = get(actorId, id);
    if (current.revision() != AgentPlatformSupport.revision(ifMatch)
        || "DISABLED".equals(current.status())) {
      throw AgentPlatformException.precondition("MCP Server revision is stale or disabled");
    }
    var result = gateway.discover(current);
    if (!"PASS".equals(result.policyStatus())) {
      throw AgentPlatformException.conflict("MCP egress policy rejected the Server");
    }
    store.replaceCapabilities(id, result.capabilities());
    return store.capabilities(id);
  }

  @Transactional
  public Map<String, Object> mapTool(
      String actorId,
      String serverId,
      String capabilityId,
      String toolVersionId,
      String ifMatch,
      String idempotencyKey) {
    AgentPlatformSupport.idempotencyKey(idempotencyKey);
    McpServer server = get(actorId, serverId);
    if (server.revision() != AgentPlatformSupport.revision(ifMatch)) {
      throw AgentPlatformException.precondition("MCP Server revision is stale");
    }
    McpCapability capability =
        store
            .capability(AgentPlatformSupport.uuid(capabilityId), serverId)
            .orElseThrow(AgentPlatformException::notFound);
    store
        .tool(AgentPlatformSupport.uuid(toolVersionId))
        .orElseThrow(AgentPlatformException::notFound);
    String id = UUID.randomUUID().toString();
    store.insertMcpToolMapping(
        id, capability.id(), toolVersionId, capability.schemaDigest(), actorId, clock.instant());
    return Map.of(
        "id",
        id,
        "capabilityId",
        capability.id(),
        "toolVersionId",
        toolVersionId,
        "status",
        "ACTIVE");
  }

  private static String credential(String value, String auth) {
    if ("NONE".equals(auth)) {
      if (value != null && !value.isBlank()) {
        throw AgentPlatformException.invalid("Credential reference is not valid for NONE auth");
      }
      return null;
    }
    if (value == null || !value.matches("^secret://[A-Za-z0-9/_-]{1,240}$")) {
      throw AgentPlatformException.invalid("MCP credentials must use a secret:// reference");
    }
    return value;
  }

  private static String validateEndpoint(String transport, String value) {
    String endpoint = AgentPlatformSupport.requiredText(value, "MCP endpoint", 2048);
    if ("STDIO".equals(transport)) {
      if (!endpoint.matches("^managed://[A-Za-z0-9._/-]{1,240}$")) {
        throw AgentPlatformException.invalid("STDIO requires a managed:// endpoint");
      }
      return endpoint;
    }
    try {
      URI uri = URI.create(endpoint);
      if (uri.getUserInfo() != null
          || uri.getHost() == null
          || uri.getFragment() != null
          || uri.getPort() == 0
          || (!"https".equals(uri.getScheme()) && !loopbackHttp(uri))) {
        throw AgentPlatformException.invalid("Unsafe MCP HTTP endpoint");
      }
      if (!isLoopbackHost(uri.getHost())) {
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
          if (address.isAnyLocalAddress()
              || address.isLoopbackAddress()
              || address.isLinkLocalAddress()
              || address.isSiteLocalAddress()
              || address.isMulticastAddress()) {
            throw AgentPlatformException.invalid("MCP endpoint resolves to a private address");
          }
        }
      }
      return uri.toASCIIString();
    } catch (AgentPlatformException exception) {
      throw exception;
    } catch (Exception exception) {
      throw AgentPlatformException.invalid("Invalid MCP endpoint");
    }
  }

  private static boolean loopbackHttp(URI uri) {
    return "http".equals(uri.getScheme()) && isLoopbackHost(uri.getHost());
  }

  private static boolean isLoopbackHost(String host) {
    return "localhost".equalsIgnoreCase(host)
        || "127.0.0.1".equals(host)
        || "::1".equals(host)
        || "[::1]".equals(host);
  }
}
