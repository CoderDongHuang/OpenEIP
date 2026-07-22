from io import BytesIO

from ocr_fixtures import blank_image, render_text
from PIL import Image

from engine_core.ocr.infrastructure.deterministic_provider import DeterministicRasterProvider


def test_provider_recognizes_documented_corpus() -> None:
    provider = DeterministicRasterProvider()
    image = Image.open(BytesIO(render_text("OCR 2026"))).convert("L")

    result = provider.recognize(image)

    assert result.text == "OCR 2026"
    assert result.confidence >= 0.95
    assert result.blocks[0].page == 1
    assert result.blocks[0].bounding_box.width > 0


def test_provider_recognizes_jpeg_container_pixels() -> None:
    provider = DeterministicRasterProvider()
    image = Image.open(BytesIO(render_text("MVP-1", image_format="JPEG"))).convert("L")

    result = provider.recognize(image)

    assert result.text == "MVP-1"
    assert result.confidence >= 0.85


def test_provider_returns_valid_empty_result_for_blank_image() -> None:
    provider = DeterministicRasterProvider()
    image = Image.open(BytesIO(blank_image())).convert("L")

    result = provider.recognize(image)

    assert result.text == ""
    assert result.blocks == ()
    assert result.confidence == 0.0


def test_provider_marks_low_confidence_pixels_as_unknown() -> None:
    provider = DeterministicRasterProvider()
    image = Image.new("L", (16, 16), 255)
    pixels = image.load()
    assert pixels is not None
    for y in range(3, 12):
        for x in range(3, 12):
            pixels[x, y] = 0

    result = provider.recognize(image)

    assert "?" in result.text
