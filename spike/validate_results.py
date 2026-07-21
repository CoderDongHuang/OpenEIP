"""Validate committed Phase 1.5 evidence with the Python standard library."""

import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).parent


def load(relative_path: str) -> dict[str, Any]:
    """Load one evidence document."""
    path = ROOT / relative_path
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def require(condition: bool, message: str) -> None:
    """Raise a readable validation failure."""
    if not condition:
        raise ValueError(message)


def main() -> None:
    """Validate all acceptance invariants represented by committed evidence."""
    grpc = load("spike-001-grpc-java-python/results/result.json")
    require(grpc["passed"], "Spike-001 did not pass")
    require(grpc["unary"]["p99_ms"] < grpc["unary"]["threshold_p99_ms"], "Spike-001 P99 failed")
    require(grpc["error_propagation"]["passed"], "Spike-001 error propagation failed")

    kafka = load("spike-002-kafka-eventing/results/result.json")
    require(kafka["passed"], "Spike-002 did not pass")
    require(kafka["records_received"] == 2_003, "Spike-002 record count changed")
    require(kafka["duplicates_skipped"] == 1, "Spike-002 duplicate assertion failed")
    require(kafka["poison_attempts"] == 3 and kafka["dlq"]["found"], "Spike-002 DLQ assertion failed")

    milvus = load("spike-003-milvus-vector/results/result.json")
    require(milvus["passed"], "Spike-003 did not pass")
    require(milvus["inserted"] == milvus["documents"], "Spike-003 insert count failed")
    require(milvus["top1_accuracy"] == 1.0, "Spike-003 correctness failed")
    require(
        milvus["milvus_search"]["p99_ms"] < milvus["search_p99_threshold_ms"],
        "Spike-003 P99 failed",
    )

    mcp = load("spike-004-mcp-runtime/results/result.json")
    require(mcp["passed"] and mcp["schemas_present"], "Spike-004 protocol assertion failed")
    require(all(mcp["errors"].values()), "Spike-004 error handling failed")

    streaming = load("spike-005-llm-streaming/results/result.json")
    require(streaming["passed"], "Spike-005 did not pass")
    require(streaming["first_token_ms"] < streaming["first_token_threshold_ms"], "Spike-005 first token failed")
    require(streaming["total_ms"] < streaming["total_threshold_ms"], "Spike-005 total latency failed")
    require(streaming["client_cancellation_observed"], "Spike-005 cancellation failed")
    require(streaming["upstream_error_visible"], "Spike-005 upstream error failed")


if __name__ == "__main__":
    main()
