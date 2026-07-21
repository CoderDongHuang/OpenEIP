package com.openeip.document.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openeip.document.DocumentTestApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = DocumentTestApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:document_api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
@AutoConfigureMockMvc
class DocumentFileIntegrationTest {

  private static final String OWNER = "11111111-1111-1111-1111-111111111111";

  @TempDir static Path storageRoot;

  @DynamicPropertySource
  static void storageProperties(DynamicPropertyRegistry registry) {
    registry.add("openeip.document.storage-root", storageRoot::toString);
    registry.add("openeip.document.max-size-bytes", () -> "1024");
  }

  @Autowired MockMvc mockMvc;

  @Test
  void authenticatedFileLifecycleEnforcesOwnership() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
    String response =
        mockMvc
            .perform(multipart("/api/v1/documents/files").file(file).with(user(OWNER)).with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.sha256").isString())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(response)
            .at("/data/id")
            .asText();

    mockMvc
        .perform(get("/api/v1/documents/files").with(user(OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    mockMvc
        .perform(get("/api/v1/documents/files/{id}", id).with(user("other-user")))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/documents/files/{id}/content", id).with(user(OWNER)))
        .andExpect(status().isOk())
        .andExpect(content().bytes("hello".getBytes(StandardCharsets.UTF_8)));
    mockMvc
        .perform(delete("/api/v1/documents/files/{id}", id).with(user(OWNER)).with(csrf()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/v1/documents/files/{id}", id).with(user(OWNER)))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsAnonymousUnsupportedEmptyAndOversizedUploads() throws Exception {
    mockMvc.perform(get("/api/v1/documents/files")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/documents/files?page=0").with(user(OWNER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DOC-V-001"));
    mockMvc
        .perform(
            multipart("/api/v1/documents/files")
                .file(
                    new MockMultipartFile(
                        "file",
                        "malware.exe",
                        "application/octet-stream",
                        "x".getBytes(StandardCharsets.UTF_8)))
                .with(user(OWNER))
                .with(csrf()))
        .andExpect(status().isUnsupportedMediaType());
    mockMvc
        .perform(
            multipart("/api/v1/documents/files")
                .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                .with(user(OWNER))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            multipart("/api/v1/documents/files")
                .file(new MockMultipartFile("file", "large.txt", "text/plain", new byte[1025]))
                .with(user(OWNER))
                .with(csrf()))
        .andExpect(status().isPayloadTooLarge());
  }
}
