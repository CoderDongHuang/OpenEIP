"""Optional official MCP SDK stdio lifecycle adapter.

The adapter is not registered as a v0.2 built-in tool. It exists to make the accepted lifecycle
concrete while remote transport, delegated authentication, and dynamic discovery remain disabled.
"""

from collections.abc import Mapping
from typing import Any

from engine_core.agent.shared.errors import AgentError


class McpStdioAdapter:
    """Use the official MCP SDK lifecycle with an explicit local tool mapping."""

    def __init__(self, command: str, args: list[str], tool_mapping: Mapping[str, str]) -> None:
        if not command or not tool_mapping:
            raise ValueError("MCP command and explicit tool mapping are required")
        self._command = command
        self._args = tuple(args)
        self._tool_mapping = dict(tool_mapping)

    async def call(self, local_name: str, arguments: Mapping[str, object]) -> str:
        """Initialize, discover, authorize the mapping, and invoke one official MCP tool."""
        remote_name = self._tool_mapping.get(local_name)
        if remote_name is None:
            raise AgentError("AGENT-S-001", "Agent execution failed", 503)
        try:
            from mcp import ClientSession, StdioServerParameters  # type: ignore[import-not-found]
            from mcp.client.stdio import stdio_client  # type: ignore[import-not-found]

            parameters = StdioServerParameters(command=self._command, args=list(self._args))
            async with (
                stdio_client(parameters) as (read_stream, write_stream),
                ClientSession(read_stream, write_stream) as session,
            ):
                await session.initialize()
                discovered = await session.list_tools()
                if remote_name not in {tool.name for tool in discovered.tools}:
                    raise AgentError("AGENT-S-001", "Agent execution failed", 503)
                result: Any = await session.call_tool(remote_name, dict(arguments))
                if result.isError:
                    raise AgentError("AGENT-S-001", "Agent execution failed", 503)
                text = "".join(getattr(item, "text", "") for item in result.content)
                if not text or len(text) > 8000:
                    raise AgentError("AGENT-S-001", "Agent execution failed", 503)
                return text
        except AgentError:
            raise
        except Exception as exception:
            raise AgentError("AGENT-S-001", "Agent execution failed", 503) from exception
