package com.openeip.connector.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.api.ConnectorDtos.CreateRequest;
import com.openeip.connector.api.ConnectorDtos.StatusRequest;
import com.openeip.connector.api.ConnectorDtos.UpdateRequest;
import com.openeip.connector.application.ConnectorService;
import com.openeip.connector.domain.ConnectorStatus;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.domain.entity.ConnectorInstance;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class ConnectorControllerTest {
  @Mock private ConnectorService service;
  private ConnectorController controller;
  private UsernamePasswordAuthenticationToken auth;
  private MockHttpServletRequest request;
  private ConnectorInstance instance;

  @BeforeEach
  void setUp() {
    controller = new ConnectorController(service, new ObjectMapper());
    auth = new UsernamePasswordAuthenticationToken("user", null, List.of());
    request = new MockHttpServletRequest();
    instance =
        new ConnectorInstance(
            "id",
            "default",
            "user",
            "db",
            ConnectorType.MYSQL,
            "{\"host\":\"db\"}",
            null,
            Instant.now());
  }

  @Test
  void delegatesCrudAndStatusOperations() throws Exception {
    var config = new ObjectMapper().readTree("{\"host\":\"db\"}");
    when(service.create("user", "db", ConnectorType.MYSQL, config, "secret://db"))
        .thenReturn(instance);
    when(service.list("user", 1, 20)).thenReturn(new PageImpl<>(List.of(instance)));
    when(service.get("user", "id")).thenReturn(instance);
    when(service.update("user", "id", "db2", config, null)).thenReturn(instance);
    when(service.setStatus("user", "id", ConnectorStatus.ACTIVE)).thenReturn(instance);

    assertThat(
            controller
                .create(
                    new CreateRequest("db", ConnectorType.MYSQL, config, "secret://db"),
                    auth,
                    request)
                .getStatusCode()
                .value())
        .isEqualTo(201);
    assertThat(controller.list(1, 20, auth, request).data().items()).hasSize(1);
    assertThat(controller.get("id", auth, request).data().id()).isEqualTo("id");
    assertThat(
            controller
                .update("id", new UpdateRequest("db2", config, null), auth, request)
                .data()
                .id())
        .isEqualTo("id");
    assertThat(
            controller
                .status("id", new StatusRequest(ConnectorStatus.ACTIVE), auth, request)
                .data()
                .id())
        .isEqualTo("id");
    assertThat(controller.delete("id", auth).getStatusCode().value()).isEqualTo(204);
    verify(service).delete("user", "id");
  }
}
