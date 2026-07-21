"""Versioned OCR FastAPI router."""

from datetime import UTC, datetime

from fastapi import APIRouter, Header, Request

from engine_core.ocr.application.service import OcrService
from engine_core.ocr.domain.models import OcrResult
from engine_core.ocr.shared.errors import OcrError
from engine_core.shared.internal_api import (
    authenticate_internal,
    read_bounded_body,
    validate_identity,
    validate_request_id,
)


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
        authenticate_internal(internal_token, x_openeip_internal_token, "OCR", OcrError)
        validate_identity(x_tenant_id, "tenant", "OCR", OcrError)
        validate_identity(x_user_id, "user", "OCR", OcrError)
        validate_request_id(x_request_id, "OCR", OcrError)
        content = await read_bounded_body(request, max_body_bytes, "OCR", OcrError)
        media_type = request.headers.get("content-type", "").partition(";")[0].strip().lower()
        return _success_envelope(service.recognize(content, media_type), x_request_id)

    return router


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
