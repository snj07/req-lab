package com.reqlab.core.network

import com.reqlab.core.model.GraphQlBody
import com.reqlab.core.model.json.Json5
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Encodes the GraphQL-over-HTTP JSON envelope used by both Send and Copy As. */
fun encodeGraphQlEnvelope(
    body: GraphQlBody,
    variableLayers: List<Map<String, String>> = emptyList(),
    allowJson5: Boolean = true,
): String {
    val query = VariableResolver.resolve(body.query, variableLayers)
    val operationName = body.operationName
    val variables = body.variablesJson
    return buildString {
        append("{\"query\":")
        append(Json.encodeToString(String.serializer(), query))
        if (!operationName.isNullOrBlank()) {
            append(",\"operationName\":")
            append(Json.encodeToString(String.serializer(), operationName))
        }
        if (!variables.isNullOrBlank()) {
            append(",\"variables\":")
            val resolved = VariableResolver.resolve(variables, variableLayers)
            append(if (allowJson5) Json5.toWireJson(resolved).getOrElse { throw it } else resolved)
        }
        append('}')
    }
}
