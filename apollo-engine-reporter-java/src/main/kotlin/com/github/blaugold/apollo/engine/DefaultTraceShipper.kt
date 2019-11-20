package com.github.blaugold.apollo.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import mdg.engine.proto.GraphqlApolloReporing.FullTracesReport
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.logging.log4j.LogManager
import java.io.IOException
import java.time.Duration
import kotlin.math.pow
import kotlin.random.Random.Default.nextLong

/**
 * Default implementation of [TraceShipper] to send reports to an Apollo Engine server.
 * The api key is sent through the `X-Api-Key` header.
 * Uses exponential backoff to retry requests with server errors.
 */
class DefaultTraceShipper(

        /**
         * The api key to use for authentication with the Apollo Engine server.
         */
        private val apiKey: String,

        /**
         * The endpoint to send trace reports to.
         */
        private val endpointUrl: String = "https://engine-report.apollodata.com/api/ingress/traces",

        /**
         * The max number of retries to perform. Default is 5.
         */
        private val maxRetries: Long = 5,

        /**
         * The backoff time used in exponential backoff algorithm. Default is 200ms.
         */
        private val backoffTime: Duration = Duration.ofMillis(200)

) : TraceShipper {

    private val log = LogManager.getLogger(javaClass)

    private val client = OkHttpClient()

    private val apiKeyHeader = "X-Api-Key"

    private val protoBufContentType = "application/protobuf".toMediaType()

    override suspend fun ship(report: FullTracesReport) {
        val body = report.toByteArray().toRequestBody(protoBufContentType)

        val request = Request.Builder()
                .url(endpointUrl)
                .post(body)
                .header(apiKeyHeader, apiKey)
                .build()

        val response = requestWithRetry(request) {
            val isServerError = it.code >= 500

            if (isServerError) {
                log.info("Server was unable to process report: ${it.code}: ${it.body?.string()}")
            }

            isServerError
        }

        when (response) {
            null -> log.warn("Gave up sending report to server after $maxRetries retries.")
            else -> {
                if (response.isSuccessful)
                    log.debug("Successful shipped traces to Apollo Engine Server.")
                else
                    log.error("Failed to ship traces to Apollo Engine server: ${response.code}: " +
                            "${response.body?.string()}")
            }
        }
    }

    private suspend fun requestWithRetry(request: Request,
                                         shouldRetry: (Response) -> Boolean): Response? {
        var tries = 0L
        val backoffTimeMs = backoffTime.toMillis()

        while (tries < maxRetries) {
            val response = client.newCall(request).toDeferred().await()
            if (shouldRetry(response)) {
                tries++
                delay(exponentialBackoffMs(tries, backoffTimeMs))
            } else return response
        }

        return null
    }

}

private fun exponentialBackoffMs(tries: Long, slotMs: Long) =
        nextLong(0, 2.0.pow(tries.toDouble()).toLong() - 1) * slotMs

private fun Call.toDeferred(): CompletableDeferred<Response> {
    val future = CompletableDeferred<Response>()

    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            future.completeExceptionally(e)
        }

        override fun onResponse(call: Call, response: Response) {
            future.complete(response)
        }
    })

    return future
}
