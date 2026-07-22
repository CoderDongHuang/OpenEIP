"""Language-neutral OCR domain models."""

from dataclasses import dataclass
from typing import Protocol

from PIL.Image import Image


@dataclass(frozen=True)
class BoundingBox:
    """Pixel coordinates for one recognized block."""

    x: int
    y: int
    width: int
    height: int


@dataclass(frozen=True)
class OcrBlock:
    """Recognized text and confidence for one page block."""

    page: int
    text: str
    confidence: float
    bounding_box: BoundingBox


@dataclass(frozen=True)
class ProviderResult:
    """Provider-owned portion of an OCR result."""

    text: str
    blocks: tuple[OcrBlock, ...]
    confidence: float


@dataclass(frozen=True)
class DecodedImage:
    """Validated single-page raster plus source metadata."""

    image: Image
    content_sha256: str
    media_type: str


@dataclass(frozen=True)
class OcrResult:
    """Stable result returned by the OCR application service."""

    text: str
    blocks: tuple[OcrBlock, ...]
    confidence: float
    duration_ms: float
    content_sha256: str
    provider_name: str
    provider_version: str


class OcrProvider(Protocol):
    """Internal provider port; this is not a public plugin SPI."""

    name: str
    version: str

    def recognize(self, image: Image) -> ProviderResult:
        """Recognize text from one validated raster image."""
        ...
