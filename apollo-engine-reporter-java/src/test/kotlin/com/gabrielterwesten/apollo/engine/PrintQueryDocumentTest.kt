package com.gabrielterwesten.apollo.engine

import graphql.parser.Parser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class PrintQueryDocumentTest {

    @Test
    fun `anonymous operation`() = printQueryDocumentTest(
            """
            query { 
                a
            }
            """.trimIndent(),
            """
            query { a }
            """.trimIndent()
    )

    @Test
    fun `named operation`() = printQueryDocumentTest(
            """
            query A { 
                a
            }
            """.trimIndent(),
            """
            query A { a }
            """.trimIndent()
    )

    @Test
    fun `named operation with variable`() = printQueryDocumentTest(
            """
            query A(${"$"}v: String) { 
                a(v: ${"$"}v)
            }
            """.trimIndent(),
            """
            query A(${"$"}v: String) { a(v: ${"$"}v) }
            """.trimIndent()
    )

    @Test
    fun `named operation multiple variables`() = printQueryDocumentTest(
            """
            query A(${"$"}va: String, ${"$"}vb: String) { 
                a(va: ${"$"}va, vb: ${"$"}vb)
            }
            """.trimIndent(),
            """
            query A(${"$"}va: String, ${"$"}vb: String) { a(va: ${"$"}va, vb: ${"$"}vb) }
            """.trimIndent()
    )

    @Test
    fun `named operation with variable with default`() = printQueryDocumentTest(
            """
            query A(${"$"}v: String = "a") { 
                a(v: ${"$"}v)
            }
            """.trimIndent(),
            """
            query A(${"$"}v: String = "a") { a(v: ${"$"}v) }
            """.trimIndent()
    )

    @Test
    fun `null value`() = printQueryDocumentTest(
            """
            query { 
                a(a: null)
            }
            """.trimIndent(),
            """
            query { a(a: null) }
            """.trimIndent()
    )

    @Test
    fun `boolean value true`() = printQueryDocumentTest(
            """
            query { 
                a(a: true)
            }
            """.trimIndent(),
            """
            query { a(a: true) }
            """.trimIndent()
    )

    @Test
    fun `boolean value false`() = printQueryDocumentTest(
            """
            query { 
                a(a: false)
            }
            """.trimIndent(),
            """
            query { a(a: false) }
            """.trimIndent()
    )

    @Test
    fun `int value`() = printQueryDocumentTest(
            """
            query { 
                a(a: 1)
            }
            """.trimIndent(),
            """
            query { a(a: 1) }
            """.trimIndent()
    )

    @Test
    fun `float value`() = printQueryDocumentTest(
            """
            query { 
                a(a: 1.1)
            }
            """.trimIndent(),
            """
            query { a(a: 1.1) }
            """.trimIndent()
    )

    @Test
    fun `string value`() = printQueryDocumentTest(
            """
            query { 
                a(a: "a")
            }
            """.trimIndent(),
            """
            query { a(a: "a") }
            """.trimIndent()
    )

    @Test
    fun `enum value`() = printQueryDocumentTest(
            """
            query { 
                a(a: A)
            }
            """.trimIndent(),
            """
            query { a(a: A) }
            """.trimIndent()
    )

    @Test
    fun `var ref value`() = printQueryDocumentTest(
            """
            query { 
                a(a: ${"$"}a)
            }
            """.trimIndent(),
            """
            query { a(a: ${"$"}a) }
            """.trimIndent()
    )

    @Test
    fun `empty array value`() = printQueryDocumentTest(
            """
            query { 
                a(a: [])
            }
            """.trimIndent(),
            """
            query { a(a: []) }
            """.trimIndent()
    )

    @Test
    fun `single element array value`() = printQueryDocumentTest(
            """
            query { 
                a(a: [0])
            }
            """.trimIndent(),
            """
            query { a(a: [0]) }
            """.trimIndent()
    )

    @Test
    fun `multi element array value`() = printQueryDocumentTest(
            """
            query { 
                a(a: [0, 0])
            }
            """.trimIndent(),
            """
            query { a(a: [0, 0]) }
            """.trimIndent()
    )

    @Test
    fun `empty object array value`() = printQueryDocumentTest(
            """
            query { 
                a(a: {})
            }
             """.trimIndent(),
            """
            query { a(a: {}) }
            """.trimIndent()
    )

    @Test
    fun `single field object array value`() = printQueryDocumentTest(
            """
            query { 
                a(a: { a: 0 })
            }
            """.trimIndent(),
            """
            query { a(a: { a: 0 }) }
            """.trimIndent()
    )

    @Test
    fun `multi field object array value`() = printQueryDocumentTest(
            """
            query { 
                a(a: { a: 0, b: 0 })
            }
            """.trimIndent(),
            """
            query { a(a: { a: 0, b: 0 }) }
            """.trimIndent()
    )

    @Test
    fun `selection set with multiple fields`() = printQueryDocumentTest(
            """
            query { 
                a
                b
            }
            """.trimIndent(),
            """
            query { a b }
            """.trimIndent()
    )

    @Test
    fun `selection set with nested selection set`() = printQueryDocumentTest(
            """
            query { 
                a {
                    b
                }
            }
            """.trimIndent(),
            """
            query { a { b } }
            """.trimIndent()
    )

    @Test
    fun `selection set with fragment spread`() = printQueryDocumentTest(
            """
            query { 
                ... A
            }
            """.trimIndent(),
            """
            query { ... A }
            """.trimIndent()
    )

    @Test
    fun `selection set with inline fragment`() = printQueryDocumentTest(
            """
            query { 
                ... on A {
                    a
                }
            }
            """.trimIndent(),
            """
            query { ... on A { a } }
            """.trimIndent()
    )

    @Test
    fun `fragment definition`() = printQueryDocumentTest(
            """
            fragment A on B {
                a
            }
            """.trimIndent(),
            """
            fragment A on B { a }
            """.trimIndent()
    )

    @Test
    fun `operation directive`() = printQueryDocumentTest(
            """
            query @a { 
                a
            }
            """.trimIndent(),
            """
            query @a { a }
            """.trimIndent()
    )

}

private val parser = Parser()

fun printQueryDocumentTest(input: String, expected: String) {
    val queryDoc = parser.parseDocument(input)
    val printedQuery = queryDoc.printQuery()

    assertDoesNotThrow {
        parser.parseDocument(printedQuery)
    }

    assertThat(printedQuery).isEqualTo(expected)
}
