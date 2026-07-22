import math
from dataclasses import replace

import pytest

from engine_core.embedding.application.service import EmbeddingService
from engine_core.embedding.domain.models import EmbeddingChunk, EmbeddingJob
from engine_core.embedding.infrastructure.deterministic_provider import DeterministicEmbeddingProvider
from engine_core.embedding.infrastructure.memory_repository import InMemoryVectorRepository
from engine_core.embedding.shared.errors import EmbeddingError

TENANT = "11111111-1111-4111-8111-111111111111"
BASE = "22222222-2222-4222-8222-222222222222"
DOCUMENT = "33333333-3333-4333-8333-333333333333"
JOB = "44444444-4444-4444-8444-444444444444"


def _job(text: str = "alpha guide", job_id: str = JOB) -> EmbeddingJob:
    return EmbeddingJob(
        job_id,
        TENANT,
        BASE,
        DOCUMENT,
        (EmbeddingChunk("chk_" + "a" * 32, text, "b" * 64),),
    )


def _service(provider: object | None = None, max_jobs: int = 10) -> EmbeddingService:
    return EmbeddingService(
        provider=provider or DeterministicEmbeddingProvider(8),  # type: ignore[arg-type]
        repository=InMemoryVectorRepository(),
        max_batch_size=2,
        max_text_chars=20,
        max_jobs=max_jobs,
    )


def test_service_persists_valid_vectors_and_exact_replay() -> None:
    service = _service()
    first = service.execute(_job())
    second = service.execute(_job())
    assert first.replayed is False
    assert second.replayed is True
    assert first.vectors == second.vectors
    assert first.dimension == 8
    assert math.sqrt(sum(value * value for value in first.vectors[0].vector)) == pytest.approx(1.0)


def test_service_rejects_collision_duplicate_chunks_and_invalid_text() -> None:
    service = _service()
    service.execute(_job())
    with pytest.raises(EmbeddingError, match="collision"):
        service.execute(_job("different"))

    duplicate = replace(_job(job_id="55555555-5555-4555-8555-555555555555"), chunks=_job().chunks * 2)
    with pytest.raises(EmbeddingError, match="unique"):
        service.execute(duplicate)
    with pytest.raises(EmbeddingError, match="text"):
        service.execute(_job(" \n", "66666666-6666-4666-8666-666666666666"))
    with pytest.raises(EmbeddingError, match="text"):
        service.execute(_job("bad\x00text", "77777777-7777-4777-8777-777777777777"))


class PoisonProvider:
    model = "poison"
    version = "1"
    dimension = 2

    def __init__(self, vectors: tuple[tuple[float, ...], ...] | None = None) -> None:
        self.vectors = vectors

    def embed(self, texts: tuple[str, ...]) -> tuple[tuple[float, ...], ...]:
        if self.vectors is None:
            raise RuntimeError("secret provider failure")
        return self.vectors


@pytest.mark.parametrize(
    "vectors",
    [(), ((1.0,),), ((float("nan"), 0.0),), ((0.5, 0.0),)],
)
def test_service_rejects_provider_batch_dimension_finite_and_norm_poisoning(
    vectors: tuple[tuple[float, ...], ...],
) -> None:
    with pytest.raises(EmbeddingError) as failure:
        _service(PoisonProvider(vectors)).execute(_job())
    assert failure.value.code == "EMB-S-002"
    assert "secret" not in failure.value.message


def test_service_maps_provider_failure_without_details() -> None:
    with pytest.raises(EmbeddingError) as failure:
        _service(PoisonProvider()).execute(_job())
    assert failure.value.code == "EMB-S-001"
    assert "secret" not in failure.value.message


def test_service_bounds_configuration_batch_text_and_job_cache() -> None:
    with pytest.raises(ValueError):
        EmbeddingService(DeterministicEmbeddingProvider(8), InMemoryVectorRepository(), 0, 1, 1)
    service = _service(max_jobs=1)
    too_many = replace(_job(), chunks=_job().chunks * 3)
    with pytest.raises(EmbeddingError):
        service.execute(too_many)
    with pytest.raises(EmbeddingError):
        service.execute(_job("x" * 21))
    service.execute(_job(job_id="88888888-8888-4888-8888-888888888888"))
    service.execute(_job(job_id="99999999-9999-4999-8999-999999999999"))
    assert service.execute(_job(job_id="88888888-8888-4888-8888-888888888888")).replayed is False
