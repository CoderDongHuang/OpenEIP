"""Strict built-in Agent tools and the runtime-owned executor."""

import asyncio
import hashlib
import json
from collections.abc import Awaitable, Callable, Mapping
from time import perf_counter
from typing import Protocol

from engine_core.agent.shared.errors import AgentError
from engine_core.agent.spi import AgentContext, AgentLimits, ToolCall, ToolDefinition, ToolObservation
from engine_core.rag.application.service import RagService
from engine_core.rag.shared.errors import RagError


class AgentTool(Protocol):
    """Internal tool adapter contract; not directly available to plugins."""

    @property
    def definition(self) -> ToolDefinition:
        """Return the immutable definition."""

    async def invoke(self, context: AgentContext, arguments: Mapping[str, object]) -> str:
        """Validate exact arguments and return bounded text."""


class DocumentInspectTool:
    """Inspect supplied text without network, filesystem, or process access."""

    @property
    def definition(self) -> ToolDefinition:
        return ToolDefinition(
            "document.inspect",
            "Return SHA-256 and bounded text statistics",
            {
                "type": "object",
                "additionalProperties": False,
                "required": ["text"],
                "properties": {"text": {"type": "string", "minLength": 1, "maxLength": 4000}},
            },
        )

    async def invoke(self, context: AgentContext, arguments: Mapping[str, object]) -> str:
        del context
        if set(arguments) != {"text"} or not isinstance(arguments.get("text"), str):
            raise AgentError("AGENT-S-001", "Agent execution failed", 503)
        text = arguments["text"]
        assert isinstance(text, str)
        if (
            not text.strip()
            or len(text) > 4000
            or any(ord(character) < 32 and character not in "\t\n\r" for character in text)
        ):
            raise AgentError("AGENT-S-001", "Agent execution failed", 503)
        await asyncio.sleep(0)
        return json.dumps(
            {
                "sha256": hashlib.sha256(text.encode("utf-8")).hexdigest(),
                "characters": len(text),
                "words": len(text.split()),
                "lines": len(text.splitlines()) or 1,
            },
            sort_keys=True,
            separators=(",", ":"),
        )


class KnowledgeSearchTool:
    """Call the already-scoped RAG service after Java resource authorization."""

    def __init__(self, rag_service: RagService) -> None:
        self._rag_service = rag_service

    @property
    def definition(self) -> ToolDefinition:
        return ToolDefinition(
            "knowledge.search",
            "Search one authorized knowledge base through RAG",
            {
                "type": "object",
                "additionalProperties": False,
                "required": ["query", "topK"],
                "properties": {
                    "query": {"type": "string", "minLength": 1, "maxLength": 2000},
                    "topK": {"type": "integer", "minimum": 1, "maximum": 20},
                },
            },
        )

    async def invoke(self, context: AgentContext, arguments: Mapping[str, object]) -> str:
        if set(arguments) != {"query", "topK"}:
            raise AgentError("AGENT-S-001", "Agent execution failed", 503)
        query = arguments.get("query")
        top_k = arguments.get("topK")
        if (
            not isinstance(query, str)
            or not query.strip()
            or len(query) > 2000
            or isinstance(top_k, bool)
            or not isinstance(top_k, int)
            or not 1 <= top_k <= 20
            or context.knowledge_base_id is None
        ):
            raise AgentError("AGENT-S-001", "Agent execution failed", 503)
        try:
            result = await asyncio.to_thread(
                self._rag_service.query,
                context.tenant_id,
                context.knowledge_base_id,
                query,
                top_k,
            )
        except RagError as exception:
            raise AgentError("AGENT-S-001", "Agent execution failed", 503) from exception
        return json.dumps(
            {
                "answer": result.answer,
                "citations": [citation.chunk_id for citation in result.citations],
                "retrievalCount": result.retrieval_count,
            },
            sort_keys=True,
            separators=(",", ":"),
        )


LifecycleEmitter = Callable[[str, dict[str, object]], Awaitable[None]]


class RegistryToolExecutor:
    """Enforce registration, timeout, and sanitized lifecycle events around every tool."""

    def __init__(self, tools: tuple[AgentTool, ...], emit: LifecycleEmitter, limits: AgentLimits) -> None:
        self._tools = {tool.definition.name: tool for tool in tools}
        if len(self._tools) != len(tools):
            raise ValueError("Tool names must be unique")
        self._emit = emit
        self._limits = limits

    async def execute(self, call: ToolCall, context: AgentContext, step: int) -> ToolObservation:
        tool = self._tools.get(call.name)
        if tool is None or call.name not in context.allowed_tools:
            raise AgentError("AGENT-S-001", "Agent execution failed", 503)
        await self._emit(
            "tool.started",
            {"toolCallId": call.call_id, "toolName": call.name, "step": step},
        )
        started = perf_counter()
        try:
            output = await asyncio.wait_for(
                tool.invoke(context, call.arguments),
                timeout=self._limits.tool_timeout_seconds,
            )
        except TimeoutError as exception:
            raise AgentError("AGENT-S-001", "Agent execution failed", 503) from exception
        if not isinstance(output, str) or not output or len(output) > self._limits.max_result_chars:
            raise AgentError("AGENT-S-001", "Agent execution failed", 503)
        duration_ms = (perf_counter() - started) * 1000
        await self._emit(
            "tool.completed",
            {
                "toolCallId": call.call_id,
                "toolName": call.name,
                "step": step,
                "durationMs": round(duration_ms, 3),
                "resultChars": len(output),
            },
        )
        return ToolObservation(call.call_id, call.name, step, output, duration_ms)


def tool_definitions(tools: tuple[AgentTool, ...]) -> tuple[ToolDefinition, ...]:
    """Return immutable declarations for Agent registration."""
    return tuple(tool.definition for tool in tools)
