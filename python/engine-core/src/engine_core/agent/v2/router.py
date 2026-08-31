"""Authenticated internal endpoints for Agent execution, MCP, and Evaluation workers."""

import json

from fastapi import APIRouter, Header, Request
from pydantic import BaseModel, ValidationError

from engine_core.agent.v2.capability import CapabilityVerifier
from engine_core.agent.v2.evaluation import DeterministicAgentEvaluator
from engine_core.agent.v2.mcp_gateway import McpDiscoveryGateway
from engine_core.agent.v2.models import EvaluationRequest, ExecuteAgentRequest, McpDiscoveryRequest
from engine_core.agent.v2.runtime import AgentRuntimeV2
from engine_core.shared.api_error import EngineApiError
from engine_core.shared.internal_api import authenticate_internal, read_bounded_body


class AgentV2ApiError(EngineApiError):
    """Stable internal Agent v2 API error."""


def build_agent_v2_router(
    *,
    internal_token: str,
    capability_secret: str,
    max_body_bytes: int,
    runtime: AgentRuntimeV2 | None = None,
    mcp_gateway: McpDiscoveryGateway | None = None,
    evaluator: DeterministicAgentEvaluator | None = None,
) -> APIRouter:
    router = APIRouter(prefix="/api/v2/internal", tags=["agent-v2-internal"])
    verifier = CapabilityVerifier(capability_secret)
    resolved_runtime = runtime or AgentRuntimeV2()
    resolved_mcp = mcp_gateway or McpDiscoveryGateway()
    resolved_evaluator = evaluator or DeterministicAgentEvaluator()

    @router.post("/agent-runs:execute")
    async def execute(
        request: Request,
        supplied_internal: str = Header(default="", alias="X-OpenEIP-Internal-Token"),
        supplied_capability: str = Header(default="", alias="X-Agent-Capability"),
    ) -> dict[str, object]:
        _authenticate(internal_token, supplied_internal)
        capability = verifier.verify(supplied_capability)
        body = await _body(request, max_body_bytes)
        parsed = _validate(ExecuteAgentRequest, body)
        result = await resolved_runtime.execute(parsed, capability)
        return result.model_dump(mode="json", by_alias=True)

    @router.post("/mcp:discover")
    async def discover(
        request: Request,
        supplied_internal: str = Header(default="", alias="X-OpenEIP-Internal-Token"),
    ) -> dict[str, object]:
        _authenticate(internal_token, supplied_internal)
        body = await _body(request, max_body_bytes)
        result = await resolved_mcp.discover(_validate(McpDiscoveryRequest, body))
        return result.model_dump(mode="json", by_alias=True)

    @router.post("/evaluations:run")
    async def evaluate(
        request: Request,
        supplied_internal: str = Header(default="", alias="X-OpenEIP-Internal-Token"),
    ) -> dict[str, object]:
        _authenticate(internal_token, supplied_internal)
        body = await _body(request, max(max_body_bytes, 4 * 1024 * 1024))
        result = resolved_evaluator.evaluate(_validate(EvaluationRequest, body))
        return result.model_dump(mode="json", by_alias=True)

    return router


def _authenticate(expected: str, supplied: str) -> None:
    authenticate_internal(expected, supplied, "AGENT2", AgentV2ApiError)


async def _body(request: Request, limit: int) -> object:
    raw = await read_bounded_body(request, limit, "AGENT2", AgentV2ApiError)
    try:
        return json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise AgentV2ApiError("AGENT2-V-001", "Invalid Agent request", 400) from error


def _validate[ModelT: BaseModel](model: type[ModelT], value: object) -> ModelT:
    try:
        return model.model_validate(value)
    except ValidationError as error:
        raise AgentV2ApiError("AGENT2-V-001", "Invalid Agent request", 400) from error
