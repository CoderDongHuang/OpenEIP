package com.openeip.governance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openeip.common.web.RequestIdFilter;
import com.openeip.governance.application.GovernanceReadService;
import com.openeip.governance.application.budget.BudgetService;
import com.openeip.governance.application.catalog.ModelCatalogService;
import com.openeip.governance.application.catalog.PromptCatalogService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.application.usage.UsageLedgerService;
import com.openeip.governance.domain.budget.Budget;
import com.openeip.governance.domain.budget.BudgetWindowType;
import com.openeip.governance.domain.catalog.Model;
import com.openeip.governance.domain.catalog.ModelState;
import com.openeip.governance.domain.catalog.Prompt;
import com.openeip.governance.domain.catalog.PromptState;
import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.shared.exception.GovernanceAuthorizationException;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GovernanceControllerTest {
  private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID PRINCIPAL = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID RESOURCE = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
  private GovernanceReadService reads;
  private ModelCatalogService models;
  private PromptCatalogService prompts;
  private UsageLedgerService usage;
  private BudgetService budgets;
  private ObjectProvider<PromptCatalogService> promptProvider;
  private GovernanceController controller;
  private MockHttpServletRequest request;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    reads = mock(GovernanceReadService.class);
    models = mock(ModelCatalogService.class);
    prompts = mock(PromptCatalogService.class);
    usage = mock(UsageLedgerService.class);
    budgets = mock(BudgetService.class);
    promptProvider = mock(ObjectProvider.class);
    when(promptProvider.getIfAvailable()).thenReturn(prompts);
    controller = new GovernanceController(reads, models, promptProvider, usage, budgets);
    request = new MockHttpServletRequest();
    request.setAttribute(RequestIdFilter.ATTRIBUTE, "request-1");
    bind(Set.of("VIEWER"));
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void exposesOnlyCurrentTenantAndBoundedReadModels() {
    when(reads.currentTenant()).thenReturn(Map.of("id", TENANT.toString(), "revision", 0L));
    when(reads.memberships(20)).thenReturn(List.of(Map.of("principalId", PRINCIPAL.toString())));
    when(reads.audits(null, null, null, null, null, 20)).thenReturn(List.of());
    when(models.listModels(null, null, 20)).thenReturn(List.of());
    when(prompts.listPrompts(null, 20)).thenReturn(List.of());
    when(usage.list(null, null, null, 20)).thenReturn(List.of());
    when(budgets.list(20)).thenReturn(List.of());
    when(reads.traces(anyString(), anyInt())).thenReturn(List.of());

    assertThat(controller.tenants(request).data().items()).hasSize(1);
    assertThat(controller.tenant(TENANT, request).getStatusCode().value()).isEqualTo(200);
    assertThat(controller.memberships(TENANT, 20, request).data().items()).hasSize(1);
    assertThat(controller.audits(null, null, null, null, null, 20, request).data().items())
        .isEmpty();
    assertThat(controller.models(null, null, 20, request).data().items()).isEmpty();
    assertThat(controller.prompts(null, 20, request).data().items()).isEmpty();
    assertThat(controller.usage(null, null, null, 20, request).data().items()).isEmpty();
    assertThat(controller.budgets(20, request).data().items()).isEmpty();
    assertThat(controller.trace("0123456789abcdef", request).data()).isNotNull();
    assertThatThrownBy(() -> controller.tenant(UUID.randomUUID(), request))
        .isInstanceOf(GovernanceAuthorizationException.class);
  }

  @Test
  void resolvesImplicitPathAndRequestParameterNamesThroughSpringMvc() throws Exception {
    when(reads.memberships(20)).thenReturn(List.of());

    MockMvcBuilders.standaloneSetup(controller)
        .build()
        .perform(get("/api/v2/governance/tenants/{tenantId}/memberships", TENANT))
        .andExpect(status().isOk());
  }

  @Test
  void viewerCannotMutateOrVerifyEvidence() {
    var model =
        new GovernanceDtos.CreateModelRequest(
            "model", "provider", List.of("CHAT"), List.of(), "secret://env/MODEL_KEY");
    var audit = new GovernanceDtos.VerifyAuditRequest(NOW.minusSeconds(60), NOW);

    assertThatThrownBy(() -> controller.createModel("1234567890123456", model, request))
        .isInstanceOf(GovernanceAuthorizationException.class);
    assertThatThrownBy(() -> controller.verifyAudit("1234567890123456", audit, request))
        .isInstanceOf(GovernanceAuthorizationException.class);
  }

  @Test
  void administratorCanDriveModelPromptAndBudgetLifecycles() {
    bind(Set.of("GOVERNANCE_ADMIN", "OPERATOR", "VIEWER"));
    Model model =
        new Model(
            RESOURCE, TENANT, UUID.randomUUID(), "model", ModelState.DRAFT, "v1", 0, NOW, NOW);
    Prompt prompt =
        new Prompt(RESOURCE, TENANT, "prompt", "chat", null, 0, NOW, NOW, PromptState.DRAFT);
    Budget budget =
        new Budget(
            RESOURCE,
            TENANT,
            "monthly",
            "USD",
            BigDecimal.TEN,
            BudgetWindowType.MONTHLY,
            0,
            NOW,
            NOW);
    when(models.registerModelPolicy(anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(model);
    when(models.reviewModel(RESOURCE, 0)).thenReturn(model);
    when(models.enableModel(RESOURCE, 0)).thenReturn(model);
    when(models.suspendModel(RESOURCE, 0)).thenReturn(model);
    when(prompts.createPrompt(any())).thenReturn(prompt);
    when(budgets.create(any())).thenReturn(budget);
    String key = "1234567890123456";

    assertThat(
            controller
                .createModel(
                    key,
                    new GovernanceDtos.CreateModelRequest(
                        "model",
                        "provider",
                        List.of("CHAT"),
                        List.of("primary"),
                        "secret://env/MODEL_KEY"),
                    request)
                .getStatusCode()
                .value())
        .isEqualTo(201);
    assertThat(
            controller
                .reviewModel(
                    RESOURCE,
                    key,
                    "0",
                    new GovernanceDtos.ReviewRequest("APPROVE", "reviewed"),
                    request)
                .getStatusCode()
                .value())
        .isEqualTo(200);
    controller.enableModel(RESOURCE, key, "\"0\"", request);
    controller.suspendModel(RESOURCE, key, "0", request);
    assertThat(
            controller
                .createPrompt(
                    key,
                    new GovernanceDtos.CreatePromptRequest("prompt", "chat", "private content"),
                    request)
                .getStatusCode()
                .value())
        .isEqualTo(201);
    assertThat(
            controller
                .createBudget(
                    key,
                    new GovernanceDtos.CreateBudgetRequest(
                        "monthly", "USD", BigDecimal.TEN, "MONTHLY"),
                    request)
                .getStatusCode()
                .value())
        .isEqualTo(201);
    verify(models).registerModelPolicy(anyString(), anyString(), anyString(), any(), any());
    verify(prompts).createPrompt(any());
    verify(budgets).create(any());
  }

  @Test
  void failsClosedWhenPromptEncryptionIsUnavailableOrHeadersAreInvalid() {
    bind(Set.of("GOVERNANCE_ADMIN"));
    when(promptProvider.getIfAvailable()).thenReturn(null);

    assertThatThrownBy(() -> controller.prompts(null, 20, request))
        .isInstanceOf(GovernanceCatalogException.class)
        .hasMessageContaining("encryption");
    assertThatThrownBy(
            () ->
                controller.createModel(
                    "short",
                    new GovernanceDtos.CreateModelRequest(
                        "model", "provider", List.of("CHAT"), List.of(), "secret://env/KEY"),
                    request))
        .isInstanceOf(GovernanceCatalogException.class);
    assertThatThrownBy(() -> controller.enableModel(RESOURCE, "1234567890123456", "bad", request))
        .isInstanceOf(GovernanceCatalogException.class);
  }

  private static void bind(Set<String> roles) {
    TenantContextHolder.bind(
        new TenantContext(
            TENANT,
            null,
            PRINCIPAL,
            UUID.randomUUID(),
            roles,
            "governance-v1",
            "request-1",
            "0123456789abcdef0123456789abcdef",
            GovernanceScope.TENANT,
            Instant.parse("2099-01-01T00:00:00Z")));
  }
}
