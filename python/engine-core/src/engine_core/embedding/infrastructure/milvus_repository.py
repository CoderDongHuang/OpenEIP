"""Milvus vector adapter with scope filtering before ANN ranking."""

from engine_core.embedding.domain.models import VectorRecord, VectorSearchResult


class MilvusVectorRepository:
    def __init__(self, uri: str, token: str, collection: str, dimension: int) -> None:
        from pymilvus import DataType, MilvusClient  # type: ignore[import-untyped]

        self._client = MilvusClient(uri=uri, token=token or None)
        self._collection = collection
        self._dimension = dimension
        if not self._client.has_collection(collection_name=collection):
            schema = self._client.create_schema(auto_id=False, enable_dynamic_field=False)
            schema.add_field("chunk_id", DataType.VARCHAR, is_primary=True, max_length=80)
            schema.add_field("tenant_id", DataType.VARCHAR, max_length=64)
            schema.add_field("knowledge_base_id", DataType.VARCHAR, max_length=64)
            schema.add_field("document_id", DataType.VARCHAR, max_length=64)
            schema.add_field("text", DataType.VARCHAR, max_length=8192)
            schema.add_field("source_sha256", DataType.VARCHAR, max_length=64)
            schema.add_field("pages", DataType.ARRAY, element_type=DataType.INT32, max_capacity=100)
            schema.add_field("start_char", DataType.INT64)
            schema.add_field("end_char", DataType.INT64)
            schema.add_field("vector", DataType.FLOAT_VECTOR, dim=dimension)
            index = self._client.prepare_index_params()
            index.add_index("vector", index_type="HNSW", metric_type="COSINE", params={"M": 16, "efConstruction": 200})
            self._client.create_collection(collection_name=collection, schema=schema, index_params=index)

    def upsert(self, records: tuple[VectorRecord, ...]) -> None:
        if not records:
            return
        self._client.upsert(
            collection_name=self._collection,
            data=[
                {
                    "chunk_id": record.chunk_id,
                    "tenant_id": record.tenant_id,
                    "knowledge_base_id": record.knowledge_base_id,
                    "document_id": record.document_id,
                    "text": record.text,
                    "source_sha256": record.source_sha256,
                    "pages": list(record.pages),
                    "start_char": record.start_char,
                    "end_char": record.end_char,
                    "vector": list(record.vector),
                }
                for record in records
            ],
        )

    def search(
        self, tenant_id: str, knowledge_base_id: str, query: tuple[float, ...], top_k: int
    ) -> tuple[VectorSearchResult, ...]:
        if len(query) != self._dimension:
            raise ValueError("Milvus query dimension mismatch")
        expression = f'tenant_id == "{_uuid(tenant_id)}" and knowledge_base_id == "{_uuid(knowledge_base_id)}"'
        rows = self._client.search(
            collection_name=self._collection,
            data=[list(query)],
            filter=expression,
            limit=top_k,
            output_fields=["document_id", "text", "source_sha256", "pages", "start_char", "end_char"],
            search_params={"metric_type": "COSINE", "params": {"ef": max(64, top_k * 4)}},
        )[0]
        return tuple(
            VectorSearchResult(
                row["entity"]["document_id"],
                str(row.get("id") or row["chunk_id"]),
                row["entity"]["text"],
                float(row["distance"]),
                row["entity"]["source_sha256"],
                tuple(row["entity"].get("pages", ())),
                int(row["entity"].get("start_char", 0)),
                int(row["entity"].get("end_char", 0)),
            )
            for row in rows
        )

    def delete_document(self, tenant_id: str, knowledge_base_id: str, document_id: str) -> int:
        expression = (
            f'tenant_id == "{_uuid(tenant_id)}" and knowledge_base_id == "{_uuid(knowledge_base_id)}" '
            f'and document_id == "{_uuid(document_id)}"'
        )
        result = self._client.delete(collection_name=self._collection, filter=expression)
        return int(result.get("delete_count", 0))


def _uuid(value: str) -> str:
    from uuid import UUID

    return str(UUID(value))
