"""Strict authenticated batch embedding router."""

import json
from datetime import UTC, datetime
from typing import Any

from fastapi import APIRouter, Header, Request
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from engine_core.embedding.application.service import EmbeddingService
from engine_core.embedding.domain.models import EmbeddingChunk, EmbeddingJob, EmbeddingJobResult
from engine_core.embedding.shared.errors import EmbeddingError
from engine_core.shared.internal_api import (
    authenticate_internal,
    read_bounded_body,
    validate_identity,
    validate_request_id,
)


class EmbeddingChunkRequest(BaseModel):
    """Strict chunk input matching the parser output identity."""

    model_config = ConfigDict(extra="forbid")

    chunk_id: str = Field(alias="chunkId", pattern=r"^chk_[a-f0-9]{32}$")
    text: str = Field(min_length=1, max_length=8192)
    source_sha256: str = Field(alias="sourceSha256", pattern=r"^[a-f0-9]{64}$")


class EmbeddingBatchRequest(BaseModel):
    """Strict one-document embedding batch."""

    model_config = ConfigDict(extra="forbid")

    job_id: str = Field(alias="jobId")
    knowledge_base_id: str = Field(alias="knowledgeBaseId")
    document_id: str = Field(alias="documentId")
    chunks: tuple[EmbeddingChunkRequest, ...] = Field(min_length=1, max_length=32)


def build_embedding_router(
    service: EmbeddingService,
    internal_token: str,
    max_body_bytes: int,
) -> APIRouter:
    """Build the internal router with authentication before bounded decoding."""
    router = APIRouter(prefix="/api/v1/embedding", tags=["embedding"])

    @router.post("/batches")
    async def embed_batch(
        request: Request,
        x_openeip_internal_token: str = Header(default=""),
        x_tenant_id: str = Header(default=""),
        x_user_id: str = Header(default=""),
        x_request_id: str = Header(default=""),
    ) -> dict[str, object]:
        authenticate_internal(internal_token, x_openeip_internal_token, "EMB", EmbeddingError)
        tenant_id = validate_identity(x_tenant_id, "tenant", "EMB", EmbeddingError)
        validate_identity(x_user_id, "user", "EMB", EmbeddingError)
        validate_request_id(x_request_id, "EMB", EmbeddingError)
        if request.headers.get("content-type", "").lower() != "application/json":
            raise EmbeddingError("EMB-V-005", "Embedding request must use application/json", 415)
        body = await read_bounded_body(request, max_body_bytes, "EMB", EmbeddingError)
        decoded = _decode(body)
        job = EmbeddingJob(
            job_id=validate_identity(decoded.job_id, "job", "EMB", EmbeddingError),
            tenant_id=tenant_id,
            knowledge_base_id=validate_identity(decoded.knowledge_base_id, "knowledge base", "EMB", EmbeddingError),
            document_id=validate_identity(decoded.document_id, "document", "EMB", EmbeddingError),
            chunks=tuple(EmbeddingChunk(item.chunk_id, item.text, item.source_sha256) for item in decoded.chunks),
        )
        return _success(service.execute(job), x_request_id)

    return router


def _decode(body: bytes) -> EmbeddingBatchRequest:
    try:
        text = body.decode("utf-8")
        if text.startswith("\ufeff"):
            raise ValueError("BOM is forbidden")
        value = json.loads(text, object_pairs_hook=_unique_object, parse_constant=_reject_constant)
        return EmbeddingBatchRequest.model_validate(value)
    except (UnicodeDecodeError, json.JSONDecodeError, ValidationError, ValueError) as exception:
        raise EmbeddingError("EMB-V-001", "Invalid embedding request", 400) from exception


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def _reject_constant(value: str) -> None:
    raise ValueError(f"Non-finite JSON number is forbidden: {value}")


def _success(result: EmbeddingJobResult, request_id: str) -> dict[str, object]:
    return {
        "code": 0,
        "message": "success",
        "data": {
            "jobId": result.job_id,
            "knowledgeBaseId": result.knowledge_base_id,
            "documentId": result.document_id,
            "model": result.model,
            "modelVersion": result.model_version,
            "dimension": result.dimension,
            "vectorCount": len(result.vectors),
            "replayed": result.replayed,
            "vectors": [{"chunkId": item.chunk_id, "vector": list(item.vector)} for item in result.vectors],
            "durationMs": round(result.duration_ms, 3),
        },
        "requestId": request_id,
        "timestamp": datetime.now(UTC).isoformat(),
    }
