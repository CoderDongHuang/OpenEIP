"""Elasticsearch full-text metadata plus Milvus vector retrieval."""

from engine_core.embedding.domain.models import VectorRecord, VectorSearchResult
from engine_core.embedding.domain.ports import VectorRepository


class ElasticsearchChunkIndex:
    def __init__(self, url: str, index: str, api_key: str = "") -> None:
        from elasticsearch import Elasticsearch

        self._client = Elasticsearch(url, api_key=api_key or None)
        self._index = index
        if not self._client.indices.exists(index=index):
            self._client.indices.create(
                index=index,
                mappings={
                    "dynamic": "strict",
                    "properties": {
                        "tenantId": {"type": "keyword"},
                        "knowledgeBaseId": {"type": "keyword"},
                        "documentId": {"type": "keyword"},
                        "chunkId": {"type": "keyword"},
                        "text": {"type": "text", "analyzer": "standard"},
                        "sourceSha256": {"type": "keyword"},
                        "pages": {"type": "integer"},
                        "startChar": {"type": "long"},
                        "endChar": {"type": "long"},
                    },
                },
            )

    def upsert(self, records: tuple[VectorRecord, ...]) -> None:
        from elasticsearch.helpers import bulk

        bulk(
            self._client,
            (
                {
                    "_op_type": "index",
                    "_index": self._index,
                    "_id": f"{record.tenant_id}:{record.knowledge_base_id}:{record.chunk_id}",
                    "tenantId": record.tenant_id,
                    "knowledgeBaseId": record.knowledge_base_id,
                    "documentId": record.document_id,
                    "chunkId": record.chunk_id,
                    "text": record.text,
                    "sourceSha256": record.source_sha256,
                    "pages": list(record.pages),
                    "startChar": record.start_char,
                    "endChar": record.end_char,
                }
                for record in records
            ),
            refresh="wait_for",
        )

    def search(self, tenant_id: str, knowledge_base_id: str, query: str, top_k: int) -> tuple[VectorSearchResult, ...]:
        response = self._client.search(
            index=self._index,
            size=top_k,
            query={
                "bool": {
                    "filter": [
                        {"term": {"tenantId": tenant_id}},
                        {"term": {"knowledgeBaseId": knowledge_base_id}},
                    ],
                    "must": [{"match": {"text": {"query": query, "operator": "or"}}}],
                }
            },
        )
        return tuple(
            VectorSearchResult(
                hit["_source"]["documentId"],
                hit["_source"]["chunkId"],
                hit["_source"]["text"],
                float(hit["_score"] or 0.0),
                hit["_source"]["sourceSha256"],
                tuple(hit["_source"].get("pages", ())),
                int(hit["_source"].get("startChar", 0)),
                int(hit["_source"].get("endChar", 0)),
            )
            for hit in response["hits"]["hits"]
        )

    def delete_document(self, tenant_id: str, knowledge_base_id: str, document_id: str) -> int:
        result = self._client.delete_by_query(
            index=self._index,
            refresh=True,
            query={
                "bool": {
                    "filter": [
                        {"term": {"tenantId": tenant_id}},
                        {"term": {"knowledgeBaseId": knowledge_base_id}},
                        {"term": {"documentId": document_id}},
                    ]
                }
            },
        )
        return int(result["deleted"])


class HybridKnowledgeRepository:
    """Atomic-at-service-boundary dual writer with deterministic RRF retrieval."""

    def __init__(self, vectors: VectorRepository, text: ElasticsearchChunkIndex) -> None:
        self._vectors = vectors
        self._text = text

    def upsert(self, records: tuple[VectorRecord, ...]) -> None:
        self._vectors.upsert(records)
        try:
            self._text.upsert(records)
        except Exception:
            for record in records:
                self._vectors.delete_document(record.tenant_id, record.knowledge_base_id, record.document_id)
            raise

    def search(
        self, tenant_id: str, knowledge_base_id: str, query: tuple[float, ...], top_k: int
    ) -> tuple[VectorSearchResult, ...]:
        return self._vectors.search(tenant_id, knowledge_base_id, query, top_k)

    def search_text(
        self, tenant_id: str, knowledge_base_id: str, query: str, top_k: int
    ) -> tuple[VectorSearchResult, ...]:
        return self._text.search(tenant_id, knowledge_base_id, query, top_k)

    def search_hybrid(
        self, tenant_id: str, knowledge_base_id: str, text: str, vector: tuple[float, ...], top_k: int
    ) -> tuple[VectorSearchResult, ...]:
        limit = min(100, max(top_k * 4, 20))
        vector_hits = self._vectors.search(tenant_id, knowledge_base_id, vector, limit)
        text_hits = self._text.search(tenant_id, knowledge_base_id, text, limit)
        by_id = {item.chunk_id: item for item in (*vector_hits, *text_hits)}
        scores: dict[str, float] = {}
        for hits in (vector_hits, text_hits):
            for rank, item in enumerate(hits, 1):
                scores[item.chunk_id] = scores.get(item.chunk_id, 0.0) + 1.0 / (60 + rank)
        ranked = sorted(by_id.values(), key=lambda item: (-scores[item.chunk_id], item.chunk_id))
        return tuple(
            VectorSearchResult(
                item.document_id,
                item.chunk_id,
                item.text,
                scores[item.chunk_id],
                item.source_sha256,
                item.pages,
                item.start_char,
                item.end_char,
            )
            for item in ranked[:top_k]
        )

    def delete_document(self, tenant_id: str, knowledge_base_id: str, document_id: str) -> int:
        vector_count = self._vectors.delete_document(tenant_id, knowledge_base_id, document_id)
        self._text.delete_document(tenant_id, knowledge_base_id, document_id)
        return vector_count
