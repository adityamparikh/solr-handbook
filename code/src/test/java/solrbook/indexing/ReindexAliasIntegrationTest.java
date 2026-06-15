// SPDX-License-Identifier: Apache-2.0

package solrbook.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import solrbook.support.SolrTestHarness;

/**
 * The §2.9 blue/green reindex: bulk-copy with cursorMark into a new collection, verify
 * counts, atomically swap the alias, and roll back by repointing the alias.
 */
@Tag("integration")
class ReindexAliasIntegrationTest {

  static final String V1 = "shows_v1";
  static final String V2 = "shows_v2";
  static final String ALIAS = "shows_alias";
  static SolrClient solr;

  @BeforeAll
  static void setUp() throws Exception {
    solr = SolrTestHarness.createCollection(V1);
    ShowsSchema.create(solr, V1);
    ShowIndexer.indexShows(solr, V1, SeedCatalog.shows());
    solr.commit(V1);
  }

  private long count(String collectionOrAlias) throws Exception {
    return solr.query(collectionOrAlias, new SolrQuery("*:*").setRows(0))
        .getResults()
        .getNumFound();
  }

  @Test
  void blueGreenReindexAndAliasSwap() throws Exception {
    // 1. Alias starts on the old collection; the app only ever talks to the alias.
    AliasSwap.pointAliasAt(solr, ALIAS, V1);
    assertEquals(30, count(ALIAS));

    // 2. Build the new collection in parallel (same schema here; in real life this is
    //    where the schema change happens).
    SolrTestHarness.createCollection(V2);
    ShowsSchema.create(solr, V2);

    // 3. Bulk copy with cursorMark paging.
    long copied = BulkReindexer.reindex(solr, V1, V2);
    assertEquals(30, copied);

    // 4. Verify before swapping: doc counts must match (§2.9.5).
    assertEquals(count(V1), count(V2));

    // 5. Atomic swap: re-issuing CREATEALIAS repoints the alias.
    AliasSwap.pointAliasAt(solr, ALIAS, V2);
    assertEquals(30, count(ALIAS));

    // 6. One-line rollback works the same way.
    AliasSwap.pointAliasAt(solr, ALIAS, V1);
    assertEquals(30, count(ALIAS));
  }
}
