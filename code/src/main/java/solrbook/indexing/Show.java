package solrbook.indexing;

import java.time.Instant;
import java.util.List;
import org.apache.solr.common.SolrInputDocument;

/**
 * One show from the seed catalog in §2.3 of the handbook. A {@code Show} maps 1:1 onto a
 * Solr document in the {@code shows} collection.
 *
 * @param embedding 4-dimensional demo embedding (the book uses 384-dim sentence embeddings;
 *     the companion code uses 4 dimensions so the vectors are readable and self-contained)
 * @param franchiseId groups related shows for the Collapse examples in §3.9; {@code null}
 *     when the show is not part of a franchise
 */
public record Show(
    String id,
    String title,
    String description,
    List<String> genres,
    List<String> platforms,
    List<String> creator,
    List<String> cast,
    int releaseYear,
    int runtimeMinutes,
    int seasons,
    float rating,
    String status,
    Instant addedAt,
    float[] embedding,
    String franchiseId) {

  public SolrInputDocument toSolrDoc() {
    SolrInputDocument doc = new SolrInputDocument();
    doc.addField("id", id);
    doc.addField("title", title);
    doc.addField("description", description);
    doc.addField("genres", genres);
    doc.addField("platforms", platforms);
    doc.addField("creator", creator);
    doc.addField("cast", cast);
    doc.addField("release_year", releaseYear);
    doc.addField("runtime_minutes", runtimeMinutes);
    doc.addField("seasons", seasons);
    doc.addField("rating", rating);
    doc.addField("status", status);
    doc.addField("added_at", addedAt.toString());
    if (embedding != null) {
      List<Float> vector = new java.util.ArrayList<>(embedding.length);
      for (float v : embedding) {
        vector.add(v);
      }
      doc.addField("embedding", vector);
    }
    if (franchiseId != null) {
      doc.addField("franchise_id", franchiseId);
    }
    // popularity and view_count are in-place-updated by the signals pipeline (Chapter 5);
    // they are deliberately not set at initial-index time.
    return doc;
  }
}
