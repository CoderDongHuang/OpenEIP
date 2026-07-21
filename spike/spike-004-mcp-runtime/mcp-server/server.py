"""Official MCP SDK server used by Spike-004."""

from mcp.server.fastmcp import FastMCP

mcp = FastMCP("OpenEIP Spike MCP Server")


@mcp.tool()
def knowledge_search(query: str, limit: int = 3) -> dict[str, object]:
    """Search the deterministic Spike knowledge fixture."""
    if not 1 <= limit <= 10:
        raise ValueError("limit must be between 1 and 10")
    return {
        "query": query,
        "matches": [f"OpenEIP result {index}: {query}" for index in range(1, limit + 1)],
    }


@mcp.tool()
def add_numbers(left: float, right: float) -> float:
    """Add two numbers to verify typed tool arguments."""
    return left + right


@mcp.tool()
def workflow_status(workflow_id: str) -> dict[str, str]:
    """Return deterministic workflow state."""
    return {"workflow_id": workflow_id, "status": "completed"}


if __name__ == "__main__":
    mcp.run(transport="stdio")
