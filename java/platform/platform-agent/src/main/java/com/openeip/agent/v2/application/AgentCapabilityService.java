package com.openeip.agent.v2.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentRun;
import com.openeip.agent.v2.domain.AgentPlatformModels.ToolGrant;
import com.openeip.agent.v2.domain.AgentPlatformModels.ToolVersion;
import com.openeip.agent.v2.shared.AgentPlatformException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@SuppressFBWarnings(
    value = "CT_CONSTRUCTOR_THROW",
    justification = "Fail-fast validation prevents use with an unsafe signing configuration.")
public class AgentCapabilityService {
  private final ObjectMapper mapper;
  private final byte[] secret;
  private final long ttlSeconds;
  private final Clock clock;

  @Autowired
  public AgentCapabilityService(
      ObjectMapper mapper,
      @Value("${openeip.agent.v2.capability-secret:}") String secret,
      @Value("${openeip.agent.v2.capability-ttl-seconds:300}") long ttlSeconds) {
    this(mapper, secret, ttlSeconds, Clock.systemUTC());
  }

  AgentCapabilityService(ObjectMapper mapper, String secret, long ttlSeconds, Clock clock) {
    if (secret == null || secret.length() < 32 || ttlSeconds < 30 || ttlSeconds > 900) {
      throw new IllegalStateException("Agent capability signing is not safely configured");
    }
    this.mapper = mapper;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.ttlSeconds = ttlSeconds;
    this.clock = clock;
  }

  public String issue(
      AgentRun run, String agentDigest, List<ToolGrant> grants, List<ToolVersion> tools) {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("iss", "openeip-java");
    claims.put("aud", "openeip-agent-runtime");
    claims.put("tenantId", "default");
    claims.put("principalId", run.principalId());
    claims.put("runId", run.id());
    claims.put("agentVersionId", run.agentVersionId());
    claims.put("agentDigest", agentDigest);
    claims.put("dependencyDigest", run.dependencyDigest());
    claims.put("resourceHandles", jsonValue(run.resourceHandlesJson()));
    claims.put("budget", jsonValue(run.budgetJson()));
    claims.put(
        "tools",
        grants.stream()
            .map(
                grant -> {
                  ToolVersion tool =
                      tools.stream()
                          .filter(value -> value.id().equals(grant.toolVersionId()))
                          .findFirst()
                          .orElseThrow(AgentPlatformException::notFound);
                  return Map.of(
                      "id", tool.toolKey(),
                      "version", tool.version(),
                      "digest", tool.digest(),
                      "operations", jsonValue(grant.operationsJson()),
                      "approvalMode", grant.approvalMode(),
                      "riskClass", tool.riskClass());
                })
            .toList());
    long now = clock.instant().getEpochSecond();
    claims.put("iat", now);
    claims.put("exp", now + ttlSeconds);
    claims.put("nonce", java.util.UUID.randomUUID().toString());
    try {
      String payload =
          Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(claims));
      return payload + "." + sign(payload);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to issue Agent capability", exception);
    }
  }

  private Object jsonValue(String value) {
    try {
      return mapper.readValue(value, Object.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Stored capability input is invalid", exception);
    }
  }

  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception exception) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
    }
  }
}
