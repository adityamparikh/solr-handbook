// SPDX-License-Identifier: Apache-2.0

package solrbook.search;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.common.params.SolrParams;

/**
 * The {@code language-models} module lifecycle from §3.6 of the handbook (Solr 9.8+,
 * named {@code llm} before 10.0): register the {@code knn_text_to_vector} query parser
 * and the text-to-vector update processor via the Config API, upload a model reference
 * to the managed model store, and build queries that let Solr embed the query text
 * itself.
 *
 * <p>Requires the module on the Solr side: {@code SOLR_MODULES=language-models}.
 */
public final class TextToVector {

  /** Solr 10 class names; in Solr 9.8/9.9 these live under {@code org.apache.solr.llm.*}. */
  public static final String QPARSER_CLASS =
      "org.apache.solr.languagemodels.textvectorisation.search.TextToVectorQParserPlugin";

  public static final String URP_CLASS =
      "solr.languagemodels.textvectorisation.update.processor.TextToVectorUpdateProcessorFactory";

  private TextToVector() {}

  /** Registers the {@code knn_text_to_vector} query parser on the collection's config. */
  public static void registerQueryParser(SolrClient client, String collection) throws Exception {
    postConfig(
        client,
        collection,
        """
        { "add-queryparser": { "name": "knn_text_to_vector", "class": "%s" } }
        """
            .formatted(QPARSER_CLASS));
  }

  /**
   * Registers a named text-to-vector update processor. Indexing requests opt in with the
   * {@code processor=<name>} request parameter — the runtime equivalent of the
   * updateRequestProcessorChain shown in the book.
   */
  public static void registerUpdateProcessor(
      SolrClient client, String collection, String name, String inputField, String outputField,
      String model)
      throws Exception {
    postConfig(
        client,
        collection,
        """
        { "add-updateprocessor": {
            "name": "%s",
            "class": "%s",
            "inputField": "%s",
            "outputField": "%s",
            "model": "%s" } }
        """
            .formatted(name, URP_CLASS, inputField, outputField, model));
  }

  /**
   * A model in this module is a named reference to an external embedding API, not
   * weights. This builds the OpenAI-compatible variant; {@code baseUrl} makes it work
   * against any OpenAI-compatible endpoint, including locally hosted inference servers.
   */
  public static String openAiModelJson(
      String name, String baseUrl, String apiKey, String modelName) {
    return """
        {
          "class": "dev.langchain4j.model.openai.OpenAiEmbeddingModel",
          "name": "%s",
          "params": {
            "baseUrl": "%s",
            "apiKey": "%s",
            "modelName": "%s",
            "timeout": 60,
            "maxRetries": 2
          }
        }
        """
        .formatted(name, baseUrl, apiKey, modelName);
  }

  /** PUT the model JSON into the managed text-to-vector model store. */
  public static void uploadModel(SolrClient client, String collection, String modelJson)
      throws Exception {
    GenericSolrRequest req =
        new GenericSolrRequest(
                SolrRequest.METHOD.PUT,
                "/schema/text-to-vector-model-store",
                SolrParams.of())
            .withContent(modelJson.getBytes(StandardCharsets.UTF_8), "application/json")
            .setRequiresCollection(true);
    req.process(client, collection);
  }

  /** §3.6: the query body is text — Solr embeds it with the named model, then runs HNSW. */
  public static String knnTextToVectorQuery(String model, String field, int topK, String text) {
    return String.format(
        Locale.ROOT, "{!knn_text_to_vector model=%s f=%s topK=%d}%s", model, field, topK, text);
  }

  private static void postConfig(SolrClient client, String collection, String json)
      throws Exception {
    GenericSolrRequest req =
        new GenericSolrRequest(SolrRequest.METHOD.POST, "/config", SolrParams.of())
            .withContent(json.getBytes(StandardCharsets.UTF_8), "application/json")
            .setRequiresCollection(true);
    req.process(client, collection);
  }
}
