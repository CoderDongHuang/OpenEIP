package com.openeip.connector.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.application.ConnectorRuntimeService;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.spi.ConnectionTestResult;
import com.openeip.connector.spi.ConnectorMetadata;
import com.openeip.connector.spi.DataReader.ReadResult;
import com.openeip.connector.spi.DataWriter.WriteResult;
import com.openeip.connector.spi.MetadataSchema;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class ConnectorRuntimeControllerTest {
  @Mock private ConnectorRuntimeService service;
  private ConnectorRuntimeController controller;
  private UsernamePasswordAuthenticationToken auth;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    controller = new ConnectorRuntimeController(service);
    auth = new UsernamePasswordAuthenticationToken("actor", null, List.of());
    request = new MockHttpServletRequest();
  }

  @Test
  void delegatesEveryRuntimeEndpoint() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    var catalog =
        List.of(
            new ConnectorRuntimeService.CatalogEntry(
                new ConnectorMetadata(ConnectorType.MYSQL, "MySQL", "1", "db", true, true),
                List.of()));
    var metadata =
        new MetadataSchema(List.of(new MetadataSchema.ResourceSchema("rows", "table", List.of())));
    var read = new ReadResult(List.of(mapper.createObjectNode().put("id", 1)), null);
    when(service.catalog()).thenReturn(catalog);
    when(service.test("actor", "id", "corr")).thenReturn(ConnectionTestResult.success(1));
    when(service.metadata("actor", "id", "corr")).thenReturn(metadata);
    when(service.read("actor", "id", "corr", "rows", mapper.createObjectNode(), 10))
        .thenReturn(read);
    var data = mapper.createObjectNode().put("name", "one");
    when(service.write("actor", "id", "corr", "key", "rows", "INSERT", data))
        .thenReturn(new WriteResult("row-1", "STORED"));

    assertThat(controller.catalog(request).data()).isEqualTo(catalog);
    assertThat(controller.test("id", "corr", auth, request).data().success()).isTrue();
    assertThat(controller.metadata("id", "corr", auth, request).data()).isEqualTo(metadata);
    assertThat(
            controller
                .read(
                    "id",
                    "corr",
                    new ConnectorRuntimeDtos.ReadRequest("rows", mapper.createObjectNode(), 10),
                    auth,
                    request)
                .data())
        .isEqualTo(read);
    assertThat(
            controller
                .write(
                    "id",
                    "key",
                    "corr",
                    new ConnectorRuntimeDtos.WriteRequest("rows", "INSERT", data),
                    auth,
                    request)
                .data()
                .status())
        .isEqualTo("STORED");
    verify(service).write("actor", "id", "corr", "key", "rows", "INSERT", data);
  }
}
