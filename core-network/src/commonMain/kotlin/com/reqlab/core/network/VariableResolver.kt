package com.reqlab.core.network

import kotlin.random.Random
import kotlinx.datetime.Clock

private val variablePattern = Regex("\\{\\{\\s*([^{}]+?)\\s*\\}\\}")
private val randomIntPattern = Regex("^\\$?randomInt\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)$", RegexOption.IGNORE_CASE)

object VariableResolver {
    fun resolve(
        value: String,
        variableLayers: List<Map<String, String>>,
        removeUnresolved: Boolean = false,
    ): String {
        if (value.isBlank()) {
            return value
        }

        return variablePattern.replace(value) { matchResult ->
            val token = matchResult.groupValues[1].trim()
            val fromLayers = variableLayers.firstNotNullOfOrNull { layer -> layer[token] }
            fromLayers ?: resolveDynamicToken(token) ?: if (removeUnresolved) "" else matchResult.value
        }
    }

    private fun resolveDynamicToken(token: String): String? {
        val normalized = token.removePrefix("$")
        return when {
            normalized.equals("timestamp", ignoreCase = true) -> Clock.System.now().toEpochMilliseconds().toString()
            normalized.equals("isoTimestamp", ignoreCase = true) -> Clock.System.now().toString()
            randomIntPattern.matches(token) -> {
                val groups = randomIntPattern.find(token)?.groupValues ?: return null
                val minInclusive = groups[1].toIntOrNull() ?: return null
                val maxInclusive = groups[2].toIntOrNull() ?: return null
                if (minInclusive > maxInclusive) return null
                Random.nextInt(minInclusive, maxInclusive + 1).toString()
            }
            normalized.equals("randomInt", ignoreCase = true) -> Random.nextInt(0, 1000).toString()
            else -> null
        }
    }
}
