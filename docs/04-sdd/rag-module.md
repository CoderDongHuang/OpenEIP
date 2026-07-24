# RAG Module Design (Sub-SDD)

> Version: 2.0 | Date: 2026-07-24 | Status: Implemented for v0.3
> Issue: [#49](https://github.com/CoderDongHuang/OpenEIP/issues/49) | SAD: [Architecture Baseline](../03-sad/zh-CN.md)

## 1. Responsibilities and Boundaries

The Python RAG module embeds one bounded query, performs tenant and knowledge-base-filtered vector
search, builds an injection-resistant prompt, invokes an `AnswerProvider`, and verifies every returned
citation against the retrieved set. It does not authorize knowledge membership, ingest documents,
stream chat, execute tools, or expose raw prompts.

Java remains the authorization decision point. The internal caller supplies only the tenant and base
identity already authorized by Java; the repository must apply both filters before scoring. Filtering
only after global retrieval is forbidden.

## 2. API and Limits

The source contract is [rag-v1.openapi.yaml](../06-api/rag-v1.openapi.yaml). The authenticated internal
`POST /api/v1/rag/queries` endpoint accepts `knowledgeBaseId`, `query`, and `topK`. Query length is
1-2,000 characters, body size is at most 32 KiB, and `topK` is 1-20. Blank/control-character queries,
unknown/duplicate JSON fields, invalid identities, and non-JSON media types fail closed.

The response contains a bounded answer, model provenance, retrieval duration, and citations. A
citation exposes document ID, chunk ID, source hash, and score, but not hidden prompt text or vectors.
v0.3 citations additionally expose a maximum 500-character excerpt, page attribution, and normalized
character range. Direct authorized retrieval supports `FULL_TEXT`, `VECTOR`, and deterministic `HYBRID`
RRF modes without invoking an answer provider.

## 3. Data Flow

```text
authenticated query
  -> query EmbeddingProvider
  -> validate unit vector
  -> VectorRepository.search(tenant, base, vector, topK)
  -> PromptBuilder(system + question + explicitly untrusted numbered contexts)
  -> AnswerProvider
  -> citation allowlist validation
  -> answer + citations
```

The same repository instance used by Embedding is injected into RAG. Vector records retain the
validated chunk text needed for context construction. The API never accepts a tenant in the JSON body.

## 4. Prompt and Citation Rules

The canonical template is [rag-prompt-v1.md](../07-agent/rag-prompt-v1.md). Document text is delimited,
labelled untrusted, and cannot replace system instructions. Providers receive an immutable allowlist of
retrieved chunk IDs. Unknown, duplicate, or out-of-range citations cause `RAG-S-002`; citations are
never synthesized from a model string without matching repository metadata.

## 5. Providers

- `AnswerProvider.answer(question, prompt, contexts)` is constructor-injected and returns answer text,
  model/version, and cited chunk IDs.
- `DeterministicAnswerProvider` is the offline default. It quotes bounded text from the top result and
  cites only supplied contexts; it is a pipeline fixture, not a language-quality model.
- Non-deterministic production providers require explicit injection. Keys remain deployment secrets.

## 6. Security, Quality, and Compatibility

Tests cover pre-score ACL filtering, query/vector poisoning, direct/indirect prompt injection fixtures,
empty retrieval, citation forgery, provider failure, strict API decoding, and a latency/quality fixture.
P99 target is 50 ms for query embedding + 1,000-record search + deterministic answer. The module is
additive and introduces no public Plugin SPI or production vector-store claim.
