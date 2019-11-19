package com.github.blaugold.apollo.engine

import graphql.parser.Parser
import mdg.engine.proto.GraphqlApolloReporing.FullTracesReport
import org.apache.logging.log4j.LogManager
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reporter which ships traces to an Apollo Engine server.
 */
class ApolloEngineReporter(

        /**
         * The shipper to use for sending traces to an Apollo Engine server.
         */
        private val traceShipper: TraceShipper,

        /**
         * The strategy to use for computing query signatures in [FullTracesReport]s.
         */
        private val querySignatureStrategy: QuerySignatureStrategy,

        /**
         * The [ReportGenerator] used by this reporter.
         */
        private val reportGenerator: ReportGenerator,

        /**
         * The [ClientInfoFactory] used by this reporter.
         */
        private val clientInfoFactory: ClientInfoFactory = { null },

        /**
         * The amount of traces in bytes to collect in the buffer before flushing them in a report.
         * The default is 4MB. To send every trace immediately set this value to 0.
         */
        private val flushBufferThreshold: Long = 4_000_000,

        /**
         * The interval at which to send reports.
         */
        private val reportInterval: Duration = Duration.ofSeconds(20),

        /**
         * The number of threads to use for processing of traces.
         */
        private val threadPoolSize: Int = 2

) : TraceReporter {

    private var started = false

    private var stopped = false

    private val log = LogManager.getLogger(javaClass)

    private val parser = Parser()

    private val traceBuffer = TraceBuffer()

    private val threadId = AtomicInteger()

    private lateinit var executor: ScheduledExecutorService

    private lateinit var bufferFlushTask: ScheduledFuture<*>

    /**
     * Starts this reporter. Before this method completes the reporter can not be used.
     *
     * @throws IllegalStateException if this reporter has already been started
     */
    fun start() {
        synchronized(this) {
            check(!started) { "Reporter has already been started." }
            started = true
        }

        executor = Executors.newScheduledThreadPool(threadPoolSize) {
            Thread(it).apply {
                isDaemon = true
                name = "ApolloEngineReporter-${threadId.getAndIncrement()}"
            }
        }

        bufferFlushTask = executor.scheduleAtFixedRate(
                { flushBuffer() },
                reportInterval.toMillis(),
                reportInterval.toMillis(),
                TimeUnit.MILLISECONDS
        )
    }

    /**
     * Stops this reporter after flushing existing traces.
     * After this method completes this instance can not be used or restarted.
     *
     * @param timeout the amount of time to wait for flushing of buffered and in flight traces
     *
     * @throws IllegalStateException if this reporter has already been stopped.
     */
    fun stop(timeout: Duration = Duration.ofSeconds(5)) {
        synchronized(this) {
            check(started) { "Reporter has not been started yet." }
            check(!stopped) { "Reporter has already been stopped." }

            stopped = true
        }

        bufferFlushTask.cancel(false)

        executor.execute { flushBuffer() }

        executor.shutdown()

        if (!executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            log.error("Executor did not terminate after ${timeout.toMillis()} ms.")
            executor.shutdownNow()
        }
    }

    override fun reportTrace(traceContext: TraceContext) {
        synchronized(this) {
            check(started) { "Reporter has not been started yet." }
            check(!stopped) { "Reporter has already been stopped." }
        }

        processTrace(traceContext)
    }

    private fun processTrace(traceContext: TraceContext) {
        val queryDoc = parser.parseDocument(traceContext.query)
        val signature = querySignatureStrategy.computeSignature(queryDoc, traceContext.operation)
        val trace = reportGenerator.getTrace(
                traceContext.trace,
                clientInfoFactory(traceContext),
                traceContext.errors
        )

        traceBuffer.addTrace(signature, trace)

        checkBuffer()
    }

    /**
     * Checks whether the buffer is at or above [flushBufferThreshold] and flushes it if that is
     * the case.
     */
    private fun checkBuffer() {
        if (traceBuffer.bufferedBytes >= flushBufferThreshold) executor.execute { flushBuffer() }
    }

    private fun flushBuffer() {
        try {
            while (traceBuffer.isNotEmpty()) {
                val traces = traceBuffer.flush(flushBufferThreshold.coerceAtLeast(1))
                if (traces.isEmpty()) return

                log.debug("Shipping ${traces.values.map { it.size }.sum()} traces in next report.")

                traceShipper.ship(reportGenerator.getReport(reportGenerator.getReportHeader(), traces))
            }
        } catch (e: Throwable) {
            log.error("Failed to flush buffer", e)
        }
    }

}
