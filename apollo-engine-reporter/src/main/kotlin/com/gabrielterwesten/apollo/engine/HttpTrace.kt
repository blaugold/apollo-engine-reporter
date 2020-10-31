package com.gabrielterwesten.apollo.engine

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/** A HTTP method. */
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

/** Tracing data of a HTTP request. */
data class HttpTrace(

    /** The protocol of the request, e.g. `HTTP/1.0`, `HTTP/1.1` */
    val protocol: String? = null,

    /** Whether or not the request was secured through tls. */
    val secure: Boolean? = null,

    /** The method of the request. */
    val method: HttpMethod? = null,

    /** The status code of the response. */
    val statusCode: Int? = null,

    /** The path of the request. */
    val path: String? = null,

    /** The domain name of the host of the request. */
    val host: String? = null,

    /** The headers of the request. */
    val requestHeaders: Map<String, List<String>>? = null,

    /** The headers of the response. */
    val responseHeaders: Map<String, List<String>>? = null
) {

  companion object
}

/** Creates a [HttpTrace] from the given [request] and [response]. */
fun HttpTrace.Companion.fromServlet(
    request: HttpServletRequest, response: HttpServletResponse
): HttpTrace =
    HttpTrace(
        protocol = request.protocol,
        secure = request.isSecure,
        method =
            request.method.let {
              try {
                HttpMethod.valueOf(it.toLowerCase().capitalize())
              } catch (e: IllegalArgumentException) {
                HttpMethod.Unrecognized
              }
            },
        statusCode = response.status,
        // TODO should path include a possible query string?
        path = request.requestURI,
        // TODO should host include a possible non standard port?
        host = request.serverName,
        requestHeaders =
            request.headerNames.toList().associateWith { request.getHeaders(it).toList() },
        responseHeaders =
            response.headerNames.toList().associateWith { response.getHeaders(it).toList() })
