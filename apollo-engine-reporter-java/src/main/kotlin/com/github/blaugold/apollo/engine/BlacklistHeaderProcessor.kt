package com.github.blaugold.apollo.engine

/**
 * A header processor which filters out headers by name which are listed in a blacklist.
 * The processor is case insensitive for header names.
 */
class BlacklistHeaderProcessor(

        /**
         * The blacklist to use.
         */
        blacklist: Set<String>,

        /**
         * @see AbstractHeaderProcessor.headerGroups
         */
        headerGroups: Set<HeaderGroup>? = null,

        /**
         * @see AbstractHeaderFilterProcessor.replacementStrategy
         */
        replacementStrategy: HeaderReplacementStrategy? = null

) : AbstractHeaderFilterProcessor(headerGroups, replacementStrategy) {

    private val lowerCaseBlacklist = blacklist.map { it.toLowerCase() }

    override fun includeHeader(name: String, value: List<String>): Boolean =
            !lowerCaseBlacklist.contains(name.toLowerCase())

}
