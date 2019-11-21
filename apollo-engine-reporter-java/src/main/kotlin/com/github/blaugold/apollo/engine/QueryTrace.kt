package com.github.blaugold.apollo.engine

import java.time.Instant

/**
 * Root of the [tracing data](https://github.com/apollographql/apollo-tracing) generated during
 * execution of a query.
 */
data class QueryTrace(

        /**
         * Version of the tracing format of this trace.
         */
        val version: Long,

        /**
         * Start of the traced query.
         */
        val startTime: Instant,

        /**
         * End of the traced query.
         */
        val endTime: Instant,

        /**
         * Total duration of the traced query.
         */
        val duration: Long,

        /**
         * Tracing data for parsing of the traced query.
         */
        val parsing: ParsingTrace,

        /**
         * Tracing data for validation of the traced query.
         */
        val validation: ValidationTrace,

        /**
         * Tracing data for execution of the traced query.
         */
        val execution: ExecutionTrace

)

/**
 * Tracing data for parsing of a query.
 */
data class ParsingTrace(

        /**
         * Start of parsing as offset from [QueryTrace.startTime] in nano seconds.
         */
        val startOffset: Long,

        /**
         * Duration of parsing in nano seconds.
         */
        val duration: Long

)

/**
 * Tracing data for validation of a query.
 */
data class ValidationTrace(

        /**
         * Start of validation as offset from [QueryTrace.startTime] in nano seconds.
         */
        val startOffset: Long,

        /**
         * Duration of validation in nano seconds.
         */
        val duration: Long

)

/**
 * Tracing data for execution of a query.
 */
data class ExecutionTrace(

        /**
         * Tracing data for resolvers which executed as part of the traced query.
         */
        val resolvers: List<ResolverTrace>

)


/**
 * Tracing data for a resolver.
 */
data class ResolverTrace(

        /**
         * The full path to the resolved field.
         *
         * Contains the following types:
         * - String - field name or **alias** from query
         * - Integer - list index
         */
        val path: List<Any>,

        /**
         * The type of the parent object.
         */
        val parentType: String,

        /**
         * The field name as defined in the schema.
         */
        val fieldName: String,

        /**
         * The return type of field.
         */
        val returnType: String,

        /**
         * Start of execution of resolver as offset from [QueryTrace.startTime] in nano seconds.
         */
        val startOffset: Long,

        /**
         * Duration of execution of resolver in nano seconds.
         */
        val duration: Long

)
