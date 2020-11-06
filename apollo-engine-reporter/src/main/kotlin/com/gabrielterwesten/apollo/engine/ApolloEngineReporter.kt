package com.gabrielterwesten.apollo.engine

import graphql.parser.Parser
import java.time.Duration
import java.util.Collections.synchronizedSet
import kotlinx.coroutines.*
import mdg.engine.proto.GraphqlApolloReporing
import mdg.engine.proto.GraphqlApolloReporing.FullTracesReport
import org.slf4j.LoggerFactory

/**
 * Returns a list of [TraceInputProcessor] s which provide save defaults.
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

    /** Whether or not to include the [ClientInfoTraceInputProcessor] */
    clientInfo: Boolean = true,

    /** The set of headers to blacklist. */
    headerBlacklist: Set<String>? = null,

    /** The set of headers to whitelist. */
    headerWhitelist: Set<String>? = null,

    /** The strategy to use to generate a replacement value when removing a header. */
    headerReplacementStrategy: HeaderReplacementStrategy = DefaultHeaderReplacementStrategy(),

    /**
     * A function which sanitizes query variables. If the function returns `null` all variables are
     * removed.
     *
     * The function should remove all sensitive data like passwords, credit card numbers, ... which
     * should not be sent to and stored by Apollo Engine.
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
    processors.add(
        BlacklistHeaderProcessor(blacklist = it, replacementStrategy = headerReplacementStrategy))
  }

  headerWhitelist?.also {
    processors.add(
        WhitelistHeaderProcessor(whitelist = it, replacementStrategy = headerReplacementStrategy))
  }

  if (headerBlacklist == null && headerWhitelist == null) {
    processors.add(
        WhitelistHeaderProcessor(
            whitelist =
                setOf(
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
                    "Via",
                ),
            replacementStrategy = headerReplacementStrategy))
  }

  return processors
}

/**
 * Reporter which ships traces to an Apollo Engine server.
 *
 * Before use the reporter has to be started with [start]. To clean up all resource the reporter has
 * to be stopped with [stop]. Both methods are not thread safe.
 */
class ApolloEngineReporter(

    /** The shipper to use for sending traces to an Apollo Engine server. */
    private val traceShipper: TraceShipper,

    /** The strategy to use for computing query signatures in [FullTracesReport] s. */
    private val querySignatureStrategy: QuerySignatureStrategy,

    /** The [ReportGenerator] used by this reporter. */
    private val reportGenerator: ReportGenerator,

    /**
     * A list of [TraceInputProcessor] in the order in which they should be processing [TraceInput]
     * s.
     */
    private val traceInputProcessors: List<TraceInputProcessor> = traceInputProcessors(),

    /**
     * The amount of traces in bytes to collect in the buffer before flushing them in a report. The
     * default is 4MB.
     */
    private val flushBufferThreshold: Long = 4_000_000,

    /** Flush buffer every time a trace is reported. */
    private val flushImmediately: Boolean = false,

    /** The interval at which to send reports. */
    private val reportInterval: Duration = Duration.ofSeconds(20),

    /** The [CoroutineScope] in which to perform work. */
    private val coroutineScope: CoroutineScope = GlobalScope
) : TraceReporter {

  private var started = false

  private val log = LoggerFactory.getLogger(javaClass)

  private val parser = Parser()

  private val buffer = TraceBuffer()

  /**
   * A [CoroutineScope] created from [coroutineScope], in which all coroutines are launched by this
   * reporter.
   */
  private var reporterCoroutineScope: CoroutineScope? = null

  private val uncaughtExceptionHandler =
      CoroutineExceptionHandler { _, throwable ->
        log.error(
            "An unhandled exception from a coroutine was caught. " +
                "This is likely a bug in ${this::class.simpleName}.",
            throwable,
        )
      }

  private lateinit var flushSchedulerJob: Job
  private val processTraceJobs = synchronizedSet(mutableSetOf<Job>())
  private val flushJobs = synchronizedSet(mutableSetOf<Job>())

  init {
    require(flushBufferThreshold >= 0) { "flushBufferThreshold must be >= 0" }
    require(!reportInterval.isZero) { "reportInterval must not be zero" }
    require(!reportInterval.isNegative) { "reportInterval must be positive" }
  }

  /**
   * Starts this reporter. Before this method completes [reportTrace] must not be called.
   *
   * @throws IllegalStateException if this reporter has already been started
   */
  fun start() {
    require(!started) { "Reporter has already been started." }

    reporterCoroutineScope =
        CoroutineScope(coroutineScope.coroutineContext + uncaughtExceptionHandler)
    flushSchedulerJob = startScheduledFlushing()

    started = true
  }

  /**
   * Stops this reporter after flushing existing traces. After this method starts [reportTrace] must
   * not be called any more.
   *
   * The reporter tries to ship buffered traces before stopping. If the traces have not been shipped
   * after [timeout] the pending jobs are are canceled.
   *
   * @throws IllegalStateException if this reporter has not been started.
   */
  fun stop(timeout: Duration = Duration.ofSeconds(10)) {
    require(started) { "Reporter has not been started yet." }
    started = false

    if (log.isInfoEnabled) {
      log.info("Stopping...")
    }

    runBlocking(reporterCoroutineScope!!.coroutineContext) {
      try {
        withTimeout(timeout.toMillis()) {
          // Stop auto flushing
          flushSchedulerJob.cancelAndJoin()

          // Wait for any pending trace reports to create flush jobs
          processTraceJobs.joinAll()

          // Flush any traces remaining in the buffer
          flush(forceIfBelowThreshold = true)

          // Wait for all pending flush jobs to finish.
          // We need to make a copy of flushJobs because finishing jobs remove them self from it.
          flushJobs.toList().joinAll()

          if (log.isInfoEnabled) {
            log.info("Stopped gracefully")
          }
        }
      } catch (e: TimeoutCancellationException) {
        log.error(
            "Could not gracefully stop: Timed out while waiting for buffered traces to be shipped")

        // Cancel the job in the reporterCoroutineScope and with it all its children.
        cancel()
      }
    }

    reporterCoroutineScope = null
  }

  override fun reportTrace(traceContext: TraceContext) {
    require(started) { "Reporter has not been started yet" }

    if (log.isDebugEnabled) {
      log.debug("Reported trace: ${traceContext.toDebugString()}")
    }

    processTrace(traceContext)
  }

  private fun processTrace(traceContext: TraceContext): Job {
    if (log.isDebugEnabled) {
      log.debug("Processing trace: ${traceContext.toDebugString()}")
    }

    return reporterCoroutineScope!!
        .launch {
          try {
            createAndBufferTrace(traceContext)

            // Trigger a flush job in case the buffer is over the flush threshold or
            // `flushImmediately` is true.
            flush(forceIfBelowThreshold = flushImmediately)
          } catch (e: Throwable) {
            log.error("Failed to process trace", e)
          }
        }
        .also { job ->
          processTraceJobs.add(job)
          job.invokeOnCompletion { processTraceJobs.remove(job) }
        }
  }

  private fun createAndBufferTrace(traceContext: TraceContext) {
    val queryDoc = parser.parseDocument(traceContext.query)
    val signature = querySignatureStrategy.computeSignature(queryDoc, traceContext.operation)
    val input = traceContext.createTraceInput().applyInputProcessors()
    val trace = reportGenerator.getTrace(input)

    buffer.addTrace(signature, trace)

    if (log.isDebugEnabled) {
      log.debug("Added trace to buffer: ${traceContext.toDebugString()}")
    }
  }

  private fun TraceInput.applyInputProcessors(): TraceInput =
      traceInputProcessors.fold(this) { acc, traceInputProcessor ->
        traceInputProcessor.process(acc)
      }

  /**
   * Flushes [buffer] in batches of size [flushBufferThreshold]. If buffer is below
   * [flushBufferThreshold] no traces are flushed unless [forceIfBelowThreshold] is `true`.
   */
  private fun flush(forceIfBelowThreshold: Boolean = false): Job {
    if (log.isDebugEnabled) {
      log.debug("Flushing buffered traces: forceIfBelowThreshold = $forceIfBelowThreshold")
    }

    return reporterCoroutineScope!!
        .launch {
          val batches = nextBatches(forceIfBelowThreshold)

          supervisorScope {
            for (batch in batches) {
              launch {
                try {
                  shipBatch(batch)
                  if (log.isInfoEnabled) {
                    log.info("Successfully shipped batch")
                  }
                } catch (e: Throwable) {
                  log.error("Failed to ship a batch of traces", e)
                }
              }
            }
          }
        }
        .also { job ->
          flushJobs.add(job)
          job.invokeOnCompletion { flushJobs.remove(job) }
        }
  }

  private fun nextBatches(forceIfBelowThreshold: Boolean) =
      generateSequence {
            buffer.flush(flushBufferThreshold).takeIf { it.isNotEmpty() }
                ?: if (forceIfBelowThreshold) buffer.flush().takeIf { it.isNotEmpty() } else null
          }
          .toList()

  private suspend fun shipBatch(batch: Map<String, List<GraphqlApolloReporing.Trace>>) {
    if (log.isDebugEnabled) {
      val traces = batch.values.map { it.size }.sum()
      log.debug("Shipping batch of $traces traces in next report")
    }

    val report = reportGenerator.getReport(reportGenerator.getReportHeader(), batch)

    if (log.isTraceEnabled) {
      log.trace("Full report:\n$report")
    }

    traceShipper.ship(report)
  }

  private fun startScheduledFlushing(): Job =
      reporterCoroutineScope!!
          .launch {
            while (true) {
              delay(reportInterval.toMillis())

              flush(forceIfBelowThreshold = true)
            }
          }
          .also {
            if (log.isInfoEnabled) {
              log.info("Started scheduled flushing")
            }
          }
}

private fun TraceContext.createTraceInput() =
    TraceInput(
        trace,
        variables,
        null,
        errors,
        http,
    )

private fun TraceContext.toDebugString(): String = "TraceContext(${hashCode()})"
