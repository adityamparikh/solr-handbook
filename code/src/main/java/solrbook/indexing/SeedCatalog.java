// SPDX-License-Identifier: Apache-2.0

package solrbook.indexing;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The 30-show seed catalog from §2.3 (table {@code tbl-02-seed-catalog}) of the handbook.
 * Every query, facet, and signals example in the book draws from this fixed set, so the
 * ids, titles, platforms, genres, release years, and statuses here match the book table
 * exactly. Descriptions, cast, ratings, and runtimes are illustrative.
 */
public final class SeedCatalog {

  private SeedCatalog() {}

  /**
   * Demo embeddings are 4-dimensional, derived deterministically from genres and then
   * L2-normalized. Axis 0 is "speculative" (sci-fi/fantasy/supernatural), axis 1 is
   * "dark" (crime/thriller/horror), axis 2 is "light" (comedy/romance/sports), axis 3
   * is "grounded" (drama/historical/period). The book's 384-dim sentence embeddings
   * behave the same way at query time; these are just small enough to read.
   */
  private static final Map<String, Integer> GENRE_AXIS =
      Map.ofEntries(
          Map.entry("sci-fi", 0),
          Map.entry("fantasy", 0),
          Map.entry("supernatural", 0),
          Map.entry("superhero", 0),
          Map.entry("post-apocalyptic", 0),
          Map.entry("anthology", 0),
          Map.entry("crime", 1),
          Map.entry("thriller", 1),
          Map.entry("horror", 1),
          Map.entry("psychological", 1),
          Map.entry("mystery", 1),
          Map.entry("political", 1),
          Map.entry("action", 1),
          Map.entry("comedy", 2),
          Map.entry("sitcom", 2),
          Map.entry("romance", 2),
          Map.entry("sports", 2),
          Map.entry("food", 2),
          Map.entry("satire", 2),
          Map.entry("adventure", 2),
          Map.entry("drama", 3),
          Map.entry("historical", 3),
          Map.entry("period", 3),
          Map.entry("legal", 3),
          Map.entry("british", 3),
          Map.entry("korean", 3),
          Map.entry("japanese", 3),
          Map.entry("western", 3));

  public static float[] demoEmbedding(List<String> genres) {
    float[] v = new float[4];
    for (String g : genres) {
      Integer axis = GENRE_AXIS.get(g);
      if (axis != null) {
        v[axis] += 1f;
      }
    }
    return normalize(v);
  }

  public static float[] normalize(float[] v) {
    double norm = 0;
    for (float x : v) {
      norm += x * x;
    }
    if (norm == 0) {
      return v;
    }
    double inv = 1.0 / Math.sqrt(norm);
    float[] out = new float[v.length];
    for (int i = 0; i < v.length; i++) {
      out[i] = (float) (v[i] * inv);
    }
    return out;
  }

  private static Show show(
      String id,
      String title,
      String description,
      List<String> genres,
      String platform,
      List<String> creator,
      List<String> cast,
      int year,
      int runtime,
      int seasons,
      float rating,
      String status,
      String addedAt,
      String franchiseId) {
    return new Show(
        id,
        title,
        description,
        genres,
        List.of(platform),
        creator,
        cast,
        year,
        runtime,
        seasons,
        rating,
        status,
        Instant.parse(addedAt),
        demoEmbedding(genres),
        franchiseId);
  }

  public static List<Show> shows() {
    return List.of(
        show(
            "ST-001",
            "Stranger Things",
            "When a young boy vanishes, a small town uncovers a mystery involving secret"
                + " government experiments, supernatural forces, and one strange little girl.",
            List.of("sci-fi", "horror", "drama"),
            "Netflix",
            List.of("The Duffer Brothers"),
            List.of("Millie Bobby Brown", "Finn Wolfhard", "Winona Ryder", "David Harbour"),
            2016, 51, 4, 8.7f, "ongoing", "2024-01-15T00:00:00Z", null),
        show(
            "BB-001",
            "Breaking Bad",
            "A high-school chemistry teacher diagnosed with cancer turns to manufacturing"
                + " methamphetamine to secure his family's future.",
            List.of("crime", "drama", "thriller"),
            "Netflix",
            List.of("Vince Gilligan"),
            List.of("Bryan Cranston", "Aaron Paul", "Anna Gunn"),
            2008, 47, 5, 9.5f, "ended", "2024-02-01T00:00:00Z", "BREAKING-BAD-UNIVERSE"),
        show(
            "BCS-001",
            "Better Call Saul",
            "The trials and tribulations of criminal lawyer Jimmy McGill before he becomes"
                + " Walter White's attorney Saul Goodman.",
            List.of("crime", "drama", "legal"),
            "Netflix",
            List.of("Vince Gilligan", "Peter Gould"),
            List.of("Bob Odenkirk", "Rhea Seehorn", "Jonathan Banks"),
            2015, 46, 6, 9.0f, "ended", "2024-02-10T00:00:00Z", "BREAKING-BAD-UNIVERSE"),
        show(
            "TC-001",
            "The Crown",
            "The political rivalries and romances of Queen Elizabeth II's reign and the"
                + " events that shaped the second half of the twentieth century.",
            List.of("drama", "historical"),
            "Netflix",
            List.of("Peter Morgan"),
            List.of("Claire Foy", "Olivia Colman", "Imelda Staunton"),
            2016, 58, 6, 8.6f, "ended", "2024-03-01T00:00:00Z", null),
        show(
            "WED-001",
            "Wednesday",
            "Wednesday Addams investigates a monstrous mystery at the school where her"
                + " parents met, sharpening her psychic ability along the way.",
            List.of("supernatural", "comedy", "mystery"),
            "Netflix",
            List.of("Alfred Gough", "Miles Millar"),
            List.of("Jenna Ortega", "Catherine Zeta-Jones"),
            2022, 50, 2, 8.0f, "ongoing", "2024-03-15T00:00:00Z", null),
        show(
            "SG-001",
            "Squid Game",
            "Hundreds of cash-strapped players accept a strange invitation to compete in"
                + " children's games with deadly high stakes.",
            List.of("thriller", "drama", "korean"),
            "Netflix",
            List.of("Hwang Dong-hyuk"),
            List.of("Lee Jung-jae", "Park Hae-soo", "Jung Ho-yeon"),
            2021, 55, 3, 8.0f, "ongoing", "2024-04-01T00:00:00Z", null),
        show(
            "BRG-001",
            "Bridgerton",
            "Wealth, lust, and betrayal set against the backdrop of Regency-era England,"
                + " seen through the eyes of the powerful Bridgerton family.",
            List.of("romance", "period", "drama"),
            "Netflix",
            List.of("Chris Van Dusen"),
            List.of("Nicola Coughlan", "Luke Newton", "Jonathan Bailey"),
            2020, 60, 3, 7.4f, "ongoing", "2024-04-15T00:00:00Z", null),
        show(
            "OZ-001",
            "Ozark",
            "A financial advisor drags his family from Chicago to the Missouri Ozarks,"
                + " where he must launder money to appease a drug cartel.",
            List.of("crime", "drama"),
            "Netflix",
            List.of("Bill Dubuque", "Mark Williams"),
            List.of("Jason Bateman", "Laura Linney", "Julia Garner"),
            2017, 60, 4, 8.5f, "ended", "2024-05-01T00:00:00Z", null),
        show(
            "TBR-001",
            "The Bear",
            "A young chef from the fine-dining world returns to Chicago to run his"
                + " family's sandwich shop after a heartbreaking death.",
            List.of("drama", "comedy", "food"),
            "Hulu",
            List.of("Christopher Storer"),
            List.of("Jeremy Allen White", "Ayo Edebiri", "Ebon Moss-Bachrach"),
            2022, 30, 3, 8.5f, "ongoing", "2024-05-15T00:00:00Z", null),
        show(
            "OMITB-001",
            "Only Murders in the Building",
            "Three strangers who share an obsession with true crime podcasts suddenly find"
                + " themselves wrapped up in a murder in their Upper West Side building.",
            List.of("comedy", "mystery"),
            "Hulu",
            List.of("Steve Martin", "John Hoffman"),
            List.of("Steve Martin", "Martin Short", "Selena Gomez"),
            2021, 35, 4, 8.1f, "ongoing", "2024-06-01T00:00:00Z", null),
        show(
            "SH-001",
            "Shōgun",
            "An English sailor shipwrecked in feudal Japan becomes a pawn — and then a"
                + " player — in the deadly politics of warring lords.",
            List.of("drama", "historical", "japanese"),
            "Hulu",
            List.of("Rachel Kondo", "Justin Marks"),
            List.of("Hiroyuki Sanada", "Cosmo Jarvis", "Anna Sawai"),
            2024, 60, 1, 8.6f, "limited", "2024-06-15T00:00:00Z", null),
        show(
            "TBY-001",
            "The Boys",
            "A group of vigilantes sets out to take down corrupt superheroes who abuse"
                + " their celebrity status and corporate backing.",
            List.of("superhero", "satire", "action"),
            "Prime Video",
            List.of("Eric Kripke"),
            List.of("Karl Urban", "Jack Quaid", "Antony Starr"),
            2019, 60, 4, 8.7f, "ongoing", "2024-07-01T00:00:00Z", null),
        show(
            "RCH-001",
            "Reacher",
            "An ex-military investigator drifts into a small Georgia town and is promptly"
                + " arrested for a murder he did not commit.",
            List.of("action", "thriller", "crime"),
            "Prime Video",
            List.of("Nick Santora"),
            List.of("Alan Ritchson", "Maria Sten"),
            2022, 49, 3, 8.0f, "ongoing", "2024-07-15T00:00:00Z", null),
        show(
            "FB-001",
            "Fleabag",
            "A dry-witted woman navigates life and love in London while trying to cope"
                + " with tragedy, angry in a way only family can provoke.",
            List.of("comedy", "drama", "british"),
            "Prime Video",
            List.of("Phoebe Waller-Bridge"),
            List.of("Phoebe Waller-Bridge", "Sian Clifford", "Andrew Scott"),
            2016, 27, 2, 8.7f, "ended", "2024-08-01T00:00:00Z", null),
        show(
            "SVR-001",
            "Severance",
            "Workers undergo a procedure that surgically divides their memories between"
                + " their work and personal lives, until a mysterious colleague appears.",
            List.of("sci-fi", "mystery", "thriller"),
            "Apple TV+",
            List.of("Dan Erickson"),
            List.of("Adam Scott", "Britt Lower", "Patricia Arquette"),
            2022, 55, 2, 8.7f, "ongoing", "2024-08-15T00:00:00Z", null),
        show(
            "TL-001",
            "Ted Lasso",
            "An American college football coach is hired to manage an English Premier"
                + " League soccer club despite having no experience with the game.",
            List.of("comedy", "drama", "sports"),
            "Apple TV+",
            List.of("Bill Lawrence", "Jason Sudeikis"),
            List.of("Jason Sudeikis", "Hannah Waddingham", "Brett Goldstein"),
            2020, 30, 3, 8.8f, "ended", "2024-09-01T00:00:00Z", null),
        show(
            "TM-001",
            "The Mandalorian",
            "In the Star Wars galaxy, a lone bounty hunter makes his way through the outer"
                + " reaches, far from the authority of the New Republic, protecting a"
                + " mysterious child.",
            List.of("sci-fi", "western", "action"),
            "Disney+",
            List.of("Jon Favreau"),
            List.of("Pedro Pascal", "Katee Sackhoff"),
            2019, 40, 3, 8.6f, "ongoing", "2024-09-15T00:00:00Z", "STAR-WARS"),
        show(
            "LK-001",
            "Loki",
            "The mercurial god of mischief steps out of his brother's shadow to fix the"
                + " broken timelines of the multiverse alongside the Time Variance Authority.",
            List.of("sci-fi", "fantasy", "drama"),
            "Disney+",
            List.of("Michael Waldron"),
            List.of("Tom Hiddleston", "Owen Wilson", "Sophia Di Martino"),
            2021, 50, 2, 8.2f, "ongoing", "2024-10-01T00:00:00Z", "MCU"),
        show(
            "AND-001",
            "Andor",
            "A Star Wars story set in an era filled with danger, deception and intrigue:"
                + " Cassian Andor discovers the difference he can make in the rebellion"
                + " against the Empire.",
            List.of("sci-fi", "drama", "political"),
            "Disney+",
            List.of("Tony Gilroy"),
            List.of("Diego Luna", "Genevieve O'Reilly", "Stellan Skarsgård"),
            2022, 45, 2, 8.4f, "ongoing", "2024-10-15T00:00:00Z", "STAR-WARS"),
        show(
            "TLOU-001",
            "The Last of Us",
            "Twenty years after a fungal pandemic destroys civilization, a hardened"
                + " survivor smuggles a teenage girl who may be humanity's last hope.",
            List.of("drama", "post-apocalyptic", "horror"),
            "Max",
            List.of("Craig Mazin", "Neil Druckmann"),
            List.of("Pedro Pascal", "Bella Ramsey"),
            2023, 55, 2, 8.7f, "ongoing", "2024-11-01T00:00:00Z", null),
        show(
            "HOTD-001",
            "House of the Dragon",
            "The Targaryen dynasty is at the absolute apex of its power, until an internal"
                + " succession war turns dragon against dragon.",
            List.of("fantasy", "drama", "action"),
            "Max",
            List.of("Ryan Condal", "George R. R. Martin"),
            List.of("Emma D'Arcy", "Matt Smith", "Olivia Cooke"),
            2022, 60, 2, 8.4f, "ongoing", "2024-11-15T00:00:00Z", null),
        show(
            "SUC-001",
            "Succession",
            "The Roy family controls one of the biggest media conglomerates in the world,"
                + " and a battle for control erupts as their father steps back.",
            List.of("drama", "satire"),
            "Max",
            List.of("Jesse Armstrong"),
            List.of("Brian Cox", "Jeremy Strong", "Sarah Snook"),
            2018, 60, 4, 8.9f, "ended", "2024-12-01T00:00:00Z", null),
        show(
            "WW-001",
            "Westworld",
            "At a futuristic theme park staffed by androids, guests live out their"
                + " fantasies — until the hosts begin to remember.",
            List.of("sci-fi", "western", "drama"),
            "Max",
            List.of("Jonathan Nolan", "Lisa Joy"),
            List.of("Evan Rachel Wood", "Thandiwe Newton", "Jeffrey Wright"),
            2016, 60, 4, 8.5f, "cancelled", "2024-12-15T00:00:00Z", null),
        show(
            "BM-001",
            "Black Mirror",
            "An anthology series exploring a twisted, high-tech multiverse where"
                + " humanity's greatest innovations and darkest instincts collide.",
            List.of("sci-fi", "anthology", "thriller"),
            "Netflix",
            List.of("Charlie Brooker"),
            List.of("Various"),
            2011, 60, 6, 8.7f, "ongoing", "2025-01-01T00:00:00Z", null),
        show(
            "PB-001",
            "Peaky Blinders",
            "A gangster family epic set in 1900s Birmingham, centered on a gang who sew"
                + " razor blades in the peaks of their caps.",
            List.of("crime", "period", "british"),
            "Netflix",
            List.of("Steven Knight"),
            List.of("Cillian Murphy", "Paul Anderson", "Helen McCrory"),
            2013, 60, 6, 8.8f, "ended", "2025-01-15T00:00:00Z", null),
        show(
            "MH-001",
            "Mindhunter",
            "FBI agents interview imprisoned serial killers to understand their psychology"
                + " and apply that knowledge to ongoing cases.",
            List.of("crime", "psychological", "drama"),
            "Netflix",
            List.of("Joe Penhall"),
            List.of("Jonathan Groff", "Holt McCallany", "Anna Torv"),
            2017, 54, 2, 8.6f, "cancelled", "2025-02-01T00:00:00Z", null),
        show(
            "WT-001",
            "The Witcher",
            "A mutated monster-hunter for hire journeys toward his destiny in a turbulent"
                + " world where people often prove more wicked than beasts.",
            List.of("fantasy", "action", "adventure"),
            "Netflix",
            List.of("Lauren Schmidt Hissrich"),
            List.of("Henry Cavill", "Anya Chalotra", "Freya Allan"),
            2019, 60, 3, 8.0f, "ongoing", "2025-02-15T00:00:00Z", null),
        show(
            "YS-001",
            "Yellowstone",
            "A ranching family in Montana fights to defend their land and legacy from"
                + " encroaching developers, politicians, and a nearby reservation.",
            List.of("drama", "western"),
            "Peacock",
            List.of("Taylor Sheridan", "John Linson"),
            List.of("Kevin Costner", "Kelly Reilly", "Wes Bentley"),
            2018, 55, 5, 8.6f, "ended", "2025-03-01T00:00:00Z", null),
        show(
            "TO-001",
            "The Office (US)",
            "A mockumentary on a group of typical office workers at a Scranton paper"
                + " company, where the workday consists of ego clashes and tedium.",
            List.of("comedy", "sitcom"),
            "Peacock",
            List.of("Greg Daniels"),
            List.of("Steve Carell", "Rainn Wilson", "John Krasinski", "Jenna Fischer"),
            2005, 22, 9, 9.0f, "ended", "2025-03-15T00:00:00Z", "THE-OFFICE"),
        show(
            "FLG-001",
            "The Office (UK)",
            "The original mockumentary about the staff of a Slough paper merchant and"
                + " their excruciating general manager David Brent.",
            List.of("comedy", "sitcom"),
            "BritBox",
            List.of("Ricky Gervais", "Stephen Merchant"),
            List.of("Ricky Gervais", "Martin Freeman", "Mackenzie Crook"),
            2001, 30, 2, 8.5f, "ended", "2025-04-01T00:00:00Z", "THE-OFFICE"));
  }
}
