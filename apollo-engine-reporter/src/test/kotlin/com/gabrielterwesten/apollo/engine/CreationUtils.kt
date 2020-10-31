package com.gabrielterwesten.apollo.engine

import java.time.Instant

fun queryTrace(resolvers: List<ResolverTrace> = emptyList()): QueryTrace {
  return QueryTrace(
      version = 1,
      startTime = Instant.now(),
      endTime = Instant.now(),
      duration = 0,
      parsing = ParsingTrace(startOffset = 0, duration = 0),
      validation = ValidationTrace(startOffset = 0, duration = 0),
      execution = ExecutionTrace(resolvers = resolvers))
}

fun resolverTrace(
    path: List<Any>,
    parentType: String = "Parent",
    returnType: String = "Return",
    fieldName: String = path.lastOrNull().let { if (it is String) it else "FieldName" },
    startOffset: Long = 31,
    duration: Long = 5
): ResolverTrace {
  return ResolverTrace(
      path = path,
      parentType = parentType,
      returnType = returnType,
      fieldName = fieldName,
      startOffset = startOffset,
      duration = duration)
}
