# Findings - RAG incremental ingest content hash

| ID | Status | Finding | Evidence | Disposition |
| --- | --- | --- | --- | --- |
| F-001 | closed | Incremental ingest should remain opt-in; default behavior must not add lookup cost. | `skipExistingContentHash` defaults `Boolean.FALSE`; `VectorStore.exists` defaults false. | Implemented. |
| F-002 | closed | Lookup must happen before embedding to save actual cost. | `IngestionPipeline` builds metadata/hash first, filters `ingestableChunks`, then embeds only remaining chunks. | Implemented. |
| F-003 | accepted-risk | Live backend API behavior was not smoke-tested. | Local HTTP tests cover Qdrant/Milvus request bodies; PgVector/Redis are deterministic code paths without live services. | Accepted as local-required scope; live smoke opt-in only. |
