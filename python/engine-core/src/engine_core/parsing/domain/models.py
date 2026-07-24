"""Traceable document parsing domain models."""

from dataclasses import dataclass
from enum import StrEnum


class SourceType(StrEnum):
    """Supported normalized source contracts."""

    TEXT = "TEXT"
    OCR = "OCR"
    PDF = "PDF"
    DOCX = "DOCX"
    PPTX = "PPTX"
    XLSX = "XLSX"


@dataclass(frozen=True)
class PageSpan:
    """Normalized character span attributed to one source page."""

    start: int
    end: int
    page: int


@dataclass(frozen=True)
class NormalizedDocument:
    """Validated source normalized before chunking."""

    source_type: SourceType
    source_sha256: str
    text: str
    page_spans: tuple[PageSpan, ...]


@dataclass(frozen=True)
class ParsedChunk:
    """One exact slice of normalized document text."""

    chunk_id: str
    index: int
    text: str
    start_char: int
    end_char: int
    pages: tuple[int, ...]
    sha256: str


@dataclass(frozen=True)
class ParsedDocument:
    """Stable synchronous document parsing result."""

    document_id: str
    source_type: SourceType
    source_sha256: str
    normalized_text_sha256: str
    char_count: int
    chunks: tuple[ParsedChunk, ...]
    duration_ms: float
    idempotency_key: str
    parser_name: str
    parser_version: str
    chunk_size: int
    overlap: int
