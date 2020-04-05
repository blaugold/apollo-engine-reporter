package com.gabrielterwesten.apollo.engine

/**
 * Version of the apollo-engine-reporter-java library.
 */
object Version {

    val string by lazy {
        javaClass.classLoader
                .getResource("apollo-engine-reporter-java.version")!!
                .readText()
    }

    private val components: List<Int> by lazy {
        Regex("(\\d+)\\.(\\d+)\\.(\\d+).*")
                .matchEntire(string)!!
                .groupValues.slice(1..3)
                .map { it.toInt() }
    }

    val major by lazy { components[0] }

    val minor by lazy { components[1] }

    val bug by lazy { components[2] }

    override fun toString(): String = string

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = 31

}
