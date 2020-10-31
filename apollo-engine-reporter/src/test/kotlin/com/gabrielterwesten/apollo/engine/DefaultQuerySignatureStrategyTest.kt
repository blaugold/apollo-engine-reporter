package com.gabrielterwesten.apollo.engine

import com.gabrielterwesten.apollo.engine.DefaultQuerySignatureStrategy.computeSignature
import graphql.parser.Parser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DefaultQuerySignatureStrategyTest {

  @Test
  fun `add operation header for anonymous operation`() {
    // Given
    val queryStr = """
            query {
                a
            }
        """.trimIndent()
    val queryDoc = Parser().parseDocument(queryStr)

    // When
    val signature = computeSignature(queryDoc)

    // Then
    assertThat(signature)
        .isEqualTo("""
            # -
            query { a }
        """.trimIndent())
  }

  @Test
  fun `add operation header for named operation`() {
    // Given
    val queryStr =
        """
            query A {
                a
            }
        """.trimIndent()
    val queryDoc = Parser().parseDocument(queryStr)

    // When
    val signature = computeSignature(queryDoc, "A")

    // Then
    assertThat(signature)
        .isEqualTo("""
            # A
            query A { a }
        """.trimIndent())
  }

  @Test
  fun `use AstSignature to transform query`() {
    // This just checks that fields are ordered which is one the things AstSignature does.

    // Given
    val queryStr =
        """
            query {
                b
                a
            }
        """.trimIndent()
    val queryDoc = Parser().parseDocument(queryStr)

    // When
    val signature = computeSignature(queryDoc)

    // Then
    assertThat(signature)
        .isEqualTo("""
            # -
            query { a b }
        """.trimIndent())
  }
}
