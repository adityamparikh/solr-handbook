// SPDX-License-Identifier: Apache-2.0

package solrbook.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import solrbook.indexing.Show;

/**
 * The shows catalog indexed and searched with plain Lucene — no Solr, no HTTP (§1.5 of the
 * handbook). Everything Solr's schema declares in XML is spelled out here in Java: which
 * analyzer each field uses, and which Lucene fields (inverted index, points, doc values,
 * stored, vectors) back each logical field.
 *
 * <p>Not thread-safe for writes beyond what {@link IndexWriter} itself guarantees; reads go
 * through a {@link SearcherManager}, which is the standard way to share searchers.
 */
public final class LuceneShowsIndex implements AutoCloseable {

  private final Directory directory;
  private final Analyzer analyzer;
  private final IndexWriter writer;
  private final SearcherManager searcherManager;

  public LuceneShowsIndex(Directory directory) throws IOException {
    this.directory = directory;
    // Solr's <fieldType><analyzer> becomes a per-field Analyzer map. StringFields are never
    // analyzed, so only the full-text fields need an entry.
    this.analyzer =
        new PerFieldAnalyzerWrapper(
            new StandardAnalyzer(),
            Map.of("title", new EnglishAnalyzer(), "description", new EnglishAnalyzer()));
    this.writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
    // Opening the searcher from the writer gives near-real-time visibility without a commit.
    this.searcherManager = new SearcherManager(writer, null);
  }

  /** A heap-backed index, handy for tests and demos. Use {@code FSDirectory.open} for disk. */
  public static LuceneShowsIndex inMemory() throws IOException {
    return new LuceneShowsIndex(new ByteBuffersDirectory());
  }

  /**
   * Adds or replaces a show. Lucene has no primary key: "update" is an atomic delete-by-term
   * plus add, keyed on whatever field you choose — here {@code id}.
   */
  public void index(Show show) throws IOException {
    writer.updateDocument(new Term("id", show.id()), toDocument(show));
  }

  public void delete(String id) throws IOException {
    writer.deleteDocuments(new Term("id", id));
  }

  /** Makes recent changes searchable (cheap, not durable) — Solr's soft commit. */
  public void refresh() throws IOException {
    searcherManager.maybeRefreshBlocking();
  }

  /** Fsyncs segments and writes a new commit point (durable, expensive) — Solr's hard commit. */
  public void commit() throws IOException {
    writer.commit();
  }

  /** Classic Lucene query syntax against {@code title}, e.g. {@code stranger AND thing*}. */
  public List<String> search(String userQuery, int n) throws IOException, ParseException {
    Query query = new QueryParser("title", analyzer).parse(userQuery);
    return search(query, n, null);
  }

  /**
   * Full-text match on title, filtered to one platform and a release-year range, sorted by
   * rating. The filter clauses don't score — the same split as Solr's {@code q} and {@code fq}.
   */
  public List<String> searchFiltered(
      String userQuery, String platform, int fromYear, int toYear, int n)
      throws IOException, ParseException {
    Query query =
        new BooleanQuery.Builder()
            .add(new QueryParser("title", analyzer).parse(userQuery), Occur.MUST)
            .add(new TermQuery(new Term("platforms", platform)), Occur.FILTER)
            .add(IntPoint.newRangeQuery("release_year", fromYear, toYear), Occur.FILTER)
            .build();
    Sort byRating = new Sort(new SortField("rating", SortField.Type.FLOAT, true));
    return search(query, n, byRating);
  }

  /** Approximate nearest neighbours over the HNSW graph. */
  public List<String> nearest(float[] vector, int k) throws IOException {
    return search(new KnnFloatVectorQuery("embedding", vector, k), k, null);
  }

  /** Documents visible to the current searcher (live docs only). */
  public int numDocs() throws IOException {
    IndexSearcher searcher = searcherManager.acquire();
    try {
      return searcher.getIndexReader().numDocs();
    } finally {
      searcherManager.release(searcher);
    }
  }

  private List<String> search(Query query, int n, Sort sort) throws IOException {
    IndexSearcher searcher = searcherManager.acquire();
    try {
      TopDocs top = sort == null ? searcher.search(query, n) : searcher.search(query, n, sort);
      List<String> ids = new ArrayList<>(top.scoreDocs.length);
      for (ScoreDoc hit : top.scoreDocs) {
        // scoreDocs carry internal doc ids; stored fields turn them back into your data.
        ids.add(searcher.storedFields().document(hit.doc).get("id"));
      }
      return ids;
    } finally {
      searcherManager.release(searcher);
    }
  }

  /**
   * One logical field often needs several Lucene fields. Solr's {@code indexed}, {@code
   * stored}, and {@code docValues} flags decide which of these it adds for you.
   */
  static Document toDocument(Show show) {
    Document doc = new Document();
    // Exact-match key: indexed as a single token, stored so results can return it.
    doc.add(new StringField("id", show.id(), Field.Store.YES));
    // Analyzed full text: postings + positions (phrase queries need them).
    doc.add(new TextField("title", show.title(), Field.Store.YES));
    doc.add(new TextField("description", show.description(), Field.Store.NO));
    for (String platform : show.platforms()) {
      // Indexed for filtering, doc values for faceting/sorting. Same name, two structures.
      doc.add(new StringField("platforms", platform, Field.Store.YES));
      doc.add(new SortedSetDocValuesField("platforms", new BytesRef(platform)));
    }
    // Numbers: a BKD point for range queries, plus a stored copy to return.
    doc.add(new IntPoint("release_year", show.releaseYear()));
    doc.add(new StoredField("release_year", show.releaseYear()));
    // Sort-only field: doc values and nothing else.
    doc.add(new FloatDocValuesField("rating", show.rating()));
    if (show.embedding() != null) {
      doc.add(
          new KnnFloatVectorField(
              "embedding", show.embedding(), VectorSimilarityFunction.COSINE));
    }
    return doc;
  }

  @Override
  public void close() throws IOException {
    searcherManager.close();
    writer.close();
    directory.close();
  }
}
