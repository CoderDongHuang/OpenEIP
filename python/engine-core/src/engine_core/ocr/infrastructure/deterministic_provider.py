"""Deterministic 5x7 raster OCR provider for the bounded MVP corpus."""

from collections.abc import Iterable
from statistics import fmean
from typing import cast

from PIL import Image

from engine_core.ocr.domain.models import BoundingBox, OcrBlock, ProviderResult

_GLYPHS = {
    "A": ("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    "B": ("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
    "C": ("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
    "D": ("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    "E": ("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    "F": ("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    "G": ("01111", "10000", "10000", "10111", "10001", "10001", "01111"),
    "H": ("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    "I": ("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
    "J": ("00111", "00010", "00010", "00010", "10010", "10010", "01100"),
    "K": ("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    "L": ("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    "M": ("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    "N": ("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
    "O": ("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    "P": ("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    "Q": ("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
    "R": ("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    "S": ("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    "T": ("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    "U": ("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    "V": ("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
    "W": ("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
    "X": ("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
    "Y": ("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
    "Z": ("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
    "0": ("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    "1": ("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    "2": ("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    "3": ("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
    "4": ("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    "5": ("11111", "10000", "10000", "11110", "00001", "00001", "11110"),
    "6": ("01110", "10000", "10000", "11110", "10001", "10001", "01110"),
    "7": ("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    "8": ("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    "9": ("01110", "10001", "10001", "01111", "00001", "00001", "01110"),
    "-": ("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
    ".": ("00000", "00000", "00000", "00000", "00000", "00110", "00110"),
    "/": ("00001", "00010", "00010", "00100", "01000", "01000", "10000"),
    ":": ("00000", "00110", "00110", "00000", "00110", "00110", "00000"),
}


class DeterministicRasterProvider:
    """Recognize a constrained, high-contrast 5x7 corpus without model downloads."""

    name = "deterministic-raster"
    version = "1.0.0"

    def recognize(self, image: Image.Image) -> ProviderResult:
        """Match connected raster glyphs against the documented alphabet."""
        ink = image.point(lambda value: 255 if value < 128 else 0, mode="1")
        image_box = ink.getbbox()
        if image_box is None:
            return ProviderResult(text="", blocks=(), confidence=0.0)

        lines: list[OcrBlock] = []
        for y_start, y_end in _runs(_occupied_rows(ink)):
            line_box = _line_box(ink, y_start, y_end)
            line_text, confidence = self._recognize_line(ink, line_box)
            lines.append(
                OcrBlock(
                    page=1,
                    text=line_text,
                    confidence=confidence,
                    bounding_box=BoundingBox(
                        x=line_box[0],
                        y=line_box[1],
                        width=line_box[2] - line_box[0],
                        height=line_box[3] - line_box[1],
                    ),
                )
            )

        return ProviderResult(
            text="\n".join(block.text for block in lines),
            blocks=tuple(lines),
            confidence=fmean(block.confidence for block in lines),
        )

    def _recognize_line(self, image: Image.Image, line_box: tuple[int, int, int, int]) -> tuple[str, float]:
        x_start, y_start, x_end, y_end = line_box
        column_runs = list(_runs(_occupied_columns(image, x_start, x_end, y_start, y_end)))
        if not column_runs:
            return "", 0.0

        line_height = y_end - y_start
        scale = max(1.0, line_height / 7.0)
        characters: list[str] = []
        confidences: list[float] = []
        previous_end: int | None = None
        for relative_start, relative_end in column_runs:
            glyph_start = x_start + relative_start
            glyph_end = x_start + relative_end
            if previous_end is not None and glyph_start - previous_end >= round(scale * 3):
                characters.append(" ")
            glyph = image.crop((glyph_start, y_start, glyph_end, y_end))
            character, confidence = _match_glyph(glyph, line_height)
            characters.append(character if confidence >= 0.72 else "?")
            confidences.append(confidence)
            previous_end = glyph_end
        return "".join(characters), fmean(confidences) if confidences else 0.0


def _occupied_rows(image: Image.Image) -> list[bool]:
    width, height = image.size
    pixels = image.load()
    assert pixels is not None
    return [any(pixels[x, y] for x in range(width)) for y in range(height)]


def _occupied_columns(image: Image.Image, x_start: int, x_end: int, y_start: int, y_end: int) -> list[bool]:
    pixels = image.load()
    assert pixels is not None
    return [any(pixels[x, y] for y in range(y_start, y_end)) for x in range(x_start, x_end)]


def _runs(values: Iterable[bool]) -> Iterable[tuple[int, int]]:
    start: int | None = None
    for index, occupied in enumerate(values):
        if occupied and start is None:
            start = index
        elif not occupied and start is not None:
            yield start, index
            start = None
    if start is not None:
        yield start, index + 1


def _line_box(image: Image.Image, y_start: int, y_end: int) -> tuple[int, int, int, int]:
    cropped = image.crop((0, y_start, image.width, y_end))
    box = cropped.getbbox()
    if box is None:
        return 0, y_start, 1, y_end
    return box[0], y_start, box[2], y_end


def _match_glyph(glyph: Image.Image, line_height: int) -> tuple[str, float]:
    glyph_box = glyph.getbbox()
    if glyph_box is None:
        return "?", 0.0
    glyph = glyph.crop(glyph_box)
    normalized = glyph.resize((25, 35), Image.Resampling.NEAREST).convert("1")
    normalized_pixels = tuple(cast(Iterable[int], normalized.get_flattened_data()))
    best_character = "?"
    best_score = 0.0
    for character, candidate_pixels, expected_width, expected_height in _TEMPLATES:
        differing = sum(left != right for left, right in zip(normalized_pixels, candidate_pixels, strict=True))
        pixel_score = 1.0 - differing / (25 * 35)
        observed_width = glyph.width / line_height
        observed_height = glyph.height / line_height
        width_score = min(observed_width, expected_width) / max(observed_width, expected_width)
        height_score = min(observed_height, expected_height) / max(observed_height, expected_height)
        score = pixel_score * width_score * height_score
        if score > best_score:
            best_character = character
            best_score = score
    return best_character, round(best_score, 6)


def _pattern_image(pattern: tuple[str, ...]) -> Image.Image:
    image = Image.new("1", (5, 7), 0)
    pixels = image.load()
    assert pixels is not None
    for y, row in enumerate(pattern):
        for x, value in enumerate(row):
            if value == "1":
                pixels[x, y] = 255
    return image


def _build_templates() -> tuple[tuple[str, tuple[int, ...], float, float], ...]:
    templates: list[tuple[str, tuple[int, ...], float, float]] = []
    for character, pattern in _GLYPHS.items():
        template = _pattern_image(pattern)
        template_box = template.getbbox()
        if template_box is None:
            continue
        trimmed = template.crop(template_box)
        normalized = trimmed.resize((25, 35), Image.Resampling.NEAREST).convert("1")
        templates.append(
            (
                character,
                tuple(cast(Iterable[int], normalized.get_flattened_data())),
                trimmed.width / 7,
                trimmed.height / 7,
            )
        )
    return tuple(templates)


_TEMPLATES = _build_templates()
