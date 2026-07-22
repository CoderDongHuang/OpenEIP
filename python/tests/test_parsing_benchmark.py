import json
import os
import platform
import sys
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter

import pytest

from engine_core.parsing.application.service import DocumentParsingService
from engine_core.parsing.infrastructure.input_decoder import DocumentInputDecoder

WARMUPS = 5
MEASUREMENTS = 30
THRESHOLD_P99_MS = 250.0
TENANT_ID = "11111111-1111-4111-8111-111111111111"
DOCUMENT_ID = "33333333-3333-4333-8333-333333333333"


@pytest.mark.benchmark
def test_one_mib_parsing_pipeline_p99_stays_below_baseline() -> None:
    paragraph = "OpenEIP document parsing keeps traceable chunks and stable hashes.\n\n"
    payload = (paragraph * (1024 * 1024 // len(paragraph) + 1)).encode()[: 1024 * 1024]
    service = DocumentParsingService(
        decoder=DocumentInputDecoder(max_body_bytes=2 * 1024 * 1024),
        chunk_size=1000,
        overlap=100,
        max_chunks=10_000,
    )

    for _ in range(WARMUPS):
        service.parse(payload, "text/plain", TENANT_ID, DOCUMENT_ID)

    durations: list[float] = []
    chunk_count = 0
    for _ in range(MEASUREMENTS):
        started = perf_counter()
        result = service.parse(payload, "text/plain", TENANT_ID, DOCUMENT_ID)
        durations.append((perf_counter() - started) * 1000)
        chunk_count = len(result.chunks)

    durations.sort()
    p50 = _percentile(durations, 0.50)
    p95 = _percentile(durations, 0.95)
    p99 = _percentile(durations, 0.99)
    _write_evidence(len(payload), chunk_count, p50, p95, p99)
    assert p99 < THRESHOLD_P99_MS


def _percentile(values: list[float], percentile: float) -> float:
    index = max(0, min(len(values) - 1, int(percentile * len(values) + 0.999999) - 1))
    return values[index]


def _write_evidence(payload_bytes: int, chunk_count: int, p50: float, p95: float, p99: float) -> None:
    configured = os.getenv("OPENEIP_PARSING_BENCHMARK_OUTPUT")
    if not configured:
        return
    output = Path(configured)
    output.parent.mkdir(parents=True, exist_ok=True)
    evidence = {
        "benchmark": "document-parsing-one-mib",
        "recordedAt": datetime.now(UTC).isoformat(),
        "payloadBytes": payload_bytes,
        "chunkCount": chunk_count,
        "chunkSize": 1000,
        "overlap": 100,
        "warmups": WARMUPS,
        "measurements": MEASUREMENTS,
        "p50Ms": round(p50, 3),
        "p95Ms": round(p95, 3),
        "p99Ms": round(p99, 3),
        "mibPerSecondAtP50": round(1000 / p50, 2),
        "thresholdP99Ms": THRESHOLD_P99_MS,
        "pythonVersion": sys.version.split()[0],
        "os": f"{platform.system()} {platform.release()} {platform.version()}",
    }
    output.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
