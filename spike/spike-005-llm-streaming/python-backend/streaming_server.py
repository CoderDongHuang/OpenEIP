"""FastAPI SSE proxy backed by an OpenAI-compatible streaming API."""

import asyncio
import json
import os
from collections.abc import AsyncIterator

from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from openai import AsyncOpenAI
from pydantic import BaseModel

app = FastAPI(title="OpenEIP Spike-005 streaming backend")
client = AsyncOpenAI(
    base_url=os.getenv("OPENAI_BASE_URL", "http://upstream:8001/v1"),
    api_key=os.getenv("OPENAI_API_KEY", "local-spike-key"),
)
metrics = {"completed": 0, "upstream_errors": 0, "client_cancellations": 0}


class ChatRequest(BaseModel):
    """Browser chat request."""

    message: str


def sse(event: str, data: dict[str, object]) -> str:
    """Encode one Server-Sent Event."""
    return f"event: {event}\ndata: {json.dumps(data)}\n\n"


async def token_stream(message: str) -> AsyncIterator[str]:
    """Forward OpenAI streaming chunks as application SSE events."""
    try:
        stream = await client.chat.completions.create(
            model="deterministic-chat-v1",
            messages=[{"role": "user", "content": message}],
            stream=True,
        )
        async for chunk in stream:
            token = chunk.choices[0].delta.content if chunk.choices else None
            if token:
                yield sse("token", {"token": token})
        metrics["completed"] += 1
        yield sse("done", {"finishReason": "stop"})
    except asyncio.CancelledError:
        metrics["client_cancellations"] += 1
        raise
    except Exception as error:
        metrics["upstream_errors"] += 1
        yield sse("error", {"code": "UPSTREAM_ERROR", "message": type(error).__name__})


@app.get("/health")
async def health() -> dict[str, str]:
    """Expose readiness."""
    return {"status": "ok"}


@app.get("/metrics")
async def get_metrics() -> dict[str, int]:
    """Expose Spike assertions for cancellation and errors."""
    return metrics.copy()


@app.post("/chat")
async def chat(request: ChatRequest) -> StreamingResponse:
    """Stream a chat response to the browser."""
    return StreamingResponse(
        token_stream(request.message),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
