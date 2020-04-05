package com.gabrielterwesten.apollo.engine

/**
 * [TraceInputProcessor] which uses [HttpTrace], if available, to generate [ClientInfo].
 */
class ClientInfoTraceInputProcessor(

        private val clientNameHeader: String = "ApolloGraphQL-Client-Name",

        private val clientVersionHeader: String = "ApolloGraphQL-Client-Version"

): TraceInputProcessor {

    override fun process(input: TraceInput): TraceInput {
        val headers = input.httpTrace?.requestHeaders?.mapKeys { it.key.toLowerCase() }

        headers?.also {
            val name = headers[clientNameHeader.toLowerCase()]?.first()
            val version = headers[clientVersionHeader.toLowerCase()]?.first()

            if (name != null && version != null) {
                return input.copy(clientInfo = ClientInfo(name = name, version = version))
            }
        }

        return input
    }

}
