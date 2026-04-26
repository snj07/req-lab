package com.reqlab.ui.shared.persistence

import androidx.compose.runtime.mutableStateListOf
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.platform.PlatformStorage
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for [WorkspaceRepository] persistence, covering edge cases around
 * large request body payloads that cause the underlying [PlatformStorage]
 * to route data through file-backed storage instead of Java Preferences.
 */
class WorkspaceRepositoryTest {

    private val WORKSPACE_KEY = "reqlab.workspace"

    @Before
    fun setUp() {
        PlatformStorage.remove(WORKSPACE_KEY)
    }

    @After
    fun tearDown() {
        PlatformStorage.remove(WORKSPACE_KEY)
    }

    // ── Basic save / load ────────────────────────────────────────────────────

    @Test
    fun save_and_load_roundtrip_with_large_body_preserves_content_exactly() {
        // 200 KB of JSON body — forces PlatformStorage to use a backing file
        val largeBody = "{\"data\":\"" + "X".repeat(200_000) + "\"}"

        val request = CollectionNode(
            id = "large-req-1",
            name = "Large Payload",
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://api.example.com/large",
            bodyType = BodyType.JSON,
            bodyContent = largeBody,
            bodyContents = mapOf(BodyType.JSON.name to largeBody),
        )
        val collection = CollectionNode(
            id = "large-col-1",
            name = "Large Payload Collection",
            isFolder = true,
            children = mutableStateListOf(request),
        )
        val source = AppState(openDefaultTab = false)
        source.collections.add(collection)

        WorkspaceRepository.save(source)

        val restored = AppState(openDefaultTab = false)
        WorkspaceRepository.load(restored)

        val loadedRequest = restored.collections
            .firstOrNull { it.name == "Large Payload Collection" }
            ?.children
            ?.firstOrNull()

        assertNotNull(loadedRequest, "Request must be present after WorkspaceRepository load")
        assertEquals(
            largeBody.length,
            loadedRequest.bodyContent?.length,
            "Large body length must survive a WorkspaceRepository save/load cycle",
        )
        assertEquals(
            largeBody,
            loadedRequest.bodyContent,
            "Large body content must survive a WorkspaceRepository save/load cycle exactly",
        )
    }

    @Test
    fun save_and_load_with_multiple_large_per_type_bodies_preserves_all_types() {
        val jsonBody = "{\"j\":\"" + "j".repeat(100_000) + "\"}"
        val xmlBody  = "<root>" + "x".repeat(100_000) + "</root>"

        val request = CollectionNode(
            id = "multi-body-req-1",
            name = "Multi Body Request",
            isFolder = false,
            method = HttpMethodType.POST,
            url = "https://api.example.com/multi",
            bodyType = BodyType.JSON,
            bodyContent = jsonBody,
            bodyContents = mapOf(
                BodyType.JSON.name to jsonBody,
                BodyType.XML.name  to xmlBody,
            ),
        )
        val collection = CollectionNode(
            id = "multi-body-col-1",
            name = "Multi Body Collection",
            isFolder = true,
            children = mutableStateListOf(request),
        )
        val source = AppState(openDefaultTab = false)
        source.collections.add(collection)

        WorkspaceRepository.save(source)

        val restored = AppState(openDefaultTab = false)
        WorkspaceRepository.load(restored)

        val loadedRequest = restored.collections
            .firstOrNull { it.name == "Multi Body Collection" }
            ?.children
            ?.firstOrNull()

        assertNotNull(loadedRequest)
        assertEquals(
            jsonBody,
            loadedRequest.bodyContents[BodyType.JSON.name],
            "Large JSON body must survive workspace save/load",
        )
        assertEquals(
            xmlBody,
            loadedRequest.bodyContents[BodyType.XML.name],
            "Large XML body must survive workspace save/load",
        )
    }

    @Test
    fun save_overwrites_previous_save_with_updated_content() {
        val original = "original body"
        val updated  = "updated body " + "u".repeat(10_000)

        fun buildState(body: String): AppState {
            val req = CollectionNode(
                id = "overwrite-req-1",
                name = "Overwrite Request",
                isFolder = false,
                method = HttpMethodType.POST,
                url = "https://api.example.com",
                bodyType = BodyType.JSON,
                bodyContent = body,
                bodyContents = mapOf(BodyType.JSON.name to body),
            )
            val col = CollectionNode(
                id = "overwrite-col-1",
                name = "Overwrite Collection",
                isFolder = true,
                children = mutableStateListOf(req),
            )
            return AppState(openDefaultTab = false).also { it.collections.add(col) }
        }

        WorkspaceRepository.save(buildState(original))
        WorkspaceRepository.save(buildState(updated))

        val restored = AppState(openDefaultTab = false)
        WorkspaceRepository.load(restored)

        val body = restored.collections
            .firstOrNull { it.name == "Overwrite Collection" }
            ?.children?.firstOrNull()?.bodyContent

        assertEquals(updated, body, "Second save must overwrite the first; restored body must match updated value")
    }
}
