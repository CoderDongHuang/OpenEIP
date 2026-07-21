"""Python gRPC server used by Spike-001."""

import asyncio
import logging

import agent_pb2
import agent_pb2_grpc
import grpc

LOGGER = logging.getLogger(__name__)
TOKENS = ("Open", "EIP", " ", "streaming", " ", "works")


class AgentService(agent_pb2_grpc.AgentServiceServicer):
    """Deterministic implementation for cross-runtime verification."""

    async def Chat(self, request, context):  # noqa: N802
        if request.message == "trigger-error":
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "deliberate validation error")
        answer = f"received:{request.message}"
        return agent_pb2.ChatResponse(
            session_id=request.session_id,
            answer=answer,
            tokens_used=len(answer),
        )

    async def ChatStream(self, request, context):  # noqa: N802
        yield agent_pb2.ChatStreamResponse(
            session_id=request.session_id,
            thinking=agent_pb2.ThinkingEvent(step="planning", message="ready"),
        )
        for index, token in enumerate(TOKENS):
            await asyncio.sleep(0.01)
            yield agent_pb2.ChatStreamResponse(
                session_id=request.session_id,
                token=agent_pb2.TokenEvent(token=token, index=index),
            )
        yield agent_pb2.ChatStreamResponse(
            session_id=request.session_id,
            done=agent_pb2.DoneEvent(total_tokens=len(TOKENS), finish_reason="stop"),
        )

    async def ChatBidi(self, request_iterator, context):  # noqa: N802
        async for request in request_iterator:
            yield agent_pb2.ChatStreamResponse(
                session_id=request.session_id,
                done=agent_pb2.DoneEvent(total_tokens=1, finish_reason=request.message),
            )


async def serve() -> None:
    """Start the asynchronous gRPC server."""
    server = grpc.aio.server()
    agent_pb2_grpc.add_AgentServiceServicer_to_server(AgentService(), server)
    server.add_insecure_port("[::]:50051")
    await server.start()
    LOGGER.info("Spike-001 Python gRPC server listening on port 50051")
    await server.wait_for_termination()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    asyncio.run(serve())
