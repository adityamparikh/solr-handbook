package solrbook.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Guards the invariants the book states about the §2.3 seed catalog. */
class SeedCatalogTest {

  private final List<Show> shows = SeedCatalog.shows();

  @Test
  void thirtyShowsWithUniqueIds() {
    assertEquals(30, shows.size());
    assertEquals(30, shows.stream().map(Show::id).distinct().count());
  }

  @Test
  void spansEightPlatforms() {
    Set<String> platforms =
        shows.stream().flatMap(s -> s.platforms().stream()).collect(Collectors.toSet());
    assertEquals(
        Set.of(
            "Netflix", "Hulu", "Prime Video", "Apple TV+", "Disney+", "Max", "Peacock", "BritBox"),
        platforms);
  }

  @Test
  void statusValuesAreTheFourTheBookUses() {
    Set<String> statuses = shows.stream().map(Show::status).collect(Collectors.toSet());
    assertEquals(Set.of("ongoing", "ended", "cancelled", "limited"), statuses);
  }

  @Test
  void roughlyTwentyDistinctGenres() {
    Set<String> genres =
        shows.stream().flatMap(s -> s.genres().stream()).collect(Collectors.toSet());
    assertTrue(genres.size() >= 18 && genres.size() <= 30, "got " + genres.size());
  }

  @Test
  void embeddingsAreUnitVectors() {
    for (Show s : shows) {
      double norm = 0;
      for (float v : s.embedding()) {
        norm += v * v;
      }
      assertEquals(1.0, norm, 1e-5, s.id());
    }
  }

  @Test
  void strangerThingsIsTheDufferBrothersShow() {
    Show st = shows.stream().filter(s -> s.id().equals("ST-001")).findFirst().orElseThrow();
    assertEquals("Stranger Things", st.title());
    assertEquals(List.of("The Duffer Brothers"), st.creator());
    assertEquals(2016, st.releaseYear());
  }

  @Test
  void starWarsShowsShareAFranchiseForCollapseExamples() {
    List<Show> starWars =
        shows.stream().filter(s -> "STAR-WARS".equals(s.franchiseId())).toList();
    assertEquals(
        Set.of("TM-001", "AND-001"),
        starWars.stream().map(Show::id).collect(Collectors.toSet()));
  }
}
