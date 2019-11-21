package com.github.blaugold.apollo.engine

import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

/**
 * [Instrumentation] which can be used to hook into query execution to extract tracing data and pass
 * it to a [TraceReporter].
 */
open class TraceReporterInstrumentation(

        /**
         * The [TraceReporter] to submit traces to.
         */
        private val traceReporter: TraceReporter,

        /**
         * Whether or not to remove the tracing data from `extensions`.
         */
        private val removeTracingData: Boolean = true

) : SimpleInstrumentation() {

    override fun instrumentExecutionResult(executionResult: ExecutionResult, parameters: InstrumentationExecutionParameters): CompletableFuture<ExecutionResult> {
        check(executionResult.extensions != null && executionResult.extensions["tracing"] != null) {
            "Could not find tracing data in extensions. Make sure TracingInstrumentation is " +
                    "installed and chained before TraceReporterInstrumentation."
        }

        traceReporter.reportTrace(createTraceContext(executionResult, parameters))

        val nextExecutionResult = if (removeTracingData) {
            ExecutionResultImpl.newExecutionResult()
                    .from(executionResult)
                    .extensions(executionResult.extensions - "tracing")
                    .build()
        } else executionResult

        return super.instrumentExecutionResult(nextExecutionResult, parameters)
    }

    open fun createTraceContext(executionResult: ExecutionResult,
                                parameters: InstrumentationExecutionParameters): TraceContext {
        val tracingData = executionResult.extensions["tracing"]!!

        return TraceContext(
                trace = reifyTracingData(tracingData),
                query = parameters.query,
                operation = parameters.operation,
                variables = parameters.variables,
                errors = executionResult.errors,
                context = parameters.getContext()
        )
    }

}

private fun reifyTracingData(tracingData: Any): QueryTrace {
    require(tracingData is Map<*, *>)

    return QueryTrace(
            version = tracingData.getChecked("version"),
            startTime = Instant.parse(tracingData.getChecked("startTime")),
            endTime = Instant.parse(tracingData.getChecked("endTime")),
            duration = tracingData.getChecked("duration"),
            parsing = (tracingData.getChecked<Map<*, *>>("parsing")).let {
                ParsingTrace(
                        startOffset = it.getChecked("startOffset"),
                        duration = it.getChecked("duration")
                )
            },
            validation = (tracingData.getChecked<Map<*, *>>("validation")).let {
                ValidationTrace(
                        startOffset = it.getChecked("startOffset"),
                        duration = it.getChecked("duration")
                )
            },
            execution = (tracingData["execution"] as Map<*, *>).let { execution ->
                ExecutionTrace(resolvers = (execution.getChecked<List<*>>("resolvers"))
                        .map { it as Map<*, *> }
                        .map {
                            @Suppress("UNCHECKED_CAST")
                            ResolverTrace(
                                    path = it.getChecked("path") as List<Any>,
                                    parentType = it.getChecked("parentType"),
                                    fieldName = it.getChecked("fieldName"),
                                    returnType = it.getChecked("returnType"),
                                    startOffset = it.getChecked("startOffset"),
                                    duration = it.getChecked("duration")
                            )
                        }
                )
            }
    )
}

private fun <T : Any> Map<*, *>.getChecked(key: String, type: KClass<T>): T {
    val value = get(key as Any)
    requireNotNull(value) { "Expected to find value at $key but got null." }
    require(type.isInstance(value)) {
        "Expected to find value of type $type at $key but got ${value::class}."
    }

    return type.java.cast(value)
}

private inline fun <reified T : Any> Map<*, *>.getChecked(key: String): T = getChecked(key, T::class)
