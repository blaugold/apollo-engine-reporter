package com.github.blaugold.apollo.engine

import mdg.engine.proto.GraphqlApolloReporing.Trace

/**
 * A processor which is allowed to transform a [TraceInput] before it is used to generate a [Trace].
 *
 * Implementations could for example remove or obfuscate data which is privacy sensitive.
 */
interface TraceInputProcessor {

    /**
     * Returns the processed trace [input].
     */
    fun process(input: TraceInput): TraceInput

}

fun traceInputProcessor(process: (TraceInput) -> TraceInput): TraceInputProcessor =
        object : TraceInputProcessor {
            override fun process(input: TraceInput): TraceInput {
                return process(input)
            }
        }
