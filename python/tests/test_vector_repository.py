import pytest

from engine_core.embedding.domain.models import VectorRecord
from engine_core.embedding.infrastructure.memory_repository import InMemoryVectorRepository
from engine_core.embedding.shared.errors import EmbeddingError

TENANT_A = "11111111-1111-4111-8111-111111111111"
TENANT_B = "22222222-2222-4222-8222-222222222222"
BASE_A = "33333333-3333-4333-8333-333333333333"
BASE_B = "44444444-4444-4444-8444-444444444444"
DOCUMENT = "55555555-5555-4555-8555-555555555555"


def _record(tenant: str, base: str, chunk: str, vector: tuple[float, ...]) -> VectorRecord:
    return VectorRecord(tenant, base, DOCUMENT, chunk, "a" * 64, "fixture", "1", vector)


def test_upsert_search_and_delete_are_tenant_and_base_scoped() -> None:
    repository = InMemoryVectorRepository()
    repository.upsert(
        (
            _record(TENANT_A, BASE_A, "chk_" + "1" * 32, (1.0, 0.0)),
            _record(TENANT_A, BASE_A, "chk_" + "2" * 32, (0.0, 1.0)),
            _record(TENANT_B, BASE_A, "chk_" + "3" * 32, (1.0, 0.0)),
            _record(TENANT_A, BASE_B, "chk_" + "4" * 32, (1.0, 0.0)),
        )
    )
    matches = repository.search(TENANT_A, BASE_A, (1.0, 0.0), 10)
    assert [match.chunk_id for match in matches] == ["chk_" + "1" * 32, "chk_" + "2" * 32]
    assert matches[0].score == pytest.approx(1.0)

    repository.upsert((_record(TENANT_A, BASE_A, "chk_" + "1" * 32, (0.0, 1.0)),))
    assert repository.search(TENANT_A, BASE_A, (1.0, 0.0), 1)[0].chunk_id == "chk_" + "1" * 32
    assert repository.delete_document(TENANT_B, BASE_A, DOCUMENT) == 1
    assert len(repository.search(TENANT_A, BASE_A, (1.0, 0.0), 10)) == 2
    assert repository.delete_document(TENANT_A, BASE_A, DOCUMENT) == 2
    assert repository.search(TENANT_A, BASE_A, (1.0, 0.0), 10) == ()


def test_search_rejects_invalid_query_and_dimension_poisoning() -> None:
    repository = InMemoryVectorRepository()
    with pytest.raises(EmbeddingError):
        repository.search(TENANT_A, BASE_A, (), 1)
    with pytest.raises(EmbeddingError):
        repository.search(TENANT_A, BASE_A, (float("nan"),), 1)
    with pytest.raises(EmbeddingError):
        repository.search(TENANT_A, BASE_A, (0.5,), 1)
    with pytest.raises(EmbeddingError):
        repository.search(TENANT_A, BASE_A, (1.0,), 101)

    repository.upsert((_record(TENANT_A, BASE_A, "chk_" + "1" * 32, (1.0, 0.0)),))
    with pytest.raises(EmbeddingError, match="dimension"):
        repository.search(TENANT_A, BASE_A, (1.0,), 1)


def test_equal_scores_use_stable_chunk_id_tie_break() -> None:
    repository = InMemoryVectorRepository()
    repository.upsert(
        (
            _record(TENANT_A, BASE_A, "chk_" + "b" * 32, (1.0, 0.0)),
            _record(TENANT_A, BASE_A, "chk_" + "a" * 32, (1.0, 0.0)),
        )
    )
    assert [result.chunk_id for result in repository.search(TENANT_A, BASE_A, (1.0, 0.0), 2)] == [
        "chk_" + "a" * 32,
        "chk_" + "b" * 32,
    ]
