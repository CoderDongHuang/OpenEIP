"""Immutable embedding and vector-search domain values."""

from dataclasses import dataclass, replace


@dataclass(frozen=True)
class EmbeddingChunk:
    """One validated chunk submitted to the provider."""

    chunk_id: str
    text: str
    source_sha256: str


@dataclass(frozen=True)
class EmbeddingJob:
    """One tenant-scoped batch job."""

    job_id: str
    tenant_id: str
    knowledge_base_id: str
    document_id: str
    chunks: tuple[EmbeddingChunk, ...]


@dataclass(frozen=True)
class EmbeddedVector:
    """Public vector result paired to its chunk identity."""

    chunk_id: str
    vector: tuple[float, ...]


@dataclass(frozen=True)
class EmbeddingJobResult:
    """Validated result and model provenance for one job."""

    job_id: str
    knowledge_base_id: str
    document_id: str
    model: str
    model_version: str
    dimension: int
    vectors: tuple[EmbeddedVector, ...]
    duration_ms: float
    replayed: bool = False

    def as_replay(self) -> "EmbeddingJobResult":
        """Return the same immutable result marked as an idempotent replay."""
        return replace(self, replayed=True)


@dataclass(frozen=True)
class VectorRecord:
    """Tenant-filterable vector repository record."""

    tenant_id: str
    knowledge_base_id: str
    document_id: str
    chunk_id: str
    source_sha256: str
    model: str
    model_version: str
    vector: tuple[float, ...]


@dataclass(frozen=True)
class VectorSearchResult:
    """One scoped cosine-search match."""

    document_id: str
    chunk_id: str
    score: float
    source_sha256: str
