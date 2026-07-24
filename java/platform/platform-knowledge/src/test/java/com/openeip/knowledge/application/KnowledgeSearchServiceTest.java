package com.openeip.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openeip.knowledge.infrastructure.ingestion.KnowledgeIngestionGateway;
import com.openeip.knowledge.infrastructure.ingestion.KnowledgeIngestionGateway.SearchResult;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeSearchServiceTest {
  private static final String USER = "11111111-1111-4111-8111-111111111111";
  private static final String BASE = "22222222-2222-4222-8222-222222222222";

  @Test
  void authorizesBeforeDispatchingScopedSearch() {
    KnowledgeBaseService bases = mock(KnowledgeBaseService.class);
    KnowledgeIngestionGateway gateway = mock(KnowledgeIngestionGateway.class);
    SearchResult expected = new SearchResult("HYBRID", List.of());
    when(gateway.search(USER, BASE, "invoice", "HYBRID", 10)).thenReturn(expected);
    KnowledgeSearchService service = new KnowledgeSearchService(bases, gateway);

    assertThat(service.search(USER, BASE, " invoice ", "HYBRID", 10)).isSameAs(expected);
    verify(bases).get(USER, BASE);
    verify(gateway).search(USER, BASE, "invoice", "HYBRID", 10);
  }

  @Test
  void rejectsInvalidModeQueryAndLimit() {
    KnowledgeSearchService service =
        new KnowledgeSearchService(
            mock(KnowledgeBaseService.class), mock(KnowledgeIngestionGateway.class));

    assertThatThrownBy(() -> service.search(USER, BASE, "", "HYBRID", 10))
        .isInstanceOf(KnowledgeException.class);
    assertThatThrownBy(() -> service.search(USER, BASE, "q", "GLOBAL", 10))
        .isInstanceOf(KnowledgeException.class);
    assertThatThrownBy(() -> service.search(USER, BASE, "q", "VECTOR", 51))
        .isInstanceOf(KnowledgeException.class);
  }
}
