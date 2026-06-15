// SPDX-License-Identifier: Apache-2.0

package solrbook.indexing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.schema.AnalyzerDefinition;
import org.apache.solr.client.solrj.request.schema.FieldTypeDefinition;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;

/**
 * Creates the shows-catalog schema from §2.3 of the handbook on top of Solr's
 * {@code _default} configset, using the Schema API (the programmatic equivalent of the
 * managed-schema XML printed in the book).
 *
 * <p>The {@code _default} configset already ships the {@code string}, {@code pint},
 * {@code plong}, {@code pfloat}, and {@code pdate} field types; the English text type and
 * the dense-vector type are added here. The companion code uses 4-dimensional vectors
 * (the book's schema uses 384) so the demo embeddings stay readable.
 */
public final class ShowsSchema {

  public static final int VECTOR_DIMENSION = 4;

  /** The book's §2.3 {@code text_en} chain, registered under a collision-free name. */
  public static final String TEXT_EN = "text_en_shows";

  private ShowsSchema() {}

  public static void create(SolrClient client, String collection) throws Exception {
    addTextEnFieldType(client, collection);
    addVectorFieldType(client, collection);

    // Identity + text fields. text_en: tokenized, lowercased, stopworded, stemmed.
    addField(client, collection, field("title", TEXT_EN, true, true));
    addField(client, collection, withDocValues(field("title_str", "string", true, false), false));
    addField(client, collection, field("description", TEXT_EN, true, true));

    // Catalog facets: string + docValues so the JSON Facet API can aggregate them
    // without an on-heap field cache.
    addField(client, collection, multiValued(docValues(field("genres", "string", true, true))));
    addField(client, collection, multiValued(docValues(field("platforms", "string", true, true))));
    addField(client, collection, multiValued(docValues(field("creator", "string", true, true))));

    // Cast: analyzed for searchability AND a string mirror for facet/sort.
    addField(client, collection, multiValued(field("cast", TEXT_EN, true, true)));
    addField(
        client, collection, multiValued(withDocValues(field("cast_str", "string", true, false), false)));

    // Numeric range/sort fields.
    addField(client, collection, docValues(field("release_year", "pint", true, true)));
    addField(client, collection, docValues(field("runtime_minutes", "pint", true, true)));
    addField(client, collection, docValues(field("seasons", "pint", true, true)));
    addField(client, collection, docValues(field("rating", "pfloat", true, true)));
    addField(client, collection, docValues(field("status", "string", true, true)));
    addField(client, collection, docValues(field("added_at", "pdate", true, true)));

    // Hot counters: indexed=false stored=false docValues=true unlocks the in-place
    // update path (§2.8.2).
    addField(client, collection, inPlaceField("popularity", "pfloat"));
    addField(client, collection, inPlaceField("view_count", "plong"));

    // Dense vector for semantic similarity (§3.6).
    addField(client, collection, field("embedding", "knn_vector_" + VECTOR_DIMENSION, true, true));

    // Franchise grouping for the Collapse/Expand examples (§3.9). Single-valued,
    // docValues — the two schema requirements of the collapse parser.
    addField(client, collection, docValues(field("franchise_id", "string", true, true)));

    // Catch-all destination for default-q search.
    addField(client, collection, multiValued(field("text_all", TEXT_EN, true, false)));

    addCopyField(client, collection, "title", List.of("title_str", "text_all"));
    addCopyField(client, collection, "cast", List.of("cast_str", "text_all"));
    addCopyField(client, collection, "description", List.of("text_all"));
    addCopyField(client, collection, "creator", List.of("text_all"));
  }

  /**
   * The English analysis chain from §2.3/§2.4: standard tokenizer, possessive stripping,
   * lowercasing, stopword removal, Snowball (Porter2) stemming. Index and query chains
   * are symmetric here; query-time synonym expansion would be the one asymmetry worth
   * adding in production (§2.9.2).
   */
  private static void addTextEnFieldType(SolrClient client, String collection) throws Exception {
    FieldTypeDefinition def = new FieldTypeDefinition();
    Map<String, Object> attrs = new LinkedHashMap<>();
    attrs.put("name", TEXT_EN);
    attrs.put("class", "solr.TextField");
    attrs.put("positionIncrementGap", "100");
    def.setAttributes(attrs);

    AnalyzerDefinition analyzer = new AnalyzerDefinition();
    analyzer.setTokenizer(Map.of("class", "solr.StandardTokenizerFactory"));
    analyzer.setFilters(
        List.of(
            Map.of("class", "solr.EnglishPossessiveFilterFactory"),
            Map.of("class", "solr.LowerCaseFilterFactory"),
            Map.of("class", "solr.StopFilterFactory", "ignoreCase", "true"),
            Map.of("class", "solr.SnowballPorterFilterFactory", "language", "English")));
    def.setAnalyzer(analyzer);
    new SchemaRequest.AddFieldType(def).process(client, collection);
  }

  private static void addVectorFieldType(SolrClient client, String collection) throws Exception {
    FieldTypeDefinition def = new FieldTypeDefinition();
    Map<String, Object> attrs = new LinkedHashMap<>();
    attrs.put("name", "knn_vector_" + VECTOR_DIMENSION);
    attrs.put("class", "solr.DenseVectorField");
    attrs.put("vectorDimension", VECTOR_DIMENSION);
    attrs.put("similarityFunction", "cosine");
    def.setAttributes(attrs);
    new SchemaRequest.AddFieldType(def).process(client, collection);
  }

  private static Map<String, Object> field(
      String name, String type, boolean indexed, boolean stored) {
    Map<String, Object> f = new LinkedHashMap<>();
    f.put("name", name);
    f.put("type", type);
    f.put("indexed", indexed);
    f.put("stored", stored);
    return f;
  }

  private static Map<String, Object> docValues(Map<String, Object> f) {
    f.put("docValues", true);
    return f;
  }

  /**
   * docValues with explicit {@code useDocValuesAsStored=false}. copyField destinations must
   * not behave as stored, otherwise atomic updates double-write them (§2.8.1).
   */
  private static Map<String, Object> withDocValues(Map<String, Object> f, boolean asStored) {
    f.put("docValues", true);
    f.put("useDocValuesAsStored", asStored);
    return f;
  }

  private static Map<String, Object> multiValued(Map<String, Object> f) {
    f.put("multiValued", true);
    return f;
  }

  /** The §2.8.2 in-place update shape: numeric, docValues only, single-valued. */
  private static Map<String, Object> inPlaceField(String name, String type) {
    Map<String, Object> f = field(name, type, false, false);
    f.put("docValues", true);
    f.put("multiValued", false);
    return f;
  }

  private static void addField(SolrClient client, String collection, Map<String, Object> def)
      throws Exception {
    new SchemaRequest.AddField(def).process(client, collection);
  }

  private static void addCopyField(
      SolrClient client, String collection, String source, List<String> dests) throws Exception {
    new SchemaRequest.AddCopyField(source, dests).process(client, collection);
  }
}
