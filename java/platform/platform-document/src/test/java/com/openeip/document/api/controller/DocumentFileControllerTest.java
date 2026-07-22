package com.openeip.document.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openeip.common.web.RequestIdFilter;
import com.openeip.document.api.advice.DocumentExceptionHandler;
import com.openeip.document.application.service.DocumentFileService;
import com.openeip.document.domain.entity.DocumentFile;
import com.openeip.document.shared.exception.DocumentException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DocumentFileControllerTest {

  private static final String OWNER = "11111111-1111-1111-1111-111111111111";
  private static final String FILE_ID = "22222222-2222-2222-2222-222222222222";
  private static final String REQUEST_ID = "request-1";

  @Mock DocumentFileService service;
  private MockMvc mockMvc;
  private TestingAuthenticationToken user;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new DocumentFileController(service))
            .setControllerAdvice(new DocumentExceptionHandler())
            .build();
    user = new TestingAuthenticationToken(OWNER, null, "ROLE_USER");
    user.setAuthenticated(true);
  }

  @Test
  void uploadsAndReturnsPublicMetadata() throws Exception {
    MockMultipartFile part =
        new MockMultipartFile(
            "file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
    when(service.upload(eq(OWNER), any())).thenReturn(file());

    mockMvc
        .perform(
            multipart("/api/v1/documents/files")
                .file(part)
                .principal(user)
                .requestAttr(RequestIdFilter.ATTRIBUTE, REQUEST_ID))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.id").value(FILE_ID))
        .andExpect(jsonPath("$.data.objectKey").doesNotExist())
        .andExpect(jsonPath("$.requestId").value(REQUEST_ID));
  }

  @Test
  void listsOneBasedPageAndForwardsAdminAuthority() throws Exception {
    var admin = new TestingAuthenticationToken("admin-id", null, "ROLE_ADMIN");
    admin.setAuthenticated(true);
    when(service.list("admin-id", true, 2, 5))
        .thenReturn(new PageImpl<>(List.of(file()), PageRequest.of(1, 5), 6));

    mockMvc
        .perform(
            get("/api/v1/documents/files?page=2&pageSize=5")
                .principal(admin)
                .requestAttr(RequestIdFilter.ATTRIBUTE, REQUEST_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.total").value(6))
        .andExpect(jsonPath("$.data.items[0].id").value(FILE_ID));
  }

  @Test
  void returnsMetadataAndStreamsSafeDownload() throws Exception {
    when(service.get(OWNER, false, FILE_ID)).thenReturn(file());
    when(service.open(OWNER, false, FILE_ID))
        .thenReturn(
            new DocumentFileService.Download(
                file(), new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))));

    mockMvc
        .perform(
            get("/api/v1/documents/files/{id}", FILE_ID)
                .principal(user)
                .requestAttr(RequestIdFilter.ATTRIBUTE, REQUEST_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.originalName").value("report \"Q3\".txt"));
    mockMvc
        .perform(get("/api/v1/documents/files/{id}/content", FILE_ID).principal(user))
        .andExpect(status().isOk())
        .andExpect(content().bytes("hello".getBytes(StandardCharsets.UTF_8)))
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/plain"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    org.hamcrest.Matchers.containsString("attachment")));
  }

  @Test
  void deletesIdempotently() throws Exception {
    mockMvc
        .perform(delete("/api/v1/documents/files/{id}", FILE_ID).principal(user))
        .andExpect(status().isNoContent());

    verify(service).delete(OWNER, false, FILE_ID);
  }

  @Test
  void mapsDomainAndValidationFailures() throws Exception {
    when(service.get(OWNER, false, FILE_ID)).thenThrow(DocumentException.notFound());

    mockMvc
        .perform(
            get("/api/v1/documents/files/{id}", FILE_ID)
                .principal(user)
                .requestAttr(RequestIdFilter.ATTRIBUTE, REQUEST_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DOC-E-001"))
        .andExpect(jsonPath("$.requestId").value(REQUEST_ID));
    mockMvc
        .perform(multipart("/api/v1/documents/files").principal(user))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DOC-V-001"));
  }

  private static DocumentFile file() {
    return new DocumentFile(
        FILE_ID,
        DocumentFileService.MVP_TENANT,
        OWNER,
        "report \"Q3\".txt",
        "22/" + FILE_ID,
        "text/plain",
        5,
        "a".repeat(64),
        Instant.parse("2026-07-22T00:00:00Z"));
  }
}
