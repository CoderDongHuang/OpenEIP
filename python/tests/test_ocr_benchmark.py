import json
import os
import platform
import sys
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter

import pytest
from ocr_fixtures import render_text

from engine_core.ocr.application.service import OcrService
from engine_core.ocr.infrastructure.deterministic_provider import DeterministicRasterProvider
from engine_core.ocr.infrastructure.image_decoder import SafeImageDecoder

WARMUPS = 10
MEASUREMENTS = 100
THRESHOLD_P99_MS = 100.0


@pytest.mark.benchmark
def test_ocr_pipeline_p99_stays_below_mvp_baseline() -> None:
    payload = render_text("OPENEIP OCR 2026", scale=4)
    service = OcrService(
        SafeImageDecoder(
            max_body_bytes=5 * 1024 * 1024,
            max_width=10_000,
            max_height=10_000,
            max_pixels=20_000_000,
        ),
        DeterministicRasterProvider(),
    )

    for _ in range(WARMUPS):
        assert service.recognize(payload, "image/png").text == "OPENEIP OCR 2026"

    durations: list[float] = []
    for _ in range(MEASUREMENTS):
        started = perf_counter()
        result = service.recognize(payload, "image/png")
        durations.append((perf_counter() - started) * 1000)
        assert result.text == "OPENEIP OCR 2026"

    durations.sort()
    p50 = _percentile(durations, 0.50)
    p95 = _percentile(durations, 0.95)
    p99 = _percentile(durations, 0.99)
    _write_evidence(payload, p50, p95, p99)

    assert p99 < THRESHOLD_P99_MS


def _percentile(values: list[float], percentile: float) -> float:
    index = max(0, min(len(values) - 1, int(percentile * len(values) + 0.999999) - 1))
    return values[index]


def _write_evidence(payload: bytes, p50: float, p95: float, p99: float) -> None:
    configured = os.getenv("OPENEIP_OCR_BENCHMARK_OUTPUT")
    if not configured:
        return
    output = Path(configured)
    output.parent.mkdir(parents=True, exist_ok=True)
    evidence = {
        "benchmark": "ocr-deterministic-raster-pipeline",
        "recordedAt": datetime.now(UTC).isoformat(),
        "payloadBytes": len(payload),
        "corpus": "OPENEIP OCR 2026",
        "warmups": WARMUPS,
        "measurements": MEASUREMENTS,
        "p50Ms": round(p50, 3),
        "p95Ms": round(p95, 3),
        "p99Ms": round(p99, 3),
        "imagesPerSecondAtP50": round(1000 / p50, 2),
        "thresholdP99Ms": THRESHOLD_P99_MS,
        "pythonVersion": sys.version.split()[0],
        "pillowVersion": __import__("PIL").__version__,
        "os": f"{platform.system()} {platform.release()} {platform.version()}",
    }
    output.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
