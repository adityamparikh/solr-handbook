package solrbook.relevance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import solrbook.relevance.SignalsWeights.SignalCount;

class SignalsWeightsTest {

  @Test
  void heaviestClickedDocPerQueryGetsWeightOne() {
    Map<String, Double> w =
        SignalsWeights.computeWeights(
            List.of(
                new SignalCount("stranger things", "ST-001", 980),
                new SignalCount("stranger things", "WED-001", 40),
                new SignalCount("the office", "TO-001", 500),
                new SignalCount("the office", "FLG-001", 120)));

    assertEquals(1.0, w.get("stranger things|ST-001"), 1e-12);
    assertEquals(1.0, w.get("the office|TO-001"), 1e-12);
    assertEquals(Math.log1p(40) / Math.log1p(980), w.get("stranger things|WED-001"), 1e-12);
  }

  @Test
  void weightsAreInZeroToOne() {
    Map<String, Double> w =
        SignalsWeights.computeWeights(
            List.of(
                new SignalCount("q", "a", 1),
                new SignalCount("q", "b", 100),
                new SignalCount("q", "c", 10_000)));
    for (double v : w.values()) {
      assertTrue(v > 0 && v <= 1.0);
    }
  }
}
