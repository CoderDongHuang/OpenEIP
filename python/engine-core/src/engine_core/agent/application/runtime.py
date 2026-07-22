"""Finite Agent loop and cancellation-aware SSE orchestration."""

import asyncio
import json
import math
from collections.abc import AsyncIterator, Awaitable, Callable, Mapping
from contextlib import suppress
from dataclasses import dataclass
from typing import Literal, Protocol
from uuid import uuid4

from engine_core.agent.shared.errors import AgentError
from engine_core.agent.spi import (
    AgentContext,
    AgentLimits,
    AgentMetadata,
    AgentResult,
    AgentSpi,
    ToolCall,
    ToolDefinition,
    ToolObservation,
)

AGENT_ID = "openeip.constrained-v1"
AGENT_EVENTS = {
    "execution.started",
    "tool.started",
    "tool.completed",
    "answer.delta",
    "execution.completed",
    "execution.error",
}


@dataclass(frozen=True)
class AgentDecision:
    """Provider output that is independently validated by the runtime."""

    kind: Literal["tool", "final"]
    tool_name: str = ""
    arguments: Mapping[str, object] | None = None
    answer: str = ""


class AgentPlanProvider(Protocol):
    """Replaceable provider boundary; provider output has no authority by itself."""

    async def decide(self, user_input: str, observations: tuple[ToolObservation, ...], step: int) -> AgentDecision:
        """Choose a candidate tool call or final answer."""


class ConstrainedAgent(AgentSpi):
    """Built-in single Agent with a finite tool loop and execution-local memory."""

    def __init__(self, provider: AgentPlanProvider, tools: tuple[ToolDefinition, ...]) -> None:
        names = [tool.name for tool in tools]
        if len(names) != len(set(names)) or not tools:
            raise ValueError("Agent tools must be unique and non-empty")
        self._provider = provider
        self._tools = tools

    def get_metadata(self) -> AgentMetadata:
        return AgentMetadata(
            agent_id=AGENT_ID,
            name="OpenEIP Constrained Agent",
            description="Bounded single-Agent runtime with explicit Document and Search tools",
            version="1.0.0",
        )

    def get_tools(self) -> tuple[ToolDefinition, ...]:
        return self._tools

    async def execute(self, context: AgentContext) -> AgentResult:
        declared = {tool.name for tool in self._tools}
        if not context.allowed_tools or not context.allowed_tools <= declared:
            raise AgentError("AGENT-S-001", "Agent execution failed", 503)
        observations: list[ToolObservation] = []
        fingerprints: set[str] = set()
        try:
            async with asyncio.timeout(context.limits.total_timeout_seconds):
                for step in range(1, context.limits.max_steps + 1):
                    decision = await self._provider.decide(context.user_input, tuple(observations), step)
                    if decision.kind == "final":
                        answer = _bounded_text(decision.answer, context.limits.max_answer_chars)
                        return AgentResult(answer, "stop", tuple(observations))
                    if decision.kind != "tool" or decision.tool_name not in context.allowed_tools:
                        raise AgentError("AGENT-S-001", "Agent execution failed", 503)
                    arguments = dict(decision.arguments or {})
                    _validate_json(arguments)
                    encoded = json.dumps(arguments, sort_keys=True, ensure_ascii=True, separators=(",", ":"))
                    if len(encoded.encode("utf-8")) > context.limits.max_argument_bytes:
                        raise AgentError("AGENT-S-001", "Agent execution failed", 503)
                    fingerprint = f"{decision.tool_name}:{encoded}"
                    if fingerprint in fingerprints:
                        raise AgentError("AGENT-S-001", "Agent execution failed", 503)
                    fingerprints.add(fingerprint)
                    observation = await context.tool_executor.execute(
                        ToolCall(str(uuid4()), decision.tool_name, arguments), context, step
                    )
                    if len(observation.output) > context.limits.max_result_chars:
                        raise AgentError("AGENT-S-001", "Agent execution failed", 503)
                    observations.append(observation)
        except TimeoutError as exception:
            raise AgentError("AGENT-S-001", "Agent execution failed", 503) from exception
        raise AgentError("AGENT-S-001", "Agent execution failed", 503)


LifecycleEmitter = Callable[[str, dict[str, object]], Awaitable[None]]
ExecutorFactory = Callable[[LifecycleEmitter, AgentLimits], object]


class AgentStreamService:
    """Convert one SPI execution into sanitized ordered Server-Sent Events."""

    def __init__(
        self,
        agent: AgentSpi,
        executor_factory: ExecutorFactory,
        limits: AgentLimits,
        answer_chunk_chars: int,
    ) -> None:
        if answer_chunk_chars < 1 or answer_chunk_chars > 1024:
            raise ValueError("Invalid Agent answer chunk limit")
        self._agent = agent
        self._executor_factory = executor_factory
        self._limits = limits
        self._answer_chunk_chars = answer_chunk_chars

    @property
    def metadata(self) -> AgentMetadata:
        return self._agent.get_metadata()

    @property
    def tools(self) -> tuple[ToolDefinition, ...]:
        return self._agent.get_tools()

    def stream(
        self,
        tenant_id: str,
        user_id: str,
        request_id: str,
        execution_id: str,
        agent_id: str,
        user_input: str,
        knowledge_base_id: str | None,
        allowed_tools: frozenset[str],
        max_steps: int,
    ) -> AsyncIterator[str]:
        """Validate synchronously and return one cancellation-aware event stream."""
        if agent_id != self.metadata.agent_id:
            raise AgentError("AGENT-N-001", "Agent not found", 404)
        if (
            not user_input.strip()
            or len(user_input) > 4000
            or any(ord(character) < 32 and character not in "\t\n\r" for character in user_input)
            or max_steps < 1
            or max_steps > self._limits.max_steps
        ):
            raise AgentError("AGENT-V-004", "Invalid Agent execution", 400)
        declared = {tool.name for tool in self.tools}
        if not allowed_tools or not allowed_tools <= declared:
            raise AgentError("AGENT-V-003", "Invalid Agent tool allowlist", 400)
        if "knowledge.search" in allowed_tools and knowledge_base_id is None:
            raise AgentError("AGENT-V-002", "Knowledge base is required", 400)

        async def events() -> AsyncIterator[str]:
            queue: asyncio.Queue[tuple[str, dict[str, object]]] = asyncio.Queue()
            sequence = 0

            async def emit(event: str, data: dict[str, object]) -> None:
                await queue.put((event, data))

            limits = AgentLimits(
                max_steps=max_steps,
                total_timeout_seconds=self._limits.total_timeout_seconds,
                tool_timeout_seconds=self._limits.tool_timeout_seconds,
                max_argument_bytes=self._limits.max_argument_bytes,
                max_result_chars=self._limits.max_result_chars,
                max_answer_chars=self._limits.max_answer_chars,
            )
            executor = self._executor_factory(emit, limits)
            context = AgentContext(
                tenant_id,
                user_id,
                request_id,
                execution_id,
                user_input,
                knowledge_base_id,
                allowed_tools,
                limits,
                executor,  # type: ignore[arg-type]
            )
            task = asyncio.create_task(self._agent.execute(context))
            try:
                await asyncio.sleep(0)
                yield _event("execution.started", request_id, execution_id, sequence, {"agentId": agent_id})
                sequence += 1
                while not task.done() or not queue.empty():
                    try:
                        event, data = await asyncio.wait_for(queue.get(), timeout=0.01)
                    except TimeoutError:
                        continue
                    yield _event(event, request_id, execution_id, sequence, data)
                    sequence += 1
                result = await task
                for offset in range(0, len(result.answer), self._answer_chunk_chars):
                    yield _event(
                        "answer.delta",
                        request_id,
                        execution_id,
                        sequence,
                        {"text": result.answer[offset : offset + self._answer_chunk_chars]},
                    )
                    sequence += 1
                yield _event(
                    "execution.completed",
                    request_id,
                    execution_id,
                    sequence,
                    {"finishReason": result.finish_reason, "steps": len(result.observations)},
                )
            except asyncio.CancelledError:
                task.cancel()
                with suppress(asyncio.CancelledError):
                    await task
                raise
            except Exception:
                yield _event(
                    "execution.error",
                    request_id,
                    execution_id,
                    sequence,
                    {"code": "AGENT-S-001", "message": "Agent execution failed"},
                )
            finally:
                if not task.done():
                    task.cancel()
                    with suppress(asyncio.CancelledError):
                        await task

        return events()


def encode_agent_sse(event: str, data: Mapping[str, object]) -> str:
    """Encode one allowlisted event as single-line strict JSON."""
    if event not in AGENT_EVENTS:
        raise ValueError("Unsupported Agent event")
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=True, separators=(',', ':'))}\n\n"


def _event(
    event: str,
    request_id: str,
    execution_id: str,
    sequence: int,
    data: Mapping[str, object],
) -> str:
    return encode_agent_sse(
        event,
        {"requestId": request_id, "executionId": execution_id, "sequence": sequence, **data},
    )


def _bounded_text(value: str, limit: int) -> str:
    if (
        not value.strip()
        or len(value) > limit
        or any(ord(character) < 32 and character not in "\t\n\r" for character in value)
    ):
        raise AgentError("AGENT-S-001", "Agent execution failed", 503)
    return value.strip()


def _validate_json(value: object, depth: int = 0) -> None:
    if depth > 8:
        raise AgentError("AGENT-S-001", "Agent execution failed", 503)
    if value is None or isinstance(value, (str, bool, int)):
        return
    if isinstance(value, float):
        if math.isfinite(value):
            return
        raise AgentError("AGENT-S-001", "Agent execution failed", 503)
    if isinstance(value, list):
        for item in value:
            _validate_json(item, depth + 1)
        return
    if isinstance(value, dict) and all(isinstance(key, str) for key in value):
        for item in value.values():
            _validate_json(item, depth + 1)
        return
    raise AgentError("AGENT-S-001", "Agent execution failed", 503)
