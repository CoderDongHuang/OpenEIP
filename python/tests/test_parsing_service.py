import hashlib

import pytest
from parsing_fixtures import ocr_result

from engine_core.parsing.application.service import DocumentParsingService
from engine_core.parsing.infrastructure.input_decoder import OCR_MEDIA_TYPE, DocumentInputDecoder
from engine_core.parsing.shared.errors import DocumentParsingError

TENANT_ID = "11111111-1111-4111-8111-111111111111"
DOCUMENT_ID = "33333333-3333-4333-8333-333333333333"


def _service(*, chunk_size: int = 20, overlap: int = 5, max_chunks: int = 100) -> DocumentParsingService:
    return DocumentParsingService(
        decoder=DocumentInputDecoder(max_body_bytes=4096),
        chunk_size=chunk_size,
        overlap=overlap,
        max_chunks=max_chunks,
    )


def test_chunks_are_exact_bounded_traceable_slices() -> None:
    text = "FIRST PARAGRAPH.\n\nSECOND PARAGRAPH HAS MORE WORDS."

    result = _service().parse(text.encode(), "text/plain", TENANT_ID, DOCUMENT_ID)

    assert result.char_count == len(text)
    assert len(result.chunks) > 1
    for index, chunk in enumerate(result.chunks):
        assert chunk.index == index
        assert chunk.text == text[chunk.start_char : chunk.end_char]
        assert len(chunk.text) <= 20
        assert chunk.pages == (1,)
        assert chunk.sha256 == hashlib.sha256(chunk.text.encode()).hexdigest()
        assert chunk.chunk_id.startswith("chk_")
    assert result.chunks[1].start_char <= result.chunks[0].end_char


def test_parsing_is_deterministic_and_tenant_scopes_replay_key() -> None:
    service = _service()
    first = service.parse(b"ONE TWO THREE FOUR FIVE", "text/plain", TENANT_ID, DOCUMENT_ID)
    repeated = service.parse(b"ONE TWO THREE FOUR FIVE", "text/plain", TENANT_ID, DOCUMENT_ID)
    other_tenant = service.parse(
        b"ONE TWO THREE FOUR FIVE",
        "text/plain",
        "44444444-4444-4444-8444-444444444444",
        DOCUMENT_ID,
    )

    assert first.chunks == repeated.chunks
    assert first.normalized_text_sha256 == repeated.normalized_text_sha256
    assert first.idempotency_key == repeated.idempotency_key
    assert first.idempotency_key != other_tenant.idempotency_key


def test_preferred_paragraph_boundary_is_used() -> None:
    text = "A" * 10 + "\n\n" + "B" * 20

    result = _service(chunk_size=20, overlap=2).parse(text.encode(), "text/plain", TENANT_ID, DOCUMENT_ID)

    assert result.chunks[0].end_char == 12


def test_ocr_source_preserves_page_attribution() -> None:
    result = _service(chunk_size=10, overlap=2).parse(
        ocr_result("OCR ONE", "OCR TWO"), OCR_MEDIA_TYPE, TENANT_ID, DOCUMENT_ID
    )

    assert result.source_type.value == "OCR"
    assert all(chunk.pages == (1,) for chunk in result.chunks)


def test_max_chunk_count_fails_before_unbounded_output() -> None:
    with pytest.raises(DocumentParsingError) as captured:
        _service(chunk_size=5, overlap=1, max_chunks=2).parse(
            b"ABCDEFGHIJKLMNOPQRSTUVWXYZ", "text/plain", TENANT_ID, DOCUMENT_ID
        )

    assert captured.value.code == "DOC-V-010"
    assert captured.value.status_code == 413


@pytest.mark.parametrize(
    ("chunk_size", "overlap", "max_chunks"),
    [(0, 0, 1), (10, -1, 1), (10, 10, 1), (10, 0, 0)],
)
def test_invalid_chunk_configuration_is_rejected(chunk_size: int, overlap: int, max_chunks: int) -> None:
    with pytest.raises(ValueError, match="Invalid parsing chunk configuration"):
        _service(chunk_size=chunk_size, overlap=overlap, max_chunks=max_chunks)
