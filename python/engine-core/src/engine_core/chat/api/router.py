"""Strict authenticated internal chat streaming router."""

import json
from typing import Any

from fastapi import APIRouter, Header, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from engine_core.chat.application.service import ChatService
from engine_core.chat.shared.errors import ChatError
from engine_core.shared.internal_api import (
    authenticate_internal,
    read_bounded_body,
    validate_identity,
    validate_request_id,
)


class InternalChatRequest(BaseModel):
    """Strict Java-to-Python chat request."""

    model_config = ConfigDict(extra="forbid", strict=True)

    knowledge_base_id: str = Field(alias="knowledgeBaseId")
    message: str = Field(min_length=1, max_length=4000)
    top_k: int | None = Field(default=None, alias="topK")


def build_chat_router(
    service: ChatService,
    internal_token: str,
    max_body_bytes: int,
    default_top_k: int,
    max_top_k: int,
) -> APIRouter:
    """Build the internal SSE router with authentication before body decoding."""
    router = APIRouter(prefix="/api/v1/internal/chat", tags=["chat"])

    @router.post("/messages:stream")
    async def stream_chat(
        request: Request,
        x_openeip_internal_token: str = Header(default=""),
        x_tenant_id: str = Header(default=""),
        x_user_id: str = Header(default=""),
        x_session_id: str = Header(default=""),
        x_request_id: str = Header(default=""),
    ) -> StreamingResponse:
        authenticate_internal(internal_token, x_openeip_internal_token, "CHAT", ChatError)
        tenant_id = validate_identity(x_tenant_id, "tenant", "CHAT", ChatError)
        user_id = validate_identity(x_user_id, "user", "CHAT", ChatError)
        session_id = validate_identity(x_session_id, "session", "CHAT", ChatError)
        request_id = validate_request_id(x_request_id, "CHAT", ChatError)
        if request.headers.get("content-type", "").lower() != "application/json":
            raise ChatError("CHAT-V-005", "Chat request must use application/json", 415)
        body = await read_bounded_body(request, max_body_bytes, "CHAT", ChatError)
        decoded = _decode(body)
        knowledge_base_id = validate_identity(decoded.knowledge_base_id, "knowledge base", "CHAT", ChatError)
        top_k = default_top_k if decoded.top_k is None else decoded.top_k
        if top_k < 1 or top_k > max_top_k:
            raise ChatError("CHAT-V-003", "Invalid Chat topK", 400)
        stream = service.stream(
            tenant_id,
            user_id,
            session_id,
            request_id,
            knowledge_base_id,
            decoded.message,
            top_k,
        )
        return StreamingResponse(
            stream,
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache, no-transform",
                "X-Accel-Buffering": "no",
            },
        )

    return router


def _decode(body: bytes) -> InternalChatRequest:
    try:
        text = body.decode("utf-8")
        if text.startswith("\ufeff"):
            raise ValueError("BOM is forbidden")
        value = json.loads(text, object_pairs_hook=_unique_object, parse_constant=_reject_constant)
        return InternalChatRequest.model_validate(value)
    except (UnicodeDecodeError, json.JSONDecodeError, ValidationError, ValueError) as exception:
        raise ChatError("CHAT-V-001", "Invalid Chat request", 400) from exception


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def _reject_constant(value: str) -> None:
    raise ValueError(f"Non-finite JSON number is forbidden: {value}")
