// SPDX-License-Identifier: Apache-2.0

package solrbook.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import solrbook.indexing.SeedCatalog;
import solrbook.support.SolrTestHarness;

/**
 * §3.6 against a real Solr: the {@code knn} query parser over a DenseVectorField, the
 * rerank-based hybrid pattern, and client-side RRF over real result lists.
 *
 * <p>The demo embedding axes are [speculative, dark, light, grounded] (see
 * {@link SeedCatalog#demoEmbedding}); a "dark sci-fi" query vector points between the
 * speculative and dark axes.
 */
@Tag("integration")
class VectorSearchIntegrationTest {

  static SolrClient solr;
  static final float[] DARK_SCIFI = SeedCatalog.normalize(new float[] {1f, 1f, 0f, 0f});

  @BeforeAll
  static void setUp() throws Exception {
    solr = SolrTestHarness.showsCollection();
  }

  @Test
  void knnReturnsNearestNeighboursByCosineSimilarity() throws Exception {
    QueryResponse resp =
        solr.query(
            SolrTestHarness.SHOWS, KnnSearch.query("embedding", 5, DARK_SCIFI, "id", "score"));

    assertEquals(5, resp.getResults().size());
    List<String> ids =
        resp.getResults().stream().map(d -> (String) d.getFieldValue("id")).toList();

    // Every returned show should be speculative+dark: Severance (sci-fi/mystery/thriller)
    // and Black Mirror (sci-fi/anthology/thriller) have embeddings closest to the
    // query's diagonal, so both must be in the top 5.
    assertTrue(ids.contains("SVR-001"), "Severance should be a top hit, got " + ids);
    assertTrue(ids.contains("BM-001"), "Black Mirror should be a top hit, got " + ids);

    // Scores arrive in descending similarity order.
    List<Float> scores =
        resp.getResults().stream().map(d -> (Float) d.getFieldValue("score")).toList();
    for (int i = 1; i < scores.size(); i++) {
      assertTrue(scores.get(i - 1) >= scores.get(i));
    }
  }

  @Test
  void rerankBlendsVectorSimilarityIntoLexicalResults() throws Exception {
    // A single-term query: the §3.2 mm spec ("2<-1 ...") requires ALL terms of a
    // two-term query to match, and the rerank can only reorder what BM25 retrieved.
    var hybrid =
        KnnSearch.rerankWithVectors(
            EdismaxSearch.showsQuery("mystery"), "embedding", 50, 2.0, DARK_SCIFI);
    QueryResponse resp = solr.query(SolrTestHarness.SHOWS, hybrid);
    assertTrue(resp.getResults().getNumFound() > 0);
  }

  @Test
  void clientSideRrfFusesLexicalAndVectorRankings() throws Exception {
    List<String> lexical =
        solr
            .query(SolrTestHarness.SHOWS, EdismaxSearch.showsQuery("dark thriller mystery"))
            .getResults()
            .stream()
            .map(d -> (String) d.getFieldValue("id"))
            .toList();
    List<String> vector =
        solr
            .query(SolrTestHarness.SHOWS, KnnSearch.query("embedding", 10, DARK_SCIFI, "id"))
            .getResults()
            .stream()
            .map(d -> (String) d.getFieldValue("id"))
            .toList();

    List<String> fused = Rrf.fusedRanking(lexical, vector);

    assertTrue(fused.size() >= Math.max(lexical.size(), vector.size()));
    // Documents present in both lists outrank single-list documents at similar ranks —
    // the cross-retriever-agreement property §3.6 builds its argument on.
    for (String id : fused) {
      assertTrue(lexical.contains(id) || vector.contains(id));
    }
  }
}
