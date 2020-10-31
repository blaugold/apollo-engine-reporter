package com.gabrielterwesten.apollo.engine

enum class HeaderGroup {
  Request,
  Response
}

abstract class AbstractHeaderProcessor(

    /**
     * The [HeaderGroup] s to process. [HeaderGroup] s not in this set will be passed through as is.
     * If `null` is given all [HeaderGroup] s will be processed.
     */
    headerGroups: Set<HeaderGroup>? = null
) : TraceInputProcessor {

  /** Returns the processed [headers]. */
  abstract fun processHeaders(headers: Map<String, List<String>>): Map<String, List<String>>

  private val headerGroups: Set<HeaderGroup> = headerGroups ?: HeaderGroup.values().toSet()

  override fun process(input: TraceInput): TraceInput =
      input.httpTrace?.let {
        input.copy(
            httpTrace =
                it.copy(
                    requestHeaders = processHeaders(it.requestHeaders, HeaderGroup.Request),
                    responseHeaders = processHeaders(it.responseHeaders, HeaderGroup.Response)))
      }
          ?: input

  private fun processHeaders(
      headers: Map<String, List<String>>?, headerGroup: HeaderGroup
  ): Map<String, List<String>>? =
      headers?.let { if (headerGroups.contains(headerGroup)) processHeaders(it) else it }
}
