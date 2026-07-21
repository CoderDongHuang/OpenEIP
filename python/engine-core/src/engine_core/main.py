"""OpenEIP Python AI Engine — Main Entry Point."""

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from engine_core.config import Settings
from engine_core.ocr.api.router import build_ocr_router, safe_request_id
from engine_core.ocr.application.service import OcrService
from engine_core.ocr.infrastructure.deterministic_provider import DeterministicRasterProvider
from engine_core.ocr.infrastructure.image_decoder import SafeImageDecoder
from engine_core.ocr.shared.errors import OcrError


def create_app(settings: Settings | None = None) -> FastAPI:
    """Create the AI engine with explicit settings for tests and deployments."""
    resolved = settings or Settings()
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
            internal_token=resolved.ocr_internal_token.get_secret_value(),
            max_body_bytes=resolved.ocr_max_body_bytes,
        )
    )

    @application.exception_handler(OcrError)
    async def handle_ocr_error(request: Request, error: OcrError) -> JSONResponse:
        request_id = safe_request_id(request.headers.get("X-Request-Id", ""))
        return JSONResponse(status_code=error.status_code, content=error.envelope(request_id))

    @application.get("/health")
    async def health() -> dict[str, str]:
        return health_payload(resolved)

    return application


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
