"""OpenAI-compatible production embedding adapter."""

import math

import httpx


class OpenAIEmbeddingProvider:
    def __init__(self, api_key: str, base_url: str, model: str, dimension: int, timeout_seconds: float = 30.0) -> None:
        if not api_key or not model or dimension < 1:
            raise ValueError("OpenAI embedding configuration is incomplete")
        self._api_key = api_key
        self._url = f"{base_url.rstrip('/')}/embeddings"
        self._model = model
        self._dimension = dimension
        self._timeout = timeout_seconds

    @property
    def model(self) -> str:
        return self._model

    @property
    def version(self) -> str:
        return "openai-compatible-v1"

    @property
    def dimension(self) -> int:
        return self._dimension

    def embed(self, texts: tuple[str, ...]) -> tuple[tuple[float, ...], ...]:
        response = httpx.post(
            self._url,
            headers={"Authorization": f"Bearer {self._api_key}"},
            json={"model": self._model, "input": list(texts), "dimensions": self._dimension},
            timeout=self._timeout,
        )
        response.raise_for_status()
        payload = response.json()
        data = sorted(payload["data"], key=lambda item: item["index"])
        vectors = []
        for item in data:
            vector = tuple(float(value) for value in item["embedding"])
            norm = math.sqrt(sum(value * value for value in vector))
            if norm == 0:
                raise ValueError("Embedding provider returned a zero vector")
            vectors.append(tuple(value / norm for value in vector))
        return tuple(vectors)
