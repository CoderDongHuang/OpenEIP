"""Milvus insert, search, correctness, and latency verification for Spike-003."""

import json
import math
import statistics
import time
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from openai import OpenAI
from pymilvus import DataType, MilvusClient

COLLECTION = "spike_003_documents"
DIMENSIONS = 64
CATEGORIES = {
    "grpc": "grpc protobuf streaming java python rpc",
    "kafka": "kafka event topic producer consumer message",
    "milvus": "milvus vector embedding similarity retrieval",
    "mcp": "mcp tool protocol runtime agent capability",
    "sse": "sse streaming gateway browser token response",
}


def percentile(samples: list[float], quantile: float) -> float:
    """Return a nearest-rank percentile for a non-empty sample."""
    ordered = sorted(samples)
    index = max(0, math.ceil(quantile * len(ordered)) - 1)
    return ordered[index]


def wait_for_milvus() -> MilvusClient:
    """Wait until the standalone Milvus service accepts requests."""
    for attempt in range(90):
        try:
            client = MilvusClient(uri="http://milvus:19530")
            client.list_collections()
            return client
        except Exception:
            if attempt == 89:
                raise
            time.sleep(1)
    raise RuntimeError("Milvus readiness retries exhausted")


def documents() -> list[dict[str, Any]]:
    """Create a deterministic corpus with balanced semantic categories."""
    corpus: list[dict[str, Any]] = []
    identifier = 0
    for category, terms in CATEGORIES.items():
        for sequence in range(400):
            corpus.append(
                {
                    "id": identifier,
                    "category": category,
                    "text": f"{terms} OpenEIP technical document {sequence}",
                }
            )
            identifier += 1
    return corpus


def embed(client: OpenAI, texts: list[str]) -> tuple[list[list[float]], float]:
    """Call the local OpenAI-compatible embedding endpoint."""
    started = time.perf_counter()
    response = client.embeddings.create(model="deterministic-embedding-v1", input=texts)
    elapsed_ms = (time.perf_counter() - started) * 1_000
    return [item.embedding for item in response.data], elapsed_ms


def summarize(samples: list[float]) -> dict[str, float | int]:
    """Summarize latency samples."""
    return {
        "samples": len(samples),
        "mean_ms": statistics.fmean(samples),
        "p50_ms": percentile(samples, 0.50),
        "p95_ms": percentile(samples, 0.95),
        "p99_ms": percentile(samples, 0.99),
    }


def run() -> None:
    """Execute the complete embedding-to-retrieval pipeline."""
    milvus = wait_for_milvus()
    embedding = OpenAI(base_url="http://embedding-fixture:8000/v1", api_key="local-spike-key")
    corpus = documents()
    vectors, corpus_embedding_ms = embed(embedding, [item["text"] for item in corpus])

    if milvus.has_collection(COLLECTION):
        milvus.drop_collection(COLLECTION)
    schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=False)
    schema.add_field(field_name="id", datatype=DataType.INT64, is_primary=True)
    schema.add_field(field_name="category", datatype=DataType.VARCHAR, max_length=32)
    schema.add_field(field_name="text", datatype=DataType.VARCHAR, max_length=512)
    schema.add_field(field_name="vector", datatype=DataType.FLOAT_VECTOR, dim=DIMENSIONS)
    indexes = milvus.prepare_index_params()
    indexes.add_index(
        field_name="vector",
        index_name="vector_hnsw",
        index_type="HNSW",
        metric_type="COSINE",
        params={"M": 16, "efConstruction": 128},
    )
    milvus.create_collection(collection_name=COLLECTION, schema=schema, index_params=indexes)

    rows = [{**item, "vector": vector} for item, vector in zip(corpus, vectors, strict=True)]
    insert_started = time.perf_counter()
    inserted = milvus.insert(collection_name=COLLECTION, data=rows)["insert_count"]
    insert_ms = (time.perf_counter() - insert_started) * 1_000
    milvus.flush(COLLECTION)
    milvus.load_collection(COLLECTION)

    queries = [(category, f"OpenEIP {terms}") for category, terms in CATEGORIES.items()]
    for _ in range(4):
        for _, query in queries:
            vector, _ = embed(embedding, [query])
            milvus.search(
                collection_name=COLLECTION,
                data=vector,
                anns_field="vector",
                limit=5,
                output_fields=["category", "text"],
                search_params={"metric_type": "COSINE", "params": {"ef": 64}},
            )

    embedding_samples: list[float] = []
    search_samples: list[float] = []
    correct = 0
    query_count = 200
    for index in range(query_count):
        expected, query = queries[index % len(queries)]
        vector, embedding_ms = embed(embedding, [query])
        embedding_samples.append(embedding_ms)
        search_started = time.perf_counter()
        hits = milvus.search(
            collection_name=COLLECTION,
            data=vector,
            anns_field="vector",
            limit=5,
            output_fields=["category", "text"],
            search_params={"metric_type": "COSINE", "params": {"ef": 64}},
        )
        search_samples.append((time.perf_counter() - search_started) * 1_000)
        if hits and hits[0] and hits[0][0]["entity"]["category"] == expected:
            correct += 1

    search_summary = summarize(search_samples)
    correctness = correct / query_count
    passed = inserted == len(corpus) and correctness == 1.0 and search_summary["p99_ms"] < 100
    evidence = {
        "spike": "spike-003",
        "executed_at": datetime.now(UTC).isoformat(),
        "milvus_version": milvus.get_server_version(),
        "collection": COLLECTION,
        "index": {"type": "HNSW", "metric": "COSINE", "M": 16, "efConstruction": 128},
        "dimensions": DIMENSIONS,
        "documents": len(corpus),
        "inserted": inserted,
        "corpus_embedding_ms": corpus_embedding_ms,
        "insert_ms": insert_ms,
        "query_embedding": summarize(embedding_samples),
        "milvus_search": search_summary,
        "search_p99_threshold_ms": 100,
        "correct_queries": correct,
        "query_count": query_count,
        "top1_accuracy": correctness,
        "passed": passed,
    }
    results = Path("/results")
    results.mkdir(parents=True, exist_ok=True)
    (results / "result.json").write_text(json.dumps(evidence, indent=2), encoding="utf-8")
    milvus.drop_collection(COLLECTION)
    milvus.close()
    if not passed:
        raise RuntimeError("Spike-003 acceptance criteria failed")


if __name__ == "__main__":
    run()
