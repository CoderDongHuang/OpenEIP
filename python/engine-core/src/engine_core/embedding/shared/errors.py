"""Stable embedding API failures."""

from engine_core.shared.api_error import EngineApiError


class EmbeddingError(EngineApiError):
    """Embedding validation, conflict, or provider failure."""
