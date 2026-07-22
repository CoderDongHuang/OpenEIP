"""Stable document parsing API errors."""

from engine_core.shared.api_error import EngineApiError


class DocumentParsingError(EngineApiError):
    """Expected parsing failure safe for the internal API."""

    pass
