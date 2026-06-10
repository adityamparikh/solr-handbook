package solrbook.search;

import org.apache.solr.client.solrj.request.SolrQuery;

/**
 * The production eDisMax query from §3.2 of the handbook: per-field boosts, phrase
 * boosts on the full query and on bigrams, minimum-should-match, an additive boost for
 * ongoing shows, and a multiplicative recency decay.
 */
public final class EdismaxSearch {

  private EdismaxSearch() {}

  /** "Find a stranger-things-like show, prefer newer and ongoing series." */
  public static SolrQuery showsQuery(String userQuery) {
    SolrQuery q = new SolrQuery();
    q.setRequestHandler("/select");
    q.setParam("defType", "edismax");
    q.setQuery(userQuery);
    q.setParam("qf", "title^3 cast^2 description^1 text_all^0.5");
    q.setParam("pf", "title^5");
    q.setParam("pf2", "title^3 description^1");
    q.setParam("ps", "2");
    q.setParam("mm", "2<-1 5<75%");
    q.setParam("tie", "0.1");
    q.setParam("bq", "status:ongoing^5");
    // 3.16e-11 ~= 1/ms-per-year: one-year half-life recency decay.
    q.setParam("boost", "recip(ms(NOW,added_at),3.16e-11,1,1)");
    q.setFields("id", "title", "rating", "platforms", "score");
    q.setRows(20);
    return q;
  }

  /** Same query restricted by classic cached filter queries. */
  public static SolrQuery showsQuery(String userQuery, String... filterQueries) {
    SolrQuery q = showsQuery(userQuery);
    for (String fq : filterQueries) {
      q.addFilterQuery(fq);
    }
    return q;
  }
}
