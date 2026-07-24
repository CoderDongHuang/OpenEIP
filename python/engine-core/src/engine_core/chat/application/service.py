"""Bounded RAG-backed Server-Sent Event generation."""

import asyncio
import json
from collections.abc import AsyncIterator

from engine_core.rag.application.service import RagService
from engine_core.rag.shared.errors import RagError


class ChatService:
    """Turn one verified RAG result into cancellation-aware SSE token events."""

    def __init__(self, rag_service: RagService, max_message_chars: int, token_chunk_chars: int) -> None:
        if max_message_chars < 1 or token_chunk_chars < 1 or token_chunk_chars > 1024:
            raise ValueError("Invalid Chat limits")
        self._rag_service = rag_service
        self._max_message_chars = max_message_chars
        self._token_chunk_chars = token_chunk_chars

    def stream(
        self,
        tenant_id: str,
        user_id: str,
        session_id: str,
        request_id: str,
        knowledge_base_id: str,
        message: str,
        top_k: int,
    ) -> AsyncIterator[str]:
        """Validate synchronously, then return an async stream that propagates cancellation."""
        del user_id
        self._validate_message(message)

        async def events() -> AsyncIterator[str]:
            try:
                result = self._rag_service.query(tenant_id, knowledge_base_id, message, top_k)
                for sequence, offset in enumerate(range(0, len(result.answer), self._token_chunk_chars)):
                    await asyncio.sleep(0)
                    yield encode_sse(
                        "token",
                        {
                            "requestId": request_id,
                            "sessionId": session_id,
                            "sequence": sequence,
                            "token": result.answer[offset : offset + self._token_chunk_chars],
                        },
                    )
                yield encode_sse(
                    "done",
                    {
                        "requestId": request_id,
                        "sessionId": session_id,
                        "finishReason": "stop",
                        "citations": [
                            {
                                "documentId": citation.document_id,
                                "chunkId": citation.chunk_id,
                                "sourceSha256": citation.source_sha256,
                                "score": citation.score,
                                "excerpt": citation.excerpt,
                                "pages": list(citation.pages),
                                "startChar": citation.start_char,
                                "endChar": citation.end_char,
                            }
                            for citation in result.citations
                        ],
                    },
                )
            except asyncio.CancelledError:
                raise
            except RagError:
                yield encode_sse(
                    "error",
                    {
                        "requestId": request_id,
                        "sessionId": session_id,
                        "code": "CHAT-S-001",
                        "message": "Chat generation failed",
                    },
                )
            except Exception:
                yield encode_sse(
                    "error",
                    {
                        "requestId": request_id,
                        "sessionId": session_id,
                        "code": "CHAT-S-001",
                        "message": "Chat generation failed",
                    },
                )

        return events()

    def _validate_message(self, message: str) -> None:
        if (
            not message.strip()
            or len(message) > self._max_message_chars
            or any(ord(character) < 32 and character not in "\t\n\r" for character in message)
        ):
            from engine_core.chat.shared.errors import ChatError

            raise ChatError("CHAT-V-004", "Invalid Chat message", 400)


def encode_sse(event: str, data: dict[str, object]) -> str:
    """Encode one single-line JSON event without allowing SSE field injection."""
    if event not in {"token", "done", "error"}:
        raise ValueError("Unsupported Chat event")
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=True, separators=(',', ':'))}\n\n"
