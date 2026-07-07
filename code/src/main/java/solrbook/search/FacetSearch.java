// SPDX-License-Identifier: Apache-2.0

package solrbook.search;

import java.util.Map;
import org.apache.solr.client.solrj.request.json.JsonQueryRequest;
import org.apache.solr.client.solrj.request.json.RangeFacetMap;
import org.apache.solr.client.solrj.request.json.TermsFacetMap;

/**
 * JSON Facet API requests from §3.3 and the relatedness() Semantic Knowledge Graph
 * aggregation from §5.5 of the handbook.
 */
public final class FacetSearch {

  private FacetSearch() {}

  /**
   * Terms facet on platforms with an avg(rating) stat sub-facet, a release-year range
   * facet, and a genres terms facet — the §3.3 example.
   */
  public static JsonQueryRequest platformAndYearFacets(String query) {
    TermsFacetMap platformFacet =
        new TermsFacetMap("platforms").setLimit(10).withStatSubFacet("avg_rating", "avg(rating)");
    RangeFacetMap yearRange = new RangeFacetMap("release_year", 1990, 2026, 5);
    TermsFacetMap genresFacet = new TermsFacetMap("genres").setLimit(20);

    return new JsonQueryRequest()
        .setQuery(query)
        .setLimit(20)
        .withFacet("platforms", platformFacet)
        .withFacet("year_buckets", yearRange)
        .withFacet("genres", genresFacet);
  }

  /**
   * §5.5: which genres are most characteristic of a foreground document set, relative to
   * the whole corpus? Returns buckets sorted by relatedness, not by count.
   */
  public static JsonQueryRequest relatedGenres(String foregroundQuery) {
    return new JsonQueryRequest()
        .setQuery("*:*")
        .setLimit(0)
        .withParam("fg", foregroundQuery)
        .withParam("bg", "*:*")
        .withFacet(
            "related_genres",
            Map.of(
                "type", "terms",
                "field", "genres",
                "limit", 10,
                "sort", "r desc",
                "facet", Map.of("r", "relatedness($fg,$bg)")));
  }
}
