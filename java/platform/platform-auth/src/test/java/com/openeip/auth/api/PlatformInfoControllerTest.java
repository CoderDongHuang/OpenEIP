package com.openeip.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformInfoController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlatformInfoControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void returnsPlatformMetadata() throws Exception {
    mockMvc
        .perform(get("/api/v1/platform/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("OpenEIP Platform"))
        .andExpect(jsonPath("$.version").value("0.2.0-alpha"));
  }
}
