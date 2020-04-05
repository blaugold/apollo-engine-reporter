package com.gabrielterwesten.apollo.engine

import graphql.language.AstSignature
import graphql.language.Document

private val astSignature = AstSignature()

/**
 * Default [QuerySignatureStrategy] implementation which uses [AstSignature] to normalize the query
 * document and is compatible with Apollo Engine. See note in [QuerySignatureStrategy] docs.
 */
object DefaultQuerySignatureStrategy : QuerySignatureStrategy {

    override fun computeSignature(document: Document, operation: String?): String {
        val docString = astSignature.signatureQuery(document, operation).printQuery()
        val operationComment = "# ${operation ?: "-"}"
        return "$operationComment\n$docString"
    }

}
