package com.reqlab.ui.shared.persistence

import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.FormEntryType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.platform.PlatformStorage
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.CollectionNode
import com.reqlab.ui.shared.state.HeaderKind
import com.reqlab.ui.shared.state.MutableFormDataRow
import com.reqlab.ui.shared.state.MutableKeyValue
import com.reqlab.ui.shared.state.RequestTabState
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabsRepositoryTest {

    private val storeFile = File(System.getProperty("user.home"), ".reqlab/tabs.json")

    @Before
    fun before() {
        if (storeFile.exists()) storeFile.delete()
        PlatformStorage.remove("reqlab.tabs")
    }

    @After
    fun after() {
        if (storeFile.exists()) storeFile.delete()
        PlatformStorage.remove("reqlab.tabs")
    }

    @Test
    fun save_and_load_roundtrip_persists_request_fields_including_auth_and_scripts() {
        val source = AppState()
        val tab = source.activeTab!!
        tab.name = "Get Users"
        tab.method = HttpMethodType.POST
        tab.url = "https://api.example.com/users"
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "{\"name\":\"Alice\"}"
        tab.params.clear()
        tab.params.add(MutableKeyValue("q", "abc", enabled = true, secret = false))
        tab.headers.clear()
        tab.headers.add(
            MutableKeyValue(
                "Authorization",
                "Bearer xyz",
                enabled = true,
                secret = false,
                kind = HeaderKind.USER,
                keyLocked = false,
            )
        )
        tab.authType = AuthType.API_KEY
        tab.authApiKey = "x-api-key"
        tab.authApiValue = "secret"
        tab.authApiPlacement = "query"
        tab.preRequestScript = "env.set('k','v')"
        tab.testScript = "test('ok', function() { expect(response.status).to.equal(200) })"
        tab.retryEnabled = true
        tab.retryCount = 3
        tab.retryDelayMs = 500L

        tab.markSaved()
        tab.url = "https://api.example.com/users?changed=1"
        tab.markDirty()

        tab.lastSavedTimestamp = 123456789L

        TabsRepository.save(source)

        val loadedState = AppState()
        TabsRepository.load(loadedState)
        val loaded = loadedState.activeTab!!

        assertEquals("Get Users", loaded.name)
        assertEquals(HttpMethodType.POST, loaded.method)
        assertEquals("https://api.example.com/users?changed=1", loaded.url)
        assertEquals(BodyType.JSON, loaded.bodyType)
        assertEquals("{\"name\":\"Alice\"}", loaded.bodyContent)
        assertTrue(loaded.params.any { it.key == "q" && it.value == "abc" })
        assertTrue(loaded.headers.any { it.key == "Authorization" && it.value == "Bearer xyz" })
        assertEquals(AuthType.API_KEY, loaded.authType)
        assertEquals("x-api-key", loaded.authApiKey)
        assertEquals("secret", loaded.authApiValue)
        assertEquals("query", loaded.authApiPlacement)
        assertEquals("env.set('k','v')", loaded.preRequestScript)
        assertEquals("test('ok', function() { expect(response.status).to.equal(200) })", loaded.testScript)
        assertEquals(true, loaded.retryEnabled)
        assertEquals(3, loaded.retryCount)
        assertEquals(500L, loaded.retryDelayMs)
        assertEquals(123456789L, loaded.lastSavedTimestamp)
        assertTrue(loaded.headers.any { it.kind == HeaderKind.USER && !it.keyLocked })
        assertTrue(loaded.isDirty)
    }

    @Test
    fun same_length_body_and_form_value_edits_roundtrip_without_row_count_change() {
        val source = AppState()
        val tab = source.activeTab!!
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "before"
        tab.formRows.add(MutableFormDataRow("field", value = "one"))
        tab.markSaved()
        val revision = tab.persistenceRevision
        tab.bodyContent = "after!"
        tab.markDirty()
        tab.formRows.single().value = "two"
        tab.markDirty()
        assertEquals(revision + 2, tab.persistenceRevision)

        assertTrue(TabsRepository.save(source))
        val restored = AppState()
        TabsRepository.load(restored)
        assertEquals("after!", restored.activeTab!!.bodyContent)
        assertEquals("two", restored.activeTab!!.formRows.single().value)
    }

    @Test
    fun save_large_body_returns_success_and_roundtrips_content_length() {
        val source = AppState()
        val tab = source.activeTab!!
        tab.name = "Large Body"
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "{\"items\":[" + (0 until 80_000).joinToString(",") { "{\"k\":$it}" } + "]}"

        val saved = TabsRepository.save(source)
        assertTrue(saved, "TabsRepository.save must return success for large body payload")

        val loadedState = AppState()
        TabsRepository.load(loadedState)
        val loaded = loadedState.activeTab!!

        assertEquals(tab.bodyContent.length, loaded.bodyContent.length,
            "Large body content length must round-trip through persistence")
    }

    @Test
    fun save_large_body_roundtrips_exact_content_via_file_backed_storage() {
        // ~3 MB body — forces PlatformStorage to route through a backing file
        val largeBody = "{\"rows\":[" + (0 until 50_000).joinToString(",") { "{\"n\":$it}" } + "]}"

        val source = AppState()
        val tab = source.activeTab!!
        tab.bodyType = BodyType.JSON
        tab.bodyContent = largeBody

        assertTrue(TabsRepository.save(source), "save must return true for large body payload")

        val loadedState = AppState()
        TabsRepository.load(loadedState)
        val loaded = loadedState.activeTab!!

        assertEquals(
            largeBody.length,
            loaded.bodyContent.length,
            "Large body length must survive TabsRepository save/load",
        )
        assertEquals(
            largeBody,
            loaded.bodyContent,
            "Large body content must survive TabsRepository save/load exactly",
        )
    }

    // ── Dirty-state-after-restart regression tests (Bug #4) ──────────────

    @Test
    fun `clean GET tab is not dirty after save and reload`() {
        val source = AppState()
        val tab = source.activeTab!!
        tab.name = "Get Users"
        tab.method = HttpMethodType.GET
        tab.url = "https://api.example.com/users"
        tab.bodyType = BodyType.NONE
        // bodyContent intentionally left blank — mirrors addTab() behaviour
        tab.markSaved()

        TabsRepository.save(source)

        val loadedState = AppState()
        TabsRepository.load(loadedState)
        val loaded = loadedState.activeTab!!

        assertFalse(loaded.isDirty,
            "A clean GET tab must not be dirty after save/load (bodyContent blank entry bug)")
    }

    @Test
    fun `clean POST JSON tab is not dirty after save and reload`() {
        val source = AppState()
        val tab = source.activeTab!!
        tab.name = "Create User"
        tab.method = HttpMethodType.POST
        tab.url = "https://api.example.com/users"
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "{\"name\":\"Alice\"}"
        tab.markSaved()

        TabsRepository.save(source)

        val loadedState = AppState()
        TabsRepository.load(loadedState)
        val loaded = loadedState.activeTab!!

        assertFalse(loaded.isDirty,
            "A clean POST JSON tab must not be dirty after save/load")
    }

    @Test
    fun `clean form-data tab is not dirty after save and reload`() {
        val source = AppState()
        val tab = source.activeTab!!
        tab.name = "Upload Form"
        tab.method = HttpMethodType.POST
        tab.url = "https://api.example.com/upload"
        tab.bodyType = BodyType.FORM_DATA
        tab.formRows.add(MutableFormDataRow("file", FormEntryType.FILE, "/path/to/file.txt", "", true))
        tab.formRows.add(MutableFormDataRow("name", FormEntryType.TEXT, "Alice", "", true))
        tab.markSaved()

        TabsRepository.save(source)

        val loadedState = AppState()
        TabsRepository.load(loadedState)
        val loaded = loadedState.activeTab!!

        assertFalse(loaded.isDirty,
            "A clean form-data tab must not be dirty after save/load (formRows loaded after restoreSavedSnapshot bug)")
        assertEquals(2, loaded.formRows.size, "formRows must round-trip through persistence")
    }

    @Test
    fun `clean urlencoded tab is not dirty after save and reload`() {
        val source = AppState()
        val tab = source.activeTab!!
        tab.name = "Encoded Form"
        tab.method = HttpMethodType.POST
        tab.url = "https://api.example.com/form"
        tab.bodyType = BodyType.X_WWW_FORM_URLENCODED
        tab.urlencodedRows.add(MutableFormDataRow("username", FormEntryType.TEXT, "alice", "", true))
        tab.markSaved()

        TabsRepository.save(source)

        val loadedState = AppState()
        TabsRepository.load(loadedState)
        val loaded = loadedState.activeTab!!

        assertFalse(loaded.isDirty,
            "A clean url-encoded tab must not be dirty after save/load")
        assertEquals(1, loaded.urlencodedRows.size, "urlencodedRows must round-trip through persistence")
    }

    @Test
    fun load_restores_tabs_with_same_request_name_in_different_collections_using_collection_id() {
        val source = AppState(openDefaultTab = false, withDemoData = false)

        val sourceReqA = CollectionNode(id = "r-a", name = "Health", isFolder = false, method = HttpMethodType.GET, url = "https://a.example.com/health")
        val sourceReqB = CollectionNode(id = "r-b", name = "Health", isFolder = false, method = HttpMethodType.GET, url = "https://b.example.com/health")
        source.collections.add(
            CollectionNode(id = "c-a", name = "API A", isFolder = true, children = mutableListOf(sourceReqA))
        )
        source.collections.add(
            CollectionNode(id = "c-b", name = "API B", isFolder = true, children = mutableListOf(sourceReqB))
        )

        source.openTabs.clear()
        source.openTabs.add(
            RequestTabState(
                id = sourceReqA.id,
                name = sourceReqA.name,
                method = sourceReqA.method ?: HttpMethodType.GET,
                url = sourceReqA.url ?: "",
                collectionName = "API A",
                collectionId = "c-a",
            )
        )
        source.openTabs.add(
            RequestTabState(
                id = sourceReqB.id,
                name = sourceReqB.name,
                method = sourceReqB.method ?: HttpMethodType.GET,
                url = sourceReqB.url ?: "",
                collectionName = "API B",
                collectionId = "c-b",
            )
        )
        source.activeTabIndex = 1
        assertTrue(TabsRepository.save(source), "tab save should succeed")

        val restored = AppState(openDefaultTab = false, withDemoData = false)
        val restoredReqA = CollectionNode(id = "r-a-new", name = "Health", isFolder = false, method = HttpMethodType.GET, url = "https://a.example.com/health")
        val restoredReqB = CollectionNode(id = "r-b-new", name = "Health", isFolder = false, method = HttpMethodType.GET, url = "https://b.example.com/health")
        restored.collections.add(
            CollectionNode(id = "c-a", name = "API A", isFolder = true, children = mutableListOf(restoredReqA))
        )
        restored.collections.add(
            CollectionNode(id = "c-b", name = "API B", isFolder = true, children = mutableListOf(restoredReqB))
        )

        TabsRepository.load(restored)

        assertEquals(2, restored.openTabs.size)
        assertEquals("r-a-new", restored.openTabs[0].id, "API A tab must resolve to API A request")
        assertEquals("r-b-new", restored.openTabs[1].id, "API B tab must resolve to API B request")
        assertEquals("c-a", restored.openTabs[0].collectionId)
        assertEquals("c-b", restored.openTabs[1].collectionId)
    }

    @Test
    fun load_restores_tabs_with_duplicate_collection_names_without_collection_id_using_signature() {
        val source = AppState(openDefaultTab = false, withDemoData = false)
        val reqA = CollectionNode(id = "r-a", name = "Users", isFolder = false, method = HttpMethodType.GET, url = "https://a.example.com/users")
        val reqB = CollectionNode(id = "r-b", name = "Users", isFolder = false, method = HttpMethodType.GET, url = "https://b.example.com/users")
        source.collections.add(CollectionNode(id = "c-a", name = "API", isFolder = true, children = mutableListOf(reqA)))
        source.collections.add(CollectionNode(id = "c-b", name = "API", isFolder = true, children = mutableListOf(reqB)))

        source.openTabs.clear()
        source.openTabs.add(
            RequestTabState(
                id = reqB.id,
                name = reqB.name,
                method = reqB.method ?: HttpMethodType.GET,
                url = reqB.url ?: "",
                collectionName = "API",
                collectionId = null,
            )
        )
        assertTrue(TabsRepository.save(source), "tab save should succeed")

        val restored = AppState(openDefaultTab = false, withDemoData = false)
        val restoredReqA = CollectionNode(id = "r-a-new", name = "Users", isFolder = false, method = HttpMethodType.GET, url = "https://a.example.com/users")
        val restoredReqB = CollectionNode(id = "r-b-new", name = "Users", isFolder = false, method = HttpMethodType.GET, url = "https://b.example.com/users")
        restored.collections.add(CollectionNode(id = "c-a", name = "API", isFolder = true, children = mutableListOf(restoredReqA)))
        restored.collections.add(CollectionNode(id = "c-b", name = "API", isFolder = true, children = mutableListOf(restoredReqB)))

        TabsRepository.load(restored)

        assertEquals(1, restored.openTabs.size)
        assertEquals("r-b-new", restored.openTabs[0].id)
        assertEquals("c-b", restored.openTabs[0].collectionId)
    }
}
