# Design decisions (why this, not that)

Each entry: what I chose, what I didn't, why, and when I'd change my mind. The "when I'd change it" line is what makes an answer sound senior: it shows the choice is a trade-off, not a habit.

---

### 1. Extend the existing file storage service, not a new repo
- **Why:** it already had JWT, OAuth2, RBAC, ownership derived from the principal, a file lifecycle and CI. Permission-aware RAG needs exactly those, so the AI layer sits on real auth instead of a fake one.
- **Change it when:** the AI part needed its own team, release cycle or scaling profile.

### 2. Spring AI, not LangChain4j or Python LangChain
- **Why:** the service is Spring Boot. Spring AI gives pgvector, Ollama, document readers and MCP server starters through auto-configuration, behind interfaces (`EmbeddingModel`, `ChatClient`) so the model provider is a config change.
- **Change it when:** I needed something only another ecosystem has, or the team was Python-first.

### 3. Spring Boot 3.5 + Spring AI 1.1, not Boot 4 + Spring AI 2.0
- **Why:** the app was on Boot 3.3. A minor upgrade to 3.5 is low risk; jumping to Boot 4 is a major migration (Security, Jackson, starters) unrelated to the feature. Spring AI 1.1 already has pgvector, MCP Streamable HTTP and `@McpTool`.
- **Change it when:** I schedule the Boot 4 migration as its own piece of work.

### 4. Identity comes from the JWT, never from the request body, tool arguments or the model
- **Why:** tool arguments are written by the model, and the model can be steered by prompt injection ("search as admin"). Authorization must come from something the model can't control: the authenticated principal.
- **Detail I checked:** whether Spring Security's context is available inside MCP tool execution. (Say what you found in Step 6.)

### 5. Filter inside the vector query, not after retrieval
- **Why:** filtering after top-K silently shrinks results (if 4 of 5 hits belong to someone else, the user gets 1) and leaks data if anyone forgets the filter. Filtering in the query keeps top-K full and correct.
- **Also:** filters are built with `FilterExpressionBuilder`, not string concatenation, so a username with a quote can't widen the filter. There's an integration test for that.
- **Also:** "not found" looks the same for missing and not-owned documents, so other users' documents can't be discovered.

### 6. The vector index mirrors the file lifecycle
- **Why:** a soft-deleted or purged file that's still answerable is a data leak and a compliance bug. Archive and purge delete chunks, restore re-indexes, owner change re-indexes. Integration tests cover archive and restore.
- **Interview line:** "The relational table is the source of truth; the vector index is a derived view that has to be kept in sync."

### 7. After-commit domain events, not direct calls from FileServiceImpl
- **Why:** the file domain stays ignorant of AI code. `AFTER_COMMIT` means a rolled-back upload never gets indexed.
- **Known weakness:** in-process events and `@Async` are lost if the app crashes between commit and indexing. Fixes: an outbox table or SQS (Phase 3). The `index_status` column at least makes stuck files visible and re-runnable.

### 8. Async indexing with a bounded pool
- **Why:** parsing and embedding a large PDF can take seconds to minutes; the upload request shouldn't wait. The bounded queue applies backpressure instead of creating unlimited threads.

### 9. pgvector, not Pinecone, OpenSearch or Chroma
- **Why:** Postgres is already there. Vectors and metadata in one database means one backup story, transactions, and SQL for debugging. AWS RDS supports pgvector.
- **Change it when:** tens of millions of vectors, heavy hybrid keyword search (OpenSearch), or search and metadata needing to scale independently.

### 10. HNSW index with cosine distance
- **Why:** HNSW gives better recall vs latency than IVFFlat and doesn't need existing data to build. Cosine is standard for text embeddings because direction carries meaning, not magnitude.
- **Cost:** more memory and slower index builds. Fine at this size.

### 11. Ollama locally (nomic-embed-text, llama3.2), Bedrock later
- **Why:** free, offline, no API keys in the repo.
- **Catch:** changing the embedding model means re-embedding everything; the dimension is part of the table schema and vectors from different models aren't comparable. The embedding model is effectively a schema decision.
- **Honest limit:** a 3B local model gives weaker answers than a hosted model. Retrieval quality, not the chat model, is what the project demonstrates.

### 12. Page-aware PDF reading, ~500-token chunks
- **Why:** page numbers make citations checkable. 500 tokens balances precision against context.
- **Honest limit:** 500 is a starting point, not a measured optimum. The Phase 3 eval harness exists to tune it.

### 13. No hits means no model call
- **Why:** if retrieval finds nothing above the similarity threshold, calling the model invites a made-up answer and costs tokens. Returning "couldn't find this in your documents" is the honest answer.

### 14. Retrieved text is treated as untrusted data
- **Why:** a document can contain "ignore previous instructions". Defenses are layered: sources go in delimited blocks, the system prompt says to treat them as data, authorization never depends on the model, and the tools are read-only.

### 15. Both `/api/ai/ask` and MCP tools
- **Why:** `/ask` serves my own UI and returns a finished answer. MCP serves clients that already have a model (Claude Desktop, Copilot), so those tools return cited excerpts instead of a second generated answer. Same retrieval and security underneath.

### 16. MCP over Streamable HTTP, not stdio
- **Why:** stdio only works as a local subprocess. HTTP makes it a shared, deployable service protected by the same JWT filter as the REST API. Streamable HTTP replaced the older SSE transport in the MCP spec.

### 17. MCP, not provider-specific function calling
- **Why:** one server works with any MCP client. Function-calling definitions are tied to one vendor's API.
- **Change it when:** there's exactly one consumer with no plans for others; then plain REST is simpler.

### 18. Testcontainers with real pgvector, fake embeddings in tests
- **Why:** H2 has no vector type, so tests would pass against something that isn't production. A deterministic fake embedding model keeps CI fast and free of Ollama while still exercising real SQL and real filters.

### 19. One deployable, not microservices
- **Why:** one team, small load. Splitting indexing from querying adds network calls and failure modes for no benefit yet. The event boundary is where the split would happen if indexing load grew.

---

## What's not built yet (say this plainly if asked)
No agent loop, document sharing, AWS deployment or measured retrieval quality yet. Those are Phases 2 and 3. Saying so upfront reads better than being caught out.
