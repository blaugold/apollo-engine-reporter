package com.gabrielterwesten.apollo.engine

/**
 * Skeleton implementation of an [AbstractHeaderProcessor] which filters headers.
 *
 * The default is to replace excluded headers with `__REMOVED__` but not drop them completely. This
 * behaviour can be changed by supplying another [HeaderReplacementStrategy].
 */
abstract class AbstractHeaderFilterProcessor(

        /**
         * @see AbstractHeaderProcessor.headerGroups
         */
        headerGroups: Set<HeaderGroup>? = null,

        /**
         * The [HeaderReplacementStrategy] to use for excluded headers.
         */
        replacementStrategy: HeaderReplacementStrategy? = null

) : AbstractHeaderProcessor(headerGroups) {

    /**
     * Returns true if the header with the given [name] and value should be included.
     */
    abstract fun includeHeader(name: String, value: List<String>): Boolean

    private val replacementStrategy: HeaderReplacementStrategy = replacementStrategy
            ?: DefaultHeaderReplacementStrategy()

    override fun processHeaders(headers: Map<String, List<String>>): Map<String, List<String>> {
        val replacementValues = headers
                .filter { !includeHeader(it.key, it.value) }
                .mapValues { replacementStrategy.replacementValue(it.key, it.value) }

        val droppedHeaders = replacementValues.filterValues { it == null }.keys

        return (headers - droppedHeaders).mapValues { replacementValues[it.key] ?: it.value }
    }

}

/**
 * Strategy to generate a replacement value for a header which has been removed.
 */
interface HeaderReplacementStrategy {

    /**
     * Returns a replacement value for the header with the given [name] and [value] or `null` to
     * completely remove the header.
     */
    fun replacementValue(name: String, value: List<String>): List<String>?

}

class DefaultHeaderReplacementStrategy: HeaderReplacementStrategy {

    override fun replacementValue(name: String, value: List<String>): List<String>? =
            listOf("__REMOVED__")

}
