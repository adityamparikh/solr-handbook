package solrbook.relevance;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Query-time signals boosting (§5.3 of the handbook): turn the aggregated per-document
 * weights for a query into an eDisMax multiplicative {@code boost} function of the form
 * {@code sum(1,if(termfreq(id,'DOC'),WEIGHT,0),...)}.
 *
 * <p>The leading {@code 1} is load-bearing: {@code boost} multiplies the BM25 score, so
 * without it every document that has no signal would have its score multiplied by zero.
 * With it, signal-less documents keep their score and signaled documents are scaled by
 * {@code 1 + weight}.
 */
public final class SignalBoosts {

  private SignalBoosts() {}

  /**
   * @param weightsByDocId aggregated signal weight per document id for the current query
   * @return a function-query string usable as the eDisMax {@code boost} parameter, or
   *     {@code null} when there are no signals (callers should omit the parameter)
   */
  public static String boostExpression(Map<String, Double> weightsByDocId) {
    if (weightsByDocId.isEmpty()) {
      return null;
    }
    return weightsByDocId.entrySet().stream()
        .map(
            e ->
                String.format(
                    Locale.ROOT, "if(termfreq(id,'%s'),%.3f,0)", e.getKey(), e.getValue()))
        .collect(Collectors.joining(",", "sum(1,", ")"));
  }
}
