// SPDX-License-Identifier: Apache-2.0

package solrbook.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KnnSearchTest {

  @Test
  void rendersTheKnnParserSyntaxFromTheBook() {
    String q = KnnSearch.knnQuery("embedding", 20, new float[] {0.012f, -0.183f, 0.044f});
    assertEquals("{!knn f=embedding topK=20}[0.012, -0.183, 0.044]", q);
  }

  @Test
  void rerankAddsRqAndRqqParams() {
    var lexical = EdismaxSearch.showsQuery("stranger things");
    var hybrid = KnnSearch.rerankWithVectors(lexical, "embedding", 200, 2.0, new float[] {1f, 0f});
    assertEquals(
        "{!rerank reRankQuery=$rqq reRankDocs=200 reRankWeight=2.0}", hybrid.get("rq"));
    assertEquals("{!knn f=embedding topK=200}[1.0, 0.0]", hybrid.get("rqq"));
  }
}
