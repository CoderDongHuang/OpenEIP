"""Stable Agent API errors."""

from engine_core.shared.api_error import EngineApiError


class AgentError(EngineApiError):
    """Agent validation or execution failure with a stable public code."""
