package solrbook.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.apache.solr.common.SolrInputDocument;
import org.junit.jupiter.api.Test;

class ShowMappingTest {

  @Test
  void mapsAllCatalogFields() {
    Show st = SeedCatalog.shows().get(0);
    SolrInputDocument doc = st.toSolrDoc();

    assertEquals("ST-001", doc.getFieldValue("id"));
    assertEquals("Stranger Things", doc.getFieldValue("title"));
    assertEquals(List.of("sci-fi", "horror", "drama"), doc.getFieldValues("genres").stream().toList());
    assertEquals(2016, doc.getFieldValue("release_year"));
    assertEquals("ongoing", doc.getFieldValue("status"));
    assertEquals(ShowsSchema.VECTOR_DIMENSION, doc.getFieldValues("embedding").size());
  }

  @Test
  void hotCountersAreNotSetAtIndexTime() {
    SolrInputDocument doc = SeedCatalog.shows().get(0).toSolrDoc();
    // popularity and view_count are in-place-update targets (§2.8.2); the indexer
    // must not write them, the signals pipeline owns them.
    assertNull(doc.getFieldValue("popularity"));
    assertNull(doc.getFieldValue("view_count"));
  }

  @Test
  void franchiseIdIsOptional() {
    Show st = SeedCatalog.shows().get(0); // Stranger Things has no franchise
    assertNull(st.franchiseId());
    assertFalse(st.toSolrDoc().containsKey("franchise_id"));
  }
}
