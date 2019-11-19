package com.github.blaugold.apollo.engine

import mdg.engine.proto.GraphqlApolloReporing.FullTracesReport
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.logging.log4j.LogManager

class DefaultTraceShipper(

        private val apiKey: String,

        private val endpointUrl: String = "https://engine-report.apollodata.com/api/ingress/traces"

) : TraceShipper {

    private val log = LogManager.getLogger(javaClass)

    private val client = OkHttpClient()

    private val apiKeyHeader = "X-Api-Key"

    private val protoBufContentType = "application/protobuf".toMediaType()

    override fun ship(report: FullTracesReport) {
        val body = report.toByteArray().toRequestBody(protoBufContentType)

        val request = Request.Builder()
                .url(endpointUrl)
                .post(body)
                .header(apiKeyHeader, apiKey)
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                log.error("Failed to ship traces to Apollo Engine server: ${response.code}: " +
                        "${response.body?.string()}")
            } else {
                log.debug("Successful shipped traces to Apollo Engine Server.")
            }
        }
    }

}
