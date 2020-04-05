package com.gabrielterwesten.apollo.engine

import graphql.GraphQLError
import mdg.engine.proto.GraphqlApolloReporing.Trace

/**
 * Input for generating a [Trace].
 */
data class TraceInput(

        /**
         * The query tracing data to build the [Trace].
         */
        val trace: QueryTrace,

        /**
         * The query variables to include in the [Trace].
         */
        val variables: Map<String, Any>? = null,

        /**
         * The client info to include in the [Trace].
         */
        val clientInfo: ClientInfo? = null,

        /**
         * The errors to include in the [Trace].
         */
        val errors: List<GraphQLError>? = null,

        /**
         * The HTTP tracing data to include in the [Trace].
         */
        val httpTrace: HttpTrace? = null

)
