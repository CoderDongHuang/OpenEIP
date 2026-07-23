package com.openeip.auth.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class AuthMigrationRollbackTest {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(
              DockerImageName.parse(
                      "mysql:8.4.10@sha256:5700b0892591a760c4caef7a0024c887afd46317d73dd420801706e661c4db56")
                  .asCompatibleSubstituteFor("mysql"))
          .withDatabaseName("openeip_auth_rollback")
          .withUsername("openeip")
          .withPassword("rollback-password");

  @Test
  void rollbackRemovesAllAuthTablesAfterMigration() throws Exception {
    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection =
        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
      assertThat(authTableCount(connection)).isEqualTo(6);
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/rollback/U2.0.0__init_auth_schema.sql"));
      assertThat(authTableCount(connection)).isZero();
    }
  }

  private static int authTableCount(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name LIKE 'auth_%'
                """)) {
      result.next();
      return result.getInt(1);
    }
  }
}
