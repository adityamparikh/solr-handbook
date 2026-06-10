package solrbook.support;

import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.jetty.HttpJettySolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.testcontainers.containers.SolrContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers harness for the integration tests: one Solr container for the
 * whole suite (the singleton-container pattern), reached through the Jetty HTTP client at
 * the mapped port.
 *
 * <p>The image is the Docker Official Image {@code solr} (§2.7 of the handbook), pinned
 * to a concrete version and overridable with {@code -Dsolr.image=solr:9.9}.
 */
public final class SolrTestHarness {

  public static final String IMAGE = System.getProperty("solr.image", "solr:10.0.0");
  public static final String SHOWS = "shows";

  private static final SolrContainer CONTAINER =
      new SolrContainer(DockerImageName.parse(IMAGE)).withCollection(SHOWS);

  private static SolrClient client;
  private static boolean showsReady;

  private SolrTestHarness() {}

  public static synchronized SolrClient client() {
    if (client == null) {
      CONTAINER.start();
      String baseUrl =
          "http://" + CONTAINER.getHost() + ":" + CONTAINER.getSolrPort() + "/solr";
      client =
          new HttpJettySolrClient.Builder(baseUrl)
              .withConnectionTimeout(10, TimeUnit.SECONDS)
              .withRequestTimeout(60, TimeUnit.SECONDS)
              .build();
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      client.close();
                    } catch (Exception ignored) {
                      // closing on JVM shutdown; nothing useful left to do
                    }
                    CONTAINER.stop();
                  }));
    }
    return client;
  }

  /**
   * The {@code shows} collection with the §2.3 schema and the 30-show seed catalog,
   * indexed once and shared by read-only tests. Tests that mutate documents must create
   * their own collection via {@link #createCollection(String)}.
   */
  public static synchronized SolrClient showsCollection() throws Exception {
    SolrClient c = client();
    if (!showsReady) {
      solrbook.indexing.ShowsSchema.create(c, SHOWS);
      solrbook.indexing.ShowIndexer.indexShows(c, SHOWS, solrbook.indexing.SeedCatalog.shows());
      c.commit(SHOWS);
      showsReady = true;
    }
    return c;
  }

  /**
   * Creates a fresh single-shard collection on the shared container. The configset is
   * deliberately NOT specified: Solr then copies {@code _default} into a per-collection
   * configset. Passing {@code "_default"} explicitly would make every collection SHARE
   * the live {@code _default} configset, and the second collection's Schema API setup
   * would collide with fields the first one already added.
   */
  public static SolrClient createCollection(String name) throws Exception {
    SolrClient c = client();
    CollectionAdminRequest.createCollection(name, 1, 1).process(c);
    return c;
  }
}
