package solrbook.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the worked RRF example from §3.6 of the handbook, including the exact fused
 * scores in the book's table — the test doubles as a fact-check of the book's arithmetic.
 */
class RrfTest {

  // §3.6: BM25 and KNN ranked lists for "dark sci-fi thriller about memory and identity".
  private static final List<String> BM25 =
      List.of("MH-001", "BB-001", "BM-001", "WW-001", "BRG-001");
  private static final List<String> KNN =
      List.of("SVR-001", "MH-001", "BM-001", "BB-001", "WW-001");

  @Test
  void fusedScoresMatchTheBookTable() {
    Map<String, Double> fused = Rrf.fuse(60, BM25, KNN);

    assertEquals(1.0 / 61 + 1.0 / 62, fused.get("MH-001"), 1e-9); // 0.03252
    assertEquals(1.0 / 62 + 1.0 / 64, fused.get("BB-001"), 1e-9); // 0.03175
    assertEquals(1.0 / 63 + 1.0 / 63, fused.get("BM-001"), 1e-9); // 0.03175
    assertEquals(1.0 / 64 + 1.0 / 65, fused.get("WW-001"), 1e-9); // 0.03101
    assertEquals(1.0 / 61, fused.get("SVR-001"), 1e-9); // 0.01639
    assertEquals(1.0 / 65, fused.get("BRG-001"), 1e-9); // 0.01538
  }

  @Test
  void finalRankingMatchesTheBook() {
    List<String> ranking = Rrf.fusedRanking(BM25, KNN);
    assertEquals(
        List.of("MH-001", "BB-001", "BM-001", "WW-001", "SVR-001", "BRG-001"), ranking);
  }

  @Test
  void crossRetrieverAgreementBeatsSingleListTopRank() {
    // SVR-001 is #1 in the KNN list but appears nowhere in BM25; WW-001 is rank 4 and 5.
    Map<String, Double> fused = Rrf.fuse(60, BM25, KNN);
    assertTrue(fused.get("WW-001") > fused.get("SVR-001"));
  }
}
