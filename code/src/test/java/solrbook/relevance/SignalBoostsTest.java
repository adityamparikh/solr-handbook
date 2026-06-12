package solrbook.relevance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignalBoostsTest {

  @Test
  void buildsAMultiplicativeSafeBoostExpression() {
    Map<String, Double> weights = new LinkedHashMap<>();
    weights.put("ST-001", 1.0);
    weights.put("WED-001", 0.554);

    String boost = SignalBoosts.boostExpression(weights);
    assertEquals(
        "sum(1,if(termfreq(id,'ST-001'),1.000,0),if(termfreq(id,'WED-001'),0.554,0))", boost);
  }

  @Test
  void leadingOneKeepsUnsignaledDocsAlive() {
    // boost is multiplicative: without the leading 1, a doc with no signal would have
    // its BM25 score multiplied by 0.
    String boost = SignalBoosts.boostExpression(Map.of("ST-001", 0.7));
    assertTrue(boost.startsWith("sum(1,"));
  }

  @Test
  void emptySignalsMeansNoBoostParameter() {
    assertNull(SignalBoosts.boostExpression(Map.of()));
  }
}
