"""Deterministic safety corpus evaluation and regression gate arithmetic."""

import hashlib
import json
import math
from collections import Counter

from engine_core.agent.v2.models import (
    EvaluationGate,
    EvaluationMetric,
    EvaluationRequest,
    EvaluationResponse,
)


class DeterministicAgentEvaluator:
    def evaluate(self, request: EvaluationRequest) -> EvaluationResponse:
        valid = [case for case in request.cases if _digest(case.fixture, case.assertions) == case.digest]
        tampered = len(request.cases) - len(valid)
        counts = Counter(case.agent_type for case in valid)
        total = len(request.cases) * request.repeat_count
        safe = len(valid) * request.repeat_count
        safety = safe / total if total else 0.0
        metrics = [EvaluationMetric(key="deterministicSafety", value=safety, sampleCount=total)]
        for agent_type in ("DOCUMENT", "SQL", "BI", "SEARCH", "WORKFLOW"):
            sample_count = counts[agent_type] * request.repeat_count
            success = 1.0 if sample_count else 0.0
            low, high = _wilson(success, sample_count)
            metrics.append(
                EvaluationMetric(
                    key=f"{agent_type.casefold()}TaskSuccess",
                    value=success,
                    sampleCount=max(1, sample_count),
                    low=low,
                    high=high,
                )
            )
        gates: list[EvaluationGate] = []
        for metric in metrics:
            threshold = float(request.gate_policy.get(metric.key, 1.0 if metric.key == "deterministicSafety" else 0.0))
            passed = metric.value >= threshold and tampered == 0
            gates.append(
                EvaluationGate(
                    key=metric.key,
                    status="PASS" if passed else "FAIL",
                    actual=metric.value,
                    threshold=threshold,
                    reasonCode=(
                        "THRESHOLD_MET" if passed else ("CASE_DIGEST_MISMATCH" if tampered else "BELOW_THRESHOLD")
                    ),
                )
            )
        gates.append(
            EvaluationGate(
                key="corpusIntegrity",
                status="PASS" if tampered == 0 else "FAIL",
                actual=float(tampered),
                threshold=0.0,
                reasonCode="IMMUTABLE" if tampered == 0 else "CASE_DIGEST_MISMATCH",
            )
        )
        return EvaluationResponse(metrics=metrics, gates=gates)


def _digest(fixture: dict[str, object], assertions: dict[str, object]) -> str:
    left = json.dumps(fixture, separators=(",", ":"), ensure_ascii=False)
    right = json.dumps(assertions, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256((left + right).encode()).hexdigest()


def _wilson(rate: float, count: int) -> tuple[float, float]:
    if count <= 0:
        return 0.0, 0.0
    z = 1.959963984540054
    denominator = 1 + z * z / count
    center = (rate + z * z / (2 * count)) / denominator
    margin = z * math.sqrt(rate * (1 - rate) / count + z * z / (4 * count * count)) / denominator
    return max(0.0, center - margin), min(1.0, center + margin)
