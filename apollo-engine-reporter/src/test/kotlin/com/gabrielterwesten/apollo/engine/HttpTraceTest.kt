package com.gabrielterwesten.apollo.engine

import io.mockk.every
import io.mockk.mockk
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HttpTraceTest {

  @Test
  fun `create from Servlet API`() {
    // Given
    val request =
        testRequest(
            _protocol = "HTTP/1.1",
            _isSecure = true,
            _method = "GET",
            _requestURI = "/path",
            _serverName = "example.com",
            headers = mapOf("A" to listOf("A")))
    val response = testResponse(_status = 200, headers = mapOf("B" to listOf("B")))

    // When
    val httpTrace = HttpTrace.fromServlet(request, response)

    // Then
    assertThat(httpTrace.protocol).isEqualTo("HTTP/1.1")
    assertThat(httpTrace.secure).isEqualTo(true)
    assertThat(httpTrace.method).isEqualTo(HttpMethod.Get)
    assertThat(httpTrace.path).isEqualTo("/path")
    assertThat(httpTrace.host).isEqualTo("example.com")
    assertThat(httpTrace.requestHeaders).isEqualTo(mapOf("A" to listOf("A")))
    assertThat(httpTrace.responseHeaders).isEqualTo(mapOf("B" to listOf("B")))
  }

  private fun testRequest(
      _protocol: String,
      _isSecure: Boolean,
      _method: String,
      _requestURI: String,
      _serverName: String,
      headers: Map<String, List<String>> = emptyMap()
  ): HttpServletRequest =
      mockk {
        every { protocol } returns _protocol
        every { isSecure } returns _isSecure
        every { method } returns _method
        every { requestURI } returns _requestURI
        every { serverName } returns _serverName
        every { headerNames } returns headers.keys.iterator().asEnumerator()
        every { getHeaders(any()) } answers
            {
              headers.getValue(firstArg() as String).iterator().asEnumerator()
            }
      }

  private fun testResponse(
      _status: Int = 200, headers: Map<String, List<String>> = emptyMap()
  ): HttpServletResponse =
      mockk {
        every { status } returns _status
        every { headerNames } returns headers.keys
        every { getHeaders(any()) } answers { headers.getValue(firstArg() as String) }
      }
}

fun <T> Iterator<T>.asEnumerator(): Enumeration<T> =
    object : Enumeration<T> {
      override fun hasMoreElements(): Boolean = hasNext()
      override fun nextElement(): T = next()
    }
