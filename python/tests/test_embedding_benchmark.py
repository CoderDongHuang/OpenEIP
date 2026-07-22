import json
import os
from pathlib import Path
from statistics import median
from time import perf_counter
from uuid import uuid4

import pytest

from engine_core.embedding.application.service import EmbeddingService
from engine_core.embedding.domain.models import EmbeddingChunk, EmbeddingJob
from engine_core.embedding.infrastructure.deterministic_provider import DeterministicEmbeddingProvider
from engine_core.embedding.infrastructure.memory_repository import InMemoryVectorRepository

TENANT = "11111111-1111-4111-8111-111111111111"
BASE = "22222222-2222-4222-8222-222222222222"
DOCUMENT = "33333333-3333-4333-8333-333333333333"
WARMUPS = 5
SAMPLES = 100


@pytest.mark.benchmark
def test_embedding_batch_latency_throughput_and_search_correctness() -> None:
    provider = DeterministicEmbeddingProvider(64)
    repository = InMemoryVectorRepository()
    service = EmbeddingService(provider, repository, 32, 8192, WARMUPS + SAMPLES + 1)
    chunks = tuple(
        EmbeddingChunk(
            f"chk_{index:032x}",
            "exact alpha operations guide" if index == 0 else f"unrelated invoice record {index}",
            f"{index:064x}",
        )
        for index in range(32)
    )

    for _ in range(WARMUPS):
        service.execute(EmbeddingJob(str(uuid4()), TENANT, BASE, DOCUMENT, chunks))

    durations: list[float] = []
    for _ in range(SAMPLES):
        started = perf_counter()
        service.execute(EmbeddingJob(str(uuid4()), TENANT, BASE, DOCUMENT, chunks))
        durations.append((perf_counter() - started) * 1000)

    ordered = sorted(durations)
    p95 = percentile(ordered, 0.95)
    p99 = percentile(ordered, 0.99)
    assert p99 < 50.0
    query = provider.embed(("exact alpha operations guide",))[0]
    matches = repository.search(TENANT, BASE, query, 3)
    assert matches[0].chunk_id == chunks[0].chunk_id

    output = os.getenv("OPENEIP_EMBEDDING_BENCHMARK_OUTPUT")
    if output:
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(
                {
                    "module": "embedding",
                    "operation": "32 text deterministic embed and scoped upsert",
                    "dimension": 64,
                    "batchSize": 32,
                    "warmups": WARMUPS,
                    "samples": SAMPLES,
                    "p50Ms": round(median(ordered), 3),
                    "p95Ms": round(p95, 3),
                    "p99Ms": round(p99, 3),
                    "batchesPerSecondAtP50": round(1000 / median(ordered), 2),
                    "vectorsPerSecondAtP50": round(32_000 / median(ordered), 2),
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
