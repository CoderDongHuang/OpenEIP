"""Deterministic answer fixture for offline pipeline validation."""

from engine_core.rag.domain.models import ProviderAnswer, RagContext


class DeterministicAnswerProvider:
    """Build a bounded answer from the top supplied context without interpreting instructions."""

    model = "openeip-deterministic-rag"
    version = "1.0.0"

    def answer(
        self,
        question: str,
        prompt: str,
        contexts: tuple[RagContext, ...],
    ) -> ProviderAnswer:
        """Return the top context as evidence, or an explicit no-context answer."""
        del question, prompt
        if not contexts:
            return ProviderAnswer(
                "No relevant context was found.",
                self.model,
                self.version,
                (),
            )
        evidence = " ".join(contexts[0].text.split())[:1000]
        return ProviderAnswer(
            f"Based on retrieved context: {evidence}",
            self.model,
            self.version,
            (contexts[0].chunk_id,),
        )
