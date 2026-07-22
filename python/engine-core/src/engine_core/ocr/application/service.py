"""OCR orchestration service."""

from time import perf_counter

from engine_core.ocr.domain.models import OcrProvider, OcrResult
from engine_core.ocr.infrastructure.image_decoder import SafeImageDecoder
from engine_core.ocr.shared.errors import OcrError


class OcrService:
    """Validate one image and normalize provider output."""

    def __init__(self, decoder: SafeImageDecoder, provider: OcrProvider) -> None:
        self._decoder = decoder
        self._provider = provider

    def recognize(self, content: bytes, media_type: str) -> OcrResult:
        """Decode and recognize one image without persisting caller or content data."""
        decoded = self._decoder.decode(content, media_type)
        started = perf_counter()
        try:
            provider_result = self._provider.recognize(decoded.image)
        except Exception as error:
            raise OcrError("OCR-S-001", "OCR provider failed", 500) from error
        duration_ms = (perf_counter() - started) * 1000
        return OcrResult(
            text=provider_result.text,
            blocks=provider_result.blocks,
            confidence=provider_result.confidence,
            duration_ms=duration_ms,
            content_sha256=decoded.content_sha256,
            provider_name=self._provider.name,
            provider_version=self._provider.version,
        )
