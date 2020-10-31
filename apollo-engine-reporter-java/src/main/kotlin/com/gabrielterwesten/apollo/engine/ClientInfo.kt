package com.gabrielterwesten.apollo.engine

/** Client info used to differentiate traces from different clients. */
data class ClientInfo(

    /** The name of the client. */
    val name: String? = null,

    /** The version of the client. */
    val version: String? = null,

    /** The address of the client. */
    val address: String? = null,

    /** The reference id of the client. */
    val referenceId: String? = null)
