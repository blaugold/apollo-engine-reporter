package com.gabrielterwesten.apollo.engine

import java.io.IOException
import java.time.Duration
import kotlin.coroutines.resumeWithException
import kotlin.math.pow
import kotlin.random.Random.Default.nextLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import mdg.engine.proto.GraphqlApolloReporing.FullTracesReport
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory

/**
 * Default implementation of [TraceShipper] to send reports to an Apollo Engine server. The api key
 * is sent through the `X-Api-Key` header. Uses exponential backoff to retry requests with server
 * errors.
 */
class DefaultTraceShipper(

    /** The api key to use for authentication with the Apollo Engine server. */
    private val apiKey: String,

    /** The endpoint to send trace reports to. */
    private val endpointUrl: String = "https://engine-report.apollodata.com/api/ingress/traces",

    /** The max number of retries to perform. Default is 5. */
    private val maxRetries: Int = 5,

    /** The backoff time used in exponential backoff algorithm. Default is 200ms. */
    private val backoffTime: Duration = Duration.ofMillis(200)
) : TraceShipper {

  private val log = LoggerFactory.getLogger(javaClass)

  private val client = OkHttpClient()

  private val apiKeyHeader = "X-Api-Key"

  private val protoBufContentType = "application/protobuf".toMediaType()

  @Suppress("BlockingMethodInNonBlockingContext")
  override suspend fun ship(report: FullTracesReport) {
    val body = report.toByteArray().toRequestBody(protoBufContentType)
    val request = Request.Builder().url(endpointUrl).post(body).header(apiKeyHeader, apiKey).build()
    val response = requestWithRetry(request, this::shouldRetry)
    if (response == null) log.warn("Gave up sending report to server after $maxRetries retries.")
    else handleResponse(response)
  }

  private fun shouldRetry(it: CallResult): Boolean =
      when (it) {
        is CallResult.Response -> {
          val response = it.response
          val isServerError = response.code >= 500

          if (isServerError) {
            log.info(
                "Server was unable to process report: ${response.code}: ${response.body?.string()}")
          }

          isServerError
        }
        is CallResult.Error ->
            // Could not find any useful exceptions to handle since okhttp implements retries
            // for exceptions at the network level.
            // https://square.github.io/okhttp/calls/#retrying-requests
            false
      }

  private fun handleResponse(response: Response) {
    if (!response.isSuccessful) {
      val code = response.code
      val body = response.body?.string()
      log.error("Failed to ship traces to Apollo Engine server: [$code]:\n$body")
    }

    response.body?.close()
  }

  private suspend fun requestWithRetry(
      request: Request, shouldRetry: (CallResult) -> Boolean
  ): Response? {
    var tries = 0
    val backoffTimeMs = backoffTime.toMillis()

    while (tries < maxRetries) {
      tries++

      var retry: Boolean
      var response: Response? = null

      try {
        response = client.newCall(request).await()
        retry = shouldRetry(CallResult.Response(response))
      } catch (e: IOException) {
        retry = shouldRetry(CallResult.Error(e))
      }

      if (retry) {
        response?.body?.close()
        delay(exponentialBackoffMs(tries, backoffTimeMs))
      } else return response
    }

    return null
  }

  sealed class CallResult {
    data class Error(val exception: IOException) : CallResult()
    data class Response(val response: okhttp3.Response) : CallResult()
  }
}

private fun exponentialBackoffMs(tries: Int, slotMs: Long) =
    nextLong(0, 2.0.pow(tries.toDouble()).toLong() - 1) * slotMs

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { cont ->
      if (cont.isCancelled) return@suspendCancellableCoroutine

      enqueue(
          object : Callback {
            override fun onFailure(call: Call, e: IOException) {
              cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
              cont.resume(response) { response.body?.close() }
            }
          })

      cont.invokeOnCancellation { if (!(isCanceled() || isExecuted())) cancel() }
    }
