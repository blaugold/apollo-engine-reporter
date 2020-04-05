package com.gabrielterwesten.apollo.engine

/**
 * A [TraceReporter] is able to process a [QueryTrace] and send it to some kind of storage or
 * analysis system.
 */
interface TraceReporter {

    /**
     * Accepts a new trace which should be reported.
     */
    fun reportTrace(traceContext: TraceContext)

}
