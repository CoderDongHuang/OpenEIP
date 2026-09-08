package com.openeip.marketplace.application;

import com.openeip.marketplace.domain.MarketplacePackage;
import com.openeip.marketplace.domain.PackageState;
import com.openeip.marketplace.domain.PackageType;
import com.openeip.marketplace.domain.PackageVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplacePort {
  MarketplacePackage createPackage(
      UUID tenantId,
      PackageType type,
      String slug,
      String displayName,
      String description,
      String publisher);

  Optional<MarketplacePackage> packageById(UUID tenantId, UUID packageId);

  Optional<MarketplacePackage> packageBySlug(UUID tenantId, String slug);

  List<MarketplacePackage> listPackages(
      UUID tenantId, PackageType type, PackageState state, int limit);

  List<MarketplacePackage> listPublicPackages(PackageType type, int limit);

  PackageVersion createVersion(
      UUID tenantId,
      UUID packageId,
      String version,
      String artifactUri,
      String sha256,
      String runtime,
      java.util.Map<String, Object> manifest);

  Optional<PackageVersion> version(UUID tenantId, UUID packageId, UUID versionId);

  List<PackageVersion> versions(UUID tenantId, UUID packageId, int limit);

  boolean updateVersionState(
      UUID tenantId,
      UUID packageId,
      UUID versionId,
      PackageState state,
      String reviewNote,
      long expectedRevision);

  boolean updatePackageState(UUID tenantId, UUID packageId, PackageState state);

  boolean hasPublishedVersion(UUID tenantId, UUID packageId);
}
