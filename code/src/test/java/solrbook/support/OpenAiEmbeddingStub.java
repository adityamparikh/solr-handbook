package solrbook.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import solrbook.indexing.SeedCatalog;

/**
 * A hermetic stand-in for the OpenAI embeddings endpoint, so the {@code language-models}
 * integration test never leaves the machine. Speaks the wire format LangChain4j 0.35
 * (the version bundled with Solr 10) expects: POST {@code .../embeddings} with
 * {@code {"model": ..., "input": [...]}}, answering with float-array embeddings.
 *
 * <p>The "model" is the same 4-axis keyword scheme as {@link SeedCatalog#demoEmbedding}:
 * axis 0 speculative, axis 1 dark, axis 2 light, axis 3 grounded — derived from marker
 * words in the text and L2-normalized, so tests can reason about cosine ordering.
 */
public final class OpenAiEmbeddingStub implements AutoCloseable {

  private static final Map<Integer, List<String>> AXIS_MARKERS =
      Map.of(
          0, List.of("sci-fi", "scifi", "supernatural", "fantasy", "space", "android", "memory"),
          1, List.of("crime", "thriller", "dark", "horror", "mystery", "killer"),
          2, List.of("comedy", "funny", "sitcom", "romance", "feel-good", "sports"),
          3, List.of("drama", "historical", "period", "royal", "family"));

  private final HttpServer server;
  private final ObjectMapper mapper = new ObjectMapper();

  public OpenAiEmbeddingStub(int port) throws IOException {
    server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  public static float[] embed(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    float[] v = new float[4];
    AXIS_MARKERS.forEach(
        (axis, markers) -> {
          for (String marker : markers) {
            if (lower.contains(marker)) {
              v[axis] += 1f;
            }
          }
        });
    // Tiny grounded-axis baseline: DenseVectorField's cosine similarity rejects
    // zero-magnitude vectors, and "no markers matched" must still embed to something.
    v[3] += 0.05f;
    return SeedCatalog.normalize(v);
  }

  private void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
    try (exchange) {
      if (!exchange.getRequestURI().getPath().endsWith("/embeddings")) {
        exchange.sendResponseHeaders(404, -1);
        return;
      }
      JsonNode body = mapper.readTree(exchange.getRequestBody());
      JsonNode input = body.get("input");
      List<String> texts = new ArrayList<>();
      if (input != null && input.isArray()) {
        input.forEach(n -> texts.add(n.asText()));
      } else if (input != null) {
        texts.add(input.asText());
      }

      ObjectNode response = mapper.createObjectNode();
      response.put("object", "list");
      response.put("model", body.path("model").asText("stub-model"));
      ArrayNode data = response.putArray("data");
      for (int i = 0; i < texts.size(); i++) {
        ObjectNode item = data.addObject();
        item.put("object", "embedding");
        item.put("index", i);
        ArrayNode vector = item.putArray("embedding");
        for (float f : embed(texts.get(i))) {
          vector.add(f);
        }
      }
      ObjectNode usage = response.putObject("usage");
      usage.put("prompt_tokens", 0);
      usage.put("total_tokens", 0);

      byte[] payload = mapper.writeValueAsBytes(response);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
      exchange.sendResponseHeaders(200, payload.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(payload);
      }
    }
  }

  public String baseUrl(String reachableHost) {
    return "http://" + reachableHost + ":" + server.getAddress().getPort() + "/v1";
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
