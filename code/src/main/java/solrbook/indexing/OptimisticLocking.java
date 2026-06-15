// SPDX-License-Identifier: Apache-2.0

package solrbook.indexing;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;

/**
 * Optimistic concurrency via the {@code _version_} field (§2.8.3 of the handbook).
 * Version semantics: {@code N>1} = exact match required, {@code 1} = must exist,
 * {@code 0}/omitted = don't care, {@code <0} = must NOT exist. A mismatch returns
 * HTTP 409, surfaced by SolrJ as a {@link SolrException} with {@code code()==409}.
 */
public final class OptimisticLocking {

  private OptimisticLocking() {}

  /**
   * Read-modify-write with retry: re-read the current {@code _version_} via Real-Time Get
   * and retry on a 409 conflict. Returns false if the document does not exist or the
   * update lost the race {@code maxRetries} times.
   */
  public static boolean updateRatingWithRetry(
      SolrClient client, String collection, String id, float newRating, int maxRetries)
      throws Exception {
    for (int attempt = 0; attempt < maxRetries; attempt++) {
      // Real-Time Get (NOT /select — a query could return stale, pre-commit state).
      SolrDocument current = client.getById(collection, id);
      if (current == null) {
        return false;
      }
      long version = (Long) current.getFieldValue("_version_");

      SolrInputDocument doc = new SolrInputDocument();
      doc.addField("id", id);
      doc.addField("rating", AtomicOp.set(newRating));
      doc.addField(CommonParams.VERSION_FIELD, version);

      try {
        client.add(collection, doc);
        return true;
      } catch (SolrException e) {
        if (e.code() == 409) {
          continue; // lost the race; re-read and retry
        }
        throw e;
      }
    }
    return false;
  }

  /** Insert-only: fails with 409 if the document already exists. */
  public static void insertOnly(SolrClient client, String collection, SolrInputDocument doc)
      throws Exception {
    doc.setField(CommonParams.VERSION_FIELD, -1);
    client.add(collection, doc);
  }

  /** Update-only: fails with 409 if the document does not exist. */
  public static void updateOnly(SolrClient client, String collection, SolrInputDocument doc)
      throws Exception {
    doc.setField(CommonParams.VERSION_FIELD, 1);
    client.add(collection, doc);
  }
}
