"""Bounded Pillow image decoder."""

import warnings
from hashlib import sha256
from io import BytesIO

from PIL import Image, ImageFile, ImageOps, UnidentifiedImageError

from engine_core.ocr.domain.models import DecodedImage
from engine_core.ocr.shared.errors import OcrError

ImageFile.LOAD_TRUNCATED_IMAGES = False

_FORMAT_MEDIA_TYPES = {"PNG": "image/png", "JPEG": "image/jpeg"}


class SafeImageDecoder:
    """Decode one raster only after enforcing encoded and decoded resource limits."""

    def __init__(self, max_body_bytes: int, max_width: int, max_height: int, max_pixels: int) -> None:
        self._max_body_bytes = max_body_bytes
        self._max_width = max_width
        self._max_height = max_height
        self._max_pixels = max_pixels

    def decode(self, content: bytes, declared_media_type: str) -> DecodedImage:
        """Verify format, dimensions, frames, and full decoding before returning grayscale pixels."""
        if not content:
            raise OcrError("OCR-V-001", "Image body must not be empty", 400)
        if len(content) > self._max_body_bytes:
            raise OcrError("OCR-V-002", "Image body exceeds the configured limit", 413)
        if declared_media_type not in _FORMAT_MEDIA_TYPES.values():
            raise OcrError("OCR-V-003", "Only image/png and image/jpeg are supported", 415)

        try:
            with warnings.catch_warnings():
                warnings.simplefilter("error", Image.DecompressionBombWarning)
                with Image.open(BytesIO(content)) as source:
                    actual_media_type = _FORMAT_MEDIA_TYPES.get(source.format or "")
                    if actual_media_type is None:
                        raise OcrError("OCR-V-003", "Only PNG and JPEG containers are supported", 415)
                    if actual_media_type != declared_media_type:
                        raise OcrError("OCR-V-004", "Declared media type does not match image container", 415)
                    width, height = source.size
                    self._validate_dimensions(width, height)
                    if getattr(source, "n_frames", 1) != 1:
                        raise OcrError("OCR-V-005", "Animated or multi-frame images are not supported", 400)
                    source.load()
                    oriented = ImageOps.exif_transpose(source)
                    self._validate_dimensions(*oriented.size)
                    grayscale = self._to_grayscale(oriented)
        except OcrError:
            raise
        except (Image.DecompressionBombError, Image.DecompressionBombWarning) as error:
            raise OcrError("OCR-V-007", "Decoded image exceeds the configured pixel limit", 413) from error
        except (OSError, SyntaxError, UnidentifiedImageError, ValueError) as error:
            raise OcrError("OCR-V-006", "Image cannot be decoded", 400) from error

        return DecodedImage(
            image=grayscale,
            content_sha256=sha256(content).hexdigest(),
            media_type=declared_media_type,
        )

    def _validate_dimensions(self, width: int, height: int) -> None:
        if width < 1 or height < 1:
            raise OcrError("OCR-V-006", "Image dimensions are invalid", 400)
        if width > self._max_width or height > self._max_height or width * height > self._max_pixels:
            raise OcrError("OCR-V-007", "Decoded image exceeds the configured pixel limit", 413)

    @staticmethod
    def _to_grayscale(image: Image.Image) -> Image.Image:
        if image.mode in {"RGBA", "LA"} or "transparency" in image.info:
            rgba = image.convert("RGBA")
            background = Image.new("RGBA", rgba.size, "white")
            return Image.alpha_composite(background, rgba).convert("L")
        return image.convert("L")
