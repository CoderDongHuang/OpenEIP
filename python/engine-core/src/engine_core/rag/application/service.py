"""Tenant-filtered RAG orchestration with verified citations."""

import math
from time import perf_counter

from engine_core.embedding.domain.ports import EmbeddingProvider, VectorRepository
from engine_core.rag.application.prompt import PromptBuilder
from engine_core.rag.domain.models import RagCitation, RagResult
from engine_core.rag.domain.ports import AnswerProvider
from engine_core.rag.shared.errors import RagError


class RagService:
    """Embed, retrieve, prompt, answer, and independently verify citations."""

    def __init__(
        self,
        embedding_provider: EmbeddingProvider,
        repository: VectorRepository,
        answer_provider: AnswerProvider,
        prompt_builder: PromptBuilder,
        max_query_chars: int,
        max_answer_chars: int,
        max_top_k: int,
    ) -> None:
        if max_query_chars < 1 or max_answer_chars < 1 or max_top_k < 1 or max_top_k > 100:
            raise ValueError("Invalid RAG limits")
        self._embedding_provider = embedding_provider
        self._repository = repository
        self._answer_provider = answer_provider
        self._prompt_builder = prompt_builder
        self._max_query_chars = max_query_chars
        self._max_answer_chars = max_answer_chars
        self._max_top_k = max_top_k

    def query(self, tenant_id: str, knowledge_base_id: str, question: str, top_k: int) -> RagResult:
        """Run one synchronous RAG query inside an already-authorized scope."""
        started = perf_counter()
        self._validate_query(question, top_k)
        try:
            batch = self._embedding_provider.embed((question,))
        except Exception as exception:
            raise RagError("RAG-S-001", "RAG provider is unavailable", 503) from exception
        if len(batch) != 1:
            raise RagError("RAG-S-002", "RAG provider returned an invalid query vector", 503)
        query_vector = batch[0]
        self._validate_vector(query_vector)
        try:
            matches = self._repository.search(tenant_id, knowledge_base_id, query_vector, top_k)
        except Exception as exception:
            raise RagError("RAG-S-001", "RAG provider is unavailable", 503) from exception
        prompt, contexts = self._prompt_builder.build(question, matches)
        try:
            provider_answer = self._answer_provider.answer(question, prompt, contexts)
        except Exception as exception:
            raise RagError("RAG-S-001", "RAG provider is unavailable", 503) from exception
        if (
            not provider_answer.answer.strip()
            or len(provider_answer.answer) > self._max_answer_chars
            or not provider_answer.model.strip()
            or len(provider_answer.model) > 128
            or not provider_answer.model_version.strip()
            or len(provider_answer.model_version) > 64
        ):
            raise RagError("RAG-S-002", "RAG provider returned an invalid answer", 503)
        by_chunk = {context.chunk_id: context for context in contexts}
        if len(provider_answer.cited_chunk_ids) != len(set(provider_answer.cited_chunk_ids)) or any(
            chunk_id not in by_chunk for chunk_id in provider_answer.cited_chunk_ids
        ):
            raise RagError("RAG-S-002", "RAG provider returned an invalid citation", 503)
        citations = tuple(
            RagCitation(
                by_chunk[chunk_id].document_id,
                chunk_id,
                by_chunk[chunk_id].source_sha256,
                by_chunk[chunk_id].score,
            )
            for chunk_id in provider_answer.cited_chunk_ids
        )
        return RagResult(
            answer=provider_answer.answer.strip(),
            model=provider_answer.model,
            model_version=provider_answer.model_version,
            citations=citations,
            retrieval_count=len(contexts),
            duration_ms=(perf_counter() - started) * 1000,
        )

    def _validate_query(self, question: str, top_k: int) -> None:
        if (
            not question.strip()
            or len(question) > self._max_query_chars
            or any(ord(character) < 32 and character not in "\t\n\r" for character in question)
        ):
            raise RagError("RAG-V-002", "Invalid RAG query", 400)
        if top_k < 1 or top_k > self._max_top_k:
            raise RagError("RAG-V-003", "Invalid RAG topK", 400)

    def _validate_vector(self, vector: tuple[float, ...]) -> None:
        norm = math.sqrt(sum(value * value for value in vector))
        if (
            len(vector) != self._embedding_provider.dimension
            or not all(math.isfinite(value) for value in vector)
            or abs(norm - 1.0) > 1e-6
        ):
            raise RagError("RAG-S-002", "RAG provider returned an invalid query vector", 503)
