"""Versioned OCR FastAPI router."""

import hmac
import re
from datetime import UTC, datetime
from uuid import UUID

from fastapi import APIRouter, Header, Request

from engine_core.ocr.application.service import OcrService
from engine_core.ocr.domain.models import OcrResult
from engine_core.ocr.shared.errors import OcrError

_REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


def build_ocr_router(service: OcrService, internal_token: str, max_body_bytes: int) -> APIRouter:
    """Build an OCR router with deployment configuration captured by the application factory."""
    router = APIRouter(prefix="/api/v1/ocr", tags=["ocr"])

    @router.post("/recognitions")
    async def recognize(
        request: Request,
        x_openeip_internal_token: str = Header(default=""),
        x_tenant_id: str = Header(default=""),
        x_user_id: str = Header(default=""),
        x_request_id: str = Header(default=""),
    ) -> dict[str, object]:
        _authenticate(internal_token, x_openeip_internal_token)
        _validate_identity(x_tenant_id, "tenant")
        _validate_identity(x_user_id, "user")
        _validate_request_id(x_request_id)
        content = await _read_bounded_body(request, max_body_bytes)
        media_type = request.headers.get("content-type", "").partition(";")[0].strip().lower()
        return _success_envelope(service.recognize(content, media_type), x_request_id)

    return router


def _authenticate(expected: str, supplied: str) -> None:
    if not expected:
        raise OcrError("OCR-S-002", "OCR internal authentication is not configured", 503)
    if not supplied or not hmac.compare_digest(expected, supplied):
        raise OcrError("OCR-P-001", "Invalid internal credential", 401)


def _validate_identity(value: str, name: str) -> None:
    try:
        parsed = UUID(value)
    except ValueError as error:
        raise OcrError("OCR-V-008", f"Invalid {name} identity", 400) from error
    if parsed.int == 0 or str(parsed) != value:
        raise OcrError("OCR-V-008", f"Invalid {name} identity", 400)


def _validate_request_id(value: str) -> None:
    if not _REQUEST_ID_PATTERN.fullmatch(value):
        raise OcrError("OCR-V-009", "Invalid request ID", 400)


def safe_request_id(value: str) -> str:
    """Return only a bounded request ID suitable for an error response."""
    return value if _REQUEST_ID_PATTERN.fullmatch(value) else "unknown"


async def _read_bounded_body(request: Request, max_body_bytes: int) -> bytes:
    content_length = request.headers.get("content-length")
    if content_length:
        try:
            parsed_length = int(content_length)
            if parsed_length < 0:
                raise ValueError
            if parsed_length > max_body_bytes:
                raise OcrError("OCR-V-002", "Image body exceeds the configured limit", 413)
        except ValueError as error:
            raise OcrError("OCR-V-001", "Invalid Content-Length header", 400) from error

    body = bytearray()
    async for chunk in request.stream():
        body.extend(chunk)
        if len(body) > max_body_bytes:
            raise OcrError("OCR-V-002", "Image body exceeds the configured limit", 413)
    return bytes(body)


def _success_envelope(result: OcrResult, request_id: str) -> dict[str, object]:
    return {
        "code": 0,
        "message": "success",
        "data": {
            "text": result.text,
            "blocks": [
                {
                    "page": block.page,
                    "text": block.text,
                    "confidence": block.confidence,
                    "boundingBox": {
                        "x": block.bounding_box.x,
                        "y": block.bounding_box.y,
                        "width": block.bounding_box.width,
                        "height": block.bounding_box.height,
                    },
                }
                for block in result.blocks
            ],
            "pageCount": 1,
            "confidence": result.confidence,
            "durationMs": round(result.duration_ms, 3),
            "contentSha256": result.content_sha256,
            "provider": {
                "name": result.provider_name,
                "version": result.provider_version,
                "mode": "deterministic-mvp",
            },
        },
        "requestId": request_id,
        "timestamp": datetime.now(UTC).isoformat(),
    }
