package com.openeip.agent.v2.support;

import com.openeip.agent.v2.infrastructure.AgentPlatformStore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

public final class AgentV2TestDatabase {
  private AgentV2TestDatabase() {}

  public static Database create() {
    try {
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL(
          "jdbc:h2:mem:agentv2-"
              + UUID.randomUUID()
              + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      String migration =
          new ClassPathResource("db/migration/V2.6.0__init_agent_platform_schema.sql")
              .getContentAsString(StandardCharsets.UTF_8);
      try (var connection = dataSource.getConnection()) {
        ScriptUtils.executeSqlScript(
            connection,
            new ByteArrayResource(h2Compatible(migration).getBytes(StandardCharsets.UTF_8)));
      }
      return new Database(jdbc, new AgentPlatformStore(jdbc));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to create Agent v2 test database", exception);
    }
  }

  private static String h2Compatible(String mysql) {
    String transformed =
        mysql
            .replaceAll("(?m)^\\s*KEY [^\\r\\n]+,?\\r?\\n", "")
            .replaceAll("UNIQUE KEY ([A-Za-z0-9_]+) \\(", "CONSTRAINT $1 UNIQUE (")
            .replaceAll(
                "\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;", ");");
    return transformed.replaceAll(",\\s*\\);", "\n);");
  }

  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "The test fixture intentionally returns its in-memory collaborators.")
  public record Database(JdbcTemplate jdbc, AgentPlatformStore store) {}
}
