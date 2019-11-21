package com.github.blaugold.apollo.engine

/**
 * A HTTP method.
 */
enum class HttpMethod {
    Connect,
    Delete,
    Get,
    Head,
    Options,
    Patch,
    Post,
    Put,
    Trace,
    Unknown,
    Unrecognized
}

/**
 * Tracing data of a HTTP request.
 */
data class HttpTrace(

        /**
         * The protocol of the request, e.g. `HTTP/1.0`, `HTTP/1.1`
         */
        val protocol: String? = null,

        /**
         * Whether or not the request was secured through tls.
         */
        val secure: Boolean? = null,

        /**
         * The method of the request.
         */
        val method: HttpMethod? = null,

        /**
         * The status code of the response.
         */
        val statusCode: Int? = null,

        /**
         * The path of the request.
         */
        val path: String? = null,

        /**
         * The domain name of the host of the request.
         */
        val host: String? = null,

        /**
         * The headers of the request.
         */
        val requestHeaders: Map<String, List<String>>? = null,

        /**
         * The headers of the response.
         */
        val responseHeaders: Map<String, List<String>>? = null

)
