"""Stable Chat API failures."""

from engine_core.shared.api_error import EngineApiError


class ChatError(EngineApiError):
    """Chat validation or authentication failure."""
