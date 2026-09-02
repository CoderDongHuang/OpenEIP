package com.openeip.governance.application.catalog;

import com.openeip.governance.domain.catalog.Model;
import com.openeip.governance.domain.catalog.ModelRegistration;
import com.openeip.governance.domain.catalog.ModelVersion;
import com.openeip.governance.domain.catalog.Provider;
import com.openeip.governance.domain.catalog.ProviderRegistration;
import com.openeip.governance.domain.catalog.ProviderState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for tenant-scoped provider and model catalog state. */
public interface ModelCatalogPort {
  Provider registerProvider(ProviderRegistration registration);

  Optional<Provider> provider(UUID tenantId, UUID providerId);

  Optional<Provider> providerByName(UUID tenantId, String name);

  boolean updateProviderState(
      UUID tenantId, UUID providerId, long expectedRevision, ProviderState state);

  Model registerModel(ModelRegistration registration, String policyVersion);

  Optional<Model> model(UUID tenantId, UUID modelId);

  List<Model> models(UUID tenantId, String state, String capability, int limit);

  Optional<ModelVersion> latestVersion(UUID tenantId, UUID modelId);

  ModelVersion addVersion(ModelRegistration registration, UUID modelId, int versionNumber);

  boolean updateModelState(UUID tenantId, UUID modelId, long expectedRevision, String state);
}
