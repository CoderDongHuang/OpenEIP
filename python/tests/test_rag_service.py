import pytest

from engine_core.embedding.domain.models import VectorRecord, VectorSearchResult
from engine_core.embedding.infrastructure.memory_repository import InMemoryVectorRepository
from engine_core.rag.application.prompt import PromptBuilder
from engine_core.rag.application.service import RagService
from engine_core.rag.domain.models import ProviderAnswer, RagContext
from engine_core.rag.infrastructure.deterministic_provider import DeterministicAnswerProvider
from engine_core.rag.shared.errors import RagError

TENANT = "11111111-1111-4111-8111-111111111111"
OTHER_TENANT = "22222222-2222-4222-8222-222222222222"
BASE = "33333333-3333-4333-8333-333333333333"
OTHER_BASE = "44444444-4444-4444-8444-444444444444"
DOCUMENT = "55555555-5555-4555-8555-555555555555"
CHUNK = "chk_" + "a" * 32


class FixedEmbeddingProvider:
    model = "query-fixture"
    version = "1"
    dimension = 2

    def __init__(self, vectors: tuple[tuple[float, ...], ...] = ((1.0, 0.0),)) -> None:
        self.vectors = vectors

    def embed(self, texts: tuple[str, ...]) -> tuple[tuple[float, ...], ...]:
        del texts
        return self.vectors


class CapturingAnswerProvider:
    def __init__(self, answer: ProviderAnswer | None = None, fail: bool = False) -> None:
        self.answer_value = answer
        self.fail = fail
        self.contexts: tuple[RagContext, ...] = ()

    def answer(self, question: str, prompt: str, contexts: tuple[RagContext, ...]) -> ProviderAnswer:
        del question, prompt
        self.contexts = contexts
        if self.fail:
            raise RuntimeError("provider secret")
        return self.answer_value or ProviderAnswer("grounded", "fixture", "1", tuple(c.chunk_id for c in contexts))


class FailingRepository(InMemoryVectorRepository):
    def search(
        self, tenant_id: str, knowledge_base_id: str, query: tuple[float, ...], top_k: int
    ) -> tuple[VectorSearchResult, ...]:
        del tenant_id, knowledge_base_id, query, top_k
        raise RuntimeError("database credential")


def _record(tenant: str = TENANT, base: str = BASE, chunk: str = CHUNK) -> VectorRecord:
    return VectorRecord(tenant, base, DOCUMENT, chunk, "trusted product guide", "b" * 64, "fixture", "1", (1.0, 0.0))


def _service(
    repository: InMemoryVectorRepository | None = None,
    embedding_provider: object | None = None,
    answer_provider: CapturingAnswerProvider | DeterministicAnswerProvider | None = None,
) -> RagService:
    return RagService(
        embedding_provider=embedding_provider or FixedEmbeddingProvider(),  # type: ignore[arg-type]
        repository=repository or InMemoryVectorRepository(),
        answer_provider=answer_provider or DeterministicAnswerProvider(),
        prompt_builder=PromptBuilder(1000),
        max_query_chars=100,
        max_answer_chars=1000,
        max_top_k=20,
    )


def test_query_filters_tenant_and_base_before_answer_and_verifies_citation() -> None:
    repository = InMemoryVectorRepository()
    repository.upsert(
        (
            _record(),
            _record(OTHER_TENANT, BASE, "chk_" + "b" * 32),
            _record(TENANT, OTHER_BASE, "chk_" + "c" * 32),
        )
    )
    answer = CapturingAnswerProvider()
    result = _service(repository, answer_provider=answer).query(TENANT, BASE, "product guide", 10)

    assert [context.chunk_id for context in answer.contexts] == [CHUNK]
    assert result.retrieval_count == 1
    assert result.citations[0].chunk_id == CHUNK
    assert result.citations[0].source_sha256 == "b" * 64


def test_empty_retrieval_returns_explicit_no_context_result() -> None:
    result = _service().query(TENANT, BASE, "missing", 5)
    assert result.answer == "No relevant context was found."
    assert result.citations == ()
    assert result.retrieval_count == 0


@pytest.mark.parametrize("vectors", [(), ((1.0,),), ((float("nan"), 0.0),), ((0.5, 0.0),)])
def test_query_rejects_invalid_provider_vectors(vectors: tuple[tuple[float, ...], ...]) -> None:
    with pytest.raises(RagError) as failure:
        _service(embedding_provider=FixedEmbeddingProvider(vectors)).query(TENANT, BASE, "query", 1)
    assert failure.value.code == "RAG-S-002"


@pytest.mark.parametrize(
    "answer",
    [
        ProviderAnswer("ok", "fixture", "1", ("forged",)),
        ProviderAnswer("ok", "fixture", "1", (CHUNK, CHUNK)),
        ProviderAnswer("", "fixture", "1", ()),
        ProviderAnswer("ok", "", "1", ()),
    ],
)
def test_query_rejects_forged_duplicate_or_invalid_provider_output(answer: ProviderAnswer) -> None:
    repository = InMemoryVectorRepository()
    repository.upsert((_record(),))
    with pytest.raises(RagError) as failure:
        _service(repository, answer_provider=CapturingAnswerProvider(answer)).query(TENANT, BASE, "query", 1)
    assert failure.value.code == "RAG-S-002"


def test_query_maps_answer_embedding_and_repository_failures_without_details() -> None:
    cases = (
        _service(answer_provider=CapturingAnswerProvider(fail=True)),
        _service(embedding_provider=FailingEmbeddingProvider()),
        _service(repository=FailingRepository()),
    )
    for service in cases:
        with pytest.raises(RagError) as failure:
            service.query(TENANT, BASE, "query", 1)
        assert failure.value.code == "RAG-S-001"
        assert "secret" not in failure.value.message
        assert "credential" not in failure.value.message


class FailingEmbeddingProvider(FixedEmbeddingProvider):
    def embed(self, texts: tuple[str, ...]) -> tuple[tuple[float, ...], ...]:
        del texts
        raise RuntimeError("embedding secret")


def test_query_validates_question_top_k_and_configuration() -> None:
    service = _service()
    for question, top_k in (("", 1), ("bad\x00query", 1), ("x" * 101, 1), ("ok", 0), ("ok", 21)):
        with pytest.raises(RagError):
            service.query(TENANT, BASE, question, top_k)
    with pytest.raises(ValueError):
        RagService(
            FixedEmbeddingProvider(),
            InMemoryVectorRepository(),
            DeterministicAnswerProvider(),
            PromptBuilder(1),
            0,
            1,
            1,
        )
