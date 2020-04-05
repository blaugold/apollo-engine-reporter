package com.gabrielterwesten.apollo.engine

import graphql.language.*
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Prints this [Document] but only includes query related definitions (Operation and Fragment).
 *
 * Whitespace is minimized (no line brakes and indentation).
 */
fun Document.printQuery(): String {
    val sw = StringWriter()
    val out = PrintWriter(sw)

    definitions.forEach { definition ->
        when (definition) {
            is OperationDefinition -> definition.print(out)
            is FragmentDefinition -> definition.print(out)
        }

        if (definitions.last() != definition) out.print(" ")
    }

    return sw.toString()
}

private fun Type<*>.print(out: PrintWriter) {
    when (this) {
        is TypeName -> out.print(name)
        is ListType -> {
            out.print("[")
            type.print(out)
            out.print("]")
        }
        is NonNullType -> {
            type.print(out)
            out.print("!")
        }
    }
}

private fun Value<*>.print(out: PrintWriter) {
    when (this) {
        is NullValue -> out.print("null")
        is BooleanValue -> out.print(if (isValue) "true" else "false")
        is IntValue -> out.print(value.toString())
        is FloatValue -> out.print(value.toPlainString())
        is StringValue -> out.print("\"$value\"")
        is VariableReference -> {
            out.print("$"); out.print(name)
        }
        is EnumValue -> out.print(name)
        is ArrayValue -> {
            values.printContext(startToken = "[", endToken = "]", printEmpty = true, out = out) {
                it.print(out)
            }
        }
        is ObjectValue -> {
            objectFields.printContext(
                    startToken = "{",
                    endToken = "}",
                    printEmpty = true,
                    padStartAndEnd = true,
                    out = out
            ) {
                out.print(it.name)
                out.print(": ")
                it.value.print(out)
            }
        }
    }
}

@JvmName("printArguments")
private fun List<Argument>.print(out: PrintWriter) = printArgumentList(out) { it.print(out) }

private fun Argument.print(out: PrintWriter) {
    out.print(name)
    out.print(": ")
    value.print(out)
}

@JvmName("printDirectives")
private fun List<Directive>.print(out: PrintWriter) {
    forEach {
        it.print(out)

        out.print(" ")
    }
}

private fun Directive.print(out: PrintWriter) {
    out.print("@")
    out.print(name)

    arguments.print(out)
}

private fun OperationDefinition.print(out: PrintWriter) {
    out.print(operation.name.toLowerCase())
    out.print(" ")

    if (name != null) {
        out.print(name)

        variableDefinitions.print(out)

        out.print(" ")
    }

    directives.print(out)

    selectionSet.print(out)
}

@JvmName("printVariableDefinitions")
private fun List<VariableDefinition>.print(out: PrintWriter) =
        printArgumentList(out) { it.print(out) }

private fun VariableDefinition.print(out: PrintWriter) {
    out.print("$")
    out.print(name)
    out.print(": ")
    type.print(out)
    if (defaultValue != null) {
        out.print(" = ")
        defaultValue.print(out)
    }
}

private fun SelectionSet.print(out: PrintWriter) {
    selections.printContext(
            startToken = "{",
            endToken = "}",
            elementSeparator = "",
            padStartAndEnd = true,
            out = out
    ) {
        when (it) {
            is Field -> it.print(out)
            is FragmentSpread -> it.print(out)
            is InlineFragment -> it.print(out)
        }
    }
}

private fun Field.print(out: PrintWriter) {
    if (alias != null) {
        out.print(alias)
        out.print(": ")
    }

    out.print(name)

    arguments.print(out)

    if (selectionSet != null) {
        out.print(" ")
        selectionSet.print(out)
    }
}

private fun FragmentSpread.print(out: PrintWriter) {
    out.print("... ")
    out.print(name)
}

private fun InlineFragment.print(out: PrintWriter) {
    out.print("... on ")
    out.print(typeCondition.name)
    out.print(" ")
    selectionSet.print(out)
}

private fun FragmentDefinition.print(out: PrintWriter) {
    out.print("fragment ")
    out.print(name)
    out.print(" on ")
    out.print(typeCondition.name)
    out.print(" ")
    selectionSet.print(out)
}

private fun <T> List<T>.printArgumentList(out: PrintWriter, printArgument: (T) -> Unit) =
        printContext(startToken = "(", endToken = ")", out = out) { printArgument(it) }

private fun <T> List<T>.printContext(
        startToken: String,
        endToken: String,
        elementSeparator: String = ",",
        printEmpty: Boolean = false,
        padStartAndEnd: Boolean = false,
        out: PrintWriter,
        printArgument: (T) -> Unit
) {
    if (isEmpty() && !printEmpty) return

    out.print(startToken)

    forEach {
        if (first() == it && padStartAndEnd) out.print(" ")

        printArgument(it)

        if (last() == it) {
            if (padStartAndEnd) out.print(" ")
        } else {
            out.print(elementSeparator)
            out.print(" ")
        }
    }

    out.print(endToken)
}
