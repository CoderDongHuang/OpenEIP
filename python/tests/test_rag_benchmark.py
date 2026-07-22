import json
import os
from pathlib import Path
from statistics import median
from time import perf_counter

import pytest

from engine_core.embedding.domain.models import VectorRecord
from engine_core.embedding.infrastructure.deterministic_provider import DeterministicEmbeddingProvider
from engine_core.embedding.infrastructure.memory_repository import InMemoryVectorRepository
from engine_core.rag.application.prompt import PromptBuilder
from engine_core.rag.application.service import RagService
from engine_core.rag.infrastructure.deterministic_provider import DeterministicAnswerProvider

TENANT = "11111111-1111-4111-8111-111111111111"
BASE = "22222222-2222-4222-8222-222222222222"
DOCUMENT = "33333333-3333-4333-8333-333333333333"
TARGET = "exact grounded retrieval operations guide"
RECORDS = 1000
WARMUPS = 5
SAMPLES = 100


@pytest.mark.benchmark
def test_rag_thousand_record_grounded_query_latency_and_quality() -> None:
    provider = DeterministicEmbeddingProvider(64)
    repository = InMemoryVectorRepository()
    texts = tuple(TARGET if index == 0 else f"unrelated accounting archive record {index}" for index in range(RECORDS))
    vectors = provider.embed(texts)
    repository.upsert(
        tuple(
            VectorRecord(
                TENANT,
                BASE,
                DOCUMENT,
                f"chk_{index:032x}",
                text,
                f"{index:064x}",
                provider.model,
                provider.version,
                vector,
            )
            for index, (text, vector) in enumerate(zip(texts, vectors, strict=True))
        )
    )
    service = RagService(
        provider,
        repository,
        DeterministicAnswerProvider(),
        PromptBuilder(16_000),
        2000,
        8000,
        20,
    )

    for _ in range(WARMUPS):
        service.query(TENANT, BASE, TARGET, 5)

    durations: list[float] = []
    result = service.query(TENANT, BASE, TARGET, 5)
    for _ in range(SAMPLES):
        started = perf_counter()
        result = service.query(TENANT, BASE, TARGET, 5)
        durations.append((perf_counter() - started) * 1000)

    ordered = sorted(durations)
    p95 = percentile(ordered, 0.95)
    p99 = percentile(ordered, 0.99)
    assert p99 < 50.0
    assert result.citations[0].chunk_id == "chk_" + "0" * 32
    assert TARGET in result.answer

    output = os.getenv("OPENEIP_RAG_BENCHMARK_OUTPUT")
    if output:
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(
                {
                    "module": "rag",
                    "operation": "deterministic embed, 1,000-record scoped search, prompt, answer, citation verify",
                    "dimension": 64,
                    "recordCount": RECORDS,
                    "topK": 5,
                    "warmups": WARMUPS,
                    "samples": SAMPLES,
                    "p50Ms": round(median(ordered), 3),
                    "p95Ms": round(p95, 3),
                    "p99Ms": round(p99, 3),
                    "queriesPerSecondAtP50": round(1000 / median(ordered), 2),
                    "top1ExactMatch": True,
                    "thresholdP99Ms": 50,
                    "result": "PASS",
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )


def percentile(values: list[float], ratio: float) -> float:
    return values[max(0, int(len(values) * ratio + 0.999999) - 1)]
