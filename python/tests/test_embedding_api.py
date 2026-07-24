import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from engine_core.config import Settings
from engine_core.embedding.infrastructure.memory_repository import InMemoryVectorRepository
from engine_core.main import create_app

TOKEN = "embedding-internal-token"
TENANT = "11111111-1111-4111-8111-111111111111"
USER = "22222222-2222-4222-8222-222222222222"
BASE = "33333333-3333-4333-8333-333333333333"
DOCUMENT = "44444444-4444-4444-8444-444444444444"
JOB = "55555555-5555-4555-8555-555555555555"
CHUNK = "chk_" + "a" * 32


def _client(**settings: object) -> TestClient:
    values: dict[str, object] = {
        "internal_api_token": TOKEN,
        "embedding_dimension": 8,
        "embedding_max_body_bytes": 4096,
    }
    values.update(settings)
    configured = Settings(**values)
    return TestClient(create_app(configured))


def _headers(**overrides: str) -> dict[str, str]:
    headers = {
        "Content-Type": "application/json",
        "X-OpenEIP-Internal-Token": TOKEN,
        "X-Tenant-Id": TENANT,
        "X-User-Id": USER,
        "X-Request-Id": "embedding-test-1",
    }
    headers.update(overrides)
    return headers


def _payload(text: str = "alpha guide") -> dict[str, object]:
    return {
        "jobId": JOB,
        "knowledgeBaseId": BASE,
        "documentId": DOCUMENT,
        "chunks": [{"chunkId": CHUNK, "text": text, "sourceSha256": "b" * 64}],
    }


def test_api_returns_normalized_vectors_and_idempotent_replay() -> None:
    client = _client()
    first = client.post("/api/v1/embedding/batches", json=_payload(), headers=_headers())
    second = client.post("/api/v1/embedding/batches", json=_payload(), headers=_headers())
    assert first.status_code == 200
    assert first.json()["data"]["dimension"] == 8
    assert first.json()["data"]["vectorCount"] == 1
    assert first.json()["data"]["replayed"] is False
    assert second.json()["data"]["replayed"] is True
    assert first.json()["data"]["vectors"] == second.json()["data"]["vectors"]


def test_api_authenticates_before_decode_and_validates_identity_and_media_type() -> None:
    assert (
        _client()
        .post(
            "/api/v1/embedding/batches", content=b"not-json", headers=_headers(**{"X-OpenEIP-Internal-Token": "wrong"})
        )
        .status_code
        == 401
    )
    assert (
        _client()
        .post("/api/v1/embedding/batches", json=_payload(), headers=_headers(**{"X-Tenant-Id": "default"}))
        .status_code
        == 400
    )
    assert (
        _client()
        .post("/api/v1/embedding/batches", content=b"{}", headers=_headers(**{"Content-Type": "text/plain"}))
        .status_code
        == 415
    )


@pytest.mark.parametrize(
    "body",
    [
        b"not-json",
        b"\xef\xbb\xbf{}",
        b'{"jobId":"a","jobId":"b"}',
        json.dumps({**_payload(), "unexpected": True}).encode(),
        json.dumps({**_payload(), "chunks": [{"chunkId": "bad", "text": "x", "sourceSha256": "b" * 64}]}).encode(),
    ],
)
def test_api_rejects_malformed_duplicate_and_unknown_input(body: bytes) -> None:
    response = _client().post("/api/v1/embedding/batches", content=body, headers=_headers())
    assert response.status_code == 400
    assert response.json()["code"] == "EMB-V-001"


def test_api_enforces_streamed_body_limit() -> None:
    response = _client(embedding_max_body_bytes=128).post(
        "/api/v1/embedding/batches", json=_payload("x" * 1000), headers=_headers()
    )
    assert response.status_code == 413
    assert response.json()["code"] == "EMB-V-002"


def test_production_rejects_memory_repository_and_requires_explicit_adapters() -> None:
    with pytest.raises(ValueError, match="forbidden"):
        create_app(Settings(environment="production"), vector_repository=InMemoryVectorRepository())
    with pytest.raises(ValueError, match="provider"):
        create_app(Settings(embedding_provider="external"))
    with pytest.raises(ValueError, match="repository"):
        create_app(Settings(embedding_repository_backend="unsupported"))


def test_runtime_openapi_source_and_event_contract_are_synchronized() -> None:
    runtime = _client().get("/openapi.json").json()
    operation = runtime["paths"]["/api/v1/embedding/batches"]["post"]
    assert operation["operationId"].startswith("embed_batch")
    root = Path(__file__).resolve().parents[2]
    source = (root / "docs/06-api/embedding-v1.openapi.yaml").read_text(encoding="utf-8")
    assert "/api/v1/embedding/batches:" in source
    event = json.loads((root / "contracts/events/embedding.job.completed.v1.schema.json").read_text(encoding="utf-8"))
    assert event["properties"]["eventType"]["const"] == "embedding.job.completed"
    assert event["additionalProperties"] is False
    assert event["properties"]["payload"]["additionalProperties"] is False
