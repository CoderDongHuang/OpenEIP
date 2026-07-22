import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from engine_core.config import Settings
from engine_core.embedding.domain.models import VectorRecord
from engine_core.embedding.infrastructure.deterministic_provider import DeterministicEmbeddingProvider
from engine_core.embedding.infrastructure.memory_repository import InMemoryVectorRepository
from engine_core.main import create_app

TOKEN = "agent-internal-token"
TENANT = "11111111-1111-4111-8111-111111111111"
USER = "22222222-2222-4222-8222-222222222222"
EXECUTION = "33333333-3333-4333-8333-333333333333"
REQUEST = "44444444-4444-4444-8444-444444444444"
BASE = "55555555-5555-4555-8555-555555555555"
DOCUMENT = "66666666-6666-4666-8666-666666666666"
CHUNK = "chk_" + "a" * 32
PATH = "/api/v1/internal/agents/openeip.constrained-v1/executions:stream"


def _client(**settings: object) -> TestClient:
    values: dict[str, object] = {
        "internal_api_token": TOKEN,
        "embedding_dimension": 8,
        "agent_max_body_bytes": 4096,
        "agent_answer_chunk_chars": 32,
    }
    values.update(settings)
    provider = DeterministicEmbeddingProvider(8)
    repository = InMemoryVectorRepository()
    repository.upsert(
        (
            VectorRecord(
                TENANT,
                BASE,
                DOCUMENT,
                CHUNK,
                "bounded agent context",
                "b" * 64,
                provider.model,
                provider.version,
                provider.embed(("bounded agent context",))[0],
            ),
        )
    )
    return TestClient(create_app(Settings(**values), provider, repository))


def _headers(**overrides: str) -> dict[str, str]:
    headers = {
        "Content-Type": "application/json",
        "X-OpenEIP-Internal-Token": TOKEN,
        "X-Tenant-Id": TENANT,
        "X-User-Id": USER,
        "X-Execution-Id": EXECUTION,
        "X-Request-Id": REQUEST,
    }
    headers.update(overrides)
    return headers


def test_api_streams_sanitized_document_tool_lifecycle() -> None:
    response = _client().post(
        PATH,
        json={"input": "inspect: private document text", "allowedTools": ["document.inspect"]},
        headers=_headers(),
    )
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    assert response.headers["cache-control"] == "no-cache, no-transform"
    assert response.headers["x-accel-buffering"] == "no"
    events = [line[7:] for line in response.text.splitlines() if line.startswith("event: ")]
    assert events == [
        "execution.started",
        "tool.started",
        "tool.completed",
        "answer.delta",
        "answer.delta",
        "answer.delta",
        "answer.delta",
        "answer.delta",
        "execution.completed",
    ]
    assert "private document text" not in response.text
    assert "sha256" in response.text
    sequences = [json.loads(line[6:])["sequence"] for line in response.text.splitlines() if line.startswith("data: ")]
    assert sequences == list(range(len(sequences)))


def test_api_executes_authorized_search_without_leaking_prompt() -> None:
    response = _client().post(
        PATH,
        json={
            "input": "search: bounded agent context",
            "knowledgeBaseId": BASE,
            "allowedTools": ["knowledge.search"],
        },
        headers=_headers(),
    )
    assert response.status_code == 200
    assert "execution.completed" in response.text
    answer = "".join(
        json.loads(line[6:])["text"]
        for block in response.text.split("\n\n")
        if block.startswith("event: answer.delta")
        for line in block.splitlines()
        if line.startswith("data: ")
    )
    assert CHUNK in answer
    assert "citation-allowlist" not in response.text


def test_api_authenticates_before_decode_and_validates_canonical_identities() -> None:
    client = _client()
    assert (
        client.post(
            PATH,
            content=b"bad",
            headers=_headers(**{"X-OpenEIP-Internal-Token": "wrong"}),
        ).status_code
        == 401
    )
    for header in ("X-Tenant-Id", "X-User-Id", "X-Execution-Id", "X-Request-Id"):
        assert (
            client.post(
                PATH,
                json={"input": "inspect: x", "allowedTools": ["document.inspect"]},
                headers=_headers(**{header: "default"}),
            ).status_code
            == 400
        )


@pytest.mark.parametrize(
    "body",
    [
        b"not-json",
        b"\xef\xbb\xbf{}",
        b'{"input":"a","input":"b"}',
        b'{"input":"x","allowedTools":["document.inspect"],"unexpected":true}',
        b'{"input":"x","allowedTools":["document.inspect"],"maxSteps":"4"}',
        b'{"input":"x","allowedTools":["document.inspect"],"maxSteps":NaN}',
    ],
)
def test_api_rejects_malformed_duplicate_unknown_and_coerced_input(body: bytes) -> None:
    response = _client().post(PATH, content=body, headers=_headers())
    assert response.status_code == 400
    assert response.json()["code"] == "AGENT-V-001"


@pytest.mark.parametrize(
    ("payload", "code"),
    [
        ({"input": "x", "allowedTools": ["shell.exec"]}, "AGENT-V-003"),
        ({"input": "x", "allowedTools": ["document.inspect", "document.inspect"]}, "AGENT-V-003"),
        ({"input": "search: x", "allowedTools": ["knowledge.search"]}, "AGENT-V-002"),
        ({"input": "x", "allowedTools": ["document.inspect"], "maxSteps": 9}, "AGENT-V-004"),
    ],
)
def test_api_rejects_invalid_authority_and_limits(payload: dict[str, object], code: str) -> None:
    response = _client().post(PATH, json=payload, headers=_headers())
    assert response.status_code == 400
    assert response.json()["code"] == code


def test_api_enforces_media_and_observed_body_limits() -> None:
    assert _client().post(PATH, content=b"{}", headers=_headers(**{"Content-Type": "text/plain"})).status_code == 415
    response = _client(agent_max_body_bytes=128).post(
        PATH,
        json={"input": "x" * 1000, "allowedTools": ["document.inspect"]},
        headers=_headers(),
    )
    assert response.status_code == 413


def test_runtime_source_spi_and_mcp_contracts_are_synchronized() -> None:
    runtime_path = "/api/v1/internal/agents/{agent_id}/executions:stream"
    operation = _client().get("/openapi.json").json()["paths"][runtime_path]["post"]
    assert operation["operationId"].startswith("stream_agent")
    root = Path(__file__).resolve().parents[2]
    source = (root / "docs/06-api/agent-v1.openapi.yaml").read_text(encoding="utf-8")
    spi = (root / "docs/05-spi/agent-v1.md").read_text(encoding="utf-8")
    adapter = (root / "python/engine-core/src/engine_core/agent/infrastructure/mcp_adapter.py").read_text(
        encoding="utf-8"
    )
    assert "/api/v1/internal/agents/{agentId}/executions:stream:" in source
    assert "async def execute(self, context: AgentContext) -> AgentResult" in spi
    assert all(call in adapter for call in ("session.initialize()", "session.list_tools()", "session.call_tool"))
