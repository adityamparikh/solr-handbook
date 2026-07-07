// SPDX-License-Identifier: Apache-2.0

package solrbook.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.json.BucketBasedJsonFacet;
import org.apache.solr.client.solrj.response.json.BucketJsonFacet;
import org.apache.solr.client.solrj.response.json.NestableJsonFacet;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import solrbook.indexing.SeedCatalog;
import solrbook.indexing.Show;
import solrbook.support.SolrTestHarness;

/** Chapter 3 against a real Solr: eDisMax, JSON facets, collapse/expand, relatedness. */
@Tag("integration")
class SearchIntegrationTest {

  static SolrClient solr;

  @BeforeAll
  static void setUp() throws Exception {
    solr = SolrTestHarness.showsCollection();
  }

  @Test
  void edismaxFindsStrangerThingsFirst() throws Exception {
    QueryResponse resp =
        solr.query(SolrTestHarness.SHOWS, EdismaxSearch.showsQuery("stranger things"));
    SolrDocumentList docs = resp.getResults();
    assertTrue(docs.getNumFound() >= 1);
    assertEquals("ST-001", docs.get(0).getFieldValue("id"));
  }

  @Test
  void filterQueriesRestrictWithoutChangingScoring() throws Exception {
    QueryResponse resp =
        solr.query(
            SolrTestHarness.SHOWS,
            EdismaxSearch.showsQuery(
                "drama", "platforms:Netflix", "release_year:[2015 TO 2026]"));
    for (var doc : resp.getResults()) {
      String id = (String) doc.getFieldValue("id");
      Show show =
          SeedCatalog.shows().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
      assertTrue(show.platforms().contains("Netflix"), id);
      assertTrue(show.releaseYear() >= 2015, id);
    }
  }

  @Test
  void jsonFacetsMatchTheSeedCatalog() throws Exception {
    QueryResponse resp =
        FacetSearch.platformAndYearFacets("*:*").process(solr, SolrTestHarness.SHOWS);
    NestableJsonFacet facets = resp.getJsonFacetingResponse();

    // Platform bucket counts must equal what the seed catalog actually contains.
    BucketBasedJsonFacet platforms = facets.getBucketBasedFacets("platforms");
    long netflixExpected =
        SeedCatalog.shows().stream().filter(s -> s.platforms().contains("Netflix")).count();
    BucketJsonFacet netflix =
        platforms.getBuckets().stream()
            .filter(b -> b.getVal().equals("Netflix"))
            .findFirst()
            .orElseThrow();
    assertEquals(netflixExpected, netflix.getCount());

    // avg(rating) stat sub-facet is present and plausible.
    Number avgRating = (Number) netflix.getStatValue("avg_rating");
    assertNotNull(avgRating);
    assertTrue(avgRating.doubleValue() > 5 && avgRating.doubleValue() < 10);

    // Genres facet covers the catalog's most common genre.
    BucketBasedJsonFacet genres = facets.getBucketBasedFacets("genres");
    long dramaExpected =
        SeedCatalog.shows().stream().filter(s -> s.genres().contains("drama")).count();
    BucketJsonFacet drama =
        genres.getBuckets().stream()
            .filter(b -> b.getVal().equals("drama"))
            .findFirst()
            .orElseThrow();
    assertEquals(dramaExpected, drama.getCount());
  }

  @Test
  void collapseShowsOneRowPerFranchise() throws Exception {
    QueryResponse resp =
        solr.query(SolrTestHarness.SHOWS, CollapseSearch.collapseByFranchise("star wars"));

    // Only one representative of the STAR-WARS franchise survives the collapse —
    // the best-rated one (TM-001, rating 8.6 > AND-001, 8.4).
    List<String> starWarsRows =
        resp.getResults().stream()
            .filter(d -> "STAR-WARS".equals(d.getFieldValue("franchise_id")))
            .map(d -> (String) d.getFieldValue("id"))
            .toList();
    assertEquals(List.of("TM-001"), starWarsRows);

    // The expand component returns the collapsed sibling.
    Map<String, SolrDocumentList> expanded = resp.getExpandedResults();
    assertNotNull(expanded);
    SolrDocumentList siblings = expanded.get("STAR-WARS");
    assertNotNull(siblings, "expand should return the STAR-WARS group");
    assertEquals("AND-001", siblings.get(0).getFieldValue("id"));
  }

  @Test
  void relatednessRanksSciFiAsMostDufferBrothersGenre() throws Exception {
    QueryResponse resp =
        FacetSearch.relatedGenres("creator:\"The Duffer Brothers\"")
            .process(solr, SolrTestHarness.SHOWS);
    BucketBasedJsonFacet related =
        resp.getJsonFacetingResponse().getBucketBasedFacets("related_genres");
    assertNotNull(related);
    List<Object> topGenres =
        related.getBuckets().stream().limit(3).map(BucketJsonFacet::getVal).toList();
    // The Duffer Brothers show is sci-fi/horror/drama; with relatedness sorting, its
    // distinctive genres outrank ubiquitous ones like plain "drama".
    assertTrue(
        topGenres.contains("sci-fi") || topGenres.contains("horror"),
        "expected sci-fi or horror in " + topGenres);
  }

  @Test
  void debugExplainTransformerAnnotatesResults() throws Exception {
    SolrQuery q = new SolrQuery("title:stranger");
    q.setFields("id", "score", "[explain style=nl]");
    QueryResponse resp = solr.query(SolrTestHarness.SHOWS, q);
    assertTrue(resp.getResults().getNumFound() >= 1);
    assertNotNull(resp.getResults().get(0).getFieldValue("[explain]"));
  }
}
