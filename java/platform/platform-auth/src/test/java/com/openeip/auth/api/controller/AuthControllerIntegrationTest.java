package com.openeip.auth.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.auth.domain.entity.Role;
import com.openeip.auth.domain.entity.User;
import com.openeip.auth.domain.repository.RoleRepository;
import com.openeip.auth.domain.repository.UserRepository;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;

  @Test
  void registerLoginRefreshAndCurrentUserContract() throws Exception {
    Credentials credentials = uniqueCredentials("flow");
    register(credentials)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.requestId").isString())
        .andExpect(header().exists("X-Request-ID"));

    JsonNode tokens = login(credentials);
    String accessToken = tokens.path("accessToken").asText();
    String refreshToken = tokens.path("refreshToken").asText();
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value(credentials.username()))
        .andExpect(jsonPath("$.data.roles[0]").value("ROLE_USER"));

    String refreshBody = objectMapper.writeValueAsString(new RefreshBody(refreshToken));
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isString())
        .andExpect(jsonPath("$.data.refreshToken").isString());

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-E-003"));
  }

  @Test
  void returnsJsonForValidationAuthenticationAndAuthorizationFailures() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"?\",\"email\":\"bad\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH-V-001"))
        .andExpect(jsonPath("$.requestId").isString());

    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-E-003"));

    Credentials credentials = uniqueCredentials("forbidden");
    register(credentials).andExpect(status().isCreated());
    String access = login(credentials).path("accessToken").asText();
    mockMvc
        .perform(get("/api/v1/auth/roles").header("Authorization", "Bearer " + access))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH-P-001"));
  }

  @Test
  void adminCanListCreateAndAssignRoles() throws Exception {
    Credentials adminCredentials = uniqueCredentials("admin");
    Credentials targetCredentials = uniqueCredentials("target");
    register(adminCredentials).andExpect(status().isCreated());
    register(targetCredentials).andExpect(status().isCreated());
    promoteToAdmin(adminCredentials.username());
    String adminAccess = login(adminCredentials).path("accessToken").asText();

    mockMvc
        .perform(get("/api/v1/auth/roles").header("Authorization", "Bearer " + adminAccess))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(3));

    String roleName = "ROLE_TEAM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    mockMvc
        .perform(
            post("/api/v1/auth/roles")
                .header("Authorization", "Bearer " + adminAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateRoleBody(roleName, "Team role", new String[] {"document:read"}))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.name").value(roleName));

    User target = userRepository.findByUsername(targetCredentials.username()).orElseThrow();
    mockMvc
        .perform(
            get("/api/v1/auth/users")
                .header("Authorization", "Bearer " + adminAccess)
                .param("page", "1")
                .param("pageSize", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].password").doesNotExist())
        .andExpect(jsonPath("$.data.total").isNumber());

    mockMvc
        .perform(
            patch("/api/v1/auth/users/{id}/active", target.getId())
                .header("Authorization", "Bearer " + adminAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(false));

    mockMvc
        .perform(
            put("/api/v1/auth/users/{id}/roles", target.getId())
                .header("Authorization", "Bearer " + adminAccess)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new AssignRolesBody(new String[] {roleName}))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roles[0]").value(roleName));
  }

  @Test
  void disabledUserCannotLoginRefreshOrReuseAccessToken() throws Exception {
    Credentials credentials = uniqueCredentials("disabled");
    register(credentials).andExpect(status().isCreated());
    JsonNode tokens = login(credentials);
    User user = userRepository.findByUsername(credentials.username()).orElseThrow();
    user.setActive(false);
    userRepository.saveAndFlush(user);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginBody(credentials.username(), credentials.password()))))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RefreshBody(tokens.path("refreshToken").asText()))))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + tokens.path("accessToken").asText()))
        .andExpect(status().isUnauthorized());
  }

  private org.springframework.test.web.servlet.ResultActions register(Credentials credentials)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(credentials)));
  }

  private JsonNode login(Credentials credentials) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginBody(credentials.username(), credentials.password()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).path("data");
  }

  private void promoteToAdmin(String username) {
    User admin = userRepository.findByUsername(username).orElseThrow();
    Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
    admin.setRoles(new HashSet<>(java.util.Set.of(adminRole)));
    userRepository.saveAndFlush(admin);
  }

  private static Credentials uniqueCredentials(String prefix) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return new Credentials(prefix + suffix, prefix + suffix + "@openeip.org", "password123");
  }

  private record Credentials(String username, String email, String password) {}

  private record RefreshBody(String refreshToken) {}

  private record LoginBody(String username, String password) {}

  private record CreateRoleBody(String name, String description, String[] permissionCodes) {}

  private record AssignRolesBody(String[] roleNames) {}
}
