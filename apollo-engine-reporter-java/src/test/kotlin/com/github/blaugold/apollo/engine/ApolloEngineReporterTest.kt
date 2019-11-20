package com.github.blaugold.apollo.engine

import mdg.engine.proto.GraphqlApolloReporing
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

internal class ApolloEngineReporterTest {

    @Test
    fun `flush traces when stopping`() {
        val testShipper = TestTraceShipper()
        val reporter = ApolloEngineReporter(
                traceShipper = testShipper,
                querySignatureStrategy = DefaultQuerySignatureStrategy,
                reportGenerator = DefaultReportGenerator(),
                // No scheduled flush during test
                reportInterval = Duration.ofDays(1)
        )

        reporter.start()

        reporter.reportTrace(testTrace())

        assertThat(testShipper.reports).isEmpty()

        reporter.stop()

        assertThat(testShipper.reports).hasSize(1)
    }

    @Test
    fun `flush traces after interval`() {
        val testShipper = TestTraceShipper()
        val reporter = ApolloEngineReporter(
                traceShipper = testShipper,
                querySignatureStrategy = DefaultQuerySignatureStrategy,
                reportGenerator = DefaultReportGenerator(),
                reportInterval = Duration.ofMillis(100),
                // Never flush because of buffer going over threshold during this test
                flushBufferThreshold = 1_000_000_000
        )

        reporter.start()

        reporter.reportTrace(testTrace())

        assertThat(testShipper.reports).isEmpty()

        Thread.sleep(200)

        assertThat(testShipper.reports).hasSize(1)

        reporter.stop()
    }

    @Test
    fun `flush traces when buffer reaches threshold`() {
        val testShipper = TestTraceShipper()
        val reporter = ApolloEngineReporter(
                traceShipper = testShipper,
                querySignatureStrategy = DefaultQuerySignatureStrategy,
                reportGenerator = DefaultReportGenerator(),
                // No scheduled flush during test
                reportInterval = Duration.ofDays(1),
                flushImmediately = true
        )

        reporter.start()

        assertThat(testShipper.reports).isEmpty()

        reporter.reportTrace(testTrace())

        // Wait for the executor to run task which flushes buffer
        Thread.sleep(100)

        assertThat(testShipper.reports).hasSize(1)

        reporter.stop()
    }


    private fun testTrace(): TraceContext = TraceContext(
            query = "query { a }",
            operation = null,
            trace = QueryTrace(
                    version = 1,
                    startTime = Instant.now(),
                    endTime = Instant.now(),
                    duration = 0,
                    parsing = ParsingTrace(startOffset = 0, duration = 0),
                    validation = ValidationTrace(startOffset = 0, duration = 0),
                    execution = ExecutionTrace(resolvers = listOf())
            )
    )

    class TestTraceShipper : TraceShipper {

        val reports = mutableListOf<GraphqlApolloReporing.FullTracesReport>()

        override suspend fun ship(report: GraphqlApolloReporing.FullTracesReport) {
            reports.add(report)
        }

    }

}
