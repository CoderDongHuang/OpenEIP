"""OpenEIP Python AI Engine — Main Entry Point."""

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from engine_core.chat.api.router import build_chat_router
from engine_core.chat.application.service import ChatService
from engine_core.config import Settings
from engine_core.embedding.api.router import build_embedding_router
from engine_core.embedding.application.service import EmbeddingService
from engine_core.embedding.domain.ports import EmbeddingProvider, VectorRepository
from engine_core.embedding.infrastructure.deterministic_provider import DeterministicEmbeddingProvider
from engine_core.embedding.infrastructure.memory_repository import InMemoryVectorRepository
from engine_core.ocr.api.router import build_ocr_router
from engine_core.ocr.application.service import OcrService
from engine_core.ocr.infrastructure.deterministic_provider import DeterministicRasterProvider
from engine_core.ocr.infrastructure.image_decoder import SafeImageDecoder
from engine_core.parsing.api.router import build_parsing_router
from engine_core.parsing.application.service import DocumentParsingService
from engine_core.parsing.infrastructure.input_decoder import DocumentInputDecoder
from engine_core.rag.api.router import build_rag_router
from engine_core.rag.application.prompt import PromptBuilder
from engine_core.rag.application.service import RagService
from engine_core.rag.domain.ports import AnswerProvider
from engine_core.rag.infrastructure.deterministic_provider import DeterministicAnswerProvider
from engine_core.shared.api_error import EngineApiError
from engine_core.shared.internal_api import safe_request_id


def create_app(
    settings: Settings | None = None,
    embedding_provider: EmbeddingProvider | None = None,
    vector_repository: VectorRepository | None = None,
    answer_provider: AnswerProvider | None = None,
) -> FastAPI:
    """Create the AI engine with explicit settings for tests and deployments."""
    resolved = settings or Settings()
    internal_token = resolved.internal_api_secret()
    application = FastAPI(
        title="OpenEIP AI Engine",
        version=resolved.version,
        description="Agent, RAG, LLM, Document Processing Engine",
    )

    application.add_middleware(
        CORSMiddleware,
        allow_origins=resolved.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    service = OcrService(
        decoder=SafeImageDecoder(
            max_body_bytes=resolved.ocr_max_body_bytes,
            max_width=resolved.ocr_max_width,
            max_height=resolved.ocr_max_height,
            max_pixels=resolved.ocr_max_pixels,
        ),
        provider=DeterministicRasterProvider(),
    )
    application.include_router(
        build_ocr_router(
            service=service,
            internal_token=internal_token,
            max_body_bytes=resolved.ocr_max_body_bytes,
        )
    )

    provider = embedding_provider or _embedding_provider(resolved)
    repository = vector_repository or _vector_repository(resolved)
    if resolved.environment.casefold() == "production" and isinstance(repository, InMemoryVectorRepository):
        raise ValueError("In-memory vector repository is forbidden in production")
    embedding_service = EmbeddingService(
        provider=provider,
        repository=repository,
        max_batch_size=resolved.embedding_max_batch_size,
        max_text_chars=resolved.embedding_max_text_chars,
        max_jobs=resolved.embedding_max_jobs,
    )
    application.state.vector_repository = repository
    application.include_router(
        build_embedding_router(
            service=embedding_service,
            internal_token=internal_token,
            max_body_bytes=resolved.embedding_max_body_bytes,
        )
    )
    rag_service = RagService(
        embedding_provider=provider,
        repository=repository,
        answer_provider=answer_provider or _answer_provider(resolved),
        prompt_builder=PromptBuilder(resolved.rag_max_context_chars),
        max_query_chars=resolved.rag_max_query_chars,
        max_answer_chars=resolved.rag_max_answer_chars,
        max_top_k=resolved.rag_max_top_k,
    )
    application.include_router(
        build_rag_router(
            service=rag_service,
            internal_token=internal_token,
            max_body_bytes=resolved.rag_max_body_bytes,
            default_top_k=resolved.rag_default_top_k,
            max_top_k=resolved.rag_max_top_k,
        )
    )
    application.include_router(
        build_chat_router(
            service=ChatService(
                rag_service,
                max_message_chars=resolved.chat_max_message_chars,
                token_chunk_chars=resolved.chat_token_chunk_chars,
            ),
            internal_token=internal_token,
            max_body_bytes=resolved.chat_max_body_bytes,
            default_top_k=resolved.chat_default_top_k,
            max_top_k=resolved.chat_max_top_k,
        )
    )
    parsing_service = DocumentParsingService(
        decoder=DocumentInputDecoder(max_body_bytes=resolved.parsing_max_body_bytes),
        chunk_size=resolved.parsing_chunk_size,
        overlap=resolved.parsing_chunk_overlap,
        max_chunks=resolved.parsing_max_chunks,
    )
    application.include_router(
        build_parsing_router(
            service=parsing_service,
            internal_token=internal_token,
            max_body_bytes=resolved.parsing_max_body_bytes,
        )
    )

    @application.exception_handler(EngineApiError)
    async def handle_api_error(request: Request, error: EngineApiError) -> JSONResponse:
        request_id = safe_request_id(request.headers.get("X-Request-Id", ""))
        return JSONResponse(status_code=error.status_code, content=error.envelope(request_id))

    @application.get("/health")
    async def health() -> dict[str, str]:
        return health_payload(resolved)

    return application


def _embedding_provider(settings: Settings) -> EmbeddingProvider:
    if settings.embedding_provider != "deterministic":
        raise ValueError("Configured embedding provider requires an injected adapter")
    return DeterministicEmbeddingProvider(settings.embedding_dimension)


def _vector_repository(settings: Settings) -> VectorRepository:
    if settings.embedding_repository_backend != "memory":
        raise ValueError("Configured vector repository requires an injected adapter")
    return InMemoryVectorRepository()


def _answer_provider(settings: Settings) -> AnswerProvider:
    if settings.rag_answer_provider != "deterministic":
        raise ValueError("Configured RAG answer provider requires an injected adapter")
    return DeterministicAnswerProvider()


def health_payload(settings: Settings) -> dict[str, str]:
    """Return the stable health response for the configured application."""
    return {
        "status": "healthy",
        "version": settings.version,
        "service": "openeip-ai-engine",
    }


settings = Settings()
app = create_app(settings)


async def health_check() -> dict[str, str]:
    """Health check endpoint."""
    return health_payload(settings)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("engine_core.main:app", host="0.0.0.0", port=8000, reload=True)
