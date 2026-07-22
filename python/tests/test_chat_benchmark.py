import asyncio
import json
import os
from pathlib import Path
from statistics import median
from time import perf_counter

import pytest

from engine_core.chat.application.service import ChatService
from engine_core.embedding.domain.models import VectorRecord
from engine_core.embedding.infrastructure.deterministic_provider import DeterministicEmbeddingProvider
from engine_core.embedding.infrastructure.memory_repository import InMemoryVectorRepository
from engine_core.rag.application.prompt import PromptBuilder
from engine_core.rag.application.service import RagService
from engine_core.rag.infrastructure.deterministic_provider import DeterministicAnswerProvider

TENANT = "11111111-1111-4111-8111-111111111111"
USER = "22222222-2222-4222-8222-222222222222"
SESSION = "33333333-3333-4333-8333-333333333333"
BASE = "44444444-4444-4444-8444-444444444444"
DOCUMENT = "55555555-5555-4555-8555-555555555555"
TARGET = "exact streaming knowledge answer"
RECORDS = 1000
WARMUPS = 5
SAMPLES = 100
CONCURRENCY = 20


@pytest.mark.asyncio
@pytest.mark.benchmark
async def test_chat_first_token_completion_and_concurrent_stream_latency() -> None:
    service = _service()
    for index in range(WARMUPS):
        await _measure(service, f"warmup-{index}")

    first_tokens: list[float] = []
    completions: list[float] = []
    for index in range(SAMPLES):
        first, complete = await _measure(service, f"sample-{index}")
        first_tokens.append(first)
        completions.append(complete)

    concurrent_started = perf_counter()
    concurrent = await asyncio.gather(*(_measure(service, f"concurrent-{index}") for index in range(CONCURRENCY)))
    concurrent_ms = (perf_counter() - concurrent_started) * 1000
    ordered_first = sorted(first_tokens)
    ordered_complete = sorted(completions)
    first_p99 = percentile(ordered_first, 0.99)
    complete_p99 = percentile(ordered_complete, 0.99)
    assert first_p99 < 100.0
    assert complete_p99 < 500.0
    assert all(first <= complete for first, complete in concurrent)
    assert concurrent_ms < 1000.0

    output = os.getenv("OPENEIP_CHAT_BENCHMARK_OUTPUT")
    if output:
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(
                {
                    "module": "chat",
                    "operation": "1,000-record RAG query to bounded deterministic SSE completion",
                    "recordCount": RECORDS,
                    "warmups": WARMUPS,
                    "samples": SAMPLES,
                    "concurrentStreams": CONCURRENCY,
                    "firstTokenP50Ms": round(median(ordered_first), 3),
                    "firstTokenP95Ms": round(percentile(ordered_first, 0.95), 3),
                    "firstTokenP99Ms": round(first_p99, 3),
                    "completionP50Ms": round(median(ordered_complete), 3),
                    "completionP95Ms": round(percentile(ordered_complete, 0.95), 3),
                    "completionP99Ms": round(complete_p99, 3),
                    "concurrentBatchMs": round(concurrent_ms, 3),
                    "thresholdFirstTokenP99Ms": 100,
                    "thresholdCompletionP99Ms": 500,
                    "result": "PASS",
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )


async def _measure(service: ChatService, request_id: str) -> tuple[float, float]:
    started = perf_counter()
    first_token = 0.0
    terminal = False
    async for event in service.stream(TENANT, USER, SESSION, request_id, BASE, TARGET, 5):
        if event.startswith("event: token") and first_token == 0.0:
            first_token = (perf_counter() - started) * 1000
        if event.startswith("event: done"):
            terminal = True
    completed = (perf_counter() - started) * 1000
    assert first_token > 0.0
    assert terminal
    return first_token, completed


def _service() -> ChatService:
    provider = DeterministicEmbeddingProvider(64)
    repository = InMemoryVectorRepository()
    texts = tuple(TARGET if index == 0 else f"unrelated streaming archive record {index}" for index in range(RECORDS))
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
    rag = RagService(
        provider,
        repository,
        DeterministicAnswerProvider(),
        PromptBuilder(16_000),
        2000,
        8000,
        20,
    )
    return ChatService(rag, 4000, 64)


def percentile(values: list[float], ratio: float) -> float:
    return values[max(0, int(len(values) * ratio + 0.999999) - 1)]
