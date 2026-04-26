package com.reqlab.ui.shared.persistence

import com.reqlab.core.model.AuthType
import com.reqlab.core.model.BodyType
import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.platform.PlatformStorage
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.state.HeaderKind
import com.reqlab.ui.shared.state.MutableKeyValue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
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
}
