"""Strict authenticated RAG query router."""

import json
from datetime import UTC, datetime
from typing import Any

from fastapi import APIRouter, Header, Request
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from engine_core.rag.application.service import RagService
from engine_core.rag.domain.models import RagResult
from engine_core.rag.shared.errors import RagError
from engine_core.shared.internal_api import (
    authenticate_internal,
    read_bounded_body,
    validate_identity,
    validate_request_id,
)


class RagQueryRequest(BaseModel):
    """Strict tenant-external RAG request body."""

    model_config = ConfigDict(extra="forbid", strict=True)

    knowledge_base_id: str = Field(alias="knowledgeBaseId")
    query: str = Field(min_length=1, max_length=2000)
    top_k: int | None = Field(default=None, alias="topK")


def build_rag_router(
    service: RagService,
    internal_token: str,
    max_body_bytes: int,
    default_top_k: int,
    max_top_k: int,
) -> APIRouter:
    """Build the RAG router with authentication before bounded decoding."""
    router = APIRouter(prefix="/api/v1/rag", tags=["rag"])

    @router.post("/queries")
    async def query_rag(
        request: Request,
        x_openeip_internal_token: str = Header(default=""),
        x_tenant_id: str = Header(default=""),
        x_user_id: str = Header(default=""),
        x_request_id: str = Header(default=""),
    ) -> dict[str, object]:
        authenticate_internal(internal_token, x_openeip_internal_token, "RAG", RagError)
        tenant_id = validate_identity(x_tenant_id, "tenant", "RAG", RagError)
        validate_identity(x_user_id, "user", "RAG", RagError)
        validate_request_id(x_request_id, "RAG", RagError)
        if request.headers.get("content-type", "").lower() != "application/json":
            raise RagError("RAG-V-005", "RAG request must use application/json", 415)
        body = await read_bounded_body(request, max_body_bytes, "RAG", RagError)
        decoded = _decode(body)
        knowledge_base_id = validate_identity(decoded.knowledge_base_id, "knowledge base", "RAG", RagError)
        top_k = default_top_k if decoded.top_k is None else decoded.top_k
        if top_k < 1 or top_k > max_top_k:
            raise RagError("RAG-V-003", "Invalid RAG topK", 400)
        return _success(
            service.query(tenant_id, knowledge_base_id, decoded.query, top_k),
            x_request_id,
        )

    return router


def _decode(body: bytes) -> RagQueryRequest:
    try:
        text = body.decode("utf-8")
        if text.startswith("\ufeff"):
            raise ValueError("BOM is forbidden")
        value = json.loads(text, object_pairs_hook=_unique_object, parse_constant=_reject_constant)
        return RagQueryRequest.model_validate(value)
    except (UnicodeDecodeError, json.JSONDecodeError, ValidationError, ValueError) as exception:
        raise RagError("RAG-V-001", "Invalid RAG request", 400) from exception


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def _reject_constant(value: str) -> None:
    raise ValueError(f"Non-finite JSON number is forbidden: {value}")


def _success(result: RagResult, request_id: str) -> dict[str, object]:
    return {
        "code": 0,
        "message": "success",
        "data": {
            "answer": result.answer,
            "model": result.model,
            "modelVersion": result.model_version,
            "citations": [
                {
                    "documentId": citation.document_id,
                    "chunkId": citation.chunk_id,
                    "sourceSha256": citation.source_sha256,
                    "score": citation.score,
                }
                for citation in result.citations
            ],
            "retrievalCount": result.retrieval_count,
            "durationMs": round(result.duration_ms, 3),
        },
        "requestId": request_id,
        "timestamp": datetime.now(UTC).isoformat(),
    }
