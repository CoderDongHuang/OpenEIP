from io import BytesIO

import pytest
from ocr_fixtures import render_text
from PIL import Image

from engine_core.ocr.application.service import OcrService
from engine_core.ocr.domain.models import ProviderResult
from engine_core.ocr.infrastructure.deterministic_provider import DeterministicRasterProvider
from engine_core.ocr.infrastructure.image_decoder import SafeImageDecoder
from engine_core.ocr.shared.errors import OcrError


def _decoder() -> SafeImageDecoder:
    return SafeImageDecoder(max_body_bytes=4096, max_width=200, max_height=200, max_pixels=40_000)


def test_service_normalizes_provider_metadata() -> None:
    service = OcrService(_decoder(), DeterministicRasterProvider())

    result = service.recognize(render_text("OCR"), "image/png")

    assert result.text == "OCR"
    assert result.provider_name == "deterministic-raster"
    assert result.provider_version == "1.0.0"
    assert result.duration_ms >= 0
    assert len(result.content_sha256) == 64


def test_service_maps_provider_failure_without_details() -> None:
    class FailingProvider:
        name = "failing"
        version = "1"

        def recognize(self, image: Image.Image) -> ProviderResult:
            raise RuntimeError("secret provider details")

    service = OcrService(_decoder(), FailingProvider())

    with pytest.raises(OcrError) as captured:
        service.recognize(render_text("OCR"), "image/png")

    assert captured.value.code == "OCR-S-001"
    assert str(captured.value) == "OCR provider failed"


def test_fixture_image_is_decodable_by_pillow() -> None:
    image = Image.open(BytesIO(render_text("A")))

    assert image.format == "PNG"
