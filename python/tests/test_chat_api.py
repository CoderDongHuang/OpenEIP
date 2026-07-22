import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from engine_core.config import Settings
from engine_core.embedding.domain.models import VectorRecord
from engine_core.embedding.infrastructure.deterministic_provider import DeterministicEmbeddingProvider
from engine_core.embedding.infrastructure.memory_repository import InMemoryVectorRepository
from engine_core.main import create_app

TOKEN = "chat-internal-token"
TENANT = "11111111-1111-4111-8111-111111111111"
USER = "22222222-2222-4222-8222-222222222222"
SESSION = "33333333-3333-4333-8333-333333333333"
BASE = "44444444-4444-4444-8444-444444444444"
DOCUMENT = "55555555-5555-4555-8555-555555555555"
CHUNK = "chk_" + "a" * 32


def _client(**settings: object) -> TestClient:
    values: dict[str, object] = {
        "internal_api_token": TOKEN,
        "embedding_dimension": 8,
        "chat_max_body_bytes": 4096,
        "chat_token_chunk_chars": 8,
    }
    values.update(settings)
    configured = Settings(**values)
    provider = DeterministicEmbeddingProvider(8)
    repository = InMemoryVectorRepository()
    repository.upsert(
        (
            VectorRecord(
                TENANT,
                BASE,
                DOCUMENT,
                CHUNK,
                "streamed context",
                "b" * 64,
                provider.model,
                provider.version,
                provider.embed(("streamed context",))[0],
            ),
        )
    )
    return TestClient(create_app(configured, provider, repository))


def _headers(**overrides: str) -> dict[str, str]:
    headers = {
        "Content-Type": "application/json",
        "X-OpenEIP-Internal-Token": TOKEN,
        "X-Tenant-Id": TENANT,
        "X-User-Id": USER,
        "X-Session-Id": SESSION,
        "X-Request-Id": "chat-test-1",
    }
    headers.update(overrides)
    return headers


def _payload() -> dict[str, object]:
    return {"knowledgeBaseId": BASE, "message": "streamed context", "topK": 5}


def test_api_streams_only_safe_contract_events_and_headers() -> None:
    response = _client().post("/api/v1/internal/chat/messages:stream", json=_payload(), headers=_headers())
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    assert response.headers["cache-control"] == "no-cache, no-transform"
    assert response.headers["x-accel-buffering"] == "no"
    event_names = [line[7:] for line in response.text.splitlines() if line.startswith("event: ")]
    assert event_names[-1] == "done"
    assert set(event_names) <= {"token", "done", "error"}
    assert "chat-test-1" in response.text
    assert SESSION in response.text


def test_api_authenticates_before_decode_and_validates_all_identities() -> None:
    client = _client()
    wrong = _headers(**{"X-OpenEIP-Internal-Token": "wrong"})
    assert client.post("/api/v1/internal/chat/messages:stream", content=b"bad", headers=wrong).status_code == 401
    for header in ("X-Tenant-Id", "X-User-Id", "X-Session-Id"):
        assert (
            client.post(
                "/api/v1/internal/chat/messages:stream",
                json=_payload(),
                headers=_headers(**{header: "default"}),
            ).status_code
            == 400
        )


@pytest.mark.parametrize(
    "body",
    [
        b"not-json",
        b"\xef\xbb\xbf{}",
        b'{"message":"a","message":"b"}',
        json.dumps({**_payload(), "unexpected": True}).encode(),
        json.dumps({**_payload(), "topK": "5"}).encode(),
    ],
)
def test_api_rejects_malformed_duplicate_unknown_and_coerced_input(body: bytes) -> None:
    response = _client().post("/api/v1/internal/chat/messages:stream", content=body, headers=_headers())
    assert response.status_code == 400
    assert response.json()["code"] == "CHAT-V-001"


def test_api_enforces_media_body_message_and_configured_top_k_limits() -> None:
    assert (
        _client()
        .post(
            "/api/v1/internal/chat/messages:stream",
            content=b"{}",
            headers=_headers(**{"Content-Type": "text/plain"}),
        )
        .status_code
        == 415
    )
    assert (
        _client(chat_max_body_bytes=128)
        .post(
            "/api/v1/internal/chat/messages:stream",
            json={**_payload(), "message": "x" * 1000},
            headers=_headers(),
        )
        .status_code
        == 413
    )
    assert (
        _client()
        .post(
            "/api/v1/internal/chat/messages:stream",
            json={**_payload(), "message": "  \n"},
            headers=_headers(),
        )
        .status_code
        == 400
    )
    assert (
        _client(chat_max_top_k=2)
        .post(
            "/api/v1/internal/chat/messages:stream",
            json={**_payload(), "topK": 3},
            headers=_headers(),
        )
        .status_code
        == 400
    )


def test_runtime_and_source_openapi_contracts_are_synchronized() -> None:
    operation = _client().get("/openapi.json").json()["paths"]["/api/v1/internal/chat/messages:stream"]["post"]
    assert operation["operationId"].startswith("stream_chat")
    root = Path(__file__).resolve().parents[2]
    source = (root / "docs/06-api/chat-v1.openapi.yaml").read_text(encoding="utf-8")
    assert "/api/v1/internal/chat/messages:stream:" in source
    assert "token, done, or error" in source
