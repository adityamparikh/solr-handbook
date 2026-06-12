# Companion code — *Apache Solr: A Practical Engineering Handbook*

Runnable, tested Java code for the examples in the book. Everything here targets
**Solr 10.0.0** and **SolrJ 10.0.0** on **Java 21**, and is verified against a real Solr
in Docker via [Testcontainers](https://java.testcontainers.org/modules/solr/).

## Quick start

```bash
./gradlew build              # compile + unit tests (no Docker needed)
./gradlew integrationTest    # Testcontainers integration tests (requires Docker)

# Test against a different Solr version:
./gradlew integrationTest -Dsolr.image=solr:9.9
```

The integration tests pull the Docker Official Image `solr:10.0.0` on first run.

## What's here, mapped to the book

| Package / class | Book section | What it shows |
|---|---|---|
| `solrbook.indexing.Show`, `SeedCatalog` | §2.3 | The 30-show seed catalog every example draws from |
| `solrbook.indexing.ShowsSchema` | §2.3 | The shows schema built with the Schema API (field types, copyFields, in-place-update fields, `DenseVectorField`) |
| `solrbook.indexing.ShowIndexer` | §2.6 | Batched indexing and `ConcurrentUpdateJettySolrClient` fire-and-forget indexing |
| `solrbook.indexing.AtomicOp` | §2.8.1 | The six atomic-update modifier maps |
| `solrbook.indexing.OptimisticLocking` | §2.8.3 | `_version_`-based optimistic concurrency: retry loop, insert-only, update-only |
| `solrbook.indexing.BulkReindexer` | §2.9.4 | cursorMark bulk reindex with bounded in-flight batches |
| `solrbook.indexing.AliasSwap` | §2.9.3 | Blue/green alias swap and rollback |
| `solrbook.search.EdismaxSearch` | §3.2 | The production eDisMax query (qf/pf/pf2/mm/tie/bq/boost) |
| `solrbook.search.FacetSearch` | §3.3, §5.5 | JSON Facet API requests; `relatedness()` semantic knowledge graph |
| `solrbook.search.KnnSearch` | §3.6 | `{!knn}` vector queries and the `{!rerank}` hybrid pattern |
| `solrbook.search.Rrf` | §3.6 | Client-side Reciprocal Rank Fusion (until native RRF in Solr 10.1) |
| `solrbook.search.TextToVector` | §3.6 | The `language-models` module lifecycle: parser/URP registration, model store upload, `knn_text_to_vector` queries |
| `solrbook.search.CollapseSearch` | §3.9 | Collapse + Expand, one row per franchise |
| `solrbook.relevance.Ndcg` | §5.1 | NDCG@k exactly as derived in the book |
| `solrbook.relevance.SignalsWeights` | §5.2 | Signals aggregation pass 2: log-dampened, per-query-normalized weights |
| `solrbook.relevance.SignalBoosts` | §5.3 | Query-time signals boosting as an eDisMax multiplicative `boost` |

## Tests

- **Unit tests** (`./gradlew test`) — pure-JVM tests for the fusion/metric/expression
  logic. `RrfTest` reproduces the book's §3.6 worked RRF example digit for digit, and
  `NdcgTest` checks the §5.1 formula against hand-computed values.
- **Integration tests** (`./gradlew integrationTest`, JUnit tag `integration`) — start a
  real Solr 10 in Docker (one shared container for the whole suite), build the schema via
  the Schema API, index the seed catalog, and exercise: eDisMax relevance, JSON facets,
  collapse/expand, `relatedness()`, atomic vs. in-place updates, optimistic-concurrency
  409s, cursorMark reindex + alias swap, `{!knn}` vector search, hybrid rerank,
  client-side RRF, the full `language-models` text-to-vector lifecycle (the
  container runs with `SOLR_MODULES=language-models`, and a local OpenAI-compatible
  stub stands in for the embedding service so the test is hermetic), and §4.13's
  security setup (`SecurityIntegrationTest` enables Basic auth on a live cluster via
  `security.json` in ZooKeeper and asserts 401/200/403 — on its own container, since
  flipping auth on would break the shared one).

CI runs both suites on every push that touches `code/` (see
`.github/workflows/code-ci.yml`).

## Notes that differ from a first read of the book

- The demo embeddings are **4-dimensional** unit vectors derived from genres, instead of
  the 384-dim sentence embeddings the book describes — small enough to read and assert
  on, same query-time mechanics.
- `SignalBoosts` emits `sum(1, if(...), ...)` — the leading `1` keeps documents without
  signals from being multiplied to a zero score by the eDisMax `boost` parameter.
- Tests connect with `HttpJettySolrClient` against the container's mapped port. A
  `CloudSolrClient` pointed at a Testcontainers Solr would discover the *container's
  internal* address from cluster state and fail; in production (where nodes advertise
  reachable addresses) `CloudSolrClient` is the right default.
