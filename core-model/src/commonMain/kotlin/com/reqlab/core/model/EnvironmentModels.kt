package com.reqlab.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class VariableScope {
    GLOBAL,
    WORKSPACE,
    ENVIRONMENT,
    SECRET
}

@Serializable
data class VariableDefinition(
    val key: String,
    val value: String,
    val enabled: Boolean = true,
    val scope: VariableScope,
    val masked: Boolean = false
)

@Serializable
data class EnvironmentDefinition(
    val id: String,
    val workspaceId: String,
    val name: String,
    val variables: List<VariableDefinition> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
