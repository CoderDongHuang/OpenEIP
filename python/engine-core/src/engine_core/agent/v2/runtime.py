"""Bounded public-plan execution for the first-party Agent v0.6 types."""

import hashlib
from dataclasses import dataclass

from engine_core.agent.v2.models import (
    ExecuteAgentRequest,
    ExecuteAgentResponse,
    ExecutionCapability,
    RuntimeEvent,
)

_TOOLS = {
    "DOCUMENT": ("openeip.document.inspect", "openeip.knowledge.search"),
    "SQL": ("openeip.connector.sql.query",),
    "BI": ("openeip.connector.sql.query", "openeip.bi.render"),
    "SEARCH": ("openeip.knowledge.search",),
    "WORKFLOW": ("openeip.workflow.inspect", "openeip.workflow.start"),
    "CUSTOM": (),
}


@dataclass(frozen=True)
class PublicStep:
    key: str
    objective: str
    tool: str | None


class AgentRuntimeV2:
    """Validate authority and execute a deterministic, bounded Agent lifecycle."""

    async def execute(self, request: ExecuteAgentRequest, capability: ExecutionCapability) -> ExecuteAgentResponse:
        granted = {tool.id for tool in capability.tools}
        required = _TOOLS[request.agent_type]
        if not set(required).issubset(granted):
            return _failed("AGENT2-P-001", "grant_intersection_denied")
        steps = self._plan(request.agent_type, capability)
        if len(steps) > capability.budget.max_steps:
            return _failed("AGENT2-B-001", "step_budget_exceeded")

        events = [RuntimeEvent(type="plan.created", payload={"stepCount": len(steps), "planRevision": 1})]
        tool_calls = 0
        for index, step in enumerate(steps, 1):
            events.append(
                RuntimeEvent(
                    type="step.started",
                    payload={"stepKey": step.key, "sequence": index, "objective": step.objective},
                )
            )
            if step.tool:
                tool_calls += 1
                if tool_calls > capability.budget.max_tool_calls:
                    return _failed("AGENT2-B-002", "tool_budget_exceeded", events)
                events.extend(self._tool_events(step, capability))
        if request.agent_type == "WORKFLOW" and capability.budget.max_workers > 0:
            events.extend(self._worker_events(capability))
        events.append(
            RuntimeEvent(
                type="reflection.completed",
                payload={
                    "outcome": "COMPLETE",
                    "reasonCode": "GOAL_SATISFIED",
                    "evidenceCount": len(capability.resource_handles),
                },
            )
        )
        return ExecuteAgentResponse(status="SUCCEEDED", events=events)

    @staticmethod
    def _plan(agent_type: str, capability: ExecutionCapability) -> list[PublicStep]:
        tools = _TOOLS[agent_type]
        if not tools:
            return [PublicStep("custom-validate", "Validate the bounded custom request", None)]
        return [
            PublicStep(f"{agent_type.casefold()}-{index}", _objective(tool), tool)
            for index, tool in enumerate(tools, 1)
        ]

    @staticmethod
    def _tool_events(step: PublicStep, capability: ExecutionCapability) -> list[RuntimeEvent]:
        assert step.tool is not None
        grant = next(tool for tool in capability.tools if tool.id == step.tool)
        if grant.risk_class == "DESTRUCTIVE" or grant.approval_mode == "PER_CALL":
            return [
                RuntimeEvent(
                    type="tool.approval.required",
                    payload={"toolId": grant.id, "riskClass": grant.risk_class, "expiresInSeconds": 300},
                )
            ]
        digest = hashlib.sha256(f"{capability.run_id}:{step.key}:{grant.digest}".encode()).hexdigest()
        return [
            RuntimeEvent(type="tool.started", payload={"stepKey": step.key, "toolId": grant.id}),
            RuntimeEvent(
                type="tool.completed",
                payload={"stepKey": step.key, "toolId": grant.id, "resultDigest": digest, "outcome": "SAFE"},
            ),
        ]

    @staticmethod
    def _worker_events(capability: ExecutionCapability) -> list[RuntimeEvent]:
        return [
            RuntimeEvent(
                type="worker.started",
                payload={"workerIndex": 1, "depth": 1, "capabilityNarrowed": True},
            ),
            RuntimeEvent(
                type="handoff.created",
                payload={"handoffIndex": 1, "referenceCount": len(capability.resource_handles), "depth": 1},
            ),
        ]


def _objective(tool: str) -> str:
    return {
        "openeip.document.inspect": "Inspect the authorized document references",
        "openeip.knowledge.search": "Retrieve governed knowledge evidence",
        "openeip.connector.sql.query": "Execute the governed read-only query",
        "openeip.bi.render": "Render an allowlisted visualization",
        "openeip.workflow.inspect": "Inspect the published Workflow state",
        "openeip.workflow.start": "Start the authorized published Workflow",
    }[tool]


def _failed(code: str, reason: str, events: list[RuntimeEvent] | None = None) -> ExecuteAgentResponse:
    values = list(events or [])
    values.append(RuntimeEvent(type="run.failed", payload={"code": code, "reasonCode": reason}))
    return ExecuteAgentResponse(status="FAILED", failureCode=code, events=values)
