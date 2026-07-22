"""Strict authenticated internal Agent execution router."""

import json
from typing import Any

from fastapi import APIRouter, Header, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from engine_core.agent.application.runtime import AgentStreamService
from engine_core.agent.shared.errors import AgentError
from engine_core.shared.internal_api import (
    authenticate_internal,
    read_bounded_body,
    validate_identity,
)


class InternalAgentRequest(BaseModel):
    """Strict Java-to-Python Agent request."""

    model_config = ConfigDict(extra="forbid", strict=True)

    user_input: str = Field(alias="input", min_length=1, max_length=4000)
    knowledge_base_id: str | None = Field(default=None, alias="knowledgeBaseId")
    allowed_tools: list[str] = Field(alias="allowedTools", min_length=1, max_length=2)
    max_steps: int | None = Field(default=None, alias="maxSteps")


def build_agent_router(
    service: AgentStreamService,
    internal_token: str,
    max_body_bytes: int,
    default_max_steps: int,
    max_steps: int,
) -> APIRouter:
    """Build the internal Agent router with authentication before body decoding."""
    router = APIRouter(prefix="/api/v1/internal/agents", tags=["agent"])

    @router.post("/{agent_id}/executions:stream")
    async def stream_agent(
        agent_id: str,
        request: Request,
        x_openeip_internal_token: str = Header(default=""),
        x_tenant_id: str = Header(default=""),
        x_user_id: str = Header(default=""),
        x_execution_id: str = Header(default=""),
        x_request_id: str = Header(default=""),
    ) -> StreamingResponse:
        authenticate_internal(internal_token, x_openeip_internal_token, "AGENT", AgentError)
        tenant_id = validate_identity(x_tenant_id, "tenant", "AGENT", AgentError)
        user_id = validate_identity(x_user_id, "user", "AGENT", AgentError)
        execution_id = validate_identity(x_execution_id, "execution", "AGENT", AgentError)
        request_id = validate_identity(x_request_id, "request", "AGENT", AgentError)
        if request.headers.get("content-type", "").lower() != "application/json":
            raise AgentError("AGENT-V-005", "Agent request must use application/json", 415)
        body = await read_bounded_body(request, max_body_bytes, "AGENT", AgentError)
        decoded = _decode(body)
        knowledge_base_id = (
            validate_identity(decoded.knowledge_base_id, "knowledge base", "AGENT", AgentError)
            if decoded.knowledge_base_id is not None
            else None
        )
        allowed_tools = frozenset(decoded.allowed_tools)
        if len(allowed_tools) != len(decoded.allowed_tools):
            raise AgentError("AGENT-V-003", "Invalid Agent tool allowlist", 400)
        resolved_steps = default_max_steps if decoded.max_steps is None else decoded.max_steps
        if resolved_steps < 1 or resolved_steps > max_steps:
            raise AgentError("AGENT-V-004", "Invalid Agent execution", 400)
        stream = service.stream(
            tenant_id,
            user_id,
            request_id,
            execution_id,
            agent_id,
            decoded.user_input,
            knowledge_base_id,
            allowed_tools,
            resolved_steps,
        )
        return StreamingResponse(
            stream,
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache, no-transform", "X-Accel-Buffering": "no"},
        )

    return router


def _decode(body: bytes) -> InternalAgentRequest:
    try:
        text = body.decode("utf-8")
        if text.startswith("\ufeff"):
            raise ValueError("BOM is forbidden")
        value = json.loads(text, object_pairs_hook=_unique_object, parse_constant=_reject_constant)
        return InternalAgentRequest.model_validate(value)
    except (UnicodeDecodeError, json.JSONDecodeError, ValidationError, ValueError) as exception:
        raise AgentError("AGENT-V-001", "Invalid Agent request", 400) from exception


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def _reject_constant(value: str) -> None:
    raise ValueError(f"Non-finite JSON number is forbidden: {value}")
