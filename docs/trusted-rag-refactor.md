# Trusted RAG Refactor

InsightFlow is positioned as a trustworthy evidence-based research and report system. Its main distinction from AI Shop is that AI Shop protects business actions, while InsightFlow protects retrieval provenance, citations, and report conclusions.

## Implemented Retrieval Flow

```text
document upload
  -> SHA-256 document snapshot
  -> sentence/boundary-aware chunks with overlap
  -> chunk hash + index + character offsets
  -> optional embedding generation and pgvector storage
  -> lexical ranking + vector ranking
  -> weighted reciprocal rank fusion (RRF)
  -> Evidence with score breakdown and provenance
  -> EvidenceRecord task whitelist
  -> claim/report citation guard
```

The vector path is optional. When it is disabled or temporarily unavailable, internal retrieval falls back to lexical ranking and records `keyword_rrf` as the retrieval strategy.

## Enable Embeddings

The default local mode uses H2 and keyword retrieval. To enable the real embedding and pgvector path, prepare PostgreSQL with the `vector` extension and configure:

```powershell
$env:RAG_EMBEDDING_ENABLED='true'
$env:RAG_EMBEDDING_API_KEY='your-key'
$env:RAG_EMBEDDING_MODEL_NAME='text-embedding-v4'
$env:RAG_EMBEDDING_DIMENSION='1024'
$env:RAG_PGVECTOR_HOST='localhost'
$env:RAG_PGVECTOR_PORT='5432'
$env:RAG_PGVECTOR_DATABASE='agentdemo'
$env:RAG_PGVECTOR_USERNAME='postgres'
$env:RAG_PGVECTOR_PASSWORD='your-password'
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

The embedding dimension must match the model output dimension and the pgvector table dimension.

For a local database, set `DB_PASSWORD` and start the provided Compose service:

```powershell
docker compose -f compose.pgvector.yml up -d
```

The initialization script enables the PostgreSQL `vector` extension. LangChain4j creates the embedding table with the configured dimension. Exact vector search is the default for the small development corpus; `RAG_PGVECTOR_USE_INDEX=true` enables IVFFlat and `RAG_PGVECTOR_INDEX_LIST_SIZE` controls its list count for larger corpora.

## Evaluation

Run the fixed retrieval benchmark:

```http
POST /api/evaluations/retrieval
```

Run keyword and hybrid modes against the same corpus and labels:

```http
POST /api/evaluations/retrieval/compare
```

The response includes `Hit@1`, `Hit@3`, `Hit@5`, `MRR`, category metrics, per-case first relevant rank, and whether vector/rerank paths were enabled. Benchmark runs are persisted in `retrieval_benchmark_run`.

The fixed benchmark contains 10 isolated documents and 20 manually labeled queries: 10 lexical queries and 10 semantic paraphrases. All three modes run against the same `retrieval-benchmark-v1` collection.

| Mode | Hit@1 | Hit@3 | Hit@5 | MRR |
| --- | ---: | ---: | ---: | ---: |
| Keyword | 0.65 | 0.70 | 0.75 | 0.685 |
| Hybrid RRF | 0.75 | 0.85 | 0.95 | 0.817 |
| Hybrid RRF + gte-rerank-v2 | 0.95 | 1.00 | 1.00 | 0.967 |

On the semantic paraphrase subset, rerank improved `Hit@1` from `0.30` to `0.90` and MRR from `0.37` to `0.933`. Traceability coverage remained `1.00` in all modes.

## Resume Positioning

Strong claims that are already backed by code:

- Built sentence-boundary chunking with overlap, document/chunk SHA-256 snapshots, and character-level provenance.
- Integrated an optional LangChain4j embedding and pgvector indexing path with lexical fallback.
- Implemented explainable weighted RRF hybrid retrieval and persisted lexical/vector score components.
- Enforced task-scoped citation whitelisting using the intersection of runtime evidence and persisted `EvidenceRecord` rows.
- Added a reproducible retrieval benchmark API with Hit@K and MRR metrics.
- Added a dedicated `gte-rerank-v2` stage with automatic Hybrid fallback and persisted rerank scores.
- Abstracted Sogou, Bing, and DuckDuckGo HTML search behind an ordered provider chain with failover.

Claims that should wait for more data:

- Retrieval improved from X to Y.
- Hybrid retrieval significantly outperforms keyword retrieval.
- Production-grade vector search throughput or latency.
- Reranker quality improvements.

## Remaining Production Work

The current version is resume-ready. Further work is production hardening rather than evidence for the core project claim: run Testcontainers in CI with Docker, expand the benchmark beyond the current domain, add latency/cost percentiles, and replace HTML search providers with a contracted search API where required.
