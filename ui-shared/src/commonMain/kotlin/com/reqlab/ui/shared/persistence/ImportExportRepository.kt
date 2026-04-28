package com.reqlab.ui.shared.persistence

import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.FormEntryType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.state.FormDataEntryState
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import androidx.compose.runtime.mutableStateListOf
import com.reqlab.ui.shared.state.EnvState
import com.reqlab.ui.shared.state.HistoryItem
import com.reqlab.ui.shared.state.MutableKeyValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.reqlab.ui.shared.platform.generateUuid

class ImportExportException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

object ImportExportNaming {
    fun generateUniqueCollectionName(name: String, existingNames: Set<String>): String = generateUniqueName(name, existingNames)
    fun generateUniqueEnvironmentName(name: String, existingNames: Set<String>): String = generateUniqueName(name, existingNames)

    private fun generateUniqueName(name: String, existingNames: Set<String>): String {
        val base = name.trim().ifBlank { "Untitled" }
        if (base !in existingNames) return base

        var i = 1
        while (true) {
            val candidate = "$base ($i)"
            if (candidate !in existingNames) return candidate
            i++
        }
    }
}

data class ReqLabCollectionDto(
    val name: String,
    val folders: List<FolderDto>,
    val requests: List<RequestDto>,
    val id: String? = null,
)

data class FolderDto(
    val name: String,
    val folders: List<FolderDto>,
    val requests: List<RequestDto>,
    val id: String? = null,
)

data class RequestDto(
    val name: String,
    val method: String,
    val url: String,
    val preRequestScript: String? = null,
    val testScript: String? = null,
    val userHeaders: List<Pair<String, String>> = emptyList(),
    val bodyType: String? = null,
    val bodyContent: String? = null,
    /** Per-type raw body contents, keyed by BodyType.name (e.g. "JSON", "XML"). */
    val rawContents: Map<String, String>? = null,
    val formDataEntries: List<FormDataEntryState> = emptyList(),
    val urlencodedEntries: List<FormDataEntryState> = emptyList(),
    val authType: String? = null,
    val authUsername: String? = null,
    val authPassword: String? = null,
    val authToken: String? = null,
    val authApiKey: String? = null,
    val authApiValue: String? = null,
)

data class ReqLabEnvironmentDto(
    val name: String,
    val variables: Map<String, String>,
)

data class ReqLabWorkspaceDto(
    val collections: List<ReqLabCollectionDto>,
    val environments: List<ReqLabEnvironmentDto>,
    val globalVariables: List<ReqLabEnvironmentDto> = emptyList(),
    val history: List<HistoryItemDto> = emptyList(),
    /** Persisted per-folder expanded/collapsed state. Absent key → expanded (default). */
    val collectionExpandedState: Map<String, Boolean> = emptyMap(),
)

data class HistoryItemDto(
    val requestId: String,
    val method: String,
    val name: String,
    val url: String,
    val timestamp: Long,
    val collectionId: String? = null,
    val folderPath: List<String> = emptyList(),
)

data class WorkspaceImportResult(
    val importedCollections: Int,
    val importedEnvironments: Int,
)

object ImportExportRepository {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }

    fun exportCollectionToString(collectionRoot: CollectionNode): String {
        val root = collectionNodeToCollectionJson(collectionRoot)
        return json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), root)
    }

    fun importCollectionFromString(state: AppState, rawJson: String): String {
        val root = parseJsonString(rawJson)
        val dto = when {
            root["type"]?.jsonPrimitive?.contentOrNull == "reqLabCollection" -> {
                validateVersion(root)
                collectionDtoFromJson(root)
            }
            PostmanImporter.isPostmanCollection(root) -> PostmanImporter.importCollection(root)
            else -> throw ImportExportException(
                "Unrecognized collection format. Expected a ReqLab or Postman v2/v2.1 collection."
            )
        }
        val existingNames = state.collections.map { it.name }.toMutableSet()
        val uniqueName = ImportExportNaming.generateUniqueCollectionName(dto.name, existingNames)
        state.collections.add(collectionDtoToNode(dto, uniqueName))
        return uniqueName
    }

    fun exportEnvironmentToString(environment: EnvState): String {
        val root = environmentToJson(environment)
        return json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), root)
    }

    fun importEnvironmentFromString(state: AppState, rawJson: String): String {
        val root = parseJsonString(rawJson)
        val dto = when {
            root["type"]?.jsonPrimitive?.contentOrNull == "reqLabEnvironment" -> environmentDtoFromJson(root)
            PostmanImporter.isPostmanEnvironment(root) -> PostmanImporter.importEnvironment(root)
            else -> throw ImportExportException(
                "Unrecognized environment format. Expected a ReqLab or Postman environment."
            )
        }
        val existing = state.environments.map { it.name }.toSet()
        val uniqueName = ImportExportNaming.generateUniqueEnvironmentName(dto.name, existing)
        state.environments.add(environmentDtoToState(dto, uniqueName))
        return uniqueName
    }

    fun exportWorkspaceToString(state: AppState): String {
        val root = buildJsonObject {
            put("type", "reqLabWorkspace")
            put("version", "1.0")
            put("collections", buildJsonArray {
                state.collections.forEach { add(collectionNodeToCollectionJson(it)) }
            })
            put("environments", buildJsonArray {
                state.environments.forEach { add(environmentToJson(it)) }
            })
            put("globalVariables", buildJsonArray {
                state.globalVariables.forEach { v ->
                    val key = v.key.trim()
                    if (key.isNotEmpty()) {
                        add(buildJsonObject {
                            put("type", "reqLabEnvironment")
                            put("name", key)
                            put("variables", buildJsonObject { put("value", v.value) })
                        })
                    }
                }
            })
            put("history", buildJsonArray {
                state.historyItems.forEach { item ->
                    add(buildJsonObject {
                        put("requestId", item.requestId)
                        put("method", item.method.name)
                        put("name", item.name)
                        put("url", item.url)
                        put("timestamp", item.timestamp)
                        item.collectionId?.let { put("collectionId", it) }
                        put("folderPath", buildJsonArray {
                            item.folderPath.forEach { segment -> add(JsonPrimitive(segment)) }
                        })
                    })
                }
            })
            if (state.collectionExpandedState.isNotEmpty()) {
                put("collectionExpandedState", buildJsonObject {
                    state.collectionExpandedState.forEach { (id, expanded) -> put(id, expanded) }
                })
            }
        }
        return json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), root)
    }

    fun importWorkspaceFromString(state: AppState, rawJson: String): WorkspaceImportResult {
        val root = parseJsonString(rawJson)
        validateType(root, "reqLabWorkspace")
        validateVersion(root)
        val dto = workspaceDtoFromJson(root)
        return mergeWorkspaceIntoState(state, dto)
    }

    fun decodeWorkspace(rawJson: String): ReqLabWorkspaceDto {
        val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { throw ImportExportException("Invalid workspace JSON", it) }
        validateType(root, "reqLabWorkspace")
        validateVersion(root)
        return workspaceDtoFromJson(root)
    }

    fun encodeWorkspace(workspace: ReqLabWorkspaceDto): String {
        val root = workspaceToJson(workspace)
        return json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), root)
    }

    fun mergeWorkspaceIntoState(state: AppState, workspace: ReqLabWorkspaceDto): WorkspaceImportResult {
        val existingCollections = state.collections.map { it.name }.toMutableSet()
        var importedCollections = 0
        workspace.collections.forEach { collection ->
            val uniqueName = ImportExportNaming.generateUniqueCollectionName(collection.name, existingCollections)
            state.collections.add(collectionDtoToNode(collection, uniqueName))
            existingCollections.add(uniqueName)
            importedCollections++
        }

        val existingEnvironments = state.environments.map { it.name }.toMutableSet()
        var importedEnvironments = 0
        workspace.environments.forEach { env ->
            val uniqueName = ImportExportNaming.generateUniqueEnvironmentName(env.name, existingEnvironments)
            state.environments.add(environmentDtoToState(env, uniqueName))
            existingEnvironments.add(uniqueName)
            importedEnvironments++
        }

        return WorkspaceImportResult(importedCollections, importedEnvironments)
    }

    fun replaceWorkspaceState(state: AppState, workspace: ReqLabWorkspaceDto) {
        state.collections.clear()
        state.collections.addAll(workspace.collections.map { collectionDtoToNode(it, it.name) })

        state.environments.clear()
        state.environments.addAll(workspace.environments.map { environmentDtoToState(it, it.name) })
        state.selectedEnvIndex = if (state.environments.isEmpty()) 0 else state.selectedEnvIndex.coerceIn(0, state.environments.lastIndex)

        state.globalVariables.clear()
        state.globalVariables.addAll(
            workspace.globalVariables.map { env ->
                MutableKeyValue(
                    key = env.name,
                    value = env.variables["value"] ?: "",
                )
            }
        )

        state.replaceHistoryItems(
            workspace.history.map {
                HistoryItem(
                    requestId = it.requestId,
                    method = runCatching { HttpMethodType.valueOf(it.method.uppercase()) }.getOrDefault(HttpMethodType.GET),
                    name = it.name,
                    url = it.url,
                    timestamp = it.timestamp,
                    collectionId = it.collectionId,
                    folderPath = it.folderPath,
                )
            },
        )

        // Restore per-folder expanded state (Bug 1 fix)
        state.collectionExpandedState.clear()
        state.collectionExpandedState.putAll(workspace.collectionExpandedState)
    }

    private fun parseJsonString(rawJson: String): JsonObject =
        runCatching { json.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { throw ImportExportException("Invalid JSON", it) }

    private fun validateType(root: JsonObject, expected: String) {
        val type = root["type"]?.jsonPrimitive?.contentOrNull
        if (type != expected) {
            throw ImportExportException("Malformed schema: expected type=$expected")
        }
    }

    private fun validateVersion(root: JsonObject) {
        val version = root["version"]?.jsonPrimitive?.contentOrNull
        if (version != "1.0") {
            throw ImportExportException("Unsupported version: ${version ?: "<missing>"}")
        }
    }

    private fun collectionNodeToCollectionJson(root: CollectionNode): JsonObject {
        val (folders, requests) = splitChildren(root)
        return buildJsonObject {
            put("type", "reqLabCollection")
            put("version", "1.0")
            put("id", root.id)
            put("name", root.name)
            put("folders", folders)
            put("requests", requests)
        }
    }

    private fun splitChildren(node: CollectionNode): Pair<JsonArray, JsonArray> {
        val folderArray = buildJsonArray {
            if (node.isFolder) {
                node.children.filter { it.isFolder }.forEach { add(folderNodeToJson(it)) }
            }
        }
        val requestArray = buildJsonArray {
            if (node.isFolder) {
                node.children.filter { !it.isFolder }.forEach { requestNodeToJson(it)?.let { add(it) } }
            } else {
                requestNodeToJson(node)?.let { add(it) }
            }
        }
        return folderArray to requestArray
    }

    private fun folderNodeToJson(folder: CollectionNode): JsonObject {
        val (folders, requests) = splitChildren(folder)
        return buildJsonObject {
            put("id", folder.id)
            put("name", folder.name)
            put("folders", folders)
            put("requests", requests)
        }
    }

    private fun requestNodeToJson(node: CollectionNode): JsonObject? {
        val method = node.method ?: return null
        val url = node.url ?: ""
        return buildJsonObject {
            put("name", node.name)
            put("method", method.name)
            put("url", url)
            node.preRequestScript?.takeIf { it.isNotBlank() }?.let { put("preRequestScript", it) }
            node.testScript?.takeIf { it.isNotBlank() }?.let { put("testScript", it) }
            if (node.userHeaders.isNotEmpty()) {
                put("headers", buildJsonArray {
                    node.userHeaders.forEach { (k, v) -> add(buildJsonObject { put("key", k); put("value", v) }) }
                })
            }
            node.bodyType?.let { bt ->
                put("body", buildJsonObject {
                    put("type", bt.name)
                    node.bodyContent?.takeIf { it.isNotBlank() }?.let { put("content", it) }
                    if (node.bodyContents.isNotEmpty()) {
                        put("rawContents", buildJsonObject {
                            node.bodyContents.forEach { (k, v) -> put(k, v) }
                        })
                    }
                    if (node.formDataEntries.isNotEmpty()) {
                        put("formDataEntries", buildJsonArray {
                            node.formDataEntries.forEach { e ->
                                add(buildJsonObject {
                                    put("key", e.key)
                                    put("type", e.type.name)
                                    put("value", e.value)
                                    put("description", e.description)
                                    put("enabled", e.enabled)
                                })
                            }
                        })
                    }
                    if (node.urlencodedEntries.isNotEmpty()) {
                        put("urlencodedEntries", buildJsonArray {
                            node.urlencodedEntries.forEach { e ->
                                add(buildJsonObject {
                                    put("key", e.key)
                                    put("value", e.value)
                                    put("description", e.description)
                                    put("enabled", e.enabled)
                                })
                            }
                        })
                    }
                })
            }
            node.authType?.let { at ->
                put("auth", buildJsonObject {
                    put("type", at.name)
                    node.authUsername?.takeIf { it.isNotBlank() }?.let { put("username", it) }
                    node.authPassword?.takeIf { it.isNotBlank() }?.let { put("password", it) }
                    node.authToken?.takeIf { it.isNotBlank() }?.let { put("token", it) }
                    node.authApiKey?.takeIf { it.isNotBlank() }?.let { put("apiKey", it) }
                    node.authApiValue?.takeIf { it.isNotBlank() }?.let { put("apiValue", it) }
                })
            }
        }
    }

    private fun environmentToJson(environment: EnvState): JsonObject {
        val vars = buildJsonObject {
            environment.variables.forEach { v ->
                val key = v.key.trim()
                if (key.isNotEmpty()) put(key, v.value)
            }
        }
        return buildJsonObject {
            put("type", "reqLabEnvironment")
            put("name", environment.name)
            put("variables", vars)
        }
    }

    private fun workspaceToJson(workspace: ReqLabWorkspaceDto): JsonObject {
        return buildJsonObject {
            put("type", "reqLabWorkspace")
            put("version", "1.0")
            put("collections", buildJsonArray {
                workspace.collections.forEach { add(collectionDtoToJson(it)) }
            })
            put("environments", buildJsonArray {
                workspace.environments.forEach { add(environmentDtoToJson(it)) }
            })
            put("globalVariables", buildJsonArray {
                workspace.globalVariables.forEach { add(environmentDtoToJson(it)) }
            })
            put("history", buildJsonArray {
                workspace.history.forEach { item ->
                    add(buildJsonObject {
                        put("requestId", item.requestId)
                        put("method", item.method)
                        put("name", item.name)
                        put("url", item.url)
                        put("timestamp", item.timestamp)
                        item.collectionId?.let { put("collectionId", it) }
                        put("folderPath", buildJsonArray { item.folderPath.forEach { segment -> add(JsonPrimitive(segment)) } })
                    })
                }
            })
        }
    }

    private fun collectionDtoToJson(dto: ReqLabCollectionDto): JsonObject =
        buildJsonObject {
            put("type", "reqLabCollection")
            put("version", "1.0")
            put("name", dto.name)
            put("folders", buildJsonArray { dto.folders.forEach { add(folderDtoToJson(it)) } })
            put("requests", buildJsonArray { dto.requests.forEach { add(requestDtoToJson(it)) } })
        }

    private fun folderDtoToJson(dto: FolderDto): JsonObject =
        buildJsonObject {
            put("name", dto.name)
            put("folders", buildJsonArray { dto.folders.forEach { add(folderDtoToJson(it)) } })
            put("requests", buildJsonArray { dto.requests.forEach { add(requestDtoToJson(it)) } })
        }

    private fun requestDtoToJson(dto: RequestDto): JsonObject =
        buildJsonObject {
            put("name", dto.name)
            put("method", dto.method)
            put("url", dto.url)
            dto.preRequestScript?.takeIf { it.isNotBlank() }?.let { put("preRequestScript", it) }
            dto.testScript?.takeIf { it.isNotBlank() }?.let { put("testScript", it) }
            if (dto.userHeaders.isNotEmpty()) {
                put("headers", buildJsonArray {
                    dto.userHeaders.forEach { (k, v) -> add(buildJsonObject { put("key", k); put("value", v) }) }
                })
            }
            dto.bodyType?.let { bt ->
                put("body", buildJsonObject {
                    put("type", bt)
                    dto.bodyContent?.takeIf { it.isNotBlank() }?.let { put("content", it) }
                    if (!dto.rawContents.isNullOrEmpty()) {
                        put("rawContents", buildJsonObject {
                            dto.rawContents.forEach { (k, v) -> put(k, v) }
                        })
                    }
                    if (dto.formDataEntries.isNotEmpty()) {
                        put("formDataEntries", buildJsonArray {
                            dto.formDataEntries.forEach { e ->
                                add(buildJsonObject {
                                    put("key", e.key)
                                    put("type", e.type.name)
                                    put("value", e.value)
                                    put("description", e.description)
                                    put("enabled", e.enabled)
                                })
                            }
                        })
                    }
                    if (dto.urlencodedEntries.isNotEmpty()) {
                        put("urlencodedEntries", buildJsonArray {
                            dto.urlencodedEntries.forEach { e ->
                                add(buildJsonObject {
                                    put("key", e.key)
                                    put("value", e.value)
                                    put("description", e.description)
                                    put("enabled", e.enabled)
                                })
                            }
                        })
                    }
                })
            }
            dto.authType?.let { at ->
                put("auth", buildJsonObject {
                    put("type", at)
                    dto.authUsername?.takeIf { it.isNotBlank() }?.let { put("username", it) }
                    dto.authPassword?.takeIf { it.isNotBlank() }?.let { put("password", it) }
                    dto.authToken?.takeIf { it.isNotBlank() }?.let { put("token", it) }
                    dto.authApiKey?.takeIf { it.isNotBlank() }?.let { put("apiKey", it) }
                    dto.authApiValue?.takeIf { it.isNotBlank() }?.let { put("apiValue", it) }
                })
            }
        }

    private fun environmentDtoToJson(dto: ReqLabEnvironmentDto): JsonObject =
        buildJsonObject {
            put("type", "reqLabEnvironment")
            put("name", dto.name)
            put("variables", buildJsonObject { dto.variables.forEach { (k, v) -> put(k, v) } })
        }

    private fun workspaceDtoFromJson(root: JsonObject): ReqLabWorkspaceDto {
        val collections = root["collections"]?.jsonArray?.map { collectionDtoFromJson(it.jsonObject) } ?: emptyList()
        val environments = root["environments"]?.jsonArray?.map { environmentDtoFromJson(it.jsonObject) } ?: emptyList()
        val globalVariables = root["globalVariables"]?.jsonArray?.map { environmentDtoFromJson(it.jsonObject) } ?: emptyList()
        val history = root["history"]?.jsonArray?.map { historyItemDtoFromJson(it.jsonObject) } ?: emptyList()
        val collectionExpandedState = root["collectionExpandedState"]?.jsonObject?.entries
            ?.associate { (k, v) -> k to (v.jsonPrimitive.booleanOrNull ?: true) }
            ?: emptyMap()
        return ReqLabWorkspaceDto(
            collections = collections,
            environments = environments,
            globalVariables = globalVariables,
            history = history,
            collectionExpandedState = collectionExpandedState,
        )
    }

    private fun historyItemDtoFromJson(root: JsonObject): HistoryItemDto = HistoryItemDto(
        requestId = root["requestId"]?.jsonPrimitive?.contentOrNull
            ?: root["id"]?.jsonPrimitive?.contentOrNull
            ?: generateUuid(),
        method = root["method"]?.jsonPrimitive?.contentOrNull ?: HttpMethodType.GET.name,
        name = root["name"]?.jsonPrimitive?.contentOrNull ?: "Untitled",
        url = root["url"]?.jsonPrimitive?.contentOrNull ?: "",
        timestamp = root["timestamp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
        collectionId = root["collectionId"]?.jsonPrimitive?.contentOrNull,
        folderPath = root["folderPath"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
    )

    private fun collectionDtoFromJson(root: JsonObject): ReqLabCollectionDto {
        val name = root["name"]?.jsonPrimitive?.contentOrNull
            ?: throw ImportExportException("Collection name is missing")
        val id = root["id"]?.jsonPrimitive?.contentOrNull
        val folders = root["folders"]?.jsonArray?.map { folderDtoFromJson(it.jsonObject) } ?: emptyList()
        val requests = root["requests"]?.jsonArray?.map { requestDtoFromJson(it.jsonObject) } ?: emptyList()
        return ReqLabCollectionDto(name = name, folders = folders, requests = requests, id = id)
    }

    private fun folderDtoFromJson(root: JsonObject): FolderDto {
        val name = root["name"]?.jsonPrimitive?.contentOrNull
            ?: throw ImportExportException("Folder name is missing")
        val id = root["id"]?.jsonPrimitive?.contentOrNull
        val folders = root["folders"]?.jsonArray?.map { folderDtoFromJson(it.jsonObject) } ?: emptyList()
        val requests = root["requests"]?.jsonArray?.map { requestDtoFromJson(it.jsonObject) } ?: emptyList()
        return FolderDto(name = name, folders = folders, requests = requests, id = id)
    }

    private fun requestDtoFromJson(root: JsonObject): RequestDto {
        val name = root["name"]?.jsonPrimitive?.contentOrNull
            ?: throw ImportExportException("Request name is missing")
        val method = root["method"]?.jsonPrimitive?.contentOrNull ?: "GET"
        val url = root["url"]?.jsonPrimitive?.contentOrNull ?: ""
        val preRequestScript = root["preRequestScript"]?.jsonPrimitive?.contentOrNull
        val testScript = root["testScript"]?.jsonPrimitive?.contentOrNull
        val userHeaders = root["headers"]?.jsonArray?.mapNotNull { el ->
            val obj = el.jsonObject
            val k = obj["key"]?.jsonPrimitive?.contentOrNull
            val v = obj["value"]?.jsonPrimitive?.contentOrNull
            if (k != null && v != null) Pair(k, v) else null
        } ?: emptyList()
        val bodyObj = root["body"]?.jsonObject
        val bodyType = bodyObj?.get("type")?.jsonPrimitive?.contentOrNull
        val bodyContent = bodyObj?.get("content")?.jsonPrimitive?.contentOrNull
        val rawContents = bodyObj?.get("rawContents")?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.content }
            ?.takeIf { it.isNotEmpty() }
        val explicitFormDataEntries = bodyObj?.get("formDataEntries")?.jsonArray?.map { formEntryFromJson(it.jsonObject) } ?: emptyList()
        val explicitUrlencodedEntries = bodyObj?.get("urlencodedEntries")?.jsonArray?.map { formEntryFromJson(it.jsonObject) } ?: emptyList()
        // Backward compatibility: older reqLab exports (and hand-edited fixtures)
        // may store form/urlencoded bodies only in body.content.
        val formDataEntries = if (explicitFormDataEntries.isNotEmpty()) {
            explicitFormDataEntries
        } else if (bodyType.equals("FORM_DATA", ignoreCase = true)) {
            parseLegacyFormDataContent(bodyContent)
        } else emptyList()
        val urlencodedEntries = if (explicitUrlencodedEntries.isNotEmpty()) {
            explicitUrlencodedEntries
        } else if (bodyType.equals("X_WWW_FORM_URLENCODED", ignoreCase = true)) {
            parseLegacyUrlencodedContent(bodyContent)
        } else emptyList()
        val authObj = root["auth"]?.jsonObject
        val authType = authObj?.get("type")?.jsonPrimitive?.contentOrNull
        val authUsername = authObj?.get("username")?.jsonPrimitive?.contentOrNull
        val authPassword = authObj?.get("password")?.jsonPrimitive?.contentOrNull
        val authToken = authObj?.get("token")?.jsonPrimitive?.contentOrNull
        val authApiKey = authObj?.get("apiKey")?.jsonPrimitive?.contentOrNull
        val authApiValue = authObj?.get("apiValue")?.jsonPrimitive?.contentOrNull
        return RequestDto(
            name = name, method = method, url = url,
            preRequestScript = preRequestScript, testScript = testScript,
            userHeaders = userHeaders,
            bodyType = bodyType, bodyContent = bodyContent, rawContents = rawContents,
            formDataEntries = formDataEntries,
            urlencodedEntries = urlencodedEntries,
            authType = authType,
            authUsername = authUsername, authPassword = authPassword,
            authToken = authToken, authApiKey = authApiKey, authApiValue = authApiValue,
        )
    }

    private fun formEntryFromJson(obj: JsonObject): FormDataEntryState {
        val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: ""
        val type = obj["type"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { FormEntryType.valueOf(it.uppercase()) }.getOrNull() }
            ?: FormEntryType.TEXT
        val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: ""
        val description = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
        return FormDataEntryState(key = key, type = type, value = value, description = description, enabled = enabled)
    }

    private fun parseLegacyFormDataContent(content: String?): List<FormDataEntryState> {
        if (content.isNullOrBlank()) return emptyList()
        val normalized = content.replace("\r\n", "\n")
        val parts = normalized
            .split('\n', '&')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return parts.map { token ->
            val eq = token.indexOf('=')
            if (eq >= 0) {
                FormDataEntryState(
                    key = token.substring(0, eq).trim(),
                    type = FormEntryType.TEXT,
                    value = token.substring(eq + 1),
                    enabled = true,
                )
            } else {
                FormDataEntryState(
                    key = token,
                    type = FormEntryType.TEXT,
                    value = "",
                    enabled = true,
                )
            }
        }.filter { it.key.isNotBlank() }
    }

    private fun parseLegacyUrlencodedContent(content: String?): List<FormDataEntryState> {
        if (content.isNullOrBlank()) return emptyList()
        val parts = content
            .split('&')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return parts.map { token ->
            val eq = token.indexOf('=')
            if (eq >= 0) {
                FormDataEntryState(
                    key = token.substring(0, eq).trim(),
                    value = token.substring(eq + 1),
                    enabled = true,
                )
            } else {
                FormDataEntryState(
                    key = token,
                    value = "",
                    enabled = true,
                )
            }
        }.filter { it.key.isNotBlank() }
    }

    private fun environmentDtoFromJson(root: JsonObject): ReqLabEnvironmentDto {
        val name = root["name"]?.jsonPrimitive?.contentOrNull
            ?: throw ImportExportException("Environment name is missing")
        val variables = root["variables"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.contentOrNull ?: "" }
            ?: emptyMap()
        return ReqLabEnvironmentDto(name = name, variables = variables)
    }

    private fun collectionDtoToNode(dto: ReqLabCollectionDto, nameOverride: String): CollectionNode {
        val children = mutableStateListOf<CollectionNode>()
        dto.folders.forEach { children.add(folderDtoToNode(it)) }
        dto.requests.forEach { children.add(requestDtoToNode(it)) }
        return CollectionNode(
            id = dto.id ?: generateUuid(),
            name = nameOverride,
            isFolder = true,
            children = children,
        )
    }

    private fun folderDtoToNode(dto: FolderDto): CollectionNode {
        val children = mutableStateListOf<CollectionNode>()
        dto.folders.forEach { children.add(folderDtoToNode(it)) }
        dto.requests.forEach { children.add(requestDtoToNode(it)) }
        return CollectionNode(
            id = dto.id ?: generateUuid(),
            name = dto.name,
            isFolder = true,
            children = children,
        )
    }

    private fun requestDtoToNode(dto: RequestDto): CollectionNode {
        val method = runCatching { HttpMethodType.valueOf(dto.method.uppercase()) }.getOrDefault(HttpMethodType.GET)
        val bodyType = dto.bodyType?.let { runCatching { BodyType.valueOf(it.uppercase()) }.getOrNull() }
        val authType = dto.authType?.let { runCatching { AuthType.valueOf(it.uppercase()) }.getOrNull() }
        return CollectionNode(
            id = generateUuid(),
            name = dto.name,
            isFolder = false,
            method = method,
            url = dto.url,
            preRequestScript = dto.preRequestScript,
            testScript = dto.testScript,
            userHeaders = dto.userHeaders,
            bodyType = bodyType,
            bodyContent = dto.bodyContent,
            bodyContents = dto.rawContents ?: emptyMap(),
            formDataEntries = dto.formDataEntries,
            urlencodedEntries = dto.urlencodedEntries,
            authType = authType,
            authUsername = dto.authUsername,
            authPassword = dto.authPassword,
            authToken = dto.authToken,
            authApiKey = dto.authApiKey,
            authApiValue = dto.authApiValue,
        )
    }

    private fun environmentDtoToState(dto: ReqLabEnvironmentDto, nameOverride: String): EnvState {
        val variables = dto.variables.map { (k, v) -> MutableKeyValue(k, v, enabled = true, secret = false) }
        return EnvState(name = nameOverride, variables = variables)
    }
}
