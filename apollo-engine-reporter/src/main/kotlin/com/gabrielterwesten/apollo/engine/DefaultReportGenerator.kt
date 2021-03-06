package com.gabrielterwesten.apollo.engine

import com.fasterxml.jackson.databind.ObjectMapper
import graphql.GraphQLError
import java.net.InetAddress
import mdg.engine.proto.GraphqlApolloReporing.*
import mdg.engine.proto.GraphqlApolloReporing.Trace.HTTP.Method
import mdg.engine.proto.GraphqlApolloReporing.Trace.HTTP.Values
import mdg.engine.proto.GraphqlApolloReporing.Trace.Node
import org.slf4j.LoggerFactory

/** Default implementation of [ReportGenerator]. */
class DefaultReportGenerator(

    /** The schema tag to include in [ReportHeader]. */
    private val schemaTag: String? = null,

    /** The [ObjectMapper] to use to serialize variables into JSON. */
    variableObjectMapper: ObjectMapper? = null
) : ReportGenerator {

  private val log = LoggerFactory.getLogger(javaClass)

  private val variablesObjectMapper: ObjectMapper = variableObjectMapper ?: ObjectMapper()

  override fun getReportHeader(): ReportHeader =
      ReportHeader.newBuilder()
          .apply {
            hostname = InetAddress.getLocalHost().hostName
            agentVersion = "ApolloEngineReporterJava ${Version.string}"
            uname = System.getProperty("os.name")
            runtimeVersion = Runtime.version().toString()

            this@DefaultReportGenerator.schemaTag?.also { schemaTag = it }
          }
          .build()

  override fun getTrace(input: TraceInput): Trace =
      Trace.newBuilder()
          .apply {
            val (trace, variables, clientInfo, errors, httpTrace) = input

            buildTrace(trace)

            variables?.also { buildVariables(it) }

            clientInfo?.also { buildClientInfo(it) }
            httpTrace?.also { buildHttp(it) }

            trace.execution.resolvers.forEach { getNode(it.path).buildResolverNode(it) }
            errors?.forEach {
              when (it.path) {
                null -> rootBuilder.addError(it)
                else -> getNode(it.path).addError(it)
              }
            }
          }
          .build()

  override fun getReport(header: ReportHeader, traces: Map<String, List<Trace>>): FullTracesReport =
      FullTracesReport.newBuilder()
          .setHeader(header)
          .putAllTracesPerQuery(
              traces.mapValues { (_, traces) -> Traces.newBuilder().addAllTrace(traces).build() })
          .build()

  private fun Trace.Builder.buildVariables(variables: Map<String, Any>) {
    detailsBuilder.putAllVariablesJson(
        variables.mapValues {
          try {
            variablesObjectMapper.writeValueAsString(it.value)
          } catch (e: Throwable) {
            log.error("JSON serialization of GraphQL query variable failed:", e)

            "\"__JSON_SERIALIZATION_FAILED__\""
          }
        })
  }
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
      method =
          when (it) {
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
    parent =
        when (segment) {
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
