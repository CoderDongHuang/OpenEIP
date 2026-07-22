import json
import os
from pathlib import Path
from statistics import median
from time import perf_counter

import pytest

from engine_core.agent.application.runtime import (
    AGENT_ID,
    AgentDecision,
    AgentStreamService,
    ConstrainedAgent,
)
from engine_core.agent.infrastructure.deterministic_provider import DeterministicAgentPlanProvider
from engine_core.agent.infrastructure.tools import DocumentInspectTool, RegistryToolExecutor, tool_definitions
from engine_core.agent.spi import AgentLimits, ToolObservation

TENANT = "11111111-1111-4111-8111-111111111111"
USER = "22222222-2222-4222-8222-222222222222"
REQUEST = "33333333-3333-4333-8333-333333333333"
EXECUTION = "44444444-4444-4444-8444-444444444444"
WARMUPS = 5
SAMPLES = 100


@pytest.mark.asyncio
@pytest.mark.benchmark
async def test_agent_event_tool_completion_and_loop_termination_latency() -> None:
    service = _service(DeterministicAgentPlanProvider())
    for _ in range(WARMUPS):
        await _measure(service)
    samples = [await _measure(service) for _ in range(SAMPLES)]
    first = sorted(sample[0] for sample in samples)
    tool = sorted(sample[1] for sample in samples)
    completion = sorted(sample[2] for sample in samples)
    first_p99 = percentile(first, 0.99)
    tool_p99 = percentile(tool, 0.99)
    completion_p99 = percentile(completion, 0.99)

    loop_started = perf_counter()
    loop_events = await _collect(_service(RepeatingProvider()))
    loop_ms = (perf_counter() - loop_started) * 1000
    assert loop_events[-1].startswith("event: execution.error")
    assert first_p99 < 50
    assert tool_p99 < 100
    assert completion_p99 < 500
    assert loop_ms < 100

    output = os.getenv("OPENEIP_AGENT_BENCHMARK_OUTPUT")
    if output:
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(
                {
                    "module": "agent",
                    "operation": "bounded Document tool execution and loop termination",
                    "warmups": WARMUPS,
                    "samples": SAMPLES,
                    "firstEventP50Ms": round(median(first), 3),
                    "firstEventP99Ms": round(first_p99, 3),
                    "toolOverheadP50Ms": round(median(tool), 3),
                    "toolOverheadP99Ms": round(tool_p99, 3),
                    "completionP50Ms": round(median(completion), 3),
                    "completionP99Ms": round(completion_p99, 3),
                    "loopTerminationMs": round(loop_ms, 3),
                    "thresholdFirstEventP99Ms": 50,
                    "thresholdToolOverheadP99Ms": 100,
                    "thresholdCompletionP99Ms": 500,
                    "thresholdLoopTerminationMs": 100,
                    "result": "PASS",
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )


class RepeatingProvider:
    async def decide(self, user_input: str, observations: tuple[ToolObservation, ...], step: int) -> AgentDecision:
        del user_input, observations, step
        return AgentDecision("tool", "document.inspect", {"text": "repeat"})


def _service(provider: object) -> AgentStreamService:
    tools = (DocumentInspectTool(),)
    limits = AgentLimits(4, 1.0, 0.2, 8192, 8000, 8000)
    return AgentStreamService(
        ConstrainedAgent(provider, tool_definitions(tools)),  # type: ignore[arg-type]
        lambda emit, execution_limits: RegistryToolExecutor(tools, emit, execution_limits),
        limits,
        256,
    )


async def _collect(service: AgentStreamService) -> list[str]:
    return [
        event
        async for event in service.stream(
            TENANT,
            USER,
            REQUEST,
            EXECUTION,
            AGENT_ID,
            "inspect: benchmark text",
            None,
            frozenset({"document.inspect"}),
            4,
        )
    ]


async def _measure(service: AgentStreamService) -> tuple[float, float, float]:
    started = perf_counter()
    first = 0.0
    tool = 0.0
    last_event = ""
    async for event in service.stream(
        TENANT,
        USER,
        REQUEST,
        EXECUTION,
        AGENT_ID,
        "inspect: benchmark text",
        None,
        frozenset({"document.inspect"}),
        4,
    ):
        elapsed = (perf_counter() - started) * 1000
        if first == 0:
            first = elapsed
        if event.startswith("event: tool.completed"):
            data = json.loads(event.splitlines()[1][6:])
            tool = float(data["durationMs"])
        last_event = event
    completion = (perf_counter() - started) * 1000
    assert last_event.startswith("event: execution.completed")
    return first, tool, completion


def percentile(values: list[float], ratio: float) -> float:
    return values[max(0, int(len(values) * ratio + 0.999999) - 1)]
