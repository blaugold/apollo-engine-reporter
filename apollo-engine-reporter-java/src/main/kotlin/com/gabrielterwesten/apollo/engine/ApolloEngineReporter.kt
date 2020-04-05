package com.gabrielterwesten.apollo.engine

import graphql.parser.Parser
import kotlinx.coroutines.*
import mdg.engine.proto.GraphqlApolloReporing.FullTracesReport
import org.apache.logging.log4j.LogManager
import java.time.Duration
import java.util.Collections.synchronizedSet
import java.util.concurrent.Executors.newScheduledThreadPool
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Returns a list of [TraceInputProcessor]s which provide save defaults.
 *
 * Per default the [ClientInfoTraceInputProcessor] is included, variables are removed and a
 * [WhitelistHeaderProcessor] to allow theses headers is configured:
 * ```
 * setOf(
 *   "Accept",
 *   "Accept-Encoding",
 *   "Accept-Language",
 *   "ApolloGraphQL-Client-Name",
 *   "ApolloGraphQL-Client-Version",
 *   "Cache-Control",
 *   "Connection",
 *   "Content-Length",
 *   "Content-Type",
 *   "Date",
 *   "DNT",
 *   "Host",
 *   "Origin",
 *   "Referer",
 *   "Sec-Fetch-Mode",
 *   "Sec-Fetch-Site",
 *   "Transfer-Encoding",
 *   "User-Agent",
 *   "Via"
 * )
 * ```
 */
fun traceInputProcessors(

        /**
         * Whether or not to include the [ClientInfoTraceInputProcessor]
         */
        clientInfo: Boolean = true,

        /**
         * The set of headers to blacklist.
         */
        headerBlacklist: Set<String>? = null,

        /**
         * The set of headers to whitelist.
         */
        headerWhitelist: Set<String>? = null,

        /**
         * The strategy to use to generate a replacement value when removing a header.
         */
        headerReplacementStrategy: HeaderReplacementStrategy = DefaultHeaderReplacementStrategy(),

        /**
         * A function which sanitizes query variables. If the function returns `null` all
         * variables are removed.
         *
         * The function should remove all sensitive data like passwords, credit card numbers, ...
         * which should not be sent to and stored by Apollo Engine.
         *
         * Per default all variables are removed.
         */
        variablesSanitizer: (Map<String, Any>) -> Map<String, Any>? = { null }

): List<TraceInputProcessor> {
    val processors = mutableListOf<TraceInputProcessor>()

    processors.add(variablesProcessor { it?.let(variablesSanitizer) })

    if (clientInfo) {
        processors.add(ClientInfoTraceInputProcessor())
    }

    headerBlacklist?.also {
        processors.add(BlacklistHeaderProcessor(
                blacklist = it,
                replacementStrategy = headerReplacementStrategy
        ))
    }

    headerWhitelist?.also {
        processors.add(WhitelistHeaderProcessor(
                whitelist = it,
                replacementStrategy = headerReplacementStrategy
        ))
    }

    if (headerBlacklist == null && headerWhitelist == null) {
        processors.add(WhitelistHeaderProcessor(
                whitelist = setOf(
                        "Accept",
                        "Accept-Encoding",
                        "Accept-Language",
                        "ApolloGraphQL-Client-Name",
                        "ApolloGraphQL-Client-Version",
                        "Cache-Control",
                        "Connection",
                        "Content-Length",
                        "Content-Type",
                        "Date",
                        "DNT",
                        "Host",
                        "Origin",
                        "Referer",
                        "Sec-Fetch-Mode",
                        "Sec-Fetch-Site",
                        "Transfer-Encoding",
                        "User-Agent",
                        "Via"
                ),
                replacementStrategy = headerReplacementStrategy
        ))
    }

    return processors
}

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
         * A list of [TraceInputProcessor] in the order in which they should be processing
         * [TraceInput]s.
         */
        private val traceInputProcessors: List<TraceInputProcessor> = traceInputProcessors(),

        /**
         * The amount of traces in bytes to collect in the buffer before flushing them in a report.
         * The default is 4MB.
         */
        private val flushBufferThreshold: Long = 4_000_000,

        /**
         * Flush buffer every time a trace is reported.
         */
        private val flushImmediately: Boolean = false,

        /**
         * The interval at which to send reports.
         */
        private val reportInterval: Duration = Duration.ofSeconds(20),

        /**
         * The number of threads to use for processing of traces.
         */
        private val threadPoolSize: Int = 2

) : TraceReporter {

    private var started = AtomicBoolean(false)

    private var stopped = AtomicBoolean(false)

    private val log = LogManager.getLogger(javaClass)

    private val parser = Parser()

    private val buffer = TraceBuffer()

    private val threadId = AtomicInteger()

    private lateinit var executor: ScheduledExecutorService

    private lateinit var coroutineDispatcher: ExecutorCoroutineDispatcher

    private lateinit var coroutineScope: CoroutineScope

    private lateinit var scheduledFlushJob: Job

    private val activeProcessTraceJobs = synchronizedSet(mutableSetOf<Job>())

    /**
     * Starts this reporter. Before this method completes the reporter can not be used.
     *
     * @throws IllegalStateException if this reporter has already been started
     */
    fun start() {
        check(started.compareAndSet(false, true)) { "Reporter has already been started." }

        executor = createExecutor()
        coroutineDispatcher = executor.asCoroutineDispatcher()
        coroutineScope = CoroutineScope(coroutineDispatcher + SupervisorJob())
        scheduledFlushJob = startScheduledFlushing()
    }

    /**
     * Stops this reporter after flushing existing traces.
     * After this method completes this instance can not be used or restarted.
     *
     * @throws IllegalStateException if this reporter has already been stopped.
     */
    fun stop() {
        check(started.get()) { "Reporter has not been started yet." }
        check(stopped.compareAndSet(false, true)) { "Reporter has already been stopped." }

        runBlocking {
            scheduledFlushJob.cancel()

            val flushJob = coroutineScope.launch(CoroutineExceptionHandler { _, e ->
                log.error("Error while flushing during stopping:", e)
            }) { flush(emptyBuffer = true) }

            (activeProcessTraceJobs + scheduledFlushJob + flushJob).joinAll()
        }

        coroutineDispatcher.close()
    }

    override fun reportTrace(traceContext: TraceContext) {
        check(started.get()) { "Reporter has not been started yet." }
        check(!stopped.get()) { "Reporter has already been stopped." }

        val job = coroutineScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("Error while processing trace:", e)
        }) { processTrace(traceContext) }

        activeProcessTraceJobs.add(job)
        job.invokeOnCompletion { activeProcessTraceJobs.remove(job) }
    }

    private suspend fun processTrace(traceContext: TraceContext) {
        val queryDoc = parser.parseDocument(traceContext.query)
        val signature = querySignatureStrategy.computeSignature(queryDoc, traceContext.operation)

        val input = traceInputProcessors.fold(TraceInput(
                traceContext.trace,
                traceContext.variables,
                null,
                traceContext.errors,
                traceContext.http
        )) { acc, traceInputProcessor -> traceInputProcessor.process(acc) }

        val trace = reportGenerator.getTrace(input)

        buffer.addTrace(signature, trace)

        flush(emptyBuffer = flushImmediately)
    }

    /**
     * Flushes [buffer] in batches of size [flushBufferThreshold]. If buffer is below
     * [flushBufferThreshold] no traces are flushed unless [emptyBuffer] is `true`.
     */
    private suspend fun flush(emptyBuffer: Boolean = false) {
        val batches = generateSequence {
            val traces = buffer.flush(flushBufferThreshold)

            // Buffer was below flushBufferThreshold
            if (traces.isEmpty()) when {

                // We make sure buffer is empty
                emptyBuffer -> buffer.flush().let { if (it.isEmpty()) null else it }

                else -> null

            }
            else traces
        }

        val batchJobs = batches.map { batch ->
            if (log.isDebugEnabled) {
                log.debug("Shipping ${batch.values.map { it.size }.sum()} traces in next report.")
            }

            val report = reportGenerator.getReport(reportGenerator.getReportHeader(), batch)

            if (log.isTraceEnabled) {
                log.trace("Full report:\n$report")
            }

            coroutineScope.launch(CoroutineExceptionHandler { _, e ->
                log.error("Error while shipping report:", e)
            }) { traceShipper.ship(report) }
        }

        batchJobs.toList().joinAll()
    }

    private fun createExecutor(): ScheduledExecutorService =
            newScheduledThreadPool(threadPoolSize) {
                Thread(it).apply {
                    name = "ApolloEngineReporter-${threadId.getAndIncrement()}"
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY
                }
            }

    private fun startScheduledFlushing(): Job = coroutineScope.launch {
        while (isActive) {
            delay(reportInterval.toMillis())
            // We are not joining to wait for flushing to complete to keep intervals even
            coroutineScope.launch(CoroutineExceptionHandler { _, e ->
                log.error("Error while performing scheduled flush:", e)
            }) { flush(emptyBuffer = true) }
        }
    }

}
