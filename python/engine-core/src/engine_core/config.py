"""Application configuration via environment variables."""

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

    # LLM
    llm_default_model: str = "gpt-4o"
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"

    model_config = {"env_prefix": "OPENEIP_", "env_file": ".env"}
