"""Deterministic OpenAI-compatible embedding fixture for offline validation."""

import hashlib
import math
import re
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel

DIMENSIONS = 64
TOKEN_PATTERN = re.compile(r"[a-z0-9]+", re.IGNORECASE)
app = FastAPI(title="Spike-003 deterministic embedding fixture")


class EmbeddingRequest(BaseModel):
    """Subset of the OpenAI embedding request used by the Spike."""

    input: str | list[str]
    model: str


def vectorize(text: str) -> list[float]:
    """Map tokens deterministically to a normalized fixed-width vector."""
    vector = [0.0] * DIMENSIONS
    for token in TOKEN_PATTERN.findall(text.lower()):
        digest = hashlib.sha256(token.encode()).digest()
        bucket = int.from_bytes(digest[:4], "big") % DIMENSIONS
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        vector[bucket] += sign
    norm = math.sqrt(sum(value * value for value in vector)) or 1.0
    return [value / norm for value in vector]


@app.get("/health")
async def health() -> dict[str, str]:
    """Expose fixture readiness."""
    return {"status": "ok"}


@app.post("/v1/embeddings")
async def embeddings(request: EmbeddingRequest) -> dict[str, Any]:
    """Return a protocol-compatible deterministic embedding response."""
    inputs = [request.input] if isinstance(request.input, str) else request.input
    data = [{"object": "embedding", "index": index, "embedding": vectorize(text)} for index, text in enumerate(inputs)]
    return {
        "object": "list",
        "data": data,
        "model": request.model,
        "usage": {"prompt_tokens": sum(len(text.split()) for text in inputs), "total_tokens": 0},
    }
