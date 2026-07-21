"""Bounded, idempotent embedding orchestration."""

import math
from collections import OrderedDict
from hashlib import sha256
from threading import RLock
from time import perf_counter

from engine_core.embedding.domain.models import (
    EmbeddedVector,
    EmbeddingJob,
    EmbeddingJobResult,
    VectorRecord,
)
from engine_core.embedding.domain.ports import EmbeddingProvider, VectorRepository
from engine_core.embedding.shared.errors import EmbeddingError


class EmbeddingService:
    """Validate batches and provider output before tenant-scoped upsert."""

    def __init__(
        self,
        provider: EmbeddingProvider,
        repository: VectorRepository,
        max_batch_size: int,
        max_text_chars: int,
        max_jobs: int,
    ) -> None:
        if max_batch_size < 1 or max_batch_size > 100 or max_text_chars < 1 or max_jobs < 1:
            raise ValueError("Invalid embedding limits")
        self._provider = provider
        self._repository = repository
        self._max_batch_size = max_batch_size
        self._max_text_chars = max_text_chars
        self._max_jobs = max_jobs
        self._jobs: OrderedDict[tuple[str, str], tuple[str, EmbeddingJobResult]] = OrderedDict()
        self._lock = RLock()

    def execute(self, job: EmbeddingJob) -> EmbeddingJobResult:
        """Generate, validate, and persist a batch, or return its exact replay."""
        self._validate_job(job)
        fingerprint = self._fingerprint(job)
        key = (job.tenant_id, job.job_id)
        with self._lock:
            existing = self._jobs.get(key)
            if existing is not None:
                if existing[0] != fingerprint:
                    raise EmbeddingError("EMB-E-003", "Embedding job identifier collision", 409)
                self._jobs.move_to_end(key)
                return existing[1].as_replay()

            started = perf_counter()
            try:
                vectors = self._provider.embed(tuple(chunk.text for chunk in job.chunks))
            except Exception as exception:
                raise EmbeddingError("EMB-S-001", "Embedding provider is unavailable", 503) from exception
            self._validate_vectors(vectors, len(job.chunks))
            records = tuple(
                VectorRecord(
                    tenant_id=job.tenant_id,
                    knowledge_base_id=job.knowledge_base_id,
                    document_id=job.document_id,
                    chunk_id=chunk.chunk_id,
                    text=chunk.text,
                    source_sha256=chunk.source_sha256,
                    model=self._provider.model,
                    model_version=self._provider.version,
                    vector=vector,
                )
                for chunk, vector in zip(job.chunks, vectors, strict=True)
            )
            self._repository.upsert(records)
            result = EmbeddingJobResult(
                job_id=job.job_id,
                knowledge_base_id=job.knowledge_base_id,
                document_id=job.document_id,
                model=self._provider.model,
                model_version=self._provider.version,
                dimension=self._provider.dimension,
                vectors=tuple(EmbeddedVector(record.chunk_id, record.vector) for record in records),
                duration_ms=(perf_counter() - started) * 1000,
            )
            self._jobs[key] = (fingerprint, result)
            while len(self._jobs) > self._max_jobs:
                self._jobs.popitem(last=False)
            return result

    def _validate_job(self, job: EmbeddingJob) -> None:
        if not job.chunks or len(job.chunks) > self._max_batch_size:
            raise EmbeddingError("EMB-V-002", "Embedding batch exceeds configured limit", 413)
        chunk_ids = [chunk.chunk_id for chunk in job.chunks]
        if len(chunk_ids) != len(set(chunk_ids)):
            raise EmbeddingError("EMB-V-003", "Embedding chunk identifiers must be unique", 400)
        for chunk in job.chunks:
            if (
                not chunk.text.strip()
                or len(chunk.text) > self._max_text_chars
                or any(ord(character) < 32 and character not in "\t\n\r" for character in chunk.text)
            ):
                raise EmbeddingError("EMB-V-004", "Invalid embedding text", 400)

    def _validate_vectors(self, vectors: tuple[tuple[float, ...], ...], expected: int) -> None:
        if len(vectors) != expected:
            raise EmbeddingError("EMB-S-002", "Embedding provider returned an invalid batch", 503)
        for vector in vectors:
            norm = math.sqrt(sum(value * value for value in vector))
            if (
                len(vector) != self._provider.dimension
                or not all(math.isfinite(value) for value in vector)
                or abs(norm - 1.0) > 1e-6
            ):
                raise EmbeddingError("EMB-S-002", "Embedding provider returned an invalid vector", 503)

    def _fingerprint(self, job: EmbeddingJob) -> str:
        material = [
            job.tenant_id,
            job.knowledge_base_id,
            job.document_id,
            self._provider.model,
            self._provider.version,
            str(self._provider.dimension),
        ]
        material.extend(
            f"{chunk.chunk_id}:{chunk.source_sha256}:{sha256(chunk.text.encode('utf-8')).hexdigest()}"
            for chunk in job.chunks
        )
        return sha256("|".join(material).encode("utf-8")).hexdigest()
