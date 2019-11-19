package com.github.blaugold.apollo.engine

import graphql.GraphQLError
import mdg.engine.proto.GraphqlApolloReporing.*

/**
 * A generator for assembling [FullTracesReport]s and its components.
 */
interface ReportGenerator {

    /**
     * Returns a [ReportHeader] for inclusion in a [FullTracesReport].
     */
    fun getReportHeader(): ReportHeader

    /**
     * Returns a [Trace] build from the given [trace], [clientInfo] and [errors].
     */
    fun getTrace(trace: QueryTrace,
                 clientInfo: ClientInfo? = null,
                 errors: List<GraphQLError>? = null): Trace

    /**
     * Returns a [FullTracesReport] build from the given [header] and [traces].
     *
     * @param traces a mapping from query signatures to corresponding [Trace]s
     */
    fun getReport(header: ReportHeader, traces: Map<String, List<Trace>>): FullTracesReport

}
