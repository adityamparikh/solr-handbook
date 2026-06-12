package solrbook.indexing;

import java.util.Collections;
import java.util.Map;

/**
 * Helpers that wrap Solr's atomic-update modifier maps (§2.8.1 of the handbook) so
 * application code stays readable. The six modifiers below are the only ones Solr
 * supports; anything else needs a client-side read-modify-write.
 */
public final class AtomicOp {

  private AtomicOp() {}

  public static Map<String, Object> set(Object v) {
    return Collections.singletonMap("set", v);
  }

  /** {@code set} to null removes the field. */
  public static Map<String, Object> setNull() {
    return Collections.singletonMap("set", null);
  }

  public static Map<String, Object> inc(Number delta) {
    return Map.of("inc", delta);
  }

  public static Map<String, Object> add(Object v) {
    return Map.of("add", v);
  }

  public static Map<String, Object> addDistinct(Object v) {
    return Map.of("add-distinct", v);
  }

  public static Map<String, Object> remove(Object v) {
    return Map.of("remove", v);
  }

  public static Map<String, Object> removeRegex(String regex) {
    return Map.of("removeregex", regex);
  }
}
