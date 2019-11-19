package com.github.blaugold.apollo.engine

import graphql.GraphQLError

/**
 * A [QueryTrace] combined with contextual data.
 */
data class TraceContext(

        /**
         * The original query text.
         */
        val query: String,

        /**
         * The name of the operation in [query] which was actually executed. Is `null` if the
         * executed operation is nameless.
         */
        val operation: String?,

        /**
         * The tracing data of the query execution.
         */
        val trace: QueryTrace,

        /**
         * Errors which occurred during execution of the traced query.
         */
        val errors: List<GraphQLError>? = emptyList(),

        /**
         * Application specific context object.
         */
        val context: Any? = null

)
