"""Official MCP client lifecycle and tool verification for Spike-004."""

import asyncio
import json
import time
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


def content_text(result: Any) -> str:
    """Extract text from an MCP CallToolResult."""
    return "".join(getattr(item, "text", "") for item in result.content)


async def run() -> None:
    """Initialize, discover, call, and validate the MCP server."""
    parameters = StdioServerParameters(command="python", args=["/app/mcp-server/server.py"])
    started = time.perf_counter()
    async with (
        stdio_client(parameters) as (read_stream, write_stream),
        ClientSession(read_stream, write_stream) as session,
    ):
        initialization = await session.initialize()
        initialized_ms = (time.perf_counter() - started) * 1_000
        tools = await session.list_tools()
        tool_names = sorted(tool.name for tool in tools.tools)
        schemas_present = all(bool(tool.inputSchema) for tool in tools.tools)

        search = await session.call_tool("knowledge_search", {"query": "gRPC", "limit": 2})
        addition = await session.call_tool("add_numbers", {"left": 2.5, "right": 4.0})
        workflow = await session.call_tool("workflow_status", {"workflow_id": "wf-001"})
        invalid = await session.call_tool("knowledge_search", {"query": "bad", "limit": 99})
        unknown = await session.call_tool("missing_tool", {})

    expected_tools = ["add_numbers", "knowledge_search", "workflow_status"]
    calls_passed = (
        not search.isError
        and "OpenEIP result 2" in content_text(search)
        and not addition.isError
        and "6.5" in content_text(addition)
        and not workflow.isError
        and "completed" in content_text(workflow)
    )
    errors_passed = invalid.isError and unknown.isError
    evidence = {
        "spike": "spike-004",
        "executed_at": datetime.now(UTC).isoformat(),
        "protocol_version": initialization.protocolVersion,
        "server_name": initialization.serverInfo.name,
        "server_version": initialization.serverInfo.version,
        "initialization_ms": initialized_ms,
        "tools": tool_names,
        "expected_tools": expected_tools,
        "schemas_present": schemas_present,
        "calls": {
            "knowledge_search": content_text(search),
            "add_numbers": content_text(addition),
            "workflow_status": content_text(workflow),
        },
        "errors": {
            "invalid_arguments_is_error": invalid.isError,
            "unknown_tool_is_error": unknown.isError,
        },
        "passed": tool_names == expected_tools and schemas_present and calls_passed and errors_passed,
    }
    results = Path("/results")
    results.mkdir(parents=True, exist_ok=True)
    (results / "result.json").write_text(json.dumps(evidence, indent=2), encoding="utf-8")
    if not evidence["passed"]:
        raise RuntimeError("Spike-004 acceptance criteria failed")


if __name__ == "__main__":
    asyncio.run(run())
