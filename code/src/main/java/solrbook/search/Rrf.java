package solrbook.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side Reciprocal Rank Fusion (§3.6 of the handbook). Native RRF lands in Solr
 * 10.1 / 9.11 (SOLR-17319); until then, run the lexical and vector queries in parallel
 * and fuse the two ranked lists here: {@code RRF(d) = sum over retrievers of 1/(k + rank)}.
 *
 * <p>Score scales never enter the computation — only rank positions — which is the whole
 * point: BM25 scores and cosine similarities are not comparable, ranks are.
 */
public final class Rrf {

  /** The canonical Cormack/Clarke/Büttcher (2009) constant. */
  public static final int DEFAULT_K = 60;

  private Rrf() {}

  /**
   * Fuses ranked lists of document ids (best first). Returns doc id to fused score,
   * ordered by descending score.
   */
  @SafeVarargs
  public static Map<String, Double> fuse(int k, List<String>... rankedLists) {
    Map<String, Double> fused = new HashMap<>();
    for (List<String> list : rankedLists) {
      for (int i = 0; i < list.size(); i++) {
        int rank = i + 1; // ranks are 1-based
        fused.merge(list.get(i), 1.0 / (k + rank), Double::sum);
      }
    }
    Map<String, Double> ordered = new LinkedHashMap<>();
    fused.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .forEach(e -> ordered.put(e.getKey(), e.getValue()));
    return ordered;
  }

  /** Fused ranking with the canonical k=60. */
  @SafeVarargs
  public static List<String> fusedRanking(List<String>... rankedLists) {
    return new ArrayList<>(fuse(DEFAULT_K, rankedLists).keySet());
  }
}
