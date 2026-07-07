// SPDX-License-Identifier: Apache-2.0

package solrbook.relevance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NdcgTest {

  @Test
  void perfectRankingScoresOne() {
    Map<String, Integer> grades = Map.of("a", 3, "b", 2, "c", 1);
    assertEquals(1.0, Ndcg.ndcg(List.of("a", "b", "c"), grades, 10), 1e-12);
  }

  @Test
  void wholeListIrrelevantScoresZero() {
    Map<String, Integer> grades = Map.of("a", 3);
    assertEquals(0.0, Ndcg.ndcg(List.of("x", "y", "z"), grades, 10), 1e-12);
  }

  @Test
  void handComputedExample() {
    // Ranking: [b(grade 2), a(grade 3)], judged also c(grade 1).
    // DCG  = (2^2-1)/log2(2) + (2^3-1)/log2(3)            = 3 + 7/1.58496...
    // IDCG = (2^3-1)/log2(2) + (2^2-1)/log2(3) + (2^1-1)/log2(4)
    Map<String, Integer> grades = Map.of("a", 3, "b", 2, "c", 1);
    double dcg = 3.0 / 1.0 + 7.0 / (Math.log(3) / Math.log(2));
    double idcg =
        7.0 / 1.0 + 3.0 / (Math.log(3) / Math.log(2)) + 1.0 / (Math.log(4) / Math.log(2));
    assertEquals(dcg / idcg, Ndcg.ndcg(List.of("b", "a"), grades, 10), 1e-12);
  }

  @Test
  void truncatesAtK() {
    Map<String, Integer> grades = Map.of("a", 3);
    // "a" sits at rank 3; with k=2 it contributes nothing.
    assertEquals(0.0, Ndcg.ndcg(List.of("x", "y", "a"), grades, 2), 1e-12);
  }

  @Test
  void meanNdcgAveragesAcrossQueries() {
    var ranked =
        Map.of(
            "q1", List.of("a"),
            "q2", List.of("x"));
    var grades =
        Map.of(
            "q1", Map.of("a", 3),
            "q2", Map.of("b", 2));
    assertEquals(0.5, Ndcg.meanNdcg(ranked, grades, 10), 1e-12);
  }
}
