// SPDX-License-Identifier: Apache-2.0

package solrbook.lucene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import solrbook.indexing.SeedCatalog;
import solrbook.indexing.Show;

/** Runs the §1.5 plain-Lucene examples against the §2.3 seed catalog, in memory. */
class LuceneShowsIndexTest {

  private final Map<String, Show> byId =
      SeedCatalog.shows().stream().collect(Collectors.toMap(Show::id, Function.identity()));

  private LuceneShowsIndex index;

  @BeforeEach
  void indexCatalog() throws Exception {
    index = LuceneShowsIndex.inMemory();
    for (Show show : SeedCatalog.shows()) {
      index.index(show);
    }
    index.commit();
    index.refresh();
  }

  @AfterEach
  void close() throws Exception {
    index.close();
  }

  @Test
  void allThirtyShowsAreSearchable() throws Exception {
    assertEquals(30, index.numDocs());
  }

  @Test
  void englishAnalyzerStemsQueryAndIndexAlike() throws Exception {
    // "thing" is not in any title; "Things" stems to it at index time.
    assertEquals(List.of("ST-001"), index.search("thing", 10));
  }

  @Test
  void stopWordsAreGoneFromTheIndex() throws Exception {
    // Eight titles start with "The", but EnglishAnalyzer drops it.
    assertEquals(List.of(), index.search("the", 10));
  }

  @Test
  void prefixAndBooleanSyntax() throws Exception {
    assertEquals(List.of("TM-001"), index.search("mandalor*", 10));
    assertEquals(
        List.of("FLG-001", "TO-001"), index.search("office", 10).stream().sorted().toList());
    assertEquals(List.of(), index.search("+stranger +office", 10));
  }

  @Test
  void filtersDoNotScoreAndSortUsesDocValues() throws Exception {
    // Breaking Bad matches the text but not the year range; The Bear is on Hulu.
    List<String> hits =
        index.searchFiltered(
            "crown OR witcher OR stranger OR ozark OR breaking OR bear", "Netflix", 2016, 2019, 10);
    assertEquals(List.of("ST-001", "TC-001", "OZ-001", "WT-001"), hits); // 8.7, 8.6, 8.5, 8.0
  }

  @Test
  void updateIsDeleteThenAddByTerm() throws Exception {
    Show st = byId.get("ST-001");
    Show retitled =
        new Show(
            st.id(), "Stranger Things: The Final Season", st.description(), st.genres(),
            st.platforms(), st.creator(), st.cast(), st.releaseYear(), st.runtimeMinutes(),
            st.seasons(), st.rating(), st.status(), st.addedAt(), st.embedding(),
            st.franchiseId());
    index.index(retitled);

    // Near-real-time: invisible until the searcher is refreshed — no commit needed.
    assertEquals(List.of(), index.search("final", 10));
    index.refresh();
    assertEquals(List.of("ST-001"), index.search("final", 10));
    assertEquals(30, index.numDocs());

    index.delete("ST-001");
    index.refresh();
    assertEquals(29, index.numDocs());
  }

  @Test
  void knnFindsTheClosestEmbedding() throws Exception {
    float[] query = byId.get("ST-001").embedding();
    List<String> hits = index.nearest(query, 3);
    assertEquals(3, hits.size());
    // Several shows can share a genre mix (and so an embedding); the top hit must be exact.
    assertArrayEquals(query, byId.get(hits.get(0)).embedding());
    assertTrue(hits.stream().allMatch(byId::containsKey));
  }
}
