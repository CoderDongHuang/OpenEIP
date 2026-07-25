package com.openeip.connector.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.api.ConnectorDtos.CreateRequest;
import com.openeip.connector.api.ConnectorDtos.PageResponse;
import com.openeip.connector.api.ConnectorDtos.Response;
import com.openeip.connector.api.ConnectorDtos.StatusRequest;
import com.openeip.connector.api.ConnectorDtos.UpdateRequest;
import com.openeip.connector.domain.ConnectorStatus;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.domain.entity.ConnectorInstance;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

class ConnectorDtosTest {
  @Test
  void mapsResponsesAndDefensivelyCopiesPageItems() throws Exception {
    ConnectorInstance instance =
        new ConnectorInstance(
            "id",
            "tenant",
            "owner",
            "name",
            ConnectorType.GITHUB,
            "{\"url\":\"https://git\"}",
            null,
            Instant.now());
    Response response = Response.from(instance, new ObjectMapper());
    assertThat(response.id()).isEqualTo("id");
    assertThat(response.config().path("url").asText()).isEqualTo("https://git");
    PageResponse page = PageResponse.from(new PageImpl<>(List.of(response)));
    assertThat(page.items()).containsExactly(response);
    assertThatThrownByMutation(page.items());
  }

  @Test
  void recordContractsExposeAllRequestFields() throws Exception {
    var config = new ObjectMapper().readTree("{\"host\":\"db\"}");
    CreateRequest create = new CreateRequest("db", ConnectorType.MYSQL, config, "secret://db");
    UpdateRequest update = new UpdateRequest("db2", config, null);
    StatusRequest status = new StatusRequest(ConnectorStatus.ACTIVE);
    assertThat(create.name()).isEqualTo("db");
    assertThat(create.type()).isEqualTo(ConnectorType.MYSQL);
    assertThat(create.config()).isSameAs(config);
    assertThat(create.credentialRef()).isEqualTo("secret://db");
    assertThat(update.name()).isEqualTo("db2");
    assertThat(update.config()).isSameAs(config);
    assertThat(status.status()).isEqualTo(ConnectorStatus.ACTIVE);
  }

  private static void assertThatThrownByMutation(List<Response> items) {
    try {
      items.add(items.get(0));
      throw new AssertionError("expected immutable list");
    } catch (UnsupportedOperationException expected) {
      assertThat(expected).isNotNull();
    }
  }
}
