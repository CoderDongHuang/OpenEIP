"""Immutable RAG context, answer, and citation values."""

from dataclasses import dataclass


@dataclass(frozen=True)
class RagContext:
    """One retrieved and prompt-bounded untrusted context."""

    document_id: str
    chunk_id: str
    text: str
    source_sha256: str
    score: float


@dataclass(frozen=True)
class ProviderAnswer:
    """Untrusted answer-provider output before citation validation."""

    answer: str
    model: str
    model_version: str
    cited_chunk_ids: tuple[str, ...]


@dataclass(frozen=True)
class RagCitation:
    """Verified citation resolved from repository metadata."""

    document_id: str
    chunk_id: str
    source_sha256: str
    score: float


@dataclass(frozen=True)
class RagResult:
    """Grounded answer returned by the application service."""

    answer: str
    model: str
    model_version: str
    citations: tuple[RagCitation, ...]
    retrieval_count: int
    duration_ms: float
