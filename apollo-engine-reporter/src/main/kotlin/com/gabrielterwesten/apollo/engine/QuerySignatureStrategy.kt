package com.gabrielterwesten.apollo.engine

import graphql.language.Document

/**
 * Strategy for computing the signatures of GraphQL queries.
 *
 * A signature is used to aggregate functionally equivalent queries in reporting tools. A signature
 * is usually a normalized form of the original query.
 * [Operation Signing](https://www.apollographql.com/docs/graph-manager/setup-analytics/#operation-signing)
 *
 * **Note:** Apollo Engine expects a leading comment on its own line in the signature:
 * - Nameless operation: `# -`
 * - Named operation: `# OP_NAME`
 */
interface QuerySignatureStrategy {

  /**
   * Returns the signature for the given query. If [operation] is `null` [document] contains a
   * nameless query for which the signature should be computed.
   */
  fun computeSignature(document: Document, operation: String? = null): String
}
