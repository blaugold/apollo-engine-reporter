![CI](https://github.com/blaugold/apollo-engine-reporter/workflows/CI/badge.svg)
[![Download](https://api.bintray.com/packages/gabriel-terwesten-oss/maven/apollo-engine-reporter/images/download.svg) ](https://bintray.com/gabriel-terwesten-oss/maven/graphql-kotlin-support/_latestVersion)

# Apollo Engine Reporter

This is a reporter which sends GraphQL query traces to an Apollo Studio server (formerly Apollo Engine), written in
kotlin for the JVM.

## Install

The module is available through `jcenter`.

With maven:

```xml

<dependency>
    <groupId>com.gabrielterwesten</groupId>
    <artifactId>apollo-engine-reporter</artifactId>
    <version>1.2.1</version>
</dependency>
```

With gradle:

```kotlin
implementation("com.gabrielterwesten:apollo-engine-reporter:1.2.1")
```

## Usage

The code snippet below demonstrates the basic usage of the reporter.

```kotlin
val apiKey = "..."

val schema =
    """
        type Query {
            hello: String
        }
    """.trimIndent()

val typeDefinitionRegistry = SchemaParser().parse(schema)

val runtimeWiring =
    RuntimeWiring.newRuntimeWiring()
        .type("Query") {
          it.dataFetcher("hello", StaticDataFetcher("World"))
        }
        .build()

val reporter =
    ApolloEngineReporter(
        traceShipper = DefaultTraceShipper(apiKey = apiKey),
        querySignatureStrategy = DefaultQuerySignatureStrategy,
        reportGenerator = DefaultReportGenerator(),
    )

reporter.start()

val instrumentation =
    ChainedInstrumentation(listOf(
        TracingInstrumentation(),
        TraceReporterInstrumentation(reporter),
    ))

val graphQLSchema =
    SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)

val execution = GraphQL.newGraphQL(graphQLSchema).instrumentation(instrumentation).build()

val result = execution.execute("query { hello }")

println(result.getData<Any>().toString())

reporter.stop()
```

### License

apollo-engine-reporter is licensed under the MIT License. See [LICENSE](./LICENSE) for details.
