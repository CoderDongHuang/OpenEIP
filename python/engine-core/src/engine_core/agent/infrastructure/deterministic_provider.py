"""Deterministic offline Agent plan provider."""

from engine_core.agent.application.runtime import AgentDecision
from engine_core.agent.spi import ToolObservation


class DeterministicAgentPlanProvider:
    """Select one bounded tool from an explicit fixture prefix, then finish."""

    async def decide(self, user_input: str, observations: tuple[ToolObservation, ...], step: int) -> AgentDecision:
        del step
        if observations:
            latest = observations[-1]
            return AgentDecision(
                kind="final",
                answer=f"{latest.tool_name} completed: {latest.output}",
            )
        stripped = user_input.strip()
        lowered = stripped.casefold()
        if lowered.startswith("search:"):
            return AgentDecision(
                kind="tool",
                tool_name="knowledge.search",
                arguments={"query": stripped[7:].strip(), "topK": 3},
            )
        if lowered.startswith("inspect:"):
            return AgentDecision(
                kind="tool",
                tool_name="document.inspect",
                arguments={"text": stripped[8:].strip()},
            )
        return AgentDecision(kind="final", answer="No tool execution was requested.")
