package com.github.blaugold.apollo.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ClientInfoTraceInputProcessTest {

    @Test
    fun `add client info if headers are available`() {
        // Given
        val processor = ClientInfoTraceInputProcessor()
        val name = "Name"
        val version = "Version"
        val httpTrace = HttpTrace(requestHeaders = mapOf(
                "ApolloGraphQL-Client-Name" to listOf(name),
                "ApolloGraphQL-Client-Version" to listOf(version)
        ))

        // When
        val input = processor.process(TraceInput(trace = queryTrace(), httpTrace = httpTrace))

        // Then
        assertThat(input.clientInfo).isEqualTo(ClientInfo(name = name, version = version))
    }

}
