package com.reqlab.core.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceDefinition(
    val id: String,
    val name: String,
    val description: String? = null,
    val collectionIds: List<String> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Serializable
data class CollectionDefinition(
    val id: String,
    val workspaceId: String,
    val name: String,
    val description: String? = null,
    val folderIds: List<String> = emptyList(),
    val requestIds: List<String> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Serializable
data class FolderDefinition(
    val id: String,
    val collectionId: String,
    val parentFolderId: String? = null,
    val name: String,
    val folderIds: List<String> = emptyList(),
    val requestIds: List<String> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
