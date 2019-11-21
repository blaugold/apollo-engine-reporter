package com.github.blaugold.apollo.engine

import graphql.GraphQLError
import mdg.engine.proto.GraphqlApolloReporing.*
import mdg.engine.proto.GraphqlApolloReporing.Trace.HTTP.Method
import mdg.engine.proto.GraphqlApolloReporing.Trace.HTTP.Values
import mdg.engine.proto.GraphqlApolloReporing.Trace.Node
import java.net.InetAddress

/**
 * Default implementation of [ReportGenerator].
 */
class DefaultReportGenerator(

        /**
         * The schema tag to include in [ReportHeader].
         */
        private val schemaTag: String? = null

) : ReportGenerator {

    override fun getReportHeader(): ReportHeader = ReportHeader
            .newBuilder()
            .apply {
                hostname = InetAddress.getLocalHost().hostName
                agentVersion = "ApolloEngineReporterJava ${Version.string}"
                uname = System.getProperty("os.name")
                runtimeVersion = Runtime.version().toString()

                this@DefaultReportGenerator.schemaTag?.also {
                    schemaTag = it
                }
            }
            .build()

    override fun getTrace(input: TraceInput): Trace = Trace.newBuilder().apply {
        val (trace, clientInfo, errors, httpTrace) = input

        buildTrace(trace)

        clientInfo?.also { buildClientInfo(it) }
        httpTrace?.also { buildHttp(it) }

        trace.execution.resolvers.forEach { getNode(it.path).buildResolverNode(it) }
        errors?.forEach { getNode(it.path).addError(it) }
    }.build()

    override fun getReport(
            header: ReportHeader,
            traces: Map<String, List<Trace>>
    ): FullTracesReport = FullTracesReport
            .newBuilder()
            .setHeader(header)
            .putAllTracesPerQuery(traces.mapValues { (_, traces) ->
                Traces.newBuilder().addAllTrace(traces).build()
            })
            .build()

}

private fun Trace.Builder.buildTrace(trace: QueryTrace) {
    startTime = trace.startTime.toTimestamp()
    endTime = trace.endTime.toTimestamp()
    durationNs = trace.duration
}

private fun Trace.Builder.buildHttp(httpTrace: HttpTrace) {
    httpBuilder.apply {
        httpTrace.protocol?.also { protocol = it }
        httpTrace.secure?.also { secure = it }
        httpTrace.method?.also {
            method = when (it) {
                HttpMethod.Connect -> Method.CONNECT
                HttpMethod.Delete -> Method.DELETE
                HttpMethod.Get -> Method.GET
                HttpMethod.Head -> Method.HEAD
                HttpMethod.Options -> Method.OPTIONS
                HttpMethod.Patch -> Method.PATCH
                HttpMethod.Post -> Method.POST
                HttpMethod.Put -> Method.PUT
                HttpMethod.Trace -> Method.TRACE
                HttpMethod.Unknown -> Method.UNKNOWN
                HttpMethod.Unrecognized -> Method.UNRECOGNIZED
            }
        }
        httpTrace.path?.also { path = it }
        httpTrace.statusCode?.also { statusCode = it }
        httpTrace.host?.also { host = it }

        httpTrace.requestHeaders?.also { headers ->
            putAllRequestHeaders(headers.mapValues { Values.newBuilder().addAllValue(it.value).build() })
        }
        httpTrace.responseHeaders?.also { headers ->
            putAllResponseHeaders(headers.mapValues { Values.newBuilder().addAllValue(it.value).build() })
        }
    }
}

private fun Trace.Builder.buildClientInfo(clientInfo: ClientInfo) {
    clientInfo.name?.also { clientName = it }
    clientInfo.version?.also { clientVersion = it }
    clientInfo.address?.also { clientAddress = it }
    clientInfo.referenceId?.also { clientReferenceId = it }
}

fun Trace.Builder.getNode(path: List<Any>): Node.Builder {
    var parent = rootBuilder
    val mPath = path.toMutableList()

    while (mPath.isNotEmpty()) {
        val segment = mPath.removeAt(0)
        parent = when (segment) {
            is Int -> parent.childBuilderList.find { it.index == segment }
                    ?: parent.addChildBuilder().apply { index = segment }
            is String -> parent.childBuilderList.find { it.responseName == segment }
                    ?: parent.addChildBuilder().apply { responseName = segment }
            else -> throw IllegalArgumentException("Path segment is of unexpected type: $segment")
        }
    }

    return parent
}

private fun Node.Builder.buildResolverNode(resolver: ResolverTrace) {
    val fieldSelection = resolver.path.last() as String

    if (resolver.fieldName != fieldSelection) {
        originalFieldName = resolver.fieldName
    }

    type = resolver.returnType
    parentType = resolver.parentType

    startTime = resolver.startOffset
    endTime = resolver.startOffset + resolver.duration
}

private fun Node.Builder.addError(error: GraphQLError) {
    addErrorBuilder().apply {
        message = error.message

        error.locations?.forEach {
            addLocationBuilder().apply {
                line = it.line
                column = it.column
            }
        }
    }
}

