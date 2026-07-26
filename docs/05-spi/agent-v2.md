# Agent SPI v2

> Contract version: `2.0` | Runtime: Python >= 3.12 | Status: Proposed

```python
class AgentSpiV2(ABC):
    @abstractmethod
    def metadata(self) -> AgentMetadataV2: ...

    @abstractmethod
    async def plan(self, request: PlanRequest, context: AgentContextV2) -> PlanResult: ...

    @abstractmethod
    async def execute_step(self, step: PlannedStep, context: AgentContextV2) -> StepResult: ...

    @abstractmethod
    async def reflect(self, request: ReflectionRequest,
                      context: AgentContextV2) -> ReflectionResult: ...
```

Plans contain stable step IDs, public objectives, dependencies, declared Tool/Worker intent, output schemas,
and stop conditions, never thoughts. Runtime validates acyclic dependencies, grants, budgets, and schemas.
A plan is a proposal, not authority.

Context supplies immutable identity, exact versions, narrowed capability handles, remaining budget, sanitized
checkpoint references, and runtime Tool/Memory/Worker dispatchers. Plugins cannot persist authoritative state,
register capabilities, resolve secrets, or perform direct I/O.

Reflection returns `COMPLETE`, `REVISE_PLAN`, `RETRY_STEP`, `PAUSE`, or `FAIL`, with a public reason code,
summary, evidence IDs, and optional patch. Runtime owns the decision and limits. Methods propagate cancellation
and tolerate duplicate delivery by attempt ID. Agent SPI v1 remains through an adapter; breaking v2 lifecycle
or authority semantics requires Agent SPI v3 and an RFC.
