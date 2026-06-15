// SPDX-License-Identifier: Apache-2.0

package solrbook.relevance;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * NDCG@k exactly as implemented in §5.1 of the handbook: exponential gain
 * {@code (2^grade - 1)} with a log2 position discount, normalized by the ideal DCG over
 * the judged documents.
 */
public final class Ndcg {

  private Ndcg() {}

  /**
   * @param ranked document ids as returned by the engine, best first
   * @param grades judged relevance per document id (typically 0..3); unjudged documents
   *     count as grade 0
   * @param k evaluation depth, e.g. 10 for NDCG@10
   */
  public static double ndcg(List<String> ranked, Map<String, Integer> grades, int k) {
    double dcg = 0;
    for (int i = 0; i < Math.min(k, ranked.size()); i++) {
      int g = grades.getOrDefault(ranked.get(i), 0);
      dcg += (Math.pow(2, g) - 1) / log2(i + 2);
    }
    List<Integer> ideal =
        grades.values().stream().sorted(Comparator.reverseOrder()).limit(k).toList();
    double idcg = 0;
    for (int i = 0; i < ideal.size(); i++) {
      idcg += (Math.pow(2, ideal.get(i)) - 1) / log2(i + 2);
    }
    return idcg == 0 ? 0 : dcg / idcg;
  }

  /** Mean NDCG@k across queries: rankings and judgments keyed by query string. */
  public static double meanNdcg(
      Map<String, List<String>> rankedByQuery, Map<String, Map<String, Integer>> gradesByQuery, int k) {
    if (gradesByQuery.isEmpty()) {
      return 0;
    }
    double sum = 0;
    for (var e : gradesByQuery.entrySet()) {
      sum += ndcg(rankedByQuery.getOrDefault(e.getKey(), List.of()), e.getValue(), k);
    }
    return sum / gradesByQuery.size();
  }

  private static double log2(double x) {
    return Math.log(x) / Math.log(2);
  }
}
