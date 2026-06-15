// SPDX-License-Identifier: Apache-2.0

package solrbook.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenAiEmbeddingStubTest {

  private static double cosine(float[] a, float[] b) {
    double dot = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
    }
    return dot; // both vectors are unit-length
  }

  @Test
  void embeddingsAreUnitVectors() {
    float[] v = OpenAiEmbeddingStub.embed("a dark sci-fi thriller about memory");
    double norm = 0;
    for (float x : v) {
      norm += x * x;
    }
    assertEquals(1.0, norm, 1e-5);
  }

  @Test
  void textWithNoMarkersStillEmbedsToANonZeroVector() {
    float[] v = OpenAiEmbeddingStub.embed("completely unrelated text");
    double norm = 0;
    for (float x : v) {
      norm += x * x;
    }
    assertTrue(norm > 0, "cosine DenseVectorField rejects zero vectors");
  }

  @Test
  void semanticallySimilarTextsScoreHigherThanDissimilarOnes() {
    float[] query = OpenAiEmbeddingStub.embed("a dark sci-fi thriller about memory");
    float[] severanceLike =
        OpenAiEmbeddingStub.embed("Workers have their memory surgically divided in a dark sci-fi mystery.");
    float[] comedy =
        OpenAiEmbeddingStub.embed("A feel-good sports comedy about an underdog soccer coach.");

    assertTrue(cosine(query, severanceLike) > cosine(query, comedy));
    assertTrue(cosine(query, severanceLike) > 0.9, "same-diagonal texts should be near-parallel");
  }
}
