import json
from pathlib import Path

from fastapi.testclient import TestClient
from ocr_fixtures import render_text

from engine_core.config import Settings
from engine_core.main import create_app

TOKEN = "test-internal-token"
TENANT_ID = "11111111-1111-4111-8111-111111111111"
USER_ID = "22222222-2222-4222-8222-222222222222"
REQUEST_ID = "ocr-test-1"


def _client(*, token: str = TOKEN, max_body_bytes: int = 4096) -> TestClient:
    settings = Settings(ocr_internal_token=token, ocr_max_body_bytes=max_body_bytes)
    return TestClient(create_app(settings))


def _headers(**overrides: str) -> dict[str, str]:
    headers = {
        "Content-Type": "image/png",
        "X-OpenEIP-Internal-Token": TOKEN,
        "X-Tenant-Id": TENANT_ID,
        "X-User-Id": USER_ID,
        "X-Request-Id": REQUEST_ID,
    }
    headers.update(overrides)
    return headers


def test_recognition_api_returns_standard_contract() -> None:
    response = _client().post(
        "/api/v1/ocr/recognitions",
        content=render_text("OCR 2026"),
        headers=_headers(),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert body["requestId"] == REQUEST_ID
    assert body["data"]["text"] == "OCR 2026"
    assert body["data"]["pageCount"] == 1
    assert body["data"]["provider"] == {
        "name": "deterministic-raster",
        "version": "1.0.0",
        "mode": "deterministic-mvp",
    }
    assert "tenant" not in response.text.lower()
    assert TOKEN not in response.text


def test_invalid_credential_is_rejected_before_decode() -> None:
    response = _client().post(
        "/api/v1/ocr/recognitions",
        content=b"not-an-image",
        headers=_headers(**{"X-OpenEIP-Internal-Token": "wrong"}),
    )

    assert response.status_code == 401
    assert response.json()["code"] == "OCR-P-001"


def test_unconfigured_internal_authentication_fails_closed() -> None:
    response = _client(token="").post(
        "/api/v1/ocr/recognitions",
        content=render_text("OCR"),
        headers=_headers(),
    )

    assert response.status_code == 503
    assert response.json()["code"] == "OCR-S-002"


def test_invalid_identity_and_request_id_are_not_echoed() -> None:
    response = _client().post(
        "/api/v1/ocr/recognitions",
        content=render_text("OCR"),
        headers=_headers(**{"X-Tenant-Id": "not-a-uuid", "X-Request-Id": "unsafe id"}),
    )

    assert response.status_code == 400
    assert response.json()["code"] == "OCR-V-008"
    assert response.json()["requestId"] == "unknown"


def test_nil_user_identity_is_rejected() -> None:
    response = _client().post(
        "/api/v1/ocr/recognitions",
        content=render_text("OCR"),
        headers=_headers(**{"X-User-Id": "00000000-0000-0000-0000-000000000000"}),
    )

    assert response.status_code == 400
    assert response.json()["code"] == "OCR-V-008"


def test_streamed_body_limit_is_enforced() -> None:
    response = _client(max_body_bytes=32).post(
        "/api/v1/ocr/recognitions",
        content=render_text("OCR"),
        headers=_headers(),
    )

    assert response.status_code == 413
    assert response.json()["code"] == "OCR-V-002"


def test_declared_media_mismatch_is_rejected() -> None:
    response = _client().post(
        "/api/v1/ocr/recognitions",
        content=render_text("OCR"),
        headers=_headers(**{"Content-Type": "image/jpeg"}),
    )

    assert response.status_code == 415
    assert response.json()["code"] == "OCR-V-004"


def test_runtime_openapi_and_source_schema_remain_synchronized() -> None:
    runtime = _client().get("/openapi.json").json()
    operation = runtime["paths"]["/api/v1/ocr/recognitions"]["post"]
    assert set(operation["responses"]) >= {"200"}

    repository_root = Path(__file__).parents[2]
    schema = json.loads((repository_root / "contracts/ocr/ocr-result.v1.schema.json").read_text(encoding="utf-8"))
    assert schema["additionalProperties"] is False
    assert set(schema["required"]) == {
        "text",
        "blocks",
        "pageCount",
        "confidence",
        "durationMs",
        "contentSha256",
        "provider",
    }
    openapi_source = (repository_root / "docs/06-api/ocr-v1.openapi.yaml").read_text(encoding="utf-8")
    assert "/api/v1/ocr/recognitions:" in openapi_source
    assert "OCR-[VPS]" in openapi_source
