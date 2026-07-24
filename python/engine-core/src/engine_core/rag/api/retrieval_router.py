"""Authenticated direct full-text, vector, and hybrid retrieval API."""

import json
from datetime import UTC, datetime
from typing import Any, Literal

from fastapi import APIRouter, Header, Request
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from engine_core.embedding.domain.ports import EmbeddingProvider, VectorRepository
from engine_core.rag.shared.errors import RagError
from engine_core.shared.internal_api import (
    authenticate_internal,
    read_bounded_body,
    validate_identity,
    validate_request_id,
)


class RetrievalRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    knowledge_base_id: str = Field(alias="knowledgeBaseId")
    query: str = Field(min_length=1, max_length=2000)
    mode: Literal["FULL_TEXT", "VECTOR", "HYBRID"] = "HYBRID"
    top_k: int = Field(default=10, alias="topK", ge=1, le=50)


def build_retrieval_router(
    provider: EmbeddingProvider, repository: VectorRepository, internal_token: str, max_body_bytes: int
) -> APIRouter:
    router = APIRouter(prefix="/api/v1/retrieval", tags=["retrieval"])

    @router.post("/search")
    async def search(
        request: Request,
        x_openeip_internal_token: str = Header(default=""),
        x_tenant_id: str = Header(default=""),
        x_user_id: str = Header(default=""),
        x_request_id: str = Header(default=""),
    ) -> dict[str, object]:
        authenticate_internal(internal_token, x_openeip_internal_token, "RAG", RagError)
        tenant = validate_identity(x_tenant_id, "tenant", "RAG", RagError)
        validate_identity(x_user_id, "user", "RAG", RagError)
        validate_request_id(x_request_id, "RAG", RagError)
        if request.headers.get("content-type", "").lower() != "application/json":
            raise RagError("RAG-V-005", "Retrieval request must use application/json", 415)
        body = await read_bounded_body(request, max_body_bytes, "RAG", RagError)
        decoded = _decode(body)
        base = validate_identity(decoded.knowledge_base_id, "knowledge base", "RAG", RagError)
        try:
            if decoded.mode == "FULL_TEXT":
                method = getattr(repository, "search_text", None)
                if method is None:
                    raise RuntimeError("Full-text repository unavailable")
                results = method(tenant, base, decoded.query, decoded.top_k)
            else:
                vector = provider.embed((decoded.query,))[0]
                hybrid = getattr(repository, "search_hybrid", None)
                results = (
                    hybrid(tenant, base, decoded.query, vector, decoded.top_k)
                    if decoded.mode == "HYBRID" and hybrid is not None
                    else repository.search(tenant, base, vector, decoded.top_k)
                )
        except Exception as error:
            raise RagError("RAG-S-001", "Retrieval provider is unavailable", 503) from error
        return {
            "code": 0,
            "message": "success",
            "requestId": x_request_id,
            "timestamp": datetime.now(UTC).isoformat(),
            "data": {
                "mode": decoded.mode,
                "count": len(results),
                "results": [
                    {
                        "documentId": item.document_id,
                        "chunkId": item.chunk_id,
                        "sourceSha256": item.source_sha256,
                        "score": item.score,
                        "excerpt": item.text[:500],
                        "pages": list(item.pages),
                        "startChar": item.start_char,
                        "endChar": item.end_char,
                    }
                    for item in results
                ],
            },
        }

    return router


def _decode(body: bytes) -> RetrievalRequest:
    try:
        value = json.loads(body.decode("utf-8"), object_pairs_hook=_unique, parse_constant=_reject_constant)
        return RetrievalRequest.model_validate(value)
    except (UnicodeDecodeError, json.JSONDecodeError, ValidationError, ValueError) as error:
        raise RagError("RAG-V-001", "Invalid retrieval request", 400) from error


def _unique(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def _reject_constant(value: str) -> None:
    raise ValueError(f"Non-finite JSON number is forbidden: {value}")
