"""Provider and repository interfaces independent of vendors."""

from typing import Protocol

from engine_core.embedding.domain.models import VectorRecord, VectorSearchResult


class EmbeddingProvider(Protocol):
    """Generate fixed-width vectors for a batch."""

    @property
    def model(self) -> str: ...

    @property
    def version(self) -> str: ...

    @property
    def dimension(self) -> int: ...

    def embed(self, texts: tuple[str, ...]) -> tuple[tuple[float, ...], ...]: ...


class VectorRepository(Protocol):
    """Tenant-scoped vector persistence and retrieval contract."""

    def upsert(self, records: tuple[VectorRecord, ...]) -> None: ...

    def search(
        self,
        tenant_id: str,
        knowledge_base_id: str,
        query: tuple[float, ...],
        top_k: int,
    ) -> tuple[VectorSearchResult, ...]: ...

    def delete_document(self, tenant_id: str, knowledge_base_id: str, document_id: str) -> int: ...
