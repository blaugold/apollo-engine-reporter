package com.gabrielterwesten.apollo.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class BlacklistHeaderProcessorTest {

  @Test
  fun `process headers`() {
    // Given
    val processor = BlacklistHeaderProcessor(setOf("A", "C"))
    val input =
        TraceInput(
            queryTrace(),
            httpTrace =
                HttpTrace(
                    requestHeaders = mapOf("A" to listOf("A"), "B" to listOf("B")),
                    responseHeaders = mapOf("C" to listOf("C"), "D" to listOf("D"))))

    // When
    val result = processor.process(input)

    // Then
    assertThat(result.httpTrace)
        .isEqualTo(
            HttpTrace(
                requestHeaders = mapOf("A" to listOf("__REMOVED__"), "B" to listOf("B")),
                responseHeaders = mapOf("C" to listOf("__REMOVED__"), "D" to listOf("D"))))
  }
}
