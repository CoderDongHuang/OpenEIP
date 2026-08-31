# Tool SPI v1

> Contract version: `1.0` | Runtime: Python >= 3.12 | Status: Accepted

```python
class ToolSpi(ABC):
    @abstractmethod
    def metadata(self) -> ToolMetadata: ...

    @abstractmethod
    async def invoke(self, request: ToolRequest, context: ToolContext) -> ToolResult: ...
```

Metadata contains immutable reverse-DNS `id`, SemVer `version`, input/output JSON schemas, risk class,
idempotency mode, timeout/result bounds, resource kinds, data classification, and cancellation support.
Schemas use `additionalProperties: false`, bounded values, and no secret defaults.

Context contains runtime-verified tenant/principal, run/step/call IDs, fencing token, deadline, idempotency
key, approved resource handles, and capability digest. It contains no reusable bearer token or raw credential.
A Tool cannot alter identity, grants, budgets, or handles.

Result is a bounded schema-valid value with safe summary/provenance, or a stable error with retryability.
Tools propagate cancellation and create no untracked tasks. I/O must use its registered governed adapter.
Incompatible schemas, risk/idempotency, side effects, authority, or result meaning require a new major version.
Invocation pins exact version and digest.
