"""Versioned internal document parsing router."""

from datetime import UTC, datetime

from fastapi import APIRouter, Header, Request

from engine_core.parsing.application.service import DocumentParsingService
from engine_core.parsing.domain.models import ParsedDocument
from engine_core.parsing.shared.errors import DocumentParsingError
from engine_core.shared.internal_api import (
    authenticate_internal,
    read_bounded_body,
    validate_identity,
    validate_request_id,
)


def build_parsing_router(service: DocumentParsingService, internal_token: str, max_body_bytes: int) -> APIRouter:
    """Build a parsing router with explicit deployment configuration."""
    router = APIRouter(prefix="/api/v1/parsing", tags=["document-parsing"])

    @router.post("/documents")
    async def parse_document(
        request: Request,
        x_openeip_internal_token: str = Header(default=""),
        x_tenant_id: str = Header(default=""),
        x_user_id: str = Header(default=""),
        x_document_id: str = Header(default=""),
        x_request_id: str = Header(default=""),
    ) -> dict[str, object]:
        authenticate_internal(internal_token, x_openeip_internal_token, "DOC", DocumentParsingError)
        tenant_id = validate_identity(x_tenant_id, "tenant", "DOC", DocumentParsingError)
        validate_identity(x_user_id, "user", "DOC", DocumentParsingError)
        document_id = validate_identity(x_document_id, "document", "DOC", DocumentParsingError)
        validate_request_id(x_request_id, "DOC", DocumentParsingError)
        content = await read_bounded_body(request, max_body_bytes, "DOC", DocumentParsingError)
        result = service.parse(content, request.headers.get("content-type", ""), tenant_id, document_id)
        return _success_envelope(result, x_request_id)

    return router


def _success_envelope(result: ParsedDocument, request_id: str) -> dict[str, object]:
    return {
        "code": 0,
        "message": "success",
        "data": {
            "documentId": result.document_id,
            "sourceType": result.source_type.value,
            "sourceSha256": result.source_sha256,
            "normalizedTextSha256": result.normalized_text_sha256,
            "charCount": result.char_count,
            "chunkCount": len(result.chunks),
            "chunks": [
                {
                    "chunkId": chunk.chunk_id,
                    "index": chunk.index,
                    "text": chunk.text,
                    "startChar": chunk.start_char,
                    "endChar": chunk.end_char,
                    "pages": list(chunk.pages),
                    "sha256": chunk.sha256,
                }
                for chunk in result.chunks
            ],
            "parser": {
                "name": result.parser_name,
                "version": result.parser_version,
                "chunkSize": result.chunk_size,
                "overlap": result.overlap,
            },
            "durationMs": round(result.duration_ms, 3),
            "idempotencyKey": result.idempotency_key,
        },
        "requestId": request_id,
        "timestamp": datetime.now(UTC).isoformat(),
    }
