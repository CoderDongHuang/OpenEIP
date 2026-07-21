"""Strict plain-text and OCR-result input decoding."""

import json
import unicodedata
from hashlib import sha256
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from engine_core.parsing.domain.models import NormalizedDocument, PageSpan, SourceType
from engine_core.parsing.shared.errors import DocumentParsingError

TEXT_MEDIA_TYPE = "text/plain"
OCR_MEDIA_TYPE = "application/vnd.openeip.ocr-result.v1+json"


class _DuplicateKeyError(ValueError):
    pass


class _BoundingBoxModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    x: int = Field(ge=0)
    y: int = Field(ge=0)
    width: int = Field(ge=1)
    height: int = Field(ge=1)


class _OcrBlockModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    page: Literal[1]
    text: str
    confidence: float = Field(ge=0, le=1)
    bounding_box: _BoundingBoxModel = Field(alias="boundingBox")


class _ProviderModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    name: str = Field(min_length=1)
    version: str = Field(min_length=1)
    mode: Literal["deterministic-mvp"]


class _OcrResultModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    text: str
    blocks: list[_OcrBlockModel]
    page_count: Literal[1] = Field(alias="pageCount")
    confidence: float = Field(ge=0, le=1)
    duration_ms: float = Field(alias="durationMs", ge=0)
    content_sha256: str = Field(alias="contentSha256", pattern=r"^[a-f0-9]{64}$")
    provider: _ProviderModel


class DocumentInputDecoder:
    """Decode and normalize one supported source without filename inference."""

    def __init__(self, max_body_bytes: int) -> None:
        self._max_body_bytes = max_body_bytes

    def decode(self, content: bytes, content_type: str) -> NormalizedDocument:
        """Decode the selected versioned media type with strict resource and encoding rules."""
        if not content:
            raise DocumentParsingError("DOC-V-001", "Document body must not be empty", 400)
        if len(content) > self._max_body_bytes:
            raise DocumentParsingError("DOC-V-002", "Document body exceeds the configured limit", 413)
        media_type, parameters = _parse_content_type(content_type)
        if media_type == TEXT_MEDIA_TYPE:
            if set(parameters) - {"charset"}:
                raise DocumentParsingError("DOC-V-004", "Unsupported text Content-Type parameter", 415)
            charset = parameters.get("charset", "utf-8").lower()
            if charset not in {"utf-8", "utf8"}:
                raise DocumentParsingError("DOC-V-003", "Plain text must use UTF-8", 415)
            return self._decode_text(content)
        if media_type == OCR_MEDIA_TYPE:
            if parameters:
                raise DocumentParsingError("DOC-V-004", "OCR media type does not accept parameters", 415)
            return self._decode_ocr(content)
        raise DocumentParsingError("DOC-V-004", "Unsupported document media type", 415)

    def _decode_text(self, content: bytes) -> NormalizedDocument:
        text = _strict_utf8(content)
        normalized = _normalize_text(text)
        return NormalizedDocument(
            source_type=SourceType.TEXT,
            source_sha256=sha256(content).hexdigest(),
            text=normalized,
            page_spans=(PageSpan(0, len(normalized), 1),),
        )

    def _decode_ocr(self, content: bytes) -> NormalizedDocument:
        raw_text = _strict_utf8(content)
        try:
            parsed = json.loads(raw_text, object_pairs_hook=_without_duplicate_keys)
            result = _OcrResultModel.model_validate(parsed)
        except (_DuplicateKeyError, json.JSONDecodeError, ValidationError) as error:
            raise DocumentParsingError("DOC-V-005", "OCR result does not match v1 contract", 400) from error

        expected_text = "\n".join(block.text for block in result.blocks)
        if result.text != expected_text:
            raise DocumentParsingError("DOC-V-005", "OCR text and blocks are inconsistent", 400)

        parts: list[str] = []
        spans: list[PageSpan] = []
        offset = 0
        for block in result.blocks:
            normalized_block = _normalize_text(block.text, allow_empty=True)
            if not normalized_block:
                continue
            if parts:
                parts.append("\n")
                offset += 1
            parts.append(normalized_block)
            spans.append(PageSpan(offset, offset + len(normalized_block), block.page))
            offset += len(normalized_block)
        normalized = "".join(parts)
        if not normalized:
            raise DocumentParsingError("DOC-V-001", "Document text must not be blank", 400)
        return NormalizedDocument(
            source_type=SourceType.OCR,
            source_sha256=result.content_sha256,
            text=normalized,
            page_spans=tuple(spans),
        )


def _parse_content_type(value: str) -> tuple[str, dict[str, str]]:
    pieces = [piece.strip() for piece in value.split(";")]
    media_type = pieces[0].lower()
    parameters: dict[str, str] = {}
    for piece in pieces[1:]:
        name, separator, parameter_value = piece.partition("=")
        if not separator or not name.strip() or name.strip().lower() in parameters:
            raise DocumentParsingError("DOC-V-004", "Invalid Content-Type parameters", 415)
        parameters[name.strip().lower()] = parameter_value.strip().strip('"')
    return media_type, parameters


def _strict_utf8(content: bytes) -> str:
    if content.startswith(b"\xef\xbb\xbf"):
        raise DocumentParsingError("DOC-V-006", "UTF-8 BOM is not accepted", 400)
    try:
        return content.decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        raise DocumentParsingError("DOC-V-006", "Document is not valid UTF-8", 400) from error


def _normalize_text(text: str, *, allow_empty: bool = False) -> str:
    normalized = unicodedata.normalize("NFC", text.replace("\r\n", "\n").replace("\r", "\n"))
    for character in normalized:
        if unicodedata.category(character) == "Cc" and character not in {"\n", "\t"}:
            raise DocumentParsingError("DOC-V-007", "Document contains disallowed control characters", 400)
    normalized = "\n".join(line.rstrip(" \t") for line in normalized.split("\n")).strip("\n")
    if not allow_empty and not normalized.strip():
        raise DocumentParsingError("DOC-V-001", "Document text must not be blank", 400)
    return normalized


def _without_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise _DuplicateKeyError(key)
        result[key] = value
    return result
