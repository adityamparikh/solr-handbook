package solrbook.relevance;

import java.util.HashMap;
import java.util.Map;

/**
 * Pass 2 of the signals aggregation in §5.2 of the handbook: turn raw per-(query, doc)
 * click counts into a dampened, per-query-normalized weight
 * {@code log1p(count) / log1p(maxCountForQuery)}, so the heaviest-clicked document for
 * each query gets weight 1.0 and the long tail is compressed logarithmically.
 */
public final class SignalsWeights {

  /** A raw aggregated signal row: one (query, docId) pair with its click count. */
  public record SignalCount(String query, String docId, long count) {}

  private SignalsWeights() {}

  /** Returns weight per (query, docId), keyed as {@code query + "|" + docId}. */
  public static Map<String, Double> computeWeights(Iterable<SignalCount> rows) {
    Map<String, Long> maxByQuery = new HashMap<>();
    for (SignalCount r : rows) {
      maxByQuery.merge(r.query(), r.count(), Math::max);
    }
    Map<String, Double> weights = new HashMap<>();
    for (SignalCount r : rows) {
      long max = maxByQuery.get(r.query());
      double w = Math.log1p(r.count()) / Math.log1p(max);
      weights.put(r.query() + "|" + r.docId(), w);
    }
    return weights;
  }
}
