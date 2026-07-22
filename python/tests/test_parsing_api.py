import json
from pathlib import Path

from fastapi.testclient import TestClient
from parsing_fixtures import ocr_result

from engine_core.config import Settings
from engine_core.main import create_app
from engine_core.parsing.infrastructure.input_decoder import OCR_MEDIA_TYPE

TOKEN = "test-internal-token"
TENANT_ID = "11111111-1111-4111-8111-111111111111"
USER_ID = "22222222-2222-4222-8222-222222222222"
DOCUMENT_ID = "33333333-3333-4333-8333-333333333333"
REQUEST_ID = "parse-test-1"


def _client(*, max_body_bytes: int = 4096) -> TestClient:
    settings = Settings(
        internal_api_token=TOKEN,
        parsing_max_body_bytes=max_body_bytes,
        parsing_chunk_size=16,
        parsing_chunk_overlap=4,
    )
    return TestClient(create_app(settings))


def _headers(content_type: str = "text/plain", **overrides: str) -> dict[str, str]:
    headers = {
        "Content-Type": content_type,
        "X-OpenEIP-Internal-Token": TOKEN,
        "X-Tenant-Id": TENANT_ID,
        "X-User-Id": USER_ID,
        "X-Document-Id": DOCUMENT_ID,
        "X-Request-Id": REQUEST_ID,
    }
    headers.update(overrides)
    return headers


def test_plain_text_api_returns_standard_traceable_result() -> None:
    response = _client().post(
        "/api/v1/parsing/documents",
        content=b"FIRST LINE\r\nSECOND LINE WITH WORDS",
        headers=_headers(),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert body["requestId"] == REQUEST_ID
    assert body["data"]["documentId"] == DOCUMENT_ID
    assert body["data"]["sourceType"] == "TEXT"
    assert body["data"]["chunkCount"] == len(body["data"]["chunks"])
    assert body["data"]["idempotencyKey"].startswith("parse_")
    assert TOKEN not in response.text
    assert TENANT_ID not in response.text


def test_ocr_result_api_is_compatible_with_ocr_v1() -> None:
    response = _client().post(
        "/api/v1/parsing/documents",
        content=ocr_result("OCR ONE", "OCR TWO"),
        headers=_headers(OCR_MEDIA_TYPE),
    )

    assert response.status_code == 200
    assert response.json()["data"]["sourceType"] == "OCR"
    assert response.json()["data"]["sourceSha256"] == "a" * 64


def test_invalid_token_is_rejected_before_content() -> None:
    response = _client().post(
        "/api/v1/parsing/documents",
        content=b"\xff",
        headers=_headers(**{"X-OpenEIP-Internal-Token": "wrong"}),
    )

    assert response.status_code == 401
    assert response.json()["code"] == "DOC-P-001"


def test_invalid_document_identity_is_rejected() -> None:
    response = _client().post(
        "/api/v1/parsing/documents",
        content=b"text",
        headers=_headers(**{"X-Document-Id": "not-a-uuid"}),
    )

    assert response.status_code == 400
    assert response.json()["code"] == "DOC-V-008"


def test_streamed_body_limit_is_enforced() -> None:
    response = _client(max_body_bytes=4).post(
        "/api/v1/parsing/documents",
        content=b"hello",
        headers=_headers(),
    )

    assert response.status_code == 413
    assert response.json()["code"] == "DOC-V-002"


def test_unsupported_media_type_is_rejected() -> None:
    response = _client().post(
        "/api/v1/parsing/documents",
        content=b"hello",
        headers=_headers("application/octet-stream"),
    )

    assert response.status_code == 415
    assert response.json()["code"] == "DOC-V-004"


def test_openapi_and_result_event_sources_remain_synchronized() -> None:
    runtime = _client().get("/openapi.json").json()
    assert "/api/v1/parsing/documents" in runtime["paths"]

    repository_root = Path(__file__).parents[2]
    result_schema = json.loads(
        (repository_root / "contracts/document/document-parsed-result.v1.schema.json").read_text(encoding="utf-8")
    )
    event_schema = json.loads(
        (repository_root / "contracts/events/document.lifecycle.parsed.v1.schema.json").read_text(encoding="utf-8")
    )
    assert result_schema["additionalProperties"] is False
    assert {"chunks", "normalizedTextSha256", "idempotencyKey"} <= set(result_schema["required"])
    assert event_schema["properties"]["eventType"]["const"] == "document.lifecycle.parsed"
    assert "text" not in event_schema["properties"]["payload"]["properties"]
    openapi = (repository_root / "docs/06-api/document-parsing-v1.openapi.yaml").read_text(encoding="utf-8")
    assert OCR_MEDIA_TYPE in openapi
