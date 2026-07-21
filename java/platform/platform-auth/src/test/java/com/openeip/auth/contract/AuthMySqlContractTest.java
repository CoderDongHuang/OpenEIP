package com.openeip.auth.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.openeip.auth.domain.entity.Role;
import com.openeip.auth.domain.repository.RoleRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthMySqlContractTest {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(
              DockerImageName.parse(
                      "mysql:8.4.4@sha256:23818b7d7de427096ab1427b2e3d9d5e14a5b933f9a4431a482d6414bc879091")
                  .asCompatibleSubstituteFor("mysql"))
          .withDatabaseName("openeip_auth_contract")
          .withUsername("openeip")
          .withPassword("contract-password");

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("openeip.jwt.allow-ephemeral-key", () -> "true");
    registry.add("openeip.auth.rate-limit.requests", () -> "1000");
  }

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private RoleRepository roleRepository;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void emptyDatabaseMigrationCreatesTablesForeignKeysAndUniqueConstraints() {
    Integer tableCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name LIKE 'auth_%'
            """,
            Integer.class);
    Integer foreignKeyCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.referential_constraints
            WHERE constraint_schema = DATABASE()
            """,
            Integer.class);
    assertThat(tableCount).isEqualTo(6);
    assertThat(foreignKeyCount).isEqualTo(5);
    assertThat(roleRepository.findByName("ROLE_USER")).isPresent();

    assertThatThrownBy(
            () ->
                roleRepository.saveAndFlush(
                    Role.builder().name("ROLE_USER").description("duplicate").build()))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO auth_refresh_tokens
                    (id, user_id, token_hash, expires_at, created_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """,
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "a".repeat(64)))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void apiContractAndRefreshReplayMatchOpenApi() throws Exception {
    assertOpenApiPaths();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Map<String, String> credentials =
        Map.of(
            "username", "mysql" + suffix,
            "email", "mysql" + suffix + "@openeip.org",
            "password", "password123");
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(credentials)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.id").isString())
        .andExpect(jsonPath("$.requestId").isString())
        .andExpect(jsonPath("$.timestamp").isString());

    String loginResponse =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "username",
                                credentials.get("username"),
                                "password",
                                credentials.get("password")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode tokens = objectMapper.readTree(loginResponse).path("data");
    String refreshRequest =
        objectMapper.writeValueAsString(
            Map.of("refreshToken", tokens.path("refreshToken").asText()));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshRequest))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshRequest))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-E-003"));
  }

  private static void assertOpenApiPaths() throws Exception {
    Path specification = findRepositoryRoot().resolve("docs/06-api/auth-v1.openapi.yaml");
    assertThat(Files.isRegularFile(specification)).isTrue();
    JsonNode paths =
        new ObjectMapper(new YAMLFactory()).readTree(specification.toFile()).path("paths");
    Set<String> operations =
        Set.of(
            "/auth/register#post",
            "/auth/login#post",
            "/auth/refresh#post",
            "/auth/me#get",
            "/auth/roles#get",
            "/auth/roles#post",
            "/auth/users/{id}/roles#put");
    for (String operation : operations) {
      String[] parts = operation.split("#");
      assertThat(paths.path(parts[0]).has(parts[1])).as(operation).isTrue();
    }
    assertThat(paths.size()).isEqualTo(6);
  }

  private static Path findRepositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null && !Files.isDirectory(current.resolve("docs/06-api"))) {
      current = current.getParent();
    }
    if (current == null) {
      throw new IllegalStateException("Unable to locate repository root");
    }
    return current;
  }
}
