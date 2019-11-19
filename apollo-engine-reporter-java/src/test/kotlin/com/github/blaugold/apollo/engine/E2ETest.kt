package com.github.blaugold.apollo.engine

import graphql.GraphQL
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.tracing.TracingInstrumentation
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.junit.jupiter.api.Test

class E2ETest {

    private val testApiKey = "service:java-apollo-engine-reporter:9AHOL8XylVxVkjL_MLKGXg"

    @Test
    fun smoke() {
        val schema = """
            type Query {
                hello: String
                error: String
            }
        """.trimIndent()

        val typeDefinitionRegistry = SchemaParser().parse(schema)

        val runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query") {
                    it.dataFetcher("hello", StaticDataFetcher("World"))
                    it.dataFetcher("error") { throw IllegalArgumentException("Whops") }
                }
                .build()


        val reporter = ApolloEngineReporter(
                traceShipper = DefaultTraceShipper(apiKey = testApiKey),
                querySignatureStrategy = DefaultQuerySignatureStrategy,
                reportGenerator = DefaultReportGenerator(),
                clientInfoFactory = { ClientInfo(name = "E2ETest", version = Version.string) }
        )

        reporter.start()

        val instrumentation = ChainedInstrumentation(listOf(
                TracingInstrumentation(),
                TraceReporterInstrumentation(reporter)
        ))

        val graphQLSchema = SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)

        val execution = GraphQL.newGraphQL(graphQLSchema)
                .instrumentation(instrumentation)
                .build()

        val result = execution.execute("query { hello error }")

        println(result.getData<Any>().toString())

        reporter.stop()
    }

}
