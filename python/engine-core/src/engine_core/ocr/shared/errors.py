"""Stable OCR API errors."""

from engine_core.shared.api_error import EngineApiError


class OcrError(EngineApiError):
    """Expected OCR failure mapped to a stable public envelope."""

    pass
