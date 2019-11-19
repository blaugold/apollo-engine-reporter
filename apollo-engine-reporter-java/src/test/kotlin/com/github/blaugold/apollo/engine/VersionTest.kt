package com.github.blaugold.apollo.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class VersionTest {

    @Test
    fun components() {
        assertThat(Version.major).isGreaterThanOrEqualTo(0)
        assertThat(Version.minor).isGreaterThanOrEqualTo(0)
        assertThat(Version.bug).isGreaterThanOrEqualTo(0)
    }

}
