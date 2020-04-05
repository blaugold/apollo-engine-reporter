package com.gabrielterwesten.apollo.engine

import com.google.protobuf.Timestamp
import java.time.Instant

fun Instant.toTimestamp(): Timestamp = Timestamp.newBuilder().apply {
    seconds = epochSecond
    nanos = nano
}.build()
