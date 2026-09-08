package com.openeip.marketplace.application;

import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.marketplace.domain.MarketplacePackage;
import com.openeip.marketplace.domain.PackageState;
import com.openeip.marketplace.domain.PackageType;
import com.openeip.marketplace.domain.PackageVersion;
import com.openeip.marketplace.shared.MarketplaceException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketplaceService {
  private static final Pattern SLUG = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,126}[a-z0-9])?");
  private static final Pattern VERSION =
      Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?");
  private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");
  private final MarketplacePort catalog;
  private final AuditService audit;
  private final Clock clock;

  @Autowired
  public MarketplaceService(MarketplacePort catalog, AuditService audit) {
    this(catalog, audit, Clock.systemUTC());
  }

  MarketplaceService(MarketplacePort catalog, AuditService audit, Clock clock) {
    this.catalog = catalog;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public MarketplacePackage create(PackageType type, String slug, String name, String description) {
    Context context = context();
    requireAdmin();
    validateSlug(slug);
    requireLength(name, 128, "displayName");
    requireLength(description, 2048, "description");
    MarketplacePackage value;
    try {
      value =
          catalog.createPackage(
              context.tenantId(), type, slug, name, description, context.principalId().toString());
    } catch (RuntimeException exception) {
      throw exception instanceof MarketplaceException
          ? exception
          : MarketplaceException.conflict("Package slug already exists");
    }
    record(context, "marketplace.package.created", value.id());
    return value;
  }

  @Transactional(readOnly = true)
  public List<MarketplacePackage> list(PackageType type, PackageState state, int limit) {
    Context context = context();
    return catalog.listPackages(context.tenantId(), type, state, bounded(limit));
  }

  @Transactional(readOnly = true)
  public List<MarketplacePackage> publicList(PackageType type, int limit) {
    return catalog.listPublicPackages(type, bounded(limit));
  }

  @Transactional
  public PackageVersion addVersion(
      UUID packageId,
      String version,
      String artifactUri,
      String sha256,
      String runtime,
      Map<String, Object> manifest) {
    Context context = context();
    requireAdmin();
    if (!VERSION.matcher(version).matches()) {
      throw MarketplaceException.invalid("version must be SemVer");
    }
    if (!SHA256.matcher(sha256).matches()) {
      throw MarketplaceException.invalid("sha256 must be lowercase hex");
    }
    requireLength(artifactUri, 1024, "artifactUri");
    requireLength(runtime, 64, "runtime");
    if (manifest == null || manifest.isEmpty()) {
      throw MarketplaceException.invalid("manifest is required");
    }
    requirePackage(context.tenantId(), packageId);
    try {
      PackageVersion value =
          catalog.createVersion(
              context.tenantId(), packageId, version, artifactUri, sha256, runtime, manifest);
      record(context, "marketplace.version.created", value.id());
      return value;
    } catch (RuntimeException exception) {
      throw exception instanceof MarketplaceException
          ? exception
          : MarketplaceException.conflict("Package version already exists");
    }
  }

  @Transactional(readOnly = true)
  public List<PackageVersion> versions(UUID packageId, int limit) {
    Context context = context();
    requirePackage(context.tenantId(), packageId);
    return catalog.versions(context.tenantId(), packageId, bounded(limit));
  }

  @Transactional
  public PackageVersion review(UUID packageId, UUID versionId, long expectedRevision, String note) {
    Context context = context();
    requireAdmin();
    PackageVersion version = requireVersion(context.tenantId(), packageId, versionId);
    if (version.state() != PackageState.DRAFT) {
      throw MarketplaceException.transition("Only draft versions can be reviewed");
    }
    transition(context, packageId, versionId, PackageState.REVIEWED, note, expectedRevision);
    return requireVersion(context.tenantId(), packageId, versionId);
  }

  @Transactional
  public PackageVersion publish(UUID packageId, UUID versionId, long expectedRevision) {
    Context context = context();
    requireAdmin();
    PackageVersion version = requireVersion(context.tenantId(), packageId, versionId);
    if (version.state() != PackageState.REVIEWED) {
      throw MarketplaceException.transition("Only reviewed versions can be published");
    }
    transition(context, packageId, versionId, PackageState.PUBLISHED, null, expectedRevision);
    catalog.updatePackageState(context.tenantId(), packageId, PackageState.PUBLISHED);
    return requireVersion(context.tenantId(), packageId, versionId);
  }

  @Transactional
  public PackageVersion suspend(UUID packageId, UUID versionId, long expectedRevision) {
    Context context = context();
    requireAdmin();
    PackageVersion version = requireVersion(context.tenantId(), packageId, versionId);
    if (version.state() != PackageState.PUBLISHED) {
      throw MarketplaceException.transition("Only published versions can be suspended");
    }
    transition(context, packageId, versionId, PackageState.SUSPENDED, null, expectedRevision);
    if (!catalog.hasPublishedVersion(context.tenantId(), packageId)) {
      catalog.updatePackageState(context.tenantId(), packageId, PackageState.SUSPENDED);
    }
    return requireVersion(context.tenantId(), packageId, versionId);
  }

  private void transition(
      Context context,
      UUID packageId,
      UUID versionId,
      PackageState state,
      String note,
      long revision) {
    if (!catalog.updateVersionState(
        context.tenantId(), packageId, versionId, state, note, revision)) {
      throw MarketplaceException.conflict("Version revision is stale or version was changed");
    }
    record(context, "marketplace.version." + state.name().toLowerCase(), versionId);
  }

  private PackageVersion requireVersion(UUID tenantId, UUID packageId, UUID versionId) {
    return catalog
        .version(tenantId, packageId, versionId)
        .orElseThrow(
            () -> MarketplaceException.invalid("Package version was not found in this tenant"));
  }

  private void requirePackage(UUID tenantId, UUID packageId) {
    catalog
        .packageById(tenantId, packageId)
        .orElseThrow(() -> MarketplaceException.invalid("Package was not found in this tenant"));
  }

  private Context context() {
    var value = TenantContextHolder.required();
    if (value.expiredAt(clock.instant())) {
      throw MarketplaceException.invalid("Marketplace context expired");
    }
    return new Context(
        value.tenantId(),
        value.principalId(),
        value.requestId(),
        value.traceId(),
        value.policyVersion());
  }

  private void record(Context context, String action, UUID resourceId) {
    audit.append(
        AuditService.command(
            UUID.randomUUID(),
            context.tenantId(),
            context.principalId(),
            action,
            "marketplace",
            resourceId.toString(),
            AuditOutcome.SUCCESS,
            context.requestId(),
            context.traceId(),
            context.policyVersion(),
            Instant.now(clock),
            Map.of()));
  }

  private static void requireAdmin() {
    var roles = TenantContextHolder.required().roles();
    if (!roles.contains("GOVERNANCE_ADMIN") && !roles.contains("MARKETPLACE_ADMIN")) {
      throw MarketplaceException.forbidden("Marketplace administrator role is required");
    }
  }

  private static int bounded(int limit) {
    return Math.min(Math.max(limit, 1), 100);
  }

  private static void validateSlug(String value) {
    if (value == null || !SLUG.matcher(value).matches()) {
      throw MarketplaceException.invalid("slug is invalid");
    }
  }

  private static void requireLength(String value, int max, String field) {
    if (value == null || value.isBlank() || value.length() > max) {
      throw MarketplaceException.invalid(field + " is invalid");
    }
  }

  private record Context(
      UUID tenantId, UUID principalId, String requestId, String traceId, String policyVersion) {}
}
