package com.github.blaugold.apollo.engine

import graphql.GraphQLError
import mdg.engine.proto.GraphqlApolloReporing.*
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
                agentVersion = "JavaApolloEngineReporter ${Version.string}"
                uname = System.getProperty("os.name")
                runtimeVersion = Runtime.version().toString()

                this@DefaultReportGenerator.schemaTag?.also {
                    schemaTag = it
                }
            }
            .build()

    override fun getTrace(trace: QueryTrace,
                          clientInfo: ClientInfo?,
                          errors: List<GraphQLError>?): Trace = Trace.newBuilder().apply {
        startTime = trace.startTime.toTimestamp()
        endTime = trace.endTime.toTimestamp()
        durationNs = trace.duration

        if (clientInfo != null) setClientInfo(clientInfo)

        trace.execution.resolvers.forEach { getNode(it.path).configureNode(it) }
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

private fun Trace.Builder.setClientInfo(clientInfo: ClientInfo) {
    clientInfo.name?.also { clientName = it }
    clientInfo.version?.also { clientVersion = it }
    clientInfo.address?.also { clientAddress = it }
    clientInfo.referenceId?.also { clientReferenceId = it }
}

private fun Node.Builder.resolveSegment(segment: Any): Node.Builder = when (segment) {

    // This segments is a list index
    is Int ->
        // Find an existing node
        childBuilderList.find { it.index == segment }
        // Or create a new one
                ?: addChildBuilder().apply { index = segment }

    // This segment is a field selection
    is String -> childBuilderList.find { it.responseName == segment }
    // Should not happen
            ?: throw IllegalStateException("Could not resolve segment $segment.")

    else -> throw IllegalStateException("Unexpected type of segment $segment.")

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


private fun Node.Builder.configureNode(resolver: ResolverTrace) {
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

