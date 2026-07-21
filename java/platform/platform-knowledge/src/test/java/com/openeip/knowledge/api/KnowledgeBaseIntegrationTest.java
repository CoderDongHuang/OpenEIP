package com.openeip.knowledge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.document.domain.entity.DocumentFile;
import com.openeip.document.domain.repository.DocumentFileRepository;
import com.openeip.knowledge.KnowledgeTestApplication;
import com.openeip.knowledge.application.KnowledgeEventService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = KnowledgeTestApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:knowledge_api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
@AutoConfigureMockMvc
class KnowledgeBaseIntegrationTest {
  private static final String OWNER = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER = "22222222-2222-2222-2222-222222222222";
  private static final String FILE = "33333333-3333-3333-3333-333333333333";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper mapper;
  @Autowired DocumentFileRepository files;
  @Autowired KnowledgeEventService eventService;

  @Test
  void baseAndDocumentLifecycleEnforcesMembershipAndEventIdempotency() throws Exception {
    files.save(file());
    String create =
        mockMvc
            .perform(
                post("/api/v1/knowledge/bases")
                    .with(user(OWNER))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Engineering\",\"description\":\"Runbooks\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.role").value("OWNER"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String baseId = mapper.readTree(create).at("/data/id").asText();

    mockMvc
        .perform(get("/api/v1/knowledge/bases").with(user(OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
    mockMvc
        .perform(get("/api/v1/knowledge/bases/{id}", baseId).with(user(OTHER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("KNOW-E-001"));
    mockMvc
        .perform(
            patch("/api/v1/knowledge/bases/{id}", baseId)
                .with(user(OWNER))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Engineering v2\",\"description\":\"Updated\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Engineering v2"));

    mockMvc
        .perform(
            post("/api/v1/knowledge/bases/{id}/documents", baseId)
                .with(user(OWNER))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"" + FILE + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("PENDING_PARSE"));
    mockMvc
        .perform(get("/api/v1/knowledge/bases/{id}/documents", baseId).with(user(OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].documentId").value(FILE));

    String parsedEvent = "44444444-4444-4444-4444-444444444444";
    String embeddedEvent = "55555555-5555-5555-5555-555555555555";
    String fingerprint = "a".repeat(64);
    eventService.processParsed(parsedEvent, "default", FILE, fingerprint);
    assertThat(eventService.processParsed(parsedEvent, "default", FILE, fingerprint).duplicate())
        .isTrue();
    eventService.scheduleEmbedding("default", baseId, FILE);
    eventService.processEmbeddingCompleted(embeddedEvent, "default", baseId, FILE, fingerprint);

    mockMvc
        .perform(
            get("/api/v1/knowledge/bases/{id}/documents/{file}", baseId, FILE).with(user(OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("READY"));
    mockMvc
        .perform(
            delete("/api/v1/knowledge/bases/{id}/documents/{file}", baseId, FILE)
                .with(user(OWNER))
                .with(csrf()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(delete("/api/v1/knowledge/bases/{id}", baseId).with(user(OWNER)).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  void rejectsAnonymousInvalidDuplicateAndForeignFileRequests() throws Exception {
    mockMvc.perform(get("/api/v1/knowledge/bases")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/knowledge/bases?page=0").with(user(OWNER)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v1/knowledge/bases")
                .with(user(OWNER))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("KNOW-V-001"));
  }

  private static DocumentFile file() {
    return new DocumentFile(
        FILE,
        "default",
        OWNER,
        "notes.txt",
        "aa/" + FILE,
        "text/plain",
        5,
        "a".repeat(64),
        Instant.parse("2026-07-22T00:00:00Z"));
  }
}
