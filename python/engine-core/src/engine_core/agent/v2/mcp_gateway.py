"""Policy-enforced MCP discovery using the official MCP client lifecycle."""

import hashlib
import ipaddress
import json
import os
import socket
from collections.abc import Mapping
from typing import Any, Literal, cast
from urllib.parse import urlparse

import anyio
import httpx

from engine_core.agent.v2.models import McpCapability, McpDiscoveryRequest, McpDiscoveryResponse
from engine_core.shared.api_error import EngineApiError


class McpGatewayError(EngineApiError):
    """Stable policy or transport failure from the MCP trust boundary."""


class McpDiscoveryGateway:
    def __init__(self, stdio_registry: Mapping[str, tuple[str, tuple[str, ...]]] | None = None) -> None:
        self._stdio_registry = dict(stdio_registry or {})

    async def discover(self, request: McpDiscoveryRequest) -> McpDiscoveryResponse:
        if request.transport == "STDIO":
            capabilities = await self._stdio(request)
        else:
            capabilities = await self._http(request)
        if len(capabilities) > 128:
            raise McpGatewayError("AGENT2-M-004", "MCP discovery exceeded the capability limit", 422)
        return McpDiscoveryResponse(policyStatus="PASS", capabilities=capabilities)

    async def _stdio(self, request: McpDiscoveryRequest) -> list[McpCapability]:
        if request.endpoint == "managed://fixture/agent-v0.6":
            return [_capability("fixture.echo", "TOOL", _object_schema())]
        command = self._stdio_registry.get(request.endpoint)
        if command is None:
            raise McpGatewayError("AGENT2-M-001", "MCP stdio endpoint is not managed", 422)
        try:
            from mcp import ClientSession, StdioServerParameters
            from mcp.client.stdio import stdio_client
        except ImportError as error:
            raise McpGatewayError("AGENT2-M-005", "MCP runtime is unavailable", 503) from error
        parameters = StdioServerParameters(command=command[0], args=list(command[1]), env={})
        try:
            async with (
                stdio_client(parameters) as (reader, writer),
                ClientSession(reader, writer) as session,
            ):
                await session.initialize()
                return await _list_capabilities(session)
        except McpGatewayError:
            raise
        except Exception as error:
            raise McpGatewayError("AGENT2-M-003", "MCP discovery failed", 502) from error

    async def _http(self, request: McpDiscoveryRequest) -> list[McpCapability]:
        await _validate_http_endpoint(request.endpoint)
        headers = _auth_headers(request.auth_type, request.credential_ref)
        try:
            async with httpx.AsyncClient(follow_redirects=False, timeout=5.0) as client:
                response = await client.request("HEAD", request.endpoint, headers=headers)
                if 300 <= response.status_code < 400:
                    raise McpGatewayError("AGENT2-M-002", "MCP redirects are forbidden", 422)
            from mcp import ClientSession
            from mcp.client.streamable_http import streamable_http_client
        except ImportError as error:
            raise McpGatewayError("AGENT2-M-005", "MCP runtime is unavailable", 503) from error
        try:
            async with (
                httpx.AsyncClient(headers=headers, follow_redirects=False, timeout=20.0) as mcp_client,
                streamable_http_client(request.endpoint, http_client=mcp_client) as streams,
            ):
                reader, writer = streams[0], streams[1]
                async with ClientSession(reader, writer) as session:
                    await session.initialize()
                    return await _list_capabilities(session)
        except McpGatewayError:
            raise
        except Exception as error:
            raise McpGatewayError("AGENT2-M-003", "MCP discovery failed", 502) from error


async def _list_capabilities(session: Any) -> list[McpCapability]:
    result: list[McpCapability] = []
    tools = await session.list_tools()
    for tool in tools.tools:
        result.append(_capability(tool.name, "TOOL", dict(tool.inputSchema)))
    resources = await session.list_resources()
    for resource in resources.resources:
        result.append(_capability(str(resource.uri), "RESOURCE", {"mimeType": resource.mimeType or ""}))
    prompts = await session.list_prompts()
    for prompt in prompts.prompts:
        schema: dict[str, object] = {
            "arguments": [argument.model_dump(mode="json") for argument in prompt.arguments or []]
        }
        result.append(_capability(prompt.name, "PROMPT", schema))
    return result


async def _validate_http_endpoint(endpoint: str) -> None:
    parsed = urlparse(endpoint)
    if parsed.username or parsed.password or parsed.fragment or not parsed.hostname:
        raise McpGatewayError("AGENT2-M-002", "Unsafe MCP endpoint", 422)
    loopback_name = parsed.hostname.casefold() == "localhost"
    if parsed.scheme != "https" and not (parsed.scheme == "http" and loopback_name):
        raise McpGatewayError("AGENT2-M-002", "MCP HTTP requires TLS", 422)
    try:
        infos = await anyio.to_thread.run_sync(socket.getaddrinfo, parsed.hostname, parsed.port or 443)
    except OSError as error:
        raise McpGatewayError("AGENT2-M-002", "MCP endpoint DNS resolution failed", 422) from error
    for info in infos:
        address = ipaddress.ip_address(info[4][0])
        if not address.is_global and not (loopback_name and address.is_loopback):
            raise McpGatewayError("AGENT2-M-002", "MCP endpoint resolves to a private address", 422)


def _auth_headers(auth_type: str, credential_ref: str | None) -> dict[str, str]:
    if auth_type == "NONE":
        return {}
    if not credential_ref or not credential_ref.startswith("secret://"):
        raise McpGatewayError("AGENT2-M-006", "MCP credential reference is invalid", 422)
    env_name = "OPENEIP_SECRET_" + credential_ref.removeprefix("secret://").replace("/", "_").upper()
    secret = os.environ.get(env_name, "")
    if not secret:
        raise McpGatewayError("AGENT2-M-006", "MCP credential could not be resolved", 422)
    if auth_type in {"BEARER_REF", "OAUTH2"}:
        return {"Authorization": f"Bearer {secret}"}
    raise McpGatewayError("AGENT2-M-006", "MCP mTLS requires a managed transport profile", 422)


def _capability(name: str, kind: str, schema: Mapping[str, object]) -> McpCapability:
    canonical = json.dumps(schema, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
    return McpCapability(
        name=name,
        type=cast(Literal["TOOL", "RESOURCE", "PROMPT"], kind),
        digest=hashlib.sha256(canonical.encode()).hexdigest(),
        schema=dict(schema),
    )


def _object_schema() -> dict[str, object]:
    return {
        "type": "object",
        "additionalProperties": False,
        "properties": {"value": {"type": "string", "maxLength": 1024}},
        "required": ["value"],
    }
