// SPDX-License-Identifier: Apache-2.0

package solrbook.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import solrbook.support.SolrTestHarness;

/**
 * §2.8 against a real Solr: atomic updates, in-place updates on docValues-only numeric
 * fields, and optimistic concurrency via {@code _version_}. Uses its own collection so the
 * mutations don't disturb the shared read-only {@code shows} data.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PartialUpdateIntegrationTest {

  static final String COLLECTION = "shows_updates";
  static SolrClient solr;

  @BeforeAll
  static void setUp() throws Exception {
    solr = SolrTestHarness.createCollection(COLLECTION);
    ShowsSchema.create(solr, COLLECTION);
    ShowIndexer.indexShows(solr, COLLECTION, SeedCatalog.shows());
    solr.commit(COLLECTION);
  }

  private SolrDocument fetch(String id) throws Exception {
    return solr.getById(COLLECTION, id);
  }

  @Test
  void atomicSetAndAddDistinctModifyOnlyTheNamedFields() throws Exception {
    SolrInputDocument doc = new SolrInputDocument();
    doc.addField("id", "WED-001");
    doc.addField("rating", AtomicOp.set(8.2f));
    doc.addField("platforms", AtomicOp.addDistinct("Hulu"));
    solr.add(COLLECTION, doc);
    solr.commit(COLLECTION);

    SolrDocument updated = fetch("WED-001");
    assertEquals(8.2f, ((Number) updated.getFieldValue("rating")).floatValue());
    assertTrue(updated.getFieldValues("platforms").containsAll(List.of("Netflix", "Hulu")));
    // The rest of the document was reconstructed, not lost (§2.8.1).
    assertEquals("Wednesday", updated.getFieldValue("title"));
    assertEquals(2022, ((Number) updated.getFieldValue("release_year")).intValue());
  }

  @Test
  void atomicAddDistinctIsIdempotent() throws Exception {
    for (int i = 0; i < 2; i++) {
      SolrInputDocument doc = new SolrInputDocument();
      doc.addField("id", "FB-001");
      doc.addField("platforms", AtomicOp.addDistinct("BritBox"));
      solr.add(COLLECTION, doc);
    }
    solr.commit(COLLECTION);
    long count =
        fetch("FB-001").getFieldValues("platforms").stream()
            .filter("BritBox"::equals)
            .count();
    assertEquals(1, count);
  }

  @Test
  void incOnAMissingFieldStartsFromZero() throws Exception {
    SolrInputDocument doc = new SolrInputDocument();
    doc.addField("id", "ST-001");
    doc.addField("view_count", AtomicOp.inc(5));
    solr.add(COLLECTION, doc);
    solr.commit(COLLECTION);

    assertEquals(5L, ((Number) fetch("ST-001").getFieldValue("view_count")).longValue());
  }

  @Test
  void inPlaceUpdateRewritesTheDocValuesColumn() throws Exception {
    // view_count is indexed=false stored=false docValues=true single-valued numeric —
    // the §2.8.2 in-place shape. The update must not disturb the rest of the document.
    SolrInputDocument doc = new SolrInputDocument();
    doc.addField("id", "BB-001");
    doc.addField("view_count", AtomicOp.inc(1));
    solr.add(COLLECTION, doc);

    doc = new SolrInputDocument();
    doc.addField("id", "BB-001");
    doc.addField("popularity", AtomicOp.set(0.93f));
    solr.add(COLLECTION, doc);
    solr.commit(COLLECTION);

    SolrDocument updated = fetch("BB-001");
    assertEquals(1L, ((Number) updated.getFieldValue("view_count")).longValue());
    assertEquals(0.93f, ((Number) updated.getFieldValue("popularity")).floatValue(), 1e-6);
    assertEquals("Breaking Bad", updated.getFieldValue("title"));

    // In-place-updated fields are sortable and usable in function queries.
    var resp =
        solr.query(
            COLLECTION,
            new SolrQuery("*:*").setRows(1).addSort("popularity", SolrQuery.ORDER.desc));
    assertEquals("BB-001", resp.getResults().get(0).getFieldValue("id"));
  }

  @Test
  void versionMismatchFailsWith409() throws Exception {
    SolrDocument current = fetch("OZ-001");
    long version = (Long) current.getFieldValue("_version_");

    SolrInputDocument doc = new SolrInputDocument();
    doc.addField("id", "OZ-001");
    doc.addField("rating", AtomicOp.set(9.9f));
    doc.addField(CommonParams.VERSION_FIELD, version + 12345); // stale/wrong version

    SolrException e =
        org.junit.jupiter.api.Assertions.assertThrows(
            SolrException.class, () -> solr.add(COLLECTION, doc));
    assertEquals(409, e.code());
  }

  @Test
  void insertOnlyRejectsExistingDocuments() throws Exception {
    SolrInputDocument doc = SeedCatalog.shows().get(0).toSolrDoc(); // ST-001 already indexed
    SolrException e =
        org.junit.jupiter.api.Assertions.assertThrows(
            SolrException.class,
            () -> OptimisticLocking.insertOnly(solr, COLLECTION, doc));
    assertEquals(409, e.code());
  }

  @Test
  void updateOnlyRejectsMissingDocuments() {
    SolrInputDocument doc = new SolrInputDocument();
    doc.addField("id", "DOES-NOT-EXIST");
    doc.addField("rating", AtomicOp.set(5.0f));
    SolrException e =
        org.junit.jupiter.api.Assertions.assertThrows(
            SolrException.class,
            () -> OptimisticLocking.updateOnly(solr, COLLECTION, doc));
    assertEquals(409, e.code());
  }

  @Test
  void readModifyWriteWithRetrySucceeds() throws Exception {
    assertTrue(OptimisticLocking.updateRatingWithRetry(solr, COLLECTION, "SUC-001", 9.1f, 3));
    solr.commit(COLLECTION);
    SolrDocument updated = fetch("SUC-001");
    assertEquals(9.1f, ((Number) updated.getFieldValue("rating")).floatValue(), 1e-6);
    assertNotEquals(null, updated.getFieldValue("_version_"));
  }
}
