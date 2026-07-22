# Agent SPI v1

> Contract version: `1.0` | Runtime: Python >= 3.12 | Status: Accepted

An Agent plugin implements `engine_core.agent.spi.AgentSpi`:

```python
class AgentSpi(ABC):
    @abstractmethod
    def get_metadata(self) -> AgentMetadata: ...

    @abstractmethod
    def get_tools(self) -> tuple[ToolDefinition, ...]: ...

    @abstractmethod
    async def execute(self, context: AgentContext) -> AgentResult: ...
```

## Contract Rules

- `AgentMetadata.spi_version` must equal `1.0`; IDs use reverse-DNS style and are immutable.
- Tool names are immutable lower-case dotted identifiers. JSON schemas are objects with
  `additionalProperties: false`, bounded strings/arrays, and no secret defaults.
- `AgentContext` contains canonical tenant/user/request/execution IDs, bounded user input, the exact
  allowed tool set, runtime limits, and a runtime-owned `ToolExecutor`. Plugins cannot register tools
  during execution or widen the allowlist.
- `AgentResult` contains a bounded final answer, finish reason, and sanitized tool observations. It
  must not contain prompts, thoughts, credentials, tracebacks, or mutable working-memory references.
- Plugins must propagate cancellation and must not spawn untracked background tasks.
- Tool execution always goes through `ToolExecutor`; direct network, filesystem, process, database,
  or credential access is outside v1 and fails review.

## Compatibility

Adding optional metadata with a default is compatible within v1. Removing fields, changing execution
semantics, widening default authority, changing tool schemas incompatibly, or exposing new privileged
context requires Agent SPI v2 and a new RFC.

## Packaging

The built-in implementation is imported directly. Third-party wheel discovery and marketplace
signing remain v0.8 scope; no entry-point auto-loading occurs in v0.2.
