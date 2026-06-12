package solrbook.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.request.schema.FieldTypeDefinition;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import solrbook.support.OpenAiEmbeddingStub;
import solrbook.support.SolrTestHarness;

/**
 * The full §3.6 {@code language-models} lifecycle against a real Solr 10 with the module
 * enabled: register the {@code knn_text_to_vector} query parser and the text-to-vector
 * update processor via the Config API, upload an OpenAI-compatible model reference,
 * index documents whose vectors Solr fills in by calling the (stubbed) embedding
 * service, and query with plain text.
 *
 * <p>The OpenAI endpoint is a local stub reachable from the container via
 * {@code host.testcontainers.internal}, so the test is hermetic — same wire format,
 * deterministic embeddings.
 */
@Tag("integration")
class TextToVectorIntegrationTest {

  static final String COLLECTION = "shows_t2v";
  static final String MODEL = "stub-embed";
  static final String PROCESSOR = "textToVector";

  static SolrClient solr;
  static OpenAiEmbeddingStub stub;

  @BeforeAll
  static void setUp() throws Exception {
    solr = SolrTestHarness.createCollection(COLLECTION);
    stub = new OpenAiEmbeddingStub(SolrTestHarness.HOST_STUB_PORT);

    // Schema: an analyzed source field and a 4-dim cosine vector destination.
    FieldTypeDefinition vectorType = new FieldTypeDefinition();
    Map<String, Object> attrs = new LinkedHashMap<>();
    attrs.put("name", "knn_vector_4");
    attrs.put("class", "solr.DenseVectorField");
    attrs.put("vectorDimension", 4);
    attrs.put("similarityFunction", "cosine");
    vectorType.setAttributes(attrs);
    new SchemaRequest.AddFieldType(vectorType).process(solr, COLLECTION);
    new SchemaRequest.AddField(
            Map.of("name", "description", "type", "text_general", "indexed", true, "stored", true))
        .process(solr, COLLECTION);
    new SchemaRequest.AddField(
            Map.of("name", "embedding", "type", "knn_vector_4", "indexed", true, "stored", true))
        .process(solr, COLLECTION);

    // The three module steps from the book: parser, processor, model.
    TextToVector.registerQueryParser(solr, COLLECTION);
    TextToVector.registerUpdateProcessor(
        solr, COLLECTION, PROCESSOR, "description", "embedding", MODEL);
    TextToVector.uploadModel(
        solr,
        COLLECTION,
        TextToVector.openAiModelJson(
            MODEL, stub.baseUrl("host.testcontainers.internal"), "test-key", "stub-model"));
    // The model store is a managed resource: reload so live components see the upload.
    CollectionAdminRequest.reloadCollection(COLLECTION).process(solr);

    // Index WITHOUT vectors in the documents — the update processor fills them in by
    // calling the embedding service.
    UpdateRequest update = new UpdateRequest();
    update.setParam("processor", PROCESSOR);
    update.add(doc("T2V-1", "Workers have their memory surgically divided in a dark sci-fi mystery."));
    update.add(doc("T2V-2", "A feel-good sports comedy about an underdog soccer coach."));
    update.add(doc("T2V-3", "A period drama chronicling a royal family through the decades."));
    update.add(doc("T2V-4", "A serial killer crime thriller that follows two detectives."));
    update.process(solr, COLLECTION);
    solr.commit(COLLECTION);
  }

  @AfterAll
  static void tearDown() {
    if (stub != null) {
      stub.close();
    }
  }

  private static SolrInputDocument doc(String id, String description) {
    SolrInputDocument doc = new SolrInputDocument();
    doc.addField("id", id);
    doc.addField("description", description);
    return doc;
  }

  @Test
  void updateProcessorFillsTheVectorFieldAtIndexTime() throws Exception {
    SolrDocument indexed = solr.getById(COLLECTION, "T2V-1");
    assertNotNull(indexed.getFieldValue("embedding"), "URP should have vectorised T2V-1");
    assertEquals(4, indexed.getFieldValues("embedding").size());
  }

  @Test
  void knnTextToVectorEmbedsTheQueryAndRanksByCosine() throws Exception {
    // The query text hits the speculative axis (sci-fi, memory) AND the dark axis
    // (dark, thriller) — the same diagonal as T2V-1. T2V-4 is dark-only, so it lands
    // second; the comedy and the period drama are nearly orthogonal.
    QueryResponse resp =
        solr.query(
            COLLECTION,
            new SolrQuery(
                    TextToVector.knnTextToVectorQuery(
                        MODEL, "embedding", 2, "a dark sci-fi thriller about memory"))
                .setFields("id", "score"));

    List<String> ids =
        resp.getResults().stream().map(d -> (String) d.getFieldValue("id")).toList();
    assertEquals(List.of("T2V-1", "T2V-4"), ids);
  }
}
