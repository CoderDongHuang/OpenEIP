import os
import time

import pytest

from engine_core.embedding.domain.models import VectorRecord
from engine_core.embedding.infrastructure.hybrid_repository import ElasticsearchChunkIndex, HybridKnowledgeRepository
from engine_core.embedding.infrastructure.milvus_repository import MilvusVectorRepository

TENANT = "11111111-1111-4111-8111-111111111111"
BASE = "22222222-2222-4222-8222-222222222222"
DOCUMENT = "33333333-3333-4333-8333-333333333333"


@pytest.mark.skipif(os.getenv("OPENEIP_LIVE_KNOWLEDGE_TEST") != "1", reason="requires Milvus and Elasticsearch")
def test_live_hybrid_repository_is_scoped_traceable_and_deletable() -> None:
    repository = HybridKnowledgeRepository(
        MilvusVectorRepository(
            os.getenv("OPENEIP_LIVE_MILVUS_URI", "http://localhost:19530"),
            "",
            "openeip_test_chunks_v1",
            4,
        ),
        ElasticsearchChunkIndex(
            os.getenv("OPENEIP_LIVE_ELASTICSEARCH_URL", "http://localhost:9200"),
            "openeip-test-chunks-v1",
        ),
    )
    records = (
        VectorRecord(
            TENANT,
            BASE,
            DOCUMENT,
            "chk_" + "a" * 32,
            "invoice exact alpha",
            "b" * 64,
            "fixture",
            "1",
            (1.0, 0.0, 0.0, 0.0),
            (3,),
            20,
            39,
        ),
        VectorRecord(
            TENANT,
            "22222222-2222-4222-8222-222222222223",
            DOCUMENT,
            "chk_" + "c" * 32,
            "invoice forbidden",
            "d" * 64,
            "fixture",
            "1",
            (1.0, 0.0, 0.0, 0.0),
            (1,),
            0,
            17,
        ),
    )
    repository.upsert(records)

    results = ()
    for _ in range(20):
        results = repository.search_hybrid(TENANT, BASE, "invoice alpha", (1.0, 0.0, 0.0, 0.0), 5)
        if results:
            break
        time.sleep(0.25)

    assert [item.chunk_id for item in results] == ["chk_" + "a" * 32]
    assert results[0].pages == (3,)
    assert repository.delete_document(TENANT, BASE, DOCUMENT) == 1
