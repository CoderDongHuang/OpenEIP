import hashlib
import json

import pytest
from parsing_fixtures import ocr_result

from engine_core.parsing.domain.models import SourceType
from engine_core.parsing.infrastructure.input_decoder import (
    OCR_MEDIA_TYPE,
    DocumentInputDecoder,
)
from engine_core.parsing.shared.errors import DocumentParsingError


@pytest.fixture
def decoder() -> DocumentInputDecoder:
    return DocumentInputDecoder(max_body_bytes=4096)


def test_plain_text_is_strictly_normalized_and_hashed(decoder: DocumentInputDecoder) -> None:
    content = "  Cafe\u0301  \r\nsecond\t \r\n".encode()

    result = decoder.decode(content, "text/plain; charset=UTF-8")

    assert result.source_type is SourceType.TEXT
    assert result.text == "  Café\nsecond"
    assert result.source_sha256 == hashlib.sha256(content).hexdigest()
    assert result.page_spans[0].start == 0
    assert result.page_spans[0].end == len(result.text)


@pytest.mark.parametrize(
    ("content", "content_type", "code", "status"),
    [
        (b"", "text/plain", "DOC-V-001", 400),
        (b"  \n", "text/plain", "DOC-V-001", 400),
        (b"\xff", "text/plain", "DOC-V-006", 400),
        (b"\xef\xbb\xbfhello", "text/plain", "DOC-V-006", 400),
        (b"hello\x00world", "text/plain", "DOC-V-007", 400),
        (b"hello", "text/plain; charset=latin-1", "DOC-V-003", 415),
        (b"hello", "application/octet-stream", "DOC-V-004", 415),
        (b"hello", "text/plain; boundary=unexpected", "DOC-V-004", 415),
        (b"hello", "text/plain; charset=utf-8; charset=utf-8", "DOC-V-004", 415),
    ],
)
def test_invalid_text_inputs_return_stable_errors(
    decoder: DocumentInputDecoder, content: bytes, content_type: str, code: str, status: int
) -> None:
    with pytest.raises(DocumentParsingError) as captured:
        decoder.decode(content, content_type)

    assert captured.value.code == code
    assert captured.value.status_code == status


def test_body_limit_is_enforced_by_decoder() -> None:
    decoder = DocumentInputDecoder(max_body_bytes=4)

    with pytest.raises(DocumentParsingError) as captured:
        decoder.decode(b"hello", "text/plain")

    assert captured.value.code == "DOC-V-002"
    assert captured.value.status_code == 413


def test_ocr_result_is_validated_flattened_and_page_mapped(decoder: DocumentInputDecoder) -> None:
    result = decoder.decode(ocr_result("FIRST  ", "SECOND"), OCR_MEDIA_TYPE)

    assert result.source_type is SourceType.OCR
    assert result.source_sha256 == "a" * 64
    assert result.text == "FIRST\nSECOND"
    assert len(result.page_spans) == 2
    assert [span.page for span in result.page_spans] == [1, 1]
    assert result.page_spans[1].start == 6


def test_ocr_top_level_text_must_match_blocks(decoder: DocumentInputDecoder) -> None:
    body = json.loads(ocr_result("FIRST").decode())
    body["text"] = "DIFFERENT"

    with pytest.raises(DocumentParsingError) as captured:
        decoder.decode(json.dumps(body).encode(), OCR_MEDIA_TYPE)

    assert captured.value.code == "DOC-V-005"


def test_ocr_duplicate_key_and_extra_field_are_rejected(decoder: DocumentInputDecoder) -> None:
    valid = ocr_result("OCR").decode()
    duplicate = valid[:-1] + ',"text":"OCR"}'
    extra = json.loads(valid)
    extra["unexpected"] = True

    for body in (duplicate.encode(), json.dumps(extra).encode()):
        with pytest.raises(DocumentParsingError) as captured:
            decoder.decode(body, OCR_MEDIA_TYPE)
        assert captured.value.code == "DOC-V-005"


def test_ocr_media_type_parameters_are_rejected(decoder: DocumentInputDecoder) -> None:
    with pytest.raises(DocumentParsingError) as captured:
        decoder.decode(ocr_result("OCR"), f"{OCR_MEDIA_TYPE}; charset=utf-8")

    assert captured.value.code == "DOC-V-004"


def test_blank_ocr_result_is_not_a_parseable_document(decoder: DocumentInputDecoder) -> None:
    with pytest.raises(DocumentParsingError) as captured:
        decoder.decode(ocr_result(), OCR_MEDIA_TYPE)

    assert captured.value.code == "DOC-V-001"
