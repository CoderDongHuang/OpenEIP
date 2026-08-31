"""Strict Agent SPI v2, Tool SPI v1, MCP, and Evaluation wire models."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True, populate_by_name=True)


class AgentBudget(StrictModel):
    max_steps: int = Field(ge=1, le=64, alias="maxSteps")
    max_duration_seconds: int = Field(ge=1, le=1800, alias="maxDurationSeconds")
    max_tool_calls: int = Field(ge=0, le=128, alias="maxToolCalls")
    max_workers: int = Field(ge=0, le=16, alias="maxWorkers")


class CapabilityTool(StrictModel):
    id: str = Field(pattern=r"^[a-z][a-z0-9.:-]{0,127}$")
    version: str = Field(min_length=1, max_length=64)
    digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    operations: list[str] = Field(min_length=1, max_length=32)
    approval_mode: Literal["NONE", "POLICY", "PER_CALL"] = Field(alias="approvalMode")
    risk_class: Literal["READ", "WRITE", "DESTRUCTIVE"] = Field(alias="riskClass")


class ExecutionCapability(StrictModel):
    iss: Literal["openeip-java"]
    aud: Literal["openeip-agent-runtime"]
    tenant_id: str = Field(min_length=1, max_length=64, alias="tenantId")
    principal_id: str = Field(alias="principalId")
    run_id: str = Field(alias="runId")
    agent_version_id: str = Field(alias="agentVersionId")
    agent_digest: str = Field(pattern=r"^[0-9a-f]{64}$", alias="agentDigest")
    dependency_digest: str = Field(pattern=r"^[0-9a-f]{64}$", alias="dependencyDigest")
    resource_handles: list[str] = Field(max_length=64, alias="resourceHandles")
    budget: AgentBudget
    tools: list[CapabilityTool] = Field(max_length=64)
    iat: int
    exp: int
    nonce: str


class ExecuteAgentRequest(StrictModel):
    input: str = Field(min_length=1, max_length=32_000)
    agent_type: Literal["DOCUMENT", "SQL", "BI", "SEARCH", "WORKFLOW", "CUSTOM"] = Field(alias="agentType")
    agent_version: int = Field(ge=1, alias="agentVersion")


class RuntimeEvent(StrictModel):
    type: str = Field(min_length=1, max_length=80)
    payload: dict[str, object]


class ExecuteAgentResponse(StrictModel):
    status: Literal["SUCCEEDED", "FAILED"]
    failure_code: str | None = Field(default=None, alias="failureCode")
    events: list[RuntimeEvent] = Field(max_length=256)


class McpDiscoveryRequest(StrictModel):
    server_id: str = Field(alias="serverId")
    transport: Literal["STDIO", "STREAMABLE_HTTP"]
    endpoint: str = Field(min_length=1, max_length=2048)
    auth_type: Literal["NONE", "OAUTH2", "BEARER_REF", "MTLS_REF"] = Field(alias="authType")
    credential_ref: str | None = Field(default=None, alias="credentialRef")


class McpCapability(StrictModel):
    name: str = Field(min_length=1, max_length=128)
    type: Literal["TOOL", "RESOURCE", "PROMPT"]
    digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    schema_: dict[str, object] = Field(alias="schema")


class McpDiscoveryResponse(StrictModel):
    policy_status: Literal["PASS", "FAIL"] = Field(alias="policyStatus")
    capabilities: list[McpCapability] = Field(max_length=128)


class EvaluationCase(StrictModel):
    key: str = Field(min_length=1, max_length=128)
    agent_type: Literal["DOCUMENT", "SQL", "BI", "SEARCH", "WORKFLOW"] = Field(alias="agentType")
    fixture: dict[str, object]
    assertions: dict[str, object]
    digest: str = Field(pattern=r"^[0-9a-f]{64}$")


class EvaluationRequest(StrictModel):
    suite_digest: str = Field(pattern=r"^[0-9a-f]{64}$", alias="suiteDigest")
    candidate_digest: str = Field(pattern=r"^[0-9a-f]{64}$", alias="candidateDigest")
    baseline_digest: str = Field(pattern=r"^[0-9a-f]{64}$", alias="baselineDigest")
    repeat_count: int = Field(ge=1, le=10, alias="repeatCount")
    cases: list[EvaluationCase] = Field(min_length=1, max_length=5000)
    gate_policy: dict[str, float] = Field(max_length=32, alias="gatePolicy")


class EvaluationMetric(StrictModel):
    key: str
    value: float
    sample_count: int = Field(ge=1, alias="sampleCount")
    low: float | None = None
    high: float | None = None


class EvaluationGate(StrictModel):
    key: str
    status: Literal["PASS", "FAIL"]
    actual: float | None = None
    threshold: float | None = None
    reason_code: str = Field(alias="reasonCode")


class EvaluationResponse(StrictModel):
    metrics: list[EvaluationMetric]
    gates: list[EvaluationGate]
