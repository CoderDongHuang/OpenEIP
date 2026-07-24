from fastapi.testclient import TestClient

from engine_core.config import Settings
from engine_core.embedding.domain.models import VectorRecord
from engine_core.embedding.infrastructure.deterministic_provider import DeterministicEmbeddingProvider
from engine_core.embedding.infrastructure.memory_repository import InMemoryVectorRepository
from engine_core.main import create_app

TOKEN = "retrieval-token"
TENANT = "11111111-1111-4111-8111-111111111111"
USER = "22222222-2222-4222-8222-222222222222"
BASE = "33333333-3333-4333-8333-333333333333"
OTHER_BASE = "33333333-3333-4333-8333-333333333334"
DOCUMENT = "44444444-4444-4444-8444-444444444444"


def _client() -> TestClient:
    provider = DeterministicEmbeddingProvider(8)
    repository = InMemoryVectorRepository()
    for base, suffix, text in ((BASE, "a", "invoice alpha exact term"), (OTHER_BASE, "b", "invoice secret")):
        repository.upsert(
            (
                VectorRecord(
                    TENANT,
                    base,
                    DOCUMENT,
                    f"chk_{suffix * 32}",
                    text,
                    suffix * 64,
                    provider.model,
                    provider.version,
                    provider.embed((text,))[0],
                    (2,),
                    10,
                    34,
                ),
            )
        )
    return TestClient(create_app(Settings(internal_api_token=TOKEN, embedding_dimension=8), provider, repository))


def _headers() -> dict[str, str]:
    return {
        "Content-Type": "application/json",
        "X-OpenEIP-Internal-Token": TOKEN,
        "X-Tenant-Id": TENANT,
        "X-User-Id": USER,
        "X-Request-Id": "retrieval-test",
    }


def test_modes_return_scoped_traceable_results() -> None:
    client = _client()
    for mode in ("FULL_TEXT", "VECTOR", "HYBRID"):
        response = client.post(
            "/api/v1/retrieval/search",
            json={"knowledgeBaseId": BASE, "query": "invoice exact", "mode": mode, "topK": 10},
            headers=_headers(),
        )
        assert response.status_code == 200
        result = response.json()["data"]
        assert result["mode"] == mode
        assert result["results"][0]["documentId"] == DOCUMENT
        assert result["results"][0]["pages"] == [2]
        assert "secret" not in str(result)
        assert all("vector" not in hit for hit in result["results"])


def test_invalid_mode_unknown_field_media_type_and_auth_fail_closed() -> None:
    client = _client()
    payload = {"knowledgeBaseId": BASE, "query": "invoice", "mode": "GLOBAL", "topK": 10}
    assert client.post("/api/v1/retrieval/search", json=payload, headers=_headers()).status_code == 400
    assert (
        client.post("/api/v1/retrieval/search", json={**payload, "unexpected": True}, headers=_headers()).status_code
        == 400
    )
    headers = _headers()
    headers["Content-Type"] = "text/plain"
    assert client.post("/api/v1/retrieval/search", content=b"{}", headers=headers).status_code == 415
    headers = _headers()
    headers["X-OpenEIP-Internal-Token"] = "wrong"
    assert client.post("/api/v1/retrieval/search", content=b"bad", headers=headers).status_code == 401
