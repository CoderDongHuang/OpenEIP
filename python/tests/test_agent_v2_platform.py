import base64
import hashlib
import hmac
import json
import time
from uuid import uuid4

import pytest

from engine_core.agent.v2.capability import CapabilityError, CapabilityVerifier
from engine_core.agent.v2.evaluation import DeterministicAgentEvaluator
from engine_core.agent.v2.mcp_gateway import McpDiscoveryGateway, McpGatewayError
from engine_core.agent.v2.models import (
    EvaluationRequest,
    ExecuteAgentRequest,
    ExecutionCapability,
    McpDiscoveryRequest,
)
from engine_core.agent.v2.runtime import AgentRuntimeV2

SECRET = "test-agent-capability-secret-at-least-32-characters"


def _claims(*, expired: bool = False, tools: list[dict[str, object]] | None = None) -> dict[str, object]:
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
        "budget": {
            "maxSteps": 8,
            "maxDurationSeconds": 60,
            "maxToolCalls": 8,
            "maxWorkers": 2,
        },
        "tools": tools
        or [
            {
                "id": "openeip.knowledge.search",
                "version": "1.0.0",
                "digest": "c" * 64,
                "operations": ["search"],
                "approvalMode": "NONE",
                "riskClass": "READ",
            }
        ],
        "iat": now - 400 if expired else now,
        "exp": now - 100 if expired else now + 300,
        "nonce": str(uuid4()),
    }


def _token(claims: dict[str, object], secret: str = SECRET) -> str:
    payload = base64.urlsafe_b64encode(json.dumps(claims, separators=(",", ":")).encode()).rstrip(b"=")
    signature = hmac.new(secret.encode(), payload, hashlib.sha256).digest()
    return payload.decode() + "." + base64.urlsafe_b64encode(signature).rstrip(b"=").decode()


def test_capability_verifier_accepts_once_and_rejects_replay_tamper_and_expiry() -> None:
    verifier = CapabilityVerifier(SECRET)
    token = _token(_claims())
    assert verifier.verify(token).tenant_id == "default"
    with pytest.raises(CapabilityError):
        verifier.verify(token)
    with pytest.raises(CapabilityError):
        verifier.verify(token[:-1] + ("A" if token[-1] != "A" else "B"))
    with pytest.raises(CapabilityError):
        CapabilityVerifier(SECRET).verify(_token(_claims(expired=True)))


@pytest.mark.asyncio
async def test_runtime_enforces_grant_intersection_and_emits_safe_public_events() -> None:
    capability = ExecutionCapability.model_validate(_claims())
    runtime = AgentRuntimeV2()
    result = await runtime.execute(
        ExecuteAgentRequest.model_validate({"input": "find policy", "agentType": "SEARCH", "agentVersion": 1}),
        capability,
    )
    assert result.status == "SUCCEEDED"
    assert [event.type for event in result.events] == [
        "plan.created",
        "step.started",
        "tool.started",
        "tool.completed",
        "reflection.completed",
    ]
    serialized = result.model_dump_json(by_alias=True)
    assert "find policy" not in serialized
    assert "prompt" not in serialized.casefold()

    denied = await runtime.execute(
        ExecuteAgentRequest.model_validate({"input": "inspect", "agentType": "DOCUMENT", "agentVersion": 1}),
        capability,
    )
    assert denied.status == "FAILED"
    assert denied.failure_code == "AGENT2-P-001"


@pytest.mark.asyncio
async def test_mcp_managed_fixture_and_private_network_policy() -> None:
    gateway = McpDiscoveryGateway()
    result = await gateway.discover(
        McpDiscoveryRequest.model_validate(
            {
                "serverId": str(uuid4()),
                "transport": "STDIO",
                "endpoint": "managed://fixture/agent-v0.6",
                "authType": "NONE",
            }
        )
    )
    assert result.policy_status == "PASS"
    assert result.capabilities[0].name == "fixture.echo"

    with pytest.raises(McpGatewayError, match="private address"):
        await gateway.discover(
            McpDiscoveryRequest.model_validate(
                {
                    "serverId": str(uuid4()),
                    "transport": "STREAMABLE_HTTP",
                    "endpoint": "https://127.0.0.1/mcp",
                    "authType": "NONE",
                }
            )
        )


def test_deterministic_evaluator_gates_500_cases_and_rejects_tamper() -> None:
    cases: list[dict[str, object]] = []
    types = ("DOCUMENT", "SQL", "BI", "SEARCH", "WORKFLOW")
    for agent_type in types:
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
    request = EvaluationRequest.model_validate(
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
    result = DeterministicAgentEvaluator().evaluate(request)
    assert all(gate.status == "PASS" for gate in result.gates)
    assert result.metrics[0].sample_count == 1500

    cases[0]["fixture"] = {"agentType": "DOCUMENT", "category": "tenant", "seed": 0}
    tampered_payload = request.model_dump(by_alias=True)
    tampered_payload["cases"] = cases
    tampered = request.__class__.model_validate(tampered_payload)
    failed = DeterministicAgentEvaluator().evaluate(tampered)
    assert any(gate.reason_code == "CASE_DIGEST_MISMATCH" for gate in failed.gates)
