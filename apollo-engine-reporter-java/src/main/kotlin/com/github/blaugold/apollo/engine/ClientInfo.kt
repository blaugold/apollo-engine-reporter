package com.github.blaugold.apollo.engine

/**
 * Function which generates a [ClientInfo] from the passed [TraceContext], for inclusion
 * in the trace. May return null if a ClientInfo can not be created for the given trace.
 */
typealias ClientInfoFactory = (TraceContext) -> ClientInfo?

/**
 * Client info used to differentiate traces from different clients.
 */
data class ClientInfo(

        /**
         * The name of the client.
         */
        val name: String? = null,

        /**
         * The version of the client.
         */
        val version: String? = null,

        /**
         * The address of the client.
         */
        val address: String? = null,

        /**
         * The reference id of the client.
         */
        val referenceId: String? = null

)
