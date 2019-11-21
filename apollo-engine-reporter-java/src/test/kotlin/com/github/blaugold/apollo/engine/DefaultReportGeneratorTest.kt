package com.github.blaugold.apollo.engine

import graphql.GraphqlErrorBuilder
import graphql.execution.ExecutionPath
import graphql.language.SourceLocation
import mdg.engine.proto.GraphqlApolloReporing.Trace.HTTP.Method
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.time.Instant

internal class DefaultReportGeneratorTest {

    @Nested
    inner class Header {

        @Test
        fun `include schemaTag in header`() {
            // Given
            val generator = DefaultReportGenerator(schemaTag = "a")

            // When
            val header = generator.getReportHeader()

            // Then
            assertThat(header.schemaTag).isEqualTo("a")
        }

        @Test
        fun `skip schemaTag in header`() {
            // Given
            val generator = DefaultReportGenerator()

            // When
            val header = generator.getReportHeader()

            // Then
            assertThat(header.schemaTag).isEqualTo("")
        }

        @Test
        fun `include runtime`() {
            // Given
            val generator = DefaultReportGenerator()

            // When
            val header = generator.getReportHeader()

            // Then
            assertThat(header.runtimeVersion).isEqualTo(Runtime.version().toString())
        }

        @Test
        fun `include os name`() {
            // Given
            val generator = DefaultReportGenerator()

            // When
            val header = generator.getReportHeader()

            // Then
            assertThat(header.uname).isEqualTo(System.getProperty("os.name"))
        }

        @Test
        fun `include host name`() {
            // Given
            val generator = DefaultReportGenerator()

            // When
            val header = generator.getReportHeader()

            // Then
            assertThat(header.hostname).isEqualTo(InetAddress.getLocalHost().hostName)
        }

        @Test
        fun `include agent version`() {
            // Given
            val generator = DefaultReportGenerator()

            // When
            val header = generator.getReportHeader()

            // Then
            assertThat(header.agentVersion).isEqualTo("ApolloEngineReporterJava ${Version.string}")
        }

    }

    @Nested
    inner class Trace {

        @Test
        fun `include start time`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()

            // When
            val trace = generator.getTrace(queryTrace)

            // Then
            assertThat(trace.startTime).isEqualTo(queryTrace.startTime.toTimestamp())
        }

        @Test
        fun `include end time`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()

            // When
            val trace = generator.getTrace(queryTrace)

            // Then
            assertThat(trace.endTime).isEqualTo(queryTrace.endTime.toTimestamp())
        }

        @Test
        fun `include duration`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()

            // When
            val trace = generator.getTrace(queryTrace)

            // Then
            assertThat(trace.durationNs).isEqualTo(queryTrace.duration)
        }

        @Test
        fun `include client name`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()
            val clientInfo = ClientInfo(name = "name")

            // When
            val trace = generator.getTrace(queryTrace, clientInfo)

            // Then
            assertThat(trace.clientName).isEqualTo(clientInfo.name)
        }

        @Test
        fun `include client version`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()
            val clientInfo = ClientInfo(version = "version")

            // When
            val trace = generator.getTrace(queryTrace, clientInfo)

            // Then
            assertThat(trace.clientVersion).isEqualTo(clientInfo.version)
        }

        @Test
        fun `include client address`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()
            val clientInfo = ClientInfo(address = "address")

            // When
            val trace = generator.getTrace(queryTrace, clientInfo)

            // Then
            assertThat(trace.clientAddress).isEqualTo(clientInfo.address)
        }

        @Test
        fun `include client reference id`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()
            val clientInfo = ClientInfo(referenceId = "referenceId")

            // When
            val trace = generator.getTrace(queryTrace, clientInfo)

            // Then
            assertThat(trace.clientReferenceId).isEqualTo(clientInfo.referenceId)
        }

    }

    @Nested
    inner class Node {

        @Test
        fun `dont include originalFieldName for non-aliased selection`() {
            // Given
            val generator = DefaultReportGenerator()
            val resolverTrace = testResolverTrace(
                    path = listOf("a"),
                    fieldName = "a"
            )
            val queryTrace = testTrace(listOf(resolverTrace))

            // When
            val trace = generator.getTrace(queryTrace)

            // Then
            val node = trace.root.childList.first()
            assertThat(node.responseName).isEqualTo(resolverTrace.path.first())
            assertThat(node.originalFieldName).isEqualTo("")
        }

        @Test
        fun `aliased selection`() {
            // Given
            val generator = DefaultReportGenerator()
            val resolverTrace = testResolverTrace(
                    path = listOf("a"),
                    fieldName = "b"
            )
            val queryTrace = testTrace(listOf(resolverTrace))

            // When
            val trace = generator.getTrace(queryTrace)

            // Then
            val node = trace.root.childList.first()
            assertThat(node.responseName).isEqualTo(resolverTrace.path.first())
            assertThat(node.originalFieldName).isEqualTo(resolverTrace.fieldName)
        }

        @Test
        fun `index path segment`() {
            // Given
            val generator = DefaultReportGenerator()
            val indexSegment = 0
            val queryTrace = testTrace(listOf(
                    testResolverTrace(path = listOf("a")),
                    testResolverTrace(path = listOf("a", indexSegment, "a"))
            ))

            // When
            val trace = generator.getTrace(queryTrace)

            // Then
            val node = trace.root.childList.first().childList.first()
            assertThat(node.index).isEqualTo(indexSegment)
        }

        @Test
        fun `include type`() {
            // Given
            val generator = DefaultReportGenerator()
            val resolverTrace = testResolverTrace(path = listOf("a"))
            val queryTrace = testTrace(listOf(resolverTrace))

            // When
            val trace = generator.getTrace(queryTrace)

            // Then
            val node = trace.root.childList.first()
            assertThat(node.type).isEqualTo(resolverTrace.returnType)
        }

        @Test
        fun `include parentType`() {
            // Given
            val generator = DefaultReportGenerator()
            val resolverTrace = testResolverTrace(path = listOf("a"))
            val queryTrace = testTrace(listOf(resolverTrace))

            // When
            val trace = generator.getTrace(queryTrace)

            // Then
            val node = trace.root.childList.first()
            assertThat(node.parentType).isEqualTo(resolverTrace.parentType)
        }

        @Test
        fun `include startTime`() {
            // Given
            val generator = DefaultReportGenerator()
            val resolverTrace = testResolverTrace(path = listOf("a"))
            val queryTrace = testTrace(listOf(resolverTrace))

            // When
            val trace = generator.getTrace(queryTrace)

            // Then
            val node = trace.root.childList.first()
            assertThat(node.startTime).isEqualTo(resolverTrace.startOffset)
        }

        @Test
        fun `include endTime`() {
            // Given
            val generator = DefaultReportGenerator()
            val resolverTrace = testResolverTrace(path = listOf("a"))
            val queryTrace = testTrace(listOf(resolverTrace))

            // When
            val trace = generator.getTrace(queryTrace)

            // Then
            val node = trace.root.childList.first()
            assertThat(node.endTime).isEqualTo(resolverTrace.startOffset + resolverTrace.duration)
        }

        @Test
        fun `include error`() {
            // Given
            val generator = DefaultReportGenerator()
            val resolverTrace = testResolverTrace(path = listOf("a"))
            val queryTrace = testTrace(listOf(resolverTrace))
            val gqlError = GraphqlErrorBuilder.newError()
                    .message("Message")
                    .location(SourceLocation(5, 7))
                    .path(ExecutionPath.parse("/a"))
                    .build()

            // When
            val trace = generator.getTrace(queryTrace, errors = listOf(gqlError))
            println(trace)

            // Then
            val node = trace.root.childList.first()
            val error = node.errorList.first()
            assertThat(error.message).isEqualTo(gqlError.message)
            val location = error.locationList.first()
            assertThat(location.line).isEqualTo(gqlError.locations.first().line)
            assertThat(location.column).isEqualTo(gqlError.locations.first().column)
        }

    }

    @Nested
    inner class Http {

        @Test
        fun `include protocol`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()
            val httpTrace = HttpTrace(protocol = "HTTP/1.1")

            // When
            val trace = generator.getTrace(queryTrace, httpTrace = httpTrace)

            // Then
            assertThat(trace.http.protocol).isEqualTo(httpTrace.protocol)
        }

        @Test
        fun `include secure`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()
            val httpTrace = HttpTrace(secure = true)

            // When
            val trace = generator.getTrace(queryTrace, httpTrace = httpTrace)

            // Then
            assertThat(trace.http.secure).isEqualTo(httpTrace.secure)
        }

        @Test
        fun `include method`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()
            val httpTrace = HttpTrace(method = HttpMethod.Get)

            // When
            val trace = generator.getTrace(queryTrace, httpTrace = httpTrace)

            // Then
            assertThat(trace.http.method).isEqualTo(Method.GET)
        }

        @Test
        fun `include status code`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()
            val httpTrace = HttpTrace(statusCode = 200)

            // When
            val trace = generator.getTrace(queryTrace, httpTrace = httpTrace)

            // Then
            assertThat(trace.http.statusCode).isEqualTo(httpTrace.statusCode)
        }

        @Test
        fun `include path`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()
            val httpTrace = HttpTrace(path = "path")

            // When
            val trace = generator.getTrace(queryTrace, httpTrace = httpTrace)

            // Then
            assertThat(trace.http.path).isEqualTo(httpTrace.path)
        }


        @Test
        fun `include host`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()
            val httpTrace = HttpTrace(host = "host")

            // When
            val trace = generator.getTrace(queryTrace, httpTrace = httpTrace)

            // Then
            assertThat(trace.http.host).isEqualTo(httpTrace.host)
        }

        @Test
        fun `include request headers`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()
            val httpTrace = HttpTrace(requestHeaders = mapOf("A" to listOf("A")))

            // When
            val trace = generator.getTrace(queryTrace, httpTrace = httpTrace)

            // Then
            assertThat(trace.http.requestHeadersMap.mapValues { it.value.valueList.toList() })
                    .isEqualTo(httpTrace.requestHeaders)
        }

        @Test
        fun `include response headers`() {
            // Given
            val generator = DefaultReportGenerator()
            val queryTrace = testTrace()
            val httpTrace = HttpTrace(responseHeaders = mapOf("A" to listOf("A")))

            // When
            val trace = generator.getTrace(queryTrace, httpTrace = httpTrace)

            // Then
            assertThat(trace.http.responseHeadersMap.mapValues { it.value.valueList.toList() })
                    .isEqualTo(httpTrace.responseHeaders)
        }

    }

    private fun testResolverTrace(
            path: List<Any>,
            parentType: String = "Parent",
            returnType: String = "Return",
            fieldName: String = path.lastOrNull().let { if (it is String) it else "FieldName" },
            startOffset: Long = 31,
            duration: Long = 5
    ): ResolverTrace {
        return ResolverTrace(
                path = path,
                parentType = parentType,
                returnType = returnType,
                fieldName = fieldName,
                startOffset = startOffset,
                duration = duration
        )
    }

    private fun testTrace(resolvers: List<ResolverTrace> = emptyList()): QueryTrace {
        return QueryTrace(
                version = 1,
                startTime = Instant.now(),
                endTime = Instant.now(),
                duration = 0,
                parsing = ParsingTrace(startOffset = 0, duration = 0),
                validation = ValidationTrace(startOffset = 0, duration = 0),
                execution = ExecutionTrace(resolvers = resolvers)
        )
    }

}
