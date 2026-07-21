import math

import pytest

from engine_core.embedding.infrastructure.deterministic_provider import DeterministicEmbeddingProvider


def test_provider_is_deterministic_normalized_and_fixed_width() -> None:
    provider = DeterministicEmbeddingProvider(64)
    first = provider.embed(("Alpha beta beta", "完全离线向量"))
    second = provider.embed(("Alpha beta beta", "完全离线向量"))

    assert first == second
    assert provider.model == "openeip-deterministic-hash"
    assert provider.version == "1.0.0"
    assert all(len(vector) == 64 for vector in first)
    assert all(math.sqrt(sum(value * value for value in vector)) == pytest.approx(1.0) for vector in first)


def test_provider_preserves_casefolded_token_similarity() -> None:
    provider = DeterministicEmbeddingProvider(64)
    alpha, alpha_upper, unrelated = provider.embed(("alpha guide", "ALPHA GUIDE", "invoice payment"))
    assert alpha == alpha_upper
    assert sum(a * b for a, b in zip(alpha, alpha_upper, strict=True)) == pytest.approx(1.0)
    assert sum(a * b for a, b in zip(alpha, unrelated, strict=True)) < 0.9


@pytest.mark.parametrize("dimension", [0, 7, 1537])
def test_provider_rejects_unsupported_dimension(dimension: int) -> None:
    with pytest.raises(ValueError):
        DeterministicEmbeddingProvider(dimension)


def test_provider_handles_text_without_word_tokens() -> None:
    vector = DeterministicEmbeddingProvider(8).embed((("!"),))[0]
    assert len(vector) == 8
    assert all(math.isfinite(value) for value in vector)
