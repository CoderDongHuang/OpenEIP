"""OpenEIP Python AI Engine — Main Entry Point."""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from engine_core.config import Settings

settings = Settings()

app = FastAPI(
    title="OpenEIP AI Engine",
    version=settings.version,
    description="Agent, RAG, LLM, Document Processing Engine",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health_check() -> dict[str, str]:
    """Health check endpoint."""
    return {
        "status": "healthy",
        "version": settings.version,
        "service": "openeip-ai-engine",
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("engine_core.main:app", host="0.0.0.0", port=8000, reload=True)
