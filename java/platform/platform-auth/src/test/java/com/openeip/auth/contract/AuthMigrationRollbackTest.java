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
                      "mysql:8.4.4@sha256:23818b7d7de427096ab1427b2e3d9d5e14a5b933f9a4431a482d6414bc879091")
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
