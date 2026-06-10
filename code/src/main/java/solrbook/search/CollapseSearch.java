package solrbook.search;

import org.apache.solr.client.solrj.request.SolrQuery;

/**
 * Collapse + Expand from §3.9 of the handbook: one row per franchise, with the collapsed
 * siblings re-fetched by the expand component. The collapse field must be single-valued
 * with docValues, and in a multi-shard collection all members of a group must be routed
 * to the same shard.
 */
public final class CollapseSearch {

  private CollapseSearch() {}

  /** One representative per franchise — by default the best-rated one. */
  public static SolrQuery collapseByFranchise(String userQuery) {
    SolrQuery q =
        new SolrQuery(userQuery)
            .setRequestHandler("/select")
            .addFilterQuery("{!collapse field=franchise_id max=rating nullPolicy=expand}")
            .setFields("id", "title", "rating", "franchise_id");
    q.set("defType", "edismax");
    q.set("qf", "title^3 description^1 text_all^0.5");
    q.set("expand", "true");
    q.set("expand.rows", "4");
    return q;
  }
}
