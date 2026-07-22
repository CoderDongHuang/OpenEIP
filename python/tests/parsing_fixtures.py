"""Versioned document parsing input fixtures."""

import json


def ocr_result(*texts: str) -> bytes:
    """Build an `ocr-result.v1` body with one page and ordered blocks."""
    blocks = [
        {
            "page": 1,
            "text": text,
            "confidence": 0.99,
            "boundingBox": {"x": index * 10, "y": 0, "width": 8, "height": 7},
        }
        for index, text in enumerate(texts)
    ]
    body = {
        "text": "\n".join(texts),
        "blocks": blocks,
        "pageCount": 1,
        "confidence": 0.99,
        "durationMs": 1.25,
        "contentSha256": "a" * 64,
        "provider": {
            "name": "deterministic-raster",
            "version": "1.0.0",
            "mode": "deterministic-mvp",
        },
    }
    return json.dumps(body, separators=(",", ":")).encode()
