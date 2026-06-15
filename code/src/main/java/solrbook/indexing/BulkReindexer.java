// SPDX-License-Identifier: Apache-2.0

package solrbook.indexing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CursorMarkParams;

/**
 * Collection-to-collection bulk reindex from §2.9.4 of the handbook: read the source with
 * {@code cursorMark} (constant-time deep paging; requires a total-ordering sort on the
 * uniqueKey), transform, and batch-write the target with bounded in-flight parallelism.
 */
public final class BulkReindexer {

  private static final int ROWS_PER_PAGE = 1000;
  private static final int MAX_IN_FLIGHT = 8;

  private BulkReindexer() {}

  public static long reindex(SolrClient client, String src, String dst) throws Exception {
    SolrQuery q =
        new SolrQuery("*:*").setRows(ROWS_PER_PAGE).addSort("id", SolrQuery.ORDER.asc);

    String cursor = CursorMarkParams.CURSOR_MARK_START;
    ExecutorService pool = Executors.newFixedThreadPool(4);
    List<Future<?>> inFlight = new ArrayList<>();
    long copied = 0;

    try {
      while (true) {
        q.set(CursorMarkParams.CURSOR_MARK_PARAM, cursor);
        QueryResponse resp = client.query(src, q);

        List<SolrInputDocument> batch = new ArrayList<>(resp.getResults().size());
        for (SolrDocument d : resp.getResults()) {
          batch.add(transform(d));
        }
        copied += batch.size();

        // Bounded in-flight batches = backpressure.
        while (inFlight.size() >= MAX_IN_FLIGHT) {
          inFlight.remove(0).get();
        }
        if (!batch.isEmpty()) {
          inFlight.add(
              pool.submit(
                  () -> {
                    client.add(dst, batch);
                    return null;
                  }));
        }

        String next = resp.getNextCursorMark();
        if (cursor.equals(next)) {
          break; // cursor did not advance: we have read everything
        }
        cursor = next;
      }
      for (Future<?> f : inFlight) {
        f.get();
      }
      client.commit(dst);
      return copied;
    } finally {
      pool.shutdown();
      pool.awaitTermination(1, TimeUnit.MINUTES);
    }
  }

  private static SolrInputDocument transform(SolrDocument src) {
    SolrInputDocument out = new SolrInputDocument();
    for (String field : src.getFieldNames()) {
      // ALWAYS strip _version_: copying it makes the target reject the doc as stale.
      if ("_version_".equals(field)) {
        continue;
      }
      out.addField(field, src.getFieldValue(field));
    }
    return out;
  }
}
