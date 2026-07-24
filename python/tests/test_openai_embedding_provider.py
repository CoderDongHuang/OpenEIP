import math

import httpx
import pytest

from engine_core.embedding.infrastructure.openai_provider import OpenAIEmbeddingProvider


class _Response:
    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, object]:
        return {"data": [{"index": 1, "embedding": [0.0, 3.0]}, {"index": 0, "embedding": [4.0, 0.0]}]}


def test_openai_compatible_adapter_uses_secret_header_orders_and_normalizes(monkeypatch: pytest.MonkeyPatch) -> None:
    observed: dict[str, object] = {}

    def post(url: str, **kwargs: object) -> _Response:
        observed.update({"url": url, **kwargs})
        return _Response()

    monkeypatch.setattr(httpx, "post", post)
    provider = OpenAIEmbeddingProvider("secret-key", "https://provider.example/v1", "embed-model", 2)

    vectors = provider.embed(("first", "second"))

    assert vectors == ((1.0, 0.0), (0.0, 1.0))
    assert observed["url"] == "https://provider.example/v1/embeddings"
    assert observed["headers"] == {"Authorization": "Bearer secret-key"}
    assert observed["json"] == {"model": "embed-model", "input": ["first", "second"], "dimensions": 2}
    assert all(math.isclose(sum(value * value for value in vector), 1.0) for vector in vectors)


def test_openai_compatible_adapter_rejects_zero_vector(monkeypatch: pytest.MonkeyPatch) -> None:
    class ZeroResponse(_Response):
        def json(self) -> dict[str, object]:
            return {"data": [{"index": 0, "embedding": [0.0, 0.0]}]}

    monkeypatch.setattr(httpx, "post", lambda *args, **kwargs: ZeroResponse())
    provider = OpenAIEmbeddingProvider("secret-key", "https://provider.example/v1", "embed-model", 2)

    with pytest.raises(ValueError, match="zero vector"):
        provider.embed(("first",))
