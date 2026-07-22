from io import BytesIO

import pytest
from ocr_fixtures import blank_image, render_text
from PIL import Image

from engine_core.ocr.infrastructure.image_decoder import SafeImageDecoder
from engine_core.ocr.shared.errors import OcrError


@pytest.fixture
def decoder() -> SafeImageDecoder:
    return SafeImageDecoder(max_body_bytes=4096, max_width=100, max_height=100, max_pixels=5_000)


def test_decoder_verifies_png_and_digest(decoder: SafeImageDecoder) -> None:
    content = render_text("OCR", scale=2)

    decoded = decoder.decode(content, "image/png")

    assert decoded.image.mode == "L"
    assert decoded.media_type == "image/png"
    assert len(decoded.content_sha256) == 64


@pytest.mark.parametrize(
    ("content", "media_type", "code", "status"),
    [
        (b"", "image/png", "OCR-V-001", 400),
        (b"not-an-image", "image/png", "OCR-V-006", 400),
        (render_text("A"), "image/jpeg", "OCR-V-004", 415),
        (render_text("A"), "application/octet-stream", "OCR-V-003", 415),
    ],
)
def test_decoder_returns_stable_validation_errors(
    decoder: SafeImageDecoder, content: bytes, media_type: str, code: str, status: int
) -> None:
    with pytest.raises(OcrError) as captured:
        decoder.decode(content, media_type)

    assert captured.value.code == code
    assert captured.value.status_code == status


def test_decoder_rejects_encoded_body_limit() -> None:
    decoder = SafeImageDecoder(max_body_bytes=4, max_width=100, max_height=100, max_pixels=10_000)

    with pytest.raises(OcrError, match="configured limit") as captured:
        decoder.decode(blank_image(), "image/png")

    assert captured.value.status_code == 413


def test_decoder_rejects_decoded_pixel_limit() -> None:
    decoder = SafeImageDecoder(max_body_bytes=4096, max_width=100, max_height=100, max_pixels=100)

    with pytest.raises(OcrError) as captured:
        decoder.decode(blank_image(width=20, height=20), "image/png")

    assert captured.value.code == "OCR-V-007"
    assert captured.value.status_code == 413


def test_decoder_maps_pillow_decompression_bomb(monkeypatch, decoder: SafeImageDecoder) -> None:
    def reject_bomb(_content: BytesIO) -> Image.Image:
        raise Image.DecompressionBombError("oversized header")

    monkeypatch.setattr(Image, "open", reject_bomb)

    with pytest.raises(OcrError) as captured:
        decoder.decode(render_text("OCR"), "image/png")

    assert captured.value.code == "OCR-V-007"
    assert captured.value.status_code == 413


def test_decoder_rejects_multiframe_png(decoder: SafeImageDecoder) -> None:
    output = BytesIO()
    first = Image.new("L", (8, 8), 255)
    second = Image.new("L", (8, 8), 0)
    first.save(output, format="PNG", save_all=True, append_images=[second], duration=100, loop=0)

    with pytest.raises(OcrError) as captured:
        decoder.decode(output.getvalue(), "image/png")

    assert captured.value.code == "OCR-V-005"


def test_decoder_composites_transparency_on_white(decoder: SafeImageDecoder) -> None:
    output = BytesIO()
    image = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    image.save(output, format="PNG")

    decoded = decoder.decode(output.getvalue(), "image/png")

    assert decoded.image.getextrema() == (255, 255)
