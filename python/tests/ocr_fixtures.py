"""Deterministic OCR raster fixtures."""

from io import BytesIO

from PIL import Image

from engine_core.ocr.infrastructure.deterministic_provider import _GLYPHS


def render_text(text: str, *, scale: int = 4, image_format: str = "PNG") -> bytes:
    """Render the documented 5x7 corpus with fixed character and word spacing."""
    cell_width = 6 * scale
    word_extra = 3 * scale
    width = 2 * scale
    for character in text:
        width += word_extra if character == " " else cell_width
    image = Image.new("L", (width, 9 * scale), 255)
    pixels = image.load()
    assert pixels is not None
    x_offset = scale
    for character in text:
        if character == " ":
            x_offset += word_extra
            continue
        pattern = _GLYPHS[character]
        for y, row in enumerate(pattern):
            for x, value in enumerate(row):
                if value == "1":
                    for dy in range(scale):
                        for dx in range(scale):
                            pixels[x_offset + x * scale + dx, scale + y * scale + dy] = 0
        x_offset += cell_width

    output = BytesIO()
    save_options = {"quality": 95, "subsampling": 0} if image_format == "JPEG" else {}
    image.save(output, format=image_format, **save_options)
    return output.getvalue()


def blank_image(*, width: int = 16, height: int = 16, image_format: str = "PNG") -> bytes:
    """Create a valid blank raster."""
    output = BytesIO()
    Image.new("L", (width, height), 255).save(output, format=image_format)
    return output.getvalue()
