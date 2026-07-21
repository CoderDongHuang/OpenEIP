package com.openeip;

import static org.assertj.core.api.Assertions.assertThat;

import com.openeip.auth.application.service.AuthService;
import com.openeip.document.application.service.DocumentFileService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:platform_app;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "openeip.jwt.allow-ephemeral-key=true",
      "openeip.auth.rate-limit.requests=1000"
    })
class PlatformApplicationTest {

  @TempDir static Path storageRoot;

  @DynamicPropertySource
  static void documentProperties(DynamicPropertyRegistry registry) {
    registry.add("openeip.document.storage-root", storageRoot::toString);
  }

  @Autowired AuthService authService;
  @Autowired DocumentFileService documentFileService;

  @Test
  void composesAuthAndDocumentExactlyOnce() {
    assertThat(authService).isNotNull();
    assertThat(documentFileService).isNotNull();
  }
}
