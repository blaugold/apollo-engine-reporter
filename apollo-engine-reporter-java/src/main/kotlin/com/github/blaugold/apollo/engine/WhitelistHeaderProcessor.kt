package com.github.blaugold.apollo.engine

/**
 * A header processor which filters out headers by name which are not listed in a whitelist.
 * The processor is case insensitive for header names.
 */
class WhitelistHeaderProcessor(

        /**
         * The whitelist to use.
         */
        whitelist: Set<String>,

        /**
         * @see AbstractHeaderProcessor.headerGroups
         */
        headerGroups: Set<HeaderGroup>? = null,

        /**
         * @see AbstractHeaderFilterProcessor.replacementStrategy
         */
        replacementStrategy: HeaderReplacementStrategy? = null

) : AbstractHeaderFilterProcessor(headerGroups, replacementStrategy) {

    private val lowerCaseWhitelist = whitelist.map { it.toLowerCase() }

    override fun includeHeader(name: String, value: List<String>): Boolean =
            lowerCaseWhitelist.contains(name.toLowerCase())

}
