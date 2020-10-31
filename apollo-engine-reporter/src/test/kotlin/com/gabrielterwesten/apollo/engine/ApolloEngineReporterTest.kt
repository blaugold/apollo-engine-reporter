package com.gabrielterwesten.apollo.engine

import java.time.Duration
import mdg.engine.proto.GraphqlApolloReporing
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeout

internal class ApolloEngineReporterTest {

  @Test
  fun `flush traces when stopping`() {
    val testShipper = TestTraceShipper()
    val reporter =
        ApolloEngineReporter(
            traceShipper = testShipper,
            querySignatureStrategy = DefaultQuerySignatureStrategy,
            reportGenerator = DefaultReportGenerator(),
            // No scheduled flush during test
            reportInterval = Duration.ofDays(1))

    reporter.start()

    reporter.reportTrace(testTrace())

    assertThat(testShipper.reports).isEmpty()

    reporter.stop()

    assertThat(testShipper.reports).hasSize(1)
  }

  @Test
  fun `flush traces after interval`() {
    val testShipper = TestTraceShipper()
    val reporter =
        ApolloEngineReporter(
            traceShipper = testShipper,
            querySignatureStrategy = DefaultQuerySignatureStrategy,
            reportGenerator = DefaultReportGenerator(),
            reportInterval = Duration.ofMillis(100),
            // Never flush because of buffer going over threshold during this test
            flushBufferThreshold = 1_000_000_000)

    reporter.start()

    reporter.reportTrace(testTrace())

    assertThat(testShipper.reports).isEmpty()

    assertTimeout(Duration.ofSeconds(5)) {
      while (testShipper.reports.size != 1) {
        Thread.sleep(100)
      }
    }

    reporter.stop()
  }

  @Test
  fun `flush traces when buffer reaches threshold`() {
    val testShipper = TestTraceShipper()
    val reporter =
        ApolloEngineReporter(
            traceShipper = testShipper,
            querySignatureStrategy = DefaultQuerySignatureStrategy,
            reportGenerator = DefaultReportGenerator(),
            // No scheduled flush during test
            reportInterval = Duration.ofDays(1),
            flushImmediately = true)

    reporter.start()

    assertThat(testShipper.reports).isEmpty()

    reporter.reportTrace(testTrace())

    assertTimeout(Duration.ofSeconds(5)) {
      while (testShipper.reports.size != 1) {
        Thread.sleep(100)
      }
    }

    reporter.stop()
  }

  private fun testTrace(): TraceContext =
      TraceContext(query = "query { a }", operation = null, trace = queryTrace())

  class TestTraceShipper : TraceShipper {

    val reports = mutableListOf<GraphqlApolloReporing.FullTracesReport>()

    override suspend fun ship(report: GraphqlApolloReporing.FullTracesReport) {
      reports.add(report)
    }
  }
}
