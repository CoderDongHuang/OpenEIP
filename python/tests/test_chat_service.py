import json

import pytest

from engine_core.chat.application.service import ChatService, encode_sse
from engine_core.chat.shared.errors import ChatError
from engine_core.rag.domain.models import RagCitation, RagResult
from engine_core.rag.shared.errors import RagError


class FakeRagService:
    def __init__(self, result: RagResult | None = None, failure: Exception | None = None) -> None:
        self.result = result
        self.failure = failure
        self.calls: list[tuple[str, str, str, int]] = []

    def query(self, tenant_id: str, knowledge_base_id: str, message: str, top_k: int) -> RagResult:
        self.calls.append((tenant_id, knowledge_base_id, message, top_k))
        if self.failure:
            raise self.failure
        if self.result is None:
            raise AssertionError("missing fixture")
        return self.result


def _result(answer: str = "grounded answer") -> RagResult:
    return RagResult(
        answer,
        "fixture",
        "1",
        (RagCitation("doc", "chunk", "a" * 64, 1.0, "grounded excerpt", (1, 2), 0, 17),),
        1,
        1.0,
    )


async def _collect(service: ChatService, message: str = "question") -> list[str]:
    return [event async for event in service.stream("tenant", "user", "session", "request", "base", message, 5)]


@pytest.mark.asyncio
async def test_stream_emits_ordered_bounded_tokens_and_done_citations() -> None:
    rag = FakeRagService(_result("abcdefghij"))
    events = await _collect(ChatService(rag, 100, 4))  # type: ignore[arg-type]

    assert [event.splitlines()[0] for event in events] == [
        "event: token",
        "event: token",
        "event: token",
        "event: done",
    ]
    token_data = [json.loads(event.splitlines()[1][6:]) for event in events[:-1]]
    assert [item["sequence"] for item in token_data] == [0, 1, 2]
    assert "".join(str(item["token"]) for item in token_data) == "abcdefghij"
    done = json.loads(events[-1].splitlines()[1][6:])
    assert done["citations"] == [
        {
            "documentId": "doc",
            "chunkId": "chunk",
            "sourceSha256": "a" * 64,
            "score": 1.0,
            "excerpt": "grounded excerpt",
            "pages": [1, 2],
            "startChar": 0,
            "endChar": 17,
        }
    ]
    assert rag.calls == [("tenant", "base", "question", 5)]


@pytest.mark.asyncio
async def test_stream_escapes_sse_injection_and_stops_after_close() -> None:
    service = ChatService(FakeRagService(_result("safe\n\nevent: error\ndata: stolen")), 100, 1024)  # type: ignore[arg-type]
    stream = service.stream("tenant", "user", "session", "request", "base", "question", 1)
    first = await anext(stream)
    assert first.count("\ndata: ") == 1
    assert "safe\\n\\nevent: error\\ndata: stolen" in first
    await stream.aclose()
    with pytest.raises(StopAsyncIteration):
        await anext(stream)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "failure",
    [RagError("RAG-S-001", "provider secret", 503), RuntimeError("database credential")],
)
async def test_stream_maps_failures_without_details(failure: Exception) -> None:
    events = await _collect(ChatService(FakeRagService(failure=failure), 100, 64))  # type: ignore[arg-type]
    assert len(events) == 1
    assert events[0].startswith("event: error\n")
    assert "CHAT-S-001" in events[0]
    assert "secret" not in events[0]
    assert "credential" not in events[0]


@pytest.mark.parametrize("message", ["", "  \n", "bad\x00message", "x" * 101])
def test_stream_rejects_invalid_messages_before_opening_response(message: str) -> None:
    with pytest.raises(ChatError):
        ChatService(FakeRagService(_result()), 100, 64).stream(  # type: ignore[arg-type]
            "tenant", "user", "session", "request", "base", message, 1
        )


def test_service_and_encoder_reject_invalid_configuration_or_event() -> None:
    with pytest.raises(ValueError):
        ChatService(FakeRagService(_result()), 0, 64)  # type: ignore[arg-type]
    with pytest.raises(ValueError):
        encode_sse("message", {})
