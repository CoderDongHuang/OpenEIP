import base64
import hashlib
import hmac
import json
import os
import platform
import time
from collections.abc import Awaitable, Callable
from functools import partial
from pathlib import Path
from time import perf_counter
from uuid import uuid4

import pytest

from engine_core.agent.v2.capability import CapabilityVerifier
from engine_core.agent.v2.evaluation import DeterministicAgentEvaluator
from engine_core.agent.v2.mcp_gateway import McpDiscoveryGateway
from engine_core.agent.v2.models import (
    EvaluationRequest,
    ExecuteAgentRequest,
    ExecutionCapability,
    McpDiscoveryRequest,
)
from engine_core.agent.v2.runtime import AgentRuntimeV2

SECRET = "benchmark-agent-capability-secret-at-least-32-characters"
WARMUPS = 5
SAMPLES = 100
EVALUATION_SAMPLES = 30


@pytest.mark.asyncio
@pytest.mark.benchmark
async def test_agent_v2_control_plane_benchmark() -> None:
    capability = ExecutionCapability.model_validate(_claims(str(uuid4())))
    execute_request = ExecuteAgentRequest.model_validate(
        {"input": "find the governed policy", "agentType": "SEARCH", "agentVersion": 1}
    )
    runtime = AgentRuntimeV2()
    gateway = McpDiscoveryGateway()
    discovery_request = McpDiscoveryRequest.model_validate(
        {
            "serverId": str(uuid4()),
            "transport": "STDIO",
            "endpoint": "managed://fixture/agent-v0.6",
            "authType": "NONE",
        }
    )
    evaluation_request = _evaluation_request()

    for _ in range(WARMUPS):
        CapabilityVerifier(SECRET).verify(_token(_claims(str(uuid4()))))
        await runtime.execute(execute_request, capability)
        await gateway.discover(discovery_request)
        DeterministicAgentEvaluator().evaluate(evaluation_request)

    capability_samples = [
        _measure(partial(_verify_token, token)) for token in (_token(_claims(str(uuid4()))) for _ in range(SAMPLES))
    ]
    runtime_samples = [await _measure_async(runtime.execute(execute_request, capability)) for _ in range(SAMPLES)]
    discovery_samples = [await _measure_async(gateway.discover(discovery_request)) for _ in range(SAMPLES)]
    evaluation_samples = [
        _measure(lambda: DeterministicAgentEvaluator().evaluate(evaluation_request)) for _ in range(EVALUATION_SAMPLES)
    ]

    metrics = {
        "capabilityVerification": _summary(capability_samples, 25.0),
        "boundedSearchRuntime": _summary(runtime_samples, 25.0),
        "managedMcpDiscovery": _summary(discovery_samples, 25.0),
        "evaluation500Cases": _summary(evaluation_samples, 500.0),
    }
    assert all(metric["p99Ms"] < metric["thresholdP99Ms"] for metric in metrics.values())

    output = os.getenv("OPENEIP_AGENT_V2_BENCHMARK_OUTPUT")
    if output:
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(
                {
                    "module": "agent-v2",
                    "version": "0.6.0-alpha",
                    "operation": "capability, bounded runtime, MCP fixture discovery, and deterministic evaluation",
                    "environment": {
                        "python": platform.python_version(),
                        "implementation": platform.python_implementation(),
                        "platform": platform.platform(),
                    },
                    "warmups": WARMUPS,
                    "samples": {"default": SAMPLES, "evaluation500Cases": EVALUATION_SAMPLES},
                    "corpus": {"cases": 500, "repeats": 3, "evaluatedCases": 1500},
                    "metrics": metrics,
                    "errors": 0,
                    "result": "PASS",
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )


def _claims(nonce: str) -> dict[str, object]:
    now = int(time.time())
    return {
        "iss": "openeip-java",
        "aud": "openeip-agent-runtime",
        "tenantId": "default",
        "principalId": str(uuid4()),
        "runId": str(uuid4()),
        "agentVersionId": str(uuid4()),
        "agentDigest": "a" * 64,
        "dependencyDigest": "b" * 64,
        "resourceHandles": [f"knowledge:{uuid4()}"],
        "budget": {"maxSteps": 8, "maxDurationSeconds": 60, "maxToolCalls": 8, "maxWorkers": 2},
        "tools": [
            {
                "id": "openeip.knowledge.search",
                "version": "1.0.0",
                "digest": "c" * 64,
                "operations": ["search"],
                "approvalMode": "NONE",
                "riskClass": "READ",
            }
        ],
        "iat": now,
        "exp": now + 300,
        "nonce": nonce,
    }


def _token(claims: dict[str, object]) -> str:
    payload = base64.urlsafe_b64encode(json.dumps(claims, separators=(",", ":")).encode()).rstrip(b"=")
    signature = hmac.new(SECRET.encode(), payload, hashlib.sha256).digest()
    return payload.decode() + "." + base64.urlsafe_b64encode(signature).rstrip(b"=").decode()


def _verify_token(token: str) -> ExecutionCapability:
    return CapabilityVerifier(SECRET).verify(token)


def _evaluation_request() -> EvaluationRequest:
    cases: list[dict[str, object]] = []
    for agent_type in ("DOCUMENT", "SQL", "BI", "SEARCH", "WORKFLOW"):
        for index in range(100):
            fixture = {"agentType": agent_type, "category": "success", "seed": index}
            assertions = {"authorizedSideEffects": 0, "secretLeaks": 0}
            digest = hashlib.sha256(
                (json.dumps(fixture, separators=(",", ":")) + json.dumps(assertions, separators=(",", ":"))).encode()
            ).hexdigest()
            cases.append(
                {
                    "key": f"{agent_type.casefold()}-{index}",
                    "agentType": agent_type,
                    "fixture": fixture,
                    "assertions": assertions,
                    "digest": digest,
                }
            )
    return EvaluationRequest.model_validate(
        {
            "suiteDigest": "a" * 64,
            "candidateDigest": "b" * 64,
            "baselineDigest": "c" * 64,
            "repeatCount": 3,
            "cases": cases,
            "gatePolicy": {
                "deterministicSafety": 1.0,
                "documentTaskSuccess": 0.9,
                "sqlTaskSuccess": 0.85,
                "biTaskSuccess": 0.85,
                "searchTaskSuccess": 0.9,
                "workflowTaskSuccess": 0.9,
            },
        }
    )


def _measure(call: Callable[[], object]) -> float:
    started = perf_counter()
    call()
    return (perf_counter() - started) * 1000


async def _measure_async(awaitable: Awaitable[object]) -> float:
    started = perf_counter()
    await awaitable
    return (perf_counter() - started) * 1000


def _summary(samples: list[float], threshold: float) -> dict[str, float]:
    ordered = sorted(samples)
    return {
        "p50Ms": round(_percentile(ordered, 0.50), 3),
        "p95Ms": round(_percentile(ordered, 0.95), 3),
        "p99Ms": round(_percentile(ordered, 0.99), 3),
        "thresholdP99Ms": threshold,
    }


def _percentile(values: list[float], ratio: float) -> float:
    return values[max(0, int(len(values) * ratio + 0.999999) - 1)]
