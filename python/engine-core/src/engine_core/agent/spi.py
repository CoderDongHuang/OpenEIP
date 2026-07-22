"""Versioned public Agent SPI v1."""

from abc import ABC, abstractmethod
from collections.abc import Mapping
from dataclasses import dataclass
from typing import Literal, Protocol

SPI_VERSION = "1.0"


@dataclass(frozen=True)
class AgentMetadata:
    """Immutable Agent identity and compatibility metadata."""

    agent_id: str
    name: str
    description: str
    version: str
    spi_version: str = SPI_VERSION


@dataclass(frozen=True)
class ToolDefinition:
    """Immutable bounded tool declaration."""

    name: str
    description: str
    input_schema: Mapping[str, object]


@dataclass(frozen=True)
class ToolCall:
    """One provider-selected tool invocation."""

    call_id: str
    name: str
    arguments: Mapping[str, object]


@dataclass(frozen=True)
class ToolObservation:
    """Bounded runtime-owned tool result."""

    call_id: str
    tool_name: str
    step: int
    output: str
    duration_ms: float


@dataclass(frozen=True)
class AgentLimits:
    """Limits that plugins may narrow but never widen."""

    max_steps: int
    total_timeout_seconds: float
    tool_timeout_seconds: float
    max_argument_bytes: int
    max_result_chars: int
    max_answer_chars: int


class ToolExecutor(Protocol):
    """Only authority through which an Agent may invoke a tool."""

    async def execute(self, call: ToolCall, context: "AgentContext", step: int) -> ToolObservation:
        """Validate and invoke one allowlisted tool."""


@dataclass(frozen=True)
class AgentContext:
    """Canonical per-execution context supplied by the runtime."""

    tenant_id: str
    user_id: str
    request_id: str
    execution_id: str
    user_input: str
    knowledge_base_id: str | None
    allowed_tools: frozenset[str]
    limits: AgentLimits
    tool_executor: ToolExecutor


@dataclass(frozen=True)
class AgentResult:
    """Bounded final result without private reasoning or mutable memory."""

    answer: str
    finish_reason: Literal["stop"]
    observations: tuple[ToolObservation, ...]


class AgentSpi(ABC):
    """Public Agent plugin contract version 1.0."""

    @abstractmethod
    def get_metadata(self) -> AgentMetadata:
        """Return immutable compatibility metadata."""

    @abstractmethod
    def get_tools(self) -> tuple[ToolDefinition, ...]:
        """Return immutable tool declarations."""

    @abstractmethod
    async def execute(self, context: AgentContext) -> AgentResult:
        """Execute once and propagate cancellation."""
