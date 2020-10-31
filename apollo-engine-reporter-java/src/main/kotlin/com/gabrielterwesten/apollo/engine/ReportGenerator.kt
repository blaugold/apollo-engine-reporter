package com.gabrielterwesten.apollo.engine

import mdg.engine.proto.GraphqlApolloReporing.*

/** A generator for assembling [FullTracesReport] s and its components. */
interface ReportGenerator {

  /** Returns a [ReportHeader] for inclusion in a [FullTracesReport]. */
  fun getReportHeader(): ReportHeader

  /** Returns a [Trace] build from the given trace [input]. */
  fun getTrace(input: TraceInput): Trace

  /**
   * Returns a [FullTracesReport] build from the given [header] and [traces].
   *
   * @param traces a mapping from query signatures to corresponding [Trace] s
   */
  fun getReport(header: ReportHeader, traces: Map<String, List<Trace>>): FullTracesReport
}
