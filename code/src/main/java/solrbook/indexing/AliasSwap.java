// SPDX-License-Identifier: Apache-2.0

package solrbook.indexing;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;

/**
 * The zero-downtime blue/green alias swap from §2.9.3 of the handbook. The application
 * always reads and writes through the alias; re-issuing CREATEALIAS with the same alias
 * name atomically repoints it, and pointing it back at the old collection is the
 * one-line rollback.
 */
public final class AliasSwap {

  private AliasSwap() {}

  public static void createCollection(
      SolrClient client, String name, String configset, int numShards, int numReplicas)
      throws Exception {
    CollectionAdminRequest.createCollection(name, configset, numShards, numReplicas)
        .process(client);
  }

  /** Idempotent: CREATEALIAS replaces the alias-to-collection mapping if the alias exists. */
  public static void pointAliasAt(SolrClient client, String alias, String collection)
      throws Exception {
    CollectionAdminRequest.createAlias(alias, collection).process(client);
  }

  public static void deleteAlias(SolrClient client, String alias) throws Exception {
    CollectionAdminRequest.deleteAlias(alias).process(client);
  }

  public static void deleteCollection(SolrClient client, String name) throws Exception {
    CollectionAdminRequest.deleteCollection(name).process(client);
  }
}
