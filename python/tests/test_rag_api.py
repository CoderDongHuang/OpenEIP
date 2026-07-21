import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from engine_core.config import Settings
from engine_core.embedding.domain.models import VectorRecord
from engine_core.embedding.infrastructure.deterministic_provider import DeterministicEmbeddingProvider
from engine_core.embedding.infrastructure.memory_repository import InMemoryVectorRepository
from engine_core.main import create_app

TOKEN = "rag-internal-token"
TENANT = "11111111-1111-4111-8111-111111111111"
USER = "22222222-2222-4222-8222-222222222222"
BASE = "33333333-3333-4333-8333-333333333333"
DOCUMENT = "44444444-4444-4444-8444-444444444444"
CHUNK = "chk_" + "a" * 32


def _client(**settings: object) -> TestClient:
    values: dict[str, object] = {
        "internal_api_token": TOKEN,
        "embedding_dimension": 8,
        "rag_max_body_bytes": 4096,
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
                "retrieved answer",
                "b" * 64,
                provider.model,
                provider.version,
                provider.embed(("retrieved answer",))[0],
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
        "X-Request-Id": "rag-test-1",
    }
    headers.update(overrides)
    return headers


def _payload() -> dict[str, object]:
    return {"knowledgeBaseId": BASE, "query": "retrieved answer", "topK": 5}


def test_api_returns_grounded_result_with_verified_citation() -> None:
    response = _client().post("/api/v1/rag/queries", json=_payload(), headers=_headers())
    assert response.status_code == 200
    assert response.json()["data"]["retrievalCount"] == 1
    assert response.json()["data"]["citations"][0]["chunkId"] == CHUNK
    assert "vector" not in json.dumps(response.json()).casefold()


def test_api_authenticates_before_decode_and_validates_identity_media_type_and_default_top_k() -> None:
    client = _client(rag_default_top_k=1, rag_max_top_k=1)
    assert (
        client.post(
            "/api/v1/rag/queries", content=b"bad", headers=_headers(**{"X-OpenEIP-Internal-Token": "wrong"})
        ).status_code
        == 401
    )
    assert (
        client.post("/api/v1/rag/queries", json=_payload(), headers=_headers(**{"X-Tenant-Id": "default"})).status_code
        == 400
    )
    assert (
        client.post(
            "/api/v1/rag/queries", content=b"{}", headers=_headers(**{"Content-Type": "text/plain"})
        ).status_code
        == 415
    )
    payload = _payload()
    del payload["topK"]
    assert client.post("/api/v1/rag/queries", json=payload, headers=_headers()).status_code == 200
    assert client.post("/api/v1/rag/queries", json={**payload, "topK": 2}, headers=_headers()).status_code == 400


@pytest.mark.parametrize(
    "body",
    [
        b"not-json",
        b"\xef\xbb\xbf{}",
        b'{"query":"a","query":"b"}',
        json.dumps({**_payload(), "unexpected": True}).encode(),
        json.dumps({**_payload(), "topK": "5"}).encode(),
        b'{"knowledgeBaseId":"33333333-3333-4333-8333-333333333333","query":NaN}',
    ],
)
def test_api_rejects_malformed_duplicate_unknown_and_coerced_input(body: bytes) -> None:
    response = _client().post("/api/v1/rag/queries", content=body, headers=_headers())
    assert response.status_code == 400
    assert response.json()["code"] == "RAG-V-001"


def test_api_enforces_observed_body_limit_and_provider_configuration() -> None:
    response = _client(rag_max_body_bytes=128).post(
        "/api/v1/rag/queries", json={**_payload(), "query": "x" * 1000}, headers=_headers()
    )
    assert response.status_code == 413
    assert response.json()["code"] == "RAG-V-002"
    with pytest.raises(ValueError, match="answer provider"):
        create_app(Settings(rag_answer_provider="external"))


def test_runtime_and_source_openapi_contracts_are_synchronized() -> None:
    operation = _client().get("/openapi.json").json()["paths"]["/api/v1/rag/queries"]["post"]
    assert operation["operationId"].startswith("query_rag")
    root = Path(__file__).resolve().parents[2]
    source = (root / "docs/06-api/rag-v1.openapi.yaml").read_text(encoding="utf-8")
    assert "/api/v1/rag/queries:" in source
    assert "additionalProperties: false" in source
