package com.gabrielterwesten.apollo.engine

import mdg.engine.proto.GraphqlApolloReporing.Trace
import org.slf4j.LoggerFactory

/**
 * Ring buffer for [Trace]s.
 *
 * The buffer grows to [maxBufferedBytes] before discarding traces. Once the buffer size crosses
 * that threshold the oldest entries are discarded first.
 *
 * [bufferedBytes] is calculated by building the sum of [Trace.getSerializedSize] of all traces in
 * the buffer.
 */
class TraceBuffer(

        /**
         * The max number of bytes the buffer stores before discarding entries.
         */
        private val maxBufferedBytes: Long = 100_000_000 // 100 MB

) {

    var bufferedBytes = 0L
        private set

    private val log = LoggerFactory.getLogger(javaClass)

    private val traces = mutableListOf<Entry>()

    /**
     * Add a trace to the buffer. If the new trace brings [bufferedBytes] over [maxBufferedBytes]
     * the buffer starts discarding the oldest entries.
     */
    fun addTrace(signature: String, trace: Trace) = synchronized(this) {
        enqueEntry(Entry(signature, trace))

        val discardedEntries = mutableListOf<Entry>()
        while (bufferedBytes > maxBufferedBytes) {
            discardedEntries.add(dequeEntry())
        }

        if (discardedEntries.isNotEmpty())
            log.warn("Discarded ${discardedEntries.size} entires.")
    }

    /**
     * Flushes at least [minBytes] from the buffer but not less. Returns the traces grouped by
     * signature.
     *
     * If no [minBytes] to flush are given the complete buffer is emptied.
     */
    fun flush(minBytes: Long = maxBufferedBytes): Map<String, List<Trace>> = synchronized(this) {
        var unbufferedBytes = 0L
        val result = mutableMapOf<String, MutableList<Trace>>()

        while (unbufferedBytes < minBytes && traces.isNotEmpty()) {
            val entry = dequeEntry()
            unbufferedBytes += entry.trace.serializedSize
            result.computeIfAbsent(entry.signature) { mutableListOf() }.add(entry.trace)
        }

        return result
    }

    fun isNotEmpty(): Boolean = synchronized(this) { traces.isNotEmpty() }

    private fun enqueEntry(entry: Entry) {
        traces.add(entry)
        bufferedBytes += entry.trace.serializedSize
    }

    private fun dequeEntry(): Entry {
        val entry = traces.removeAt(0)
        bufferedBytes -= entry.trace.serializedSize

        return entry
    }

    private data class Entry(
            val signature: String,
            val trace: Trace
    )

}
