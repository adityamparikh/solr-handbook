package solrbook.search;

import java.util.Locale;
import java.util.StringJoiner;
import org.apache.solr.client.solrj.request.SolrQuery;

/**
 * Dense vector search with the {@code knn} query parser (§3.6 of the handbook):
 * {@code q={!knn f=embedding topK=N}[v1, v2, ...]}.
 */
public final class KnnSearch {

  private KnnSearch() {}

  /** Renders the {!knn} query string for a query vector. */
  public static String knnQuery(String field, int topK, float[] vector) {
    StringJoiner joined = new StringJoiner(", ", "[", "]");
    for (float v : vector) {
      joined.add(String.format(Locale.ROOT, "%s", v));
    }
    return "{!knn f=" + field + " topK=" + topK + "}" + joined;
  }

  public static SolrQuery query(String field, int topK, float[] vector, String... fl) {
    SolrQuery q = new SolrQuery(knnQuery(field, topK, vector));
    if (fl.length > 0) {
      q.setFields(fl);
    }
    q.setRows(topK);
    return q;
  }

  /**
   * The §3.6 hybrid rerank pattern: BM25 retrieves a generous pool, then the top
   * {@code reRankDocs} are re-scored with the KNN similarity blended in at
   * {@code reRankWeight}.
   */
  public static SolrQuery rerankWithVectors(
      SolrQuery lexicalQuery, String field, int reRankDocs, double reRankWeight, float[] vector) {
    StringJoiner joined = new StringJoiner(", ", "[", "]");
    for (float v : vector) {
      joined.add(String.format(Locale.ROOT, "%s", v));
    }
    lexicalQuery.setParam(
        "rq",
        "{!rerank reRankQuery=$rqq reRankDocs=" + reRankDocs + " reRankWeight=" + reRankWeight + "}");
    lexicalQuery.setParam("rqq", "{!knn f=" + field + " topK=" + reRankDocs + "}" + joined);
    return lexicalQuery;
  }
}
