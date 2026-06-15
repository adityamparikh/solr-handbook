// SPDX-License-Identifier: Apache-2.0

package solrbook.indexing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateBaseSolrClient;
import org.apache.solr.client.solrj.jetty.ConcurrentUpdateJettySolrClient;
import org.apache.solr.client.solrj.jetty.HttpJettySolrClient;
import org.apache.solr.common.SolrInputDocument;

/**
 * Bulk indexing patterns from §2.6 of the handbook: batched {@code add(Collection)} for
 * the common case, and fire-and-forget {@code ConcurrentUpdateJettySolrClient} for maximum
 * single-endpoint throughput.
 */
public final class ShowIndexer {

  public static final int DEFAULT_BATCH_SIZE = 1_000;

  private ShowIndexer() {}

  /** Batched indexing through any SolrClient. Returns the number of documents sent. */
  public static int indexShows(SolrClient client, String collection, Collection<Show> shows)
      throws Exception {
    List<SolrInputDocument> batch = new ArrayList<>(DEFAULT_BATCH_SIZE);
    int sent = 0;
    for (Show s : shows) {
      batch.add(s.toSolrDoc());
      if (batch.size() == DEFAULT_BATCH_SIZE) {
        client.add(collection, batch);
        sent += batch.size();
        batch.clear();
      }
    }
    if (!batch.isEmpty()) {
      client.add(collection, batch);
      sent += batch.size();
    }
    return sent;
  }

  /**
   * Fire-and-forget bulk indexing with internal queues and parallel HTTP (§2.6). In SolrJ
   * 10 the concurrent-update builder takes the Jetty HTTP client it should send through;
   * exceptions surface asynchronously, so production code must override
   * {@code onException} or watch the logs.
   */
  public static void concurrentIndex(
      String baseSolrUrl, String collection, Collection<Show> shows, int queueSize, int threads)
      throws Exception {
    try (HttpJettySolrClient http = new HttpJettySolrClient.Builder(baseSolrUrl).build()) {
      ConcurrentUpdateJettySolrClient.Builder builder =
          new ConcurrentUpdateJettySolrClient.Builder(baseSolrUrl, http);
      builder.withQueueSize(queueSize).withThreadCount(threads).withDefaultCollection(collection);
      try (ConcurrentUpdateBaseSolrClient client = builder.build()) {
        List<SolrInputDocument> batch = new ArrayList<>(DEFAULT_BATCH_SIZE);
        for (Show s : shows) {
          batch.add(s.toSolrDoc());
          if (batch.size() == DEFAULT_BATCH_SIZE) {
            client.add(batch);
            batch.clear();
          }
        }
        if (!batch.isEmpty()) {
          client.add(batch);
        }
        client.blockUntilFinished(); // wait for the internal queue to drain
      }
    }
  }
}
