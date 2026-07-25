package com.openeip.connector.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.shared.ConnectorAdapterException;
import com.openeip.connector.spi.ConnectorConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class VendorToken {
  private VendorToken() {}

  static String feishu(HttpClient client, ObjectMapper mapper, ConnectorConfig config) {
    String appId = required(config, "appId");
    String appSecret = required(config, "appSecret");
    JsonNode body = mapper.createObjectNode().put("app_id", appId).put("app_secret", appSecret);
    JsonNode response =
        post(client, mapper, config, "/open-apis/auth/v3/tenant_access_token/internal", body);
    String token = response.path("tenant_access_token").asText("");
    if (token.isBlank()) {
      throw new ConnectorAdapterException("CONN-AUTH", "Feishu token was not returned", false);
    }
    return token;
  }

  static String wecom(HttpClient client, ObjectMapper mapper, ConnectorConfig config) {
    String corpId = required(config, "corpId");
    String secret = required(config, "corpSecret");
    String path = "/cgi-bin/gettoken?corpid=" + encode(corpId) + "&corpsecret=" + encode(secret);
    JsonNode response = get(client, mapper, config, path);
    String token = response.path("access_token").asText("");
    if (token.isBlank()) {
      throw new ConnectorAdapterException("CONN-AUTH", "WeCom token was not returned", false);
    }
    return token;
  }

  private static JsonNode post(
      HttpClient client, ObjectMapper mapper, ConnectorConfig config, String path, JsonNode body) {
    return send(client, mapper, config, "POST", path, body);
  }

  private static JsonNode get(
      HttpClient client, ObjectMapper mapper, ConnectorConfig config, String path) {
    return send(client, mapper, config, "GET", path, null);
  }

  private static JsonNode send(
      HttpClient client,
      ObjectMapper mapper,
      ConnectorConfig config,
      String method,
      String path,
      JsonNode body) {
    try {
      URI endpoint = URI.create(config.values().path("endpoint").asText());
      URI uri = endpoint.resolve(path.substring(1));
      HttpRequest.Builder request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(20))
              .header("Accept", "application/json");
      if ("GET".equals(method)) {
        request.GET();
      } else {
        request
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
      }
      HttpResponse<String> response =
          client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ConnectorAdapterException(
            "CONN-AUTH", "Vendor token request failed", response.statusCode() >= 500);
      }
      return mapper.readTree(response.body());
    } catch (ConnectorAdapterException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ConnectorAdapterException("CONN-AUTH", "Vendor token request failed", true);
    }
  }

  private static String required(ConnectorConfig config, String name) {
    String value = config.credentials().get(name);
    if (value == null || value.isBlank()) {
      throw new ConnectorAdapterException(
          "CONN-CREDENTIAL", "Missing vendor credential: " + name, false);
    }
    return value;
  }

  private static String encode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
