package com.gabrielterwesten.apollo.engine

import graphql.GraphQL
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.tracing.TracingInstrumentation
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class TraceReporterInstrumentationTest {

  @Test
  fun `should throw IllegalStateException if extension contains no tracing data`() {
    // Given
    val graphQL =
        testGraphQL(
            TraceReporterInstrumentation(TestTraceReporter()),
            includeTracingInstrumentation = false)

    // Then
    assertThrows<IllegalStateException> { graphQL.execute("query { hello }") }
  }

  @Test
  fun `integrate with graphql tracing data`() {
    // Given
    val testReporter = TestTraceReporter()
    val instrumentation = TraceReporterInstrumentation(testReporter)
    val graphQL = testGraphQL(instrumentation)
    val query = "query Test { hello }"
    val operation = "Test"

    // When
    assertDoesNotThrow {
      graphQL.execute {
        it.query(query)
        it.operationName(operation)
      }
    }

    // Then
    val traceContext = testReporter.traces.first()
    assertThat(traceContext.query).isEqualTo(query)
    assertThat(traceContext.operation).isEqualTo(operation)
    assertThat(traceContext.trace.execution.resolvers.first().path).containsExactly("hello")
  }

  @Test
  fun `support removing tracing data from extension`() {
    // Given
    val instrumentation =
        TraceReporterInstrumentation(TestTraceReporter(), removeTracingData = true)
    val graphQL = testGraphQL(instrumentation)

    // When
    val executionResult = graphQL.execute { it.query("query { hello }") }

    // Then
    assertThat(executionResult.extensions["tracing"]).isNull()
  }

  @Test
  fun `support leaving tracing data from extension`() {
    // Given
    val instrumentation =
        TraceReporterInstrumentation(TestTraceReporter(), removeTracingData = false)
    val graphQL = testGraphQL(instrumentation)

    // When
    val executionResult = graphQL.execute { it.query("query { hello }") }

    // Then
    assertThat(executionResult.extensions["tracing"]).isNotNull
  }

  class TestTraceReporter : TraceReporter {

    val traces = mutableListOf<TraceContext>()

    override fun reportTrace(traceContext: TraceContext) {
      traces.add(traceContext)
    }
  }

  private fun testGraphQL(
      testInstrumentation: TraceReporterInstrumentation,
      includeTracingInstrumentation: Boolean = true
  ): GraphQL {
    val schema =
        """
            type Query {
                hello: String
            }
        """.trimIndent()

    val typeDefinitionRegistry = SchemaParser().parse(schema)

    val runtimeWiring =
        RuntimeWiring.newRuntimeWiring()
            .type("Query") { it.dataFetcher("hello", StaticDataFetcher("World")) }
            .build()

    val graphQLSchema =
        SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)

    val instrumentations = mutableListOf<Instrumentation>()

    if (includeTracingInstrumentation) {
      instrumentations.add(TracingInstrumentation())
    }

    instrumentations.add(testInstrumentation)

    return GraphQL.newGraphQL(graphQLSchema)
        .instrumentation(ChainedInstrumentation(instrumentations))
        .build()
  }
}
