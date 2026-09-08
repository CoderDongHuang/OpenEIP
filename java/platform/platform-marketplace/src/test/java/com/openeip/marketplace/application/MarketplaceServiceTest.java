package com.openeip.marketplace.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.marketplace.domain.MarketplacePackage;
import com.openeip.marketplace.domain.PackageState;
import com.openeip.marketplace.domain.PackageType;
import com.openeip.marketplace.domain.PackageVersion;
import com.openeip.marketplace.shared.MarketplaceException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketplaceServiceTest {
  private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID PRINCIPAL = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID PACKAGE = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final UUID VERSION = UUID.fromString("44444444-4444-4444-8444-444444444444");
  private MarketplacePort catalog;
  private MarketplaceService service;

  @BeforeEach
  void setUp() {
    catalog = mock(MarketplacePort.class);
    service = new MarketplaceService(catalog, mock(AuditService.class));
    TenantContextHolder.bind(
        new TenantContext(
            TENANT,
            null,
            PRINCIPAL,
            null,
            Set.of("GOVERNANCE_ADMIN"),
            "governance-v1",
            "request-1",
            "trace-1",
            GovernanceScope.TENANT,
            Instant.parse("2026-09-09T00:00:00Z")));
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void rejectsInvalidPackageVersionMetadata() {
    assertThatThrownBy(
            () ->
                service.addVersion(
                    PACKAGE,
                    "1.0",
                    "oci://example/plugin",
                    "not-a-digest",
                    "java-21",
                    Map.of("id", "x")))
        .isInstanceOf(MarketplaceException.class)
        .hasMessageContaining("SemVer");
  }

  @Test
  void createsListsAndPublishesPackageVersion() {
    MarketplacePackage packageValue = packageValue(PackageState.DRAFT, 0);
    when(catalog.createPackage(
            TENANT,
            PackageType.PLUGIN,
            "example-plugin",
            "Example",
            "Example package",
            PRINCIPAL.toString()))
        .thenReturn(packageValue);
    when(catalog.packageById(TENANT, PACKAGE)).thenReturn(java.util.Optional.of(packageValue));
    when(catalog.version(TENANT, PACKAGE, VERSION))
        .thenReturn(
            java.util.Optional.of(version(PackageState.REVIEWED, 0)),
            java.util.Optional.of(version(PackageState.PUBLISHED, 1)));
    when(catalog.createVersion(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(version(PackageState.DRAFT, 0));
    when(catalog.updateVersionState(any(), any(), any(), any(), any(), anyLong())).thenReturn(true);
    when(catalog.listPackages(TENANT, PackageType.PLUGIN, PackageState.DRAFT, 20))
        .thenReturn(List.of(packageValue));
    when(catalog.listPublicPackages(PackageType.PLUGIN, 20)).thenReturn(List.of(packageValue));
    when(catalog.versions(TENANT, PACKAGE, 20))
        .thenReturn(List.of(version(PackageState.PUBLISHED, 1)));

    service.create(PackageType.PLUGIN, "example-plugin", "Example", "Example package");
    service.addVersion(
        PACKAGE,
        "1.0.0",
        "oci://example/plugin",
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "java-21",
        Map.of("id", "example"));
    service.list(PackageType.PLUGIN, PackageState.DRAFT, 20);
    service.publicList(PackageType.PLUGIN, 20);
    service.versions(PACKAGE, 20);
    service.publish(PACKAGE, VERSION, 0);
  }

  @Test
  void onlyReviewedVersionCanBePublished() {
    PackageVersion draft = version(PackageState.DRAFT, 0);
    when(catalog.version(TENANT, PACKAGE, VERSION)).thenReturn(java.util.Optional.of(draft));

    assertThatThrownBy(() -> service.publish(PACKAGE, VERSION, 0))
        .isInstanceOf(MarketplaceException.class)
        .hasMessageContaining("reviewed");
  }

  @Test
  void staleRevisionIsRejectedDuringReview() {
    when(catalog.version(TENANT, PACKAGE, VERSION))
        .thenReturn(java.util.Optional.of(version(PackageState.DRAFT, 2)));
    when(catalog.updateVersionState(any(), any(), any(), any(), any(), anyLong()))
        .thenReturn(false);

    assertThatThrownBy(() -> service.review(PACKAGE, VERSION, 1, "security review"))
        .isInstanceOf(MarketplaceException.class)
        .hasMessageContaining("stale");
  }

  private static PackageVersion version(PackageState state, long revision) {
    return new PackageVersion(
        VERSION,
        TENANT,
        PACKAGE,
        "1.0.0",
        "oci://example/plugin",
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "java-21",
        Map.of("id", "example"),
        state,
        revision,
        null,
        Instant.parse("2026-09-08T00:00:00Z"),
        null,
        null);
  }

  private static MarketplacePackage packageValue(PackageState state, long revision) {
    return new MarketplacePackage(
        PACKAGE,
        TENANT,
        PackageType.PLUGIN,
        "example-plugin",
        "Example",
        "Example package",
        PRINCIPAL.toString(),
        state,
        revision,
        Instant.parse("2026-09-08T00:00:00Z"),
        Instant.parse("2026-09-08T00:00:00Z"));
  }
}
