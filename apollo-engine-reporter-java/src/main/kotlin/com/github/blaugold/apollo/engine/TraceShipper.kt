package com.github.blaugold.apollo.engine

import mdg.engine.proto.GraphqlApolloReporing.FullTracesReport

/**
 * A class that is responsible for sending a [FullTracesReport] to an Apollo Engine server.
 *
 * Implementations handle things like server location, authentication, compression and retries.
 */
interface TraceShipper {

    /**
     * Ship [report] to an Apollo Engine server.
     */
    fun ship(report: FullTracesReport)

}
