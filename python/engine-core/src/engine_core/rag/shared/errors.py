"""Stable RAG API failures."""

from engine_core.shared.api_error import EngineApiError


class RagError(EngineApiError):
    """RAG validation, provider, or integrity failure."""
