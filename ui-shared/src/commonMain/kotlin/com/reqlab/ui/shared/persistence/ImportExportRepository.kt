package com.reqlab.ui.shared.persistence

import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import androidx.compose.runtime.mutableStateListOf
import com.reqlab.ui.shared.state.EnvState
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
)

data class FolderDto(
    val name: String,
    val folders: List<FolderDto>,
    val requests: List<RequestDto>,
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
        validateType(root, "reqLabCollection")
        validateVersion(root)
        val dto = collectionDtoFromJson(root)
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
        validateType(root, "reqLabEnvironment")
        val dto = environmentDtoFromJson(root)
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
        if (state.environments.isEmpty()) state.environments.add(EnvState("Default"))
        state.selectedEnvIndex = state.selectedEnvIndex.coerceIn(0, state.environments.lastIndex)
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
        return ReqLabWorkspaceDto(collections = collections, environments = environments)
    }

    private fun collectionDtoFromJson(root: JsonObject): ReqLabCollectionDto {
        val name = root["name"]?.jsonPrimitive?.contentOrNull
            ?: throw ImportExportException("Collection name is missing")
        val folders = root["folders"]?.jsonArray?.map { folderDtoFromJson(it.jsonObject) } ?: emptyList()
        val requests = root["requests"]?.jsonArray?.map { requestDtoFromJson(it.jsonObject) } ?: emptyList()
        return ReqLabCollectionDto(name = name, folders = folders, requests = requests)
    }

    private fun folderDtoFromJson(root: JsonObject): FolderDto {
        val name = root["name"]?.jsonPrimitive?.contentOrNull
            ?: throw ImportExportException("Folder name is missing")
        val folders = root["folders"]?.jsonArray?.map { folderDtoFromJson(it.jsonObject) } ?: emptyList()
        val requests = root["requests"]?.jsonArray?.map { requestDtoFromJson(it.jsonObject) } ?: emptyList()
        return FolderDto(name = name, folders = folders, requests = requests)
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
            bodyType = bodyType, bodyContent = bodyContent,
            authType = authType,
            authUsername = authUsername, authPassword = authPassword,
            authToken = authToken, authApiKey = authApiKey, authApiValue = authApiValue,
        )
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
            id = generateUuid(),
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
            id = generateUuid(),
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
