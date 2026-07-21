"""Deterministic normalized provider for local development and CI."""

import math
import re
from hashlib import sha256

TOKEN_PATTERN = re.compile(r"[\w]+", re.UNICODE)


class DeterministicEmbeddingProvider:
    """Hash tokens into a reproducible fixed-width normalized vector."""

    model = "openeip-deterministic-hash"
    version = "1.0.0"

    def __init__(self, dimension: int = 64) -> None:
        if dimension < 8 or dimension > 1536:
            raise ValueError("Embedding dimension must be between 8 and 1536")
        self._dimension = dimension

    @property
    def dimension(self) -> int:
        """Return configured output width."""
        return self._dimension

    def embed(self, texts: tuple[str, ...]) -> tuple[tuple[float, ...], ...]:
        """Hash case-folded tokens and normalize each vector to unit length."""
        return tuple(self._vectorize(text) for text in texts)

    def _vectorize(self, text: str) -> tuple[float, ...]:
        vector = [0.0] * self._dimension
        tokens = TOKEN_PATTERN.findall(text.casefold())
        for token in tokens or [text]:
            digest = sha256(token.encode("utf-8")).digest()
            first = int.from_bytes(digest[:4], "big") % self._dimension
            second = int.from_bytes(digest[5:9], "big") % self._dimension
            vector[first] += 1.0 if digest[4] % 2 == 0 else -1.0
            vector[second] += 0.5 if digest[9] % 2 == 0 else -0.5
        norm = math.sqrt(sum(value * value for value in vector))
        if norm == 0.0:
            vector[0] = 1.0
            norm = 1.0
        return tuple(value / norm for value in vector)
