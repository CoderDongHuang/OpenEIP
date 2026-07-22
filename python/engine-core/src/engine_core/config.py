"""Application configuration via environment variables."""

from pydantic import SecretStr
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Global application settings."""

    version: str = "0.1.0-alpha"

    # Server
    host: str = "0.0.0.0"
    port: int = 8000
    cors_origins: list[str] = ["http://localhost:3000", "http://localhost:5173"]

    # Java Platform
    java_platform_url: str = "http://localhost:8080"
    grpc_java_host: str = "localhost"
    grpc_java_port: int = 9091

    # Kafka
    kafka_bootstrap_servers: str = "localhost:9092"

    # Milvus
    milvus_host: str = "localhost"
    milvus_port: int = 19530

    # Elasticsearch
    es_host: str = "http://localhost:9200"

    # Redis
    redis_url: str = "redis://localhost:6379/0"

    # OCR internal API
    internal_api_token: SecretStr = SecretStr("")
    ocr_internal_token: SecretStr = SecretStr("")
    ocr_max_body_bytes: int = 5 * 1024 * 1024
    ocr_max_width: int = 10_000
    ocr_max_height: int = 10_000
    ocr_max_pixels: int = 20_000_000

    # Document parsing internal API
    parsing_max_body_bytes: int = 2 * 1024 * 1024
    parsing_chunk_size: int = 1000
    parsing_chunk_overlap: int = 100
    parsing_max_chunks: int = 10_000

    # LLM
    llm_default_model: str = "gpt-4o"
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"

    model_config = {"env_prefix": "OPENEIP_", "env_file": ".env"}

    def internal_api_secret(self) -> str:
        """Return the generic internal credential with the OCR-era setting as migration fallback."""
        return self.internal_api_token.get_secret_value() or self.ocr_internal_token.get_secret_value()
