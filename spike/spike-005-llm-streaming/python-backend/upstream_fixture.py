"""Deterministic OpenAI-compatible streaming chat fixture."""

import asyncio
import json
import time
from collections.abc import AsyncIterator
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

app = FastAPI(title="Spike-005 local OpenAI-compatible upstream")


class ChatCompletionRequest(BaseModel):
    """Subset of the OpenAI chat completion request used by the Spike."""

    model: str
    messages: list[dict[str, Any]]
    stream: bool = False


def chunk(model: str, content: str | None, finish_reason: str | None = None) -> str:
    """Encode one OpenAI-compatible SSE chunk."""
    payload = {
        "id": "chatcmpl-spike-005",
        "object": "chat.completion.chunk",
        "created": int(time.time()),
        "model": model,
        "choices": [
            {
                "index": 0,
                "delta": {"content": content} if content is not None else {},
                "finish_reason": finish_reason,
            }
        ],
    }
    return f"data: {json.dumps(payload)}\n\n"


async def stream_tokens(model: str, long_stream: bool) -> AsyncIterator[str]:
    """Yield deterministic tokens with observable timing."""
    tokens = ["Open", "EIP", " ", "streaming", " ", "works", "."]
    if long_stream:
        tokens = [f"token-{index} " for index in range(100)]
    await asyncio.sleep(0.05)
    for token in tokens:
        yield chunk(model, token)
        await asyncio.sleep(0.03)
    yield chunk(model, None, "stop")
    yield "data: [DONE]\n\n"


@app.get("/health")
async def health() -> dict[str, str]:
    """Expose fixture readiness."""
    return {"status": "ok"}


@app.post("/v1/chat/completions")
async def chat_completions(request: ChatCompletionRequest) -> StreamingResponse:
    """Return a deterministic stream or deliberate upstream error."""
    content = str(request.messages[-1].get("content", ""))
    if content == "upstream-error":
        raise HTTPException(status_code=503, detail="deliberate upstream failure")
    if not request.stream:
        raise HTTPException(status_code=400, detail="Spike requires stream=true")
    return StreamingResponse(
        stream_tokens(request.model, content == "long-stream"),
        media_type="text/event-stream",
    )
