"""Thread-safe in-memory vector adapter for non-production profiles."""

import math
from threading import RLock

from engine_core.embedding.domain.models import VectorRecord, VectorSearchResult
from engine_core.embedding.shared.errors import EmbeddingError


class InMemoryVectorRepository:
    """Replace-by-key storage with tenant/base filtering before scoring."""

    def __init__(self) -> None:
        self._records: dict[tuple[str, str, str], VectorRecord] = {}
        self._lock = RLock()

    def upsert(self, records: tuple[VectorRecord, ...]) -> None:
        """Insert or replace records under tenant/base/chunk identity."""
        with self._lock:
            for record in records:
                self._records[(record.tenant_id, record.knowledge_base_id, record.chunk_id)] = record

    def search(
        self,
        tenant_id: str,
        knowledge_base_id: str,
        query: tuple[float, ...],
        top_k: int,
    ) -> tuple[VectorSearchResult, ...]:
        """Return deterministic cosine matches inside one tenant/base scope."""
        query_norm = math.sqrt(sum(value * value for value in query)) if query else 0.0
        if top_k < 1 or top_k > 100 or not all(math.isfinite(value) for value in query) or abs(query_norm - 1.0) > 1e-6:
            raise EmbeddingError("EMB-V-006", "Invalid vector search request", 400)
        with self._lock:
            candidates = [
                record
                for record in self._records.values()
                if record.tenant_id == tenant_id and record.knowledge_base_id == knowledge_base_id
            ]
        if any(len(record.vector) != len(query) for record in candidates):
            raise EmbeddingError("EMB-S-002", "Vector repository dimension mismatch", 503)
        results = [
            VectorSearchResult(
                document_id=record.document_id,
                chunk_id=record.chunk_id,
                text=record.text,
                score=sum(left * right for left, right in zip(query, record.vector, strict=True)),
                source_sha256=record.source_sha256,
            )
            for record in candidates
        ]
        results.sort(key=lambda result: (-result.score, result.chunk_id))
        return tuple(results[:top_k])

    def delete_document(self, tenant_id: str, knowledge_base_id: str, document_id: str) -> int:
        """Delete only records in the exact tenant/base/document scope."""
        with self._lock:
            keys = [
                key
                for key, record in self._records.items()
                if record.tenant_id == tenant_id
                and record.knowledge_base_id == knowledge_base_id
                and record.document_id == document_id
            ]
            for key in keys:
                del self._records[key]
            return len(keys)
