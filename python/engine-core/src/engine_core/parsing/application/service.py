"""Deterministic document normalization and chunking service."""

from hashlib import sha256
from time import perf_counter

from engine_core.parsing.domain.models import NormalizedDocument, ParsedChunk, ParsedDocument
from engine_core.parsing.infrastructure.input_decoder import DocumentInputDecoder
from engine_core.parsing.shared.errors import DocumentParsingError


class DocumentParsingService:
    """Create bounded traceable chunks from one validated source."""

    name = "openeip-text-parser"
    version = "1.0.0"

    def __init__(self, decoder: DocumentInputDecoder, chunk_size: int, overlap: int, max_chunks: int) -> None:
        if chunk_size < 1 or overlap < 0 or overlap >= chunk_size or max_chunks < 1:
            raise ValueError("Invalid parsing chunk configuration")
        self._decoder = decoder
        self._chunk_size = chunk_size
        self._overlap = overlap
        self._max_chunks = max_chunks

    def parse(self, content: bytes, content_type: str, tenant_id: str, document_id: str) -> ParsedDocument:
        """Decode, normalize, and chunk one document without persistence or event publication."""
        started = perf_counter()
        document = self._decoder.decode(content, content_type)
        normalized_sha = sha256(document.text.encode("utf-8")).hexdigest()
        chunks = self._chunk(document_id, normalized_sha, document)
        idempotency_material = (
            f"{tenant_id}|{document_id}|{document.source_sha256}|{normalized_sha}|{self.version}|"
            f"{self._chunk_size}|{self._overlap}"
        )
        return ParsedDocument(
            document_id=document_id,
            source_type=document.source_type,
            source_sha256=document.source_sha256,
            normalized_text_sha256=normalized_sha,
            char_count=len(document.text),
            chunks=chunks,
            duration_ms=(perf_counter() - started) * 1000,
            idempotency_key=f"parse_{sha256(idempotency_material.encode()).hexdigest()[:48]}",
            parser_name=self.name,
            parser_version=self.version,
            chunk_size=self._chunk_size,
            overlap=self._overlap,
        )

    def _chunk(self, document_id: str, normalized_sha: str, document: NormalizedDocument) -> tuple[ParsedChunk, ...]:
        chunks: list[ParsedChunk] = []
        start = 0
        while start < len(document.text):
            if len(chunks) >= self._max_chunks:
                raise DocumentParsingError("DOC-V-010", "Document exceeds the configured chunk limit", 413)
            end = _preferred_end(document.text, start, self._chunk_size)
            chunk_text = document.text[start:end]
            chunk_sha = sha256(chunk_text.encode("utf-8")).hexdigest()
            index = len(chunks)
            chunk_material = f"{document_id}|{normalized_sha}|{self.version}|{index}|{start}|{end}|{chunk_sha}"
            chunks.append(
                ParsedChunk(
                    chunk_id=f"chk_{sha256(chunk_material.encode()).hexdigest()[:32]}",
                    index=index,
                    text=chunk_text,
                    start_char=start,
                    end_char=end,
                    pages=_pages_for(document, start, end),
                    sha256=chunk_sha,
                )
            )
            if end == len(document.text):
                break
            start = max(start + 1, end - self._overlap)
        return tuple(chunks)


def _preferred_end(text: str, start: int, chunk_size: int) -> int:
    maximum = min(len(text), start + chunk_size)
    if maximum == len(text):
        return maximum
    minimum = start + max(1, chunk_size // 2)
    window = text[start:maximum]
    candidates = [
        window.rfind("\n\n") + 2,
        window.rfind("\n") + 1,
        max(window.rfind(mark) + 1 for mark in ".!?。！？"),
        max(window.rfind(mark) + 1 for mark in " \t"),
    ]
    valid = [start + candidate for candidate in candidates if candidate > 0 and start + candidate >= minimum]
    return max(valid, default=maximum)


def _pages_for(document: NormalizedDocument, start: int, end: int) -> tuple[int, ...]:
    pages = sorted({span.page for span in document.page_spans if start < span.end and end > span.start})
    if not pages:
        pages = [1]
    return tuple(pages)
