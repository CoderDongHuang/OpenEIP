"""Replaceable answer provider interface."""

from typing import Protocol

from engine_core.rag.domain.models import ProviderAnswer, RagContext


class AnswerProvider(Protocol):
    """Generate an answer from a runtime-built prompt and immutable context allowlist."""

    def answer(
        self,
        question: str,
        prompt: str,
        contexts: tuple[RagContext, ...],
    ) -> ProviderAnswer: ...
