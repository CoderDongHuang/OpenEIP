import sys
from contextlib import asynccontextmanager
from types import ModuleType, SimpleNamespace

import pytest

from engine_core.agent.infrastructure.mcp_adapter import McpStdioAdapter
from engine_core.agent.shared.errors import AgentError


@pytest.mark.asyncio
async def test_mcp_adapter_uses_official_lifecycle_and_explicit_mapping(monkeypatch: pytest.MonkeyPatch) -> None:
    events: list[object] = []
    install_fake_mcp(monkeypatch, events)
    adapter = McpStdioAdapter("python", ["server.py"], {"document.inspect": "remote.inspect"})

    result = await adapter.call("document.inspect", {"text": "safe"})

    assert result == "safe result"
    assert events == [
        ("parameters", "python", ("server.py",)),
        "transport.enter",
        ("session.enter", "read", "write"),
        "initialize",
        "list_tools",
        ("call_tool", "remote.inspect", {"text": "safe"}),
        "session.exit",
        "transport.exit",
    ]


@pytest.mark.asyncio
@pytest.mark.parametrize("mode", ["missing", "error", "empty", "oversized", "exception"])
async def test_mcp_adapter_fails_closed_without_sdk_or_server_details(
    monkeypatch: pytest.MonkeyPatch, mode: str
) -> None:
    install_fake_mcp(monkeypatch, [], mode)
    adapter = McpStdioAdapter("python", [], {"document.inspect": "remote.inspect"})

    with pytest.raises(AgentError, match="Agent execution failed") as captured:
        await adapter.call("document.inspect", {"secret": "credential"})

    assert captured.value.code == "AGENT-S-001"
    assert "credential" not in str(captured.value)


@pytest.mark.asyncio
async def test_mcp_adapter_rejects_unmapped_tool_before_transport(monkeypatch: pytest.MonkeyPatch) -> None:
    events: list[object] = []
    install_fake_mcp(monkeypatch, events)
    adapter = McpStdioAdapter("python", [], {"document.inspect": "remote.inspect"})

    with pytest.raises(AgentError, match="Agent execution failed"):
        await adapter.call("knowledge.search", {})

    assert events == []
    with pytest.raises(ValueError, match="explicit tool mapping"):
        McpStdioAdapter("", [], {})


def install_fake_mcp(monkeypatch: pytest.MonkeyPatch, events: list[object], mode: str = "success") -> None:
    class FakeParameters:
        def __init__(self, command: str, args: list[str]) -> None:
            events.append(("parameters", command, tuple(args)))

    class FakeSession:
        def __init__(self, read_stream: object, write_stream: object) -> None:
            self._streams = (read_stream, write_stream)

        async def __aenter__(self) -> "FakeSession":
            events.append(("session.enter", *self._streams))
            return self

        async def __aexit__(self, *args: object) -> None:
            events.append("session.exit")

        async def initialize(self) -> None:
            events.append("initialize")
            if mode == "exception":
                raise RuntimeError("SDK credential traceback")

        async def list_tools(self) -> object:
            events.append("list_tools")
            tools = [] if mode == "missing" else [SimpleNamespace(name="remote.inspect")]
            return SimpleNamespace(tools=tools)

        async def call_tool(self, name: str, arguments: dict[str, object]) -> object:
            events.append(("call_tool", name, arguments))
            content = [SimpleNamespace(text="safe"), SimpleNamespace(text=" result")]
            if mode == "empty":
                content = []
            elif mode == "oversized":
                content = [SimpleNamespace(text="x" * 8001)]
            return SimpleNamespace(isError=mode == "error", content=content)

    @asynccontextmanager
    async def fake_stdio_client(parameters: object):
        del parameters
        events.append("transport.enter")
        try:
            yield "read", "write"
        finally:
            events.append("transport.exit")

    mcp = ModuleType("mcp")
    mcp.ClientSession = FakeSession  # type: ignore[attr-defined]
    mcp.StdioServerParameters = FakeParameters  # type: ignore[attr-defined]
    client = ModuleType("mcp.client")
    stdio = ModuleType("mcp.client.stdio")
    stdio.stdio_client = fake_stdio_client  # type: ignore[attr-defined]
    monkeypatch.setitem(sys.modules, "mcp", mcp)
    monkeypatch.setitem(sys.modules, "mcp.client", client)
    monkeypatch.setitem(sys.modules, "mcp.client.stdio", stdio)
