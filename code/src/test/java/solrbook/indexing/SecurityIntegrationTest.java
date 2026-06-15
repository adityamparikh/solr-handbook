// SPDX-License-Identifier: Apache-2.0

package solrbook.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.jetty.HttpJettySolrClient;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.SolrContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * §4.13 of the handbook against a real Solr: enable Basic authentication and rule-based
 * authorization by uploading {@code security.json} to ZooKeeper on a *running* cluster
 * (no restart), then assert all three outcomes the chapter's production tip demands —
 * anonymous 401, authorized 200, and authenticated-but-unauthorized 403.
 *
 * <p>Runs on its own container: flipping authentication on would break every other test
 * sharing the harness singleton.
 */
@Tag("integration")
class SecurityIntegrationTest {

  static final String COLLECTION = "secured";

  /**
   * The reference guide's documented bootstrap user: {@code solr}/{@code SolrRocks},
   * password stored as {@code base64(sha256(sha256(salt+password))) base64(salt)}.
   * Updates and security edits are pinned to the admin role; reads are deliberately
   * left unmatched, so any *authenticated* user may query (the fail-open default the
   * book warns about — and what gives this test its 200-vs-403 contrast).
   */
  static final String SECURITY_JSON =
      """
      {
        "authentication": {
          "blockUnknown": true,
          "class": "solr.BasicAuthPlugin",
          "credentials": {
            "solr": "IV0EHq1OnNrj6gvRCwvFwTrZ1+z1oBbnQdiVC3otuq0= Ndd7LKvVBAaZIF0QAVi1ekCfAJXr1GGfLtRUXhgrF8c="
          },
          "realm": "shows-cluster",
          "forwardCredentials": false
        },
        "authorization": {
          "class": "solr.RuleBasedAuthorizationPlugin",
          "permissions": [
            { "name": "security-edit", "role": "admin" },
            { "name": "update",        "role": "admin" }
          ],
          "user-role": { "solr": ["admin"] }
        }
      }
      """;

  static SolrContainer container;
  static SolrClient anonymous;
  static SolrClient admin;

  @BeforeAll
  static void setUp() throws Exception {
    container =
        new SolrContainer(DockerImageName.parse(solrbook.support.SolrTestHarness.IMAGE))
            .withCollection(COLLECTION);
    container.start();
    String baseUrl = "http://" + container.getHost() + ":" + container.getSolrPort() + "/solr";

    anonymous = new HttpJettySolrClient.Builder(baseUrl).build();
    admin =
        new HttpJettySolrClient.Builder(baseUrl)
            .withBasicAuthCredentials("solr", "SolrRocks")
            .build();

    // Security is not enabled yet: index one document anonymously.
    SolrInputDocument doc = new SolrInputDocument();
    doc.addField("id", "SEC-1");
    anonymous.add(COLLECTION, doc);
    anonymous.commit(COLLECTION);

    // Enable security on the RUNNING cluster: upload security.json to ZooKeeper
    // (embedded ZK on the Solr port + 1000). Solr watches the znode — no restart.
    container.copyFileToContainer(
        Transferable.of(SECURITY_JSON.getBytes(StandardCharsets.UTF_8)), "/tmp/security.json");
    Container.ExecResult result =
        container.execInContainer(
            "solr", "zk", "cp", "/tmp/security.json", "zk:security.json", "-z", "localhost:9983");
    assertEquals(0, result.getExitCode(), result.getStdout() + result.getStderr());

    awaitTrue(
        "anonymous requests should start failing with 401",
        () -> codeOf(() -> anonymous.query(COLLECTION, new SolrQuery("*:*"))) == 401);
  }

  @AfterAll
  static void tearDown() throws Exception {
    if (anonymous != null) {
      anonymous.close();
    }
    if (admin != null) {
      admin.close();
    }
    if (container != null) {
      container.stop();
    }
  }

  @Test
  void anonymousRequestsAreRejectedWith401() {
    assertEquals(401, codeOf(() -> anonymous.query(COLLECTION, new SolrQuery("*:*"))));
  }

  @Test
  void adminCanQueryAndWrite() throws Exception {
    assertEquals(
        1, admin.query(COLLECTION, new SolrQuery("id:SEC-1")).getResults().getNumFound());

    SolrInputDocument doc = new SolrInputDocument();
    doc.addField("id", "SEC-2");
    admin.add(COLLECTION, doc);
    admin.commit(COLLECTION);
    assertEquals(
        1, admin.query(COLLECTION, new SolrQuery("id:SEC-2")).getResults().getNumFound());
  }

  @Test
  void authenticatedUserWithoutTheRoleGets403OnWrites() throws Exception {
    // Manage users through the API, not the file: it hashes server-side and applies live.
    postAsAdmin("/admin/authentication", "{\"set-user\": {\"reader\": \"readerPass\"}}");
    postAsAdmin("/admin/authorization", "{\"set-user-role\": {\"reader\": [\"readers\"]}}");

    try (SolrClient reader =
        new HttpJettySolrClient.Builder(
                "http://" + container.getHost() + ":" + container.getSolrPort() + "/solr")
            .withBasicAuthCredentials("reader", "readerPass")
            .build()) {

      // Reads match no permission, and unmatched requests are ALLOWED for
      // authenticated users — the fail-open default §4.13 warns about.
      awaitTrue(
          "reader account should become usable",
          () -> codeOf(() -> reader.query(COLLECTION, new SolrQuery("*:*"))) == 200);

      // Writes are pinned to the admin role: authenticated but unauthorized = 403.
      SolrInputDocument doc = new SolrInputDocument();
      doc.addField("id", "SEC-3");
      assertEquals(403, codeOf(() -> reader.add(COLLECTION, doc)));
    }
  }

  private static void postAsAdmin(String path, String json) throws Exception {
    new GenericSolrRequest(SolrRequest.METHOD.POST, path, SolrParams.of())
        .withContent(json.getBytes(StandardCharsets.UTF_8), "application/json")
        .process(admin);
  }

  /** Runs a Solr call and maps it to an HTTP-ish status: 200, or the SolrException code. */
  private static int codeOf(SolrCall call) {
    try {
      call.run();
      return 200;
    } catch (SolrException e) {
      return e.code();
    } catch (Exception e) {
      // Jetty surfaces some auth failures as wrapped exceptions; look for a code.
      Throwable t = e;
      while (t != null) {
        if (t instanceof SolrException se) {
          return se.code();
        }
        t = t.getCause();
      }
      throw new AssertionError("unexpected failure type: " + e, e);
    }
  }

  private static void awaitTrue(String what, java.util.function.BooleanSupplier condition)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(500);
    }
    Assertions.fail("timed out waiting for: " + what);
  }

  @FunctionalInterface
  private interface SolrCall {
    void run() throws Exception;
  }
}
