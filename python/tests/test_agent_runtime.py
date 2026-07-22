import asyncio

import pytest

from engine_core.agent.application.runtime import (
    AGENT_ID,
    AgentDecision,
    AgentPlanProvider,
    AgentStreamService,
    ConstrainedAgent,
    encode_agent_sse,
)
from engine_core.agent.infrastructure.deterministic_provider import DeterministicAgentPlanProvider
from engine_core.agent.infrastructure.tools import DocumentInspectTool, RegistryToolExecutor, tool_definitions
from engine_core.agent.shared.errors import AgentError
from engine_core.agent.spi import AgentLimits, ToolObservation

TENANT = "11111111-1111-4111-8111-111111111111"
USER = "22222222-2222-4222-8222-222222222222"
EXECUTION = "33333333-3333-4333-8333-333333333333"
REQUEST = "44444444-4444-4444-8444-444444444444"


class FixedProvider:
    def __init__(self, decision: AgentDecision, delay: float = 0) -> None:
        self.decision = decision
        self.delay = delay

    async def decide(self, user_input: str, observations: tuple[ToolObservation, ...], step: int) -> AgentDecision:
        del user_input, observations, step
        if self.delay:
            await asyncio.sleep(self.delay)
        return self.decision


def _limits(**changes: object) -> AgentLimits:
    values: dict[str, object] = {
        "max_steps": 4,
        "total_timeout_seconds": 1.0,
        "tool_timeout_seconds": 0.2,
        "max_argument_bytes": 8192,
        "max_result_chars": 8000,
        "max_answer_chars": 8000,
    }
    values.update(changes)
    return AgentLimits(**values)  # type: ignore[arg-type]


def _service(provider: AgentPlanProvider, limits: AgentLimits | None = None) -> AgentStreamService:
    tools = (DocumentInspectTool(),)
    resolved = limits or _limits()
    return AgentStreamService(
        ConstrainedAgent(provider, tool_definitions(tools)),
        lambda emit, execution_limits: RegistryToolExecutor(tools, emit, execution_limits),
        resolved,
        64,
    )


async def _collect(service: AgentStreamService, user_input: str = "inspect: hello") -> list[str]:
    return [
        event
        async for event in service.stream(
            TENANT,
            USER,
            REQUEST,
            EXECUTION,
            AGENT_ID,
            user_input,
            None,
            frozenset({"document.inspect"}),
            4,
        )
    ]


@pytest.mark.asyncio
async def test_runtime_executes_tool_with_ephemeral_observation_and_sanitized_events() -> None:
    events = await _collect(_service(DeterministicAgentPlanProvider()), "inspect: hello world")
    names = [event.splitlines()[0] for event in events]
    assert names[:3] == ["event: execution.started", "event: tool.started", "event: tool.completed"]
    assert names[-1] == "event: execution.completed"
    assert "hello world" not in "".join(events)
    assert "sha256" in "".join(events)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "decision",
    [
        AgentDecision("tool", "shell.exec", {"command": "id"}),
        AgentDecision("tool", "document.inspect", {"path": "C:/secret"}),
        AgentDecision("tool", "document.inspect", {"text": float("nan")}),
        AgentDecision("final", answer=""),
        AgentDecision("final", answer="secret\x00trace"),
    ],
)
async def test_runtime_fails_closed_on_unknown_tool_argument_injection_and_invalid_answer(
    decision: AgentDecision,
) -> None:
    events = await _collect(_service(FixedProvider(decision)))
    assert events[-1].startswith("event: execution.error")
    joined = "".join(events)
    assert "shell.exec" not in joined
    assert "C:/secret" not in joined
    assert "trace" not in joined


@pytest.mark.asyncio
async def test_runtime_terminates_repeated_identical_call() -> None:
    decision = AgentDecision("tool", "document.inspect", {"text": "repeat"})
    events = await _collect(_service(FixedProvider(decision)))
    names = [event.splitlines()[0] for event in events]
    assert names.count("event: tool.started") == 1
    assert names[-1] == "event: execution.error"


@pytest.mark.asyncio
async def test_runtime_enforces_total_timeout_without_provider_details() -> None:
    events = await _collect(
        _service(FixedProvider(AgentDecision("final", answer="late"), delay=0.1), _limits(total_timeout_seconds=0.01))
    )
    assert events[-1].startswith("event: execution.error")
    assert "late" not in events[-1]


@pytest.mark.asyncio
async def test_runtime_cancellation_stops_provider_task() -> None:
    cancelled = asyncio.Event()

    class BlockingProvider:
        async def decide(self, user_input: str, observations: tuple[ToolObservation, ...], step: int) -> AgentDecision:
            del user_input, observations, step
            try:
                await asyncio.sleep(60)
            finally:
                cancelled.set()
            return AgentDecision("final", answer="never")

    stream = _service(BlockingProvider()).stream(
        TENANT,
        USER,
        REQUEST,
        EXECUTION,
        AGENT_ID,
        "wait",
        None,
        frozenset({"document.inspect"}),
        4,
    )
    assert (await anext(stream)).startswith("event: execution.started")
    await stream.aclose()
    await asyncio.wait_for(cancelled.wait(), 1)


@pytest.mark.asyncio
async def test_runtime_rejects_invalid_agent_allowlist_limits_and_configuration() -> None:
    service = _service(DeterministicAgentPlanProvider())
    invalid = (
        ("missing", "x", frozenset({"document.inspect"}), 4),
        (AGENT_ID, "", frozenset({"document.inspect"}), 4),
        (AGENT_ID, "x", frozenset({"unknown"}), 4),
        (AGENT_ID, "x", frozenset({"document.inspect"}), 9),
    )
    for agent_id, user_input, allowed, steps in invalid:
        with pytest.raises(AgentError):
            service.stream(TENANT, USER, REQUEST, EXECUTION, agent_id, user_input, None, allowed, steps)
    tools = (DocumentInspectTool(),)
    with pytest.raises(ValueError):
        AgentStreamService(
            ConstrainedAgent(DeterministicAgentPlanProvider(), tool_definitions(tools)),
            lambda emit, execution_limits: RegistryToolExecutor(tools, emit, execution_limits),
            _limits(),
            0,
        )
    with pytest.raises(ValueError):
        encode_agent_sse("thought", {})


def test_agent_spi_metadata_and_tool_schema_are_stable() -> None:
    tools = (DocumentInspectTool(),)
    agent = ConstrainedAgent(DeterministicAgentPlanProvider(), tool_definitions(tools))
    assert agent.get_metadata().agent_id == AGENT_ID
    assert agent.get_metadata().spi_version == "1.0"
    assert agent.get_tools()[0].input_schema["additionalProperties"] is False
