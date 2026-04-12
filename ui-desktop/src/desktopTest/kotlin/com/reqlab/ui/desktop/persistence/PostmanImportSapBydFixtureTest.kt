package com.reqlab.ui.shared.persistence

import com.reqlab.core.model.HttpMethodType
import com.reqlab.ui.shared.state.AppState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test that imports a real-world SAP BYD Postman collection
 * (SAP Business ByDesign "New Business" OData examples, v2.1 schema) and
 * validates every structural aspect of the import pipeline end-to-end.
 *
 * Fixture: qa-tests/fixtures/sap-byd-new-business.postman_collection.json
 * Source:  https://github.com/SAP-samples/byd-api-samples/tree/main/Postman
 */
class PostmanImportSapBydFixtureTest {

    private val fixture by lazy {
        val candidates = listOf(
            File("qa-tests/fixtures/sap-byd-new-business.postman_collection.json"),
            File("../qa-tests/fixtures/sap-byd-new-business.postman_collection.json"),
        )
        candidates.firstOrNull { it.exists() }
            ?: error("Fixture sap-byd-new-business.postman_collection.json not found")
    }

    // ── Detection ──────────────────────────────────────────────────────────────

    @Test
    fun sap_byd_fixture_is_detected_as_postman_collection() {
        // Indirect detection: import succeeds without throwing ImportExportException.
        val state = AppState(openDefaultTab = false, withDemoData = false)
        val name = ImportExportRepository.importCollectionFromString(state, fixture.readText())
        assertEquals("New Business", name, "Collection should be detected and imported as Postman v2.1")
    }

    // ── Top-level collection structure ─────────────────────────────────────────

    @Test
    fun sap_byd_fixture_imports_via_repository() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        val name = ImportExportRepository.importCollectionFromString(state, fixture.readText())
        assertEquals("New Business", name)
        assertEquals(1, state.collections.size)
        assertEquals("New Business", state.collections[0].name)
    }

    @Test
    fun sap_byd_fixture_has_one_top_level_folder() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val col = state.collections[0]
        val folders = col.children.filter { it.isFolder }
        assertEquals(1, folders.size, "Expected exactly 1 top-level folder")
        assertEquals("Create Sales Quote with net price components", folders[0].name)
    }

    @Test
    fun sap_byd_fixture_no_top_level_requests_only_folder() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val col = state.collections[0]
        val topLevelRequests = col.children.filter { !it.isFolder }
        assertTrue(topLevelRequests.isEmpty(), "Expected no bare requests at the collection root")
    }

    // ── Folder / request counts ────────────────────────────────────────────────

    @Test
    fun sap_byd_folder_contains_14_requests() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val requests = folder.children.filter { !it.isFolder }
        assertEquals(14, requests.size)
    }

    @Test
    fun sap_byd_folder_request_names_are_correct() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val names = folder.children.filter { !it.isFolder }.map { it.name }
        assertEquals("Get xsrf-token", names[0])
        assertEquals("Get metadata", names[1])
        assertEquals("Create sales quote (prices are determined)", names[2])
        assertEquals("Get sales quote with item price components", names[3])
        assertEquals("Update item tax code", names[4])
        assertEquals("Get sales quote by ID", names[13])
    }

    // ── HTTP methods ───────────────────────────────────────────────────────────

    @Test
    fun sap_byd_folder_request_methods_map_correctly() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val methods = folder.children.filter { !it.isFolder }.map { it.method }
        val expected = listOf(
            HttpMethodType.GET, HttpMethodType.GET, HttpMethodType.POST,
            HttpMethodType.GET, HttpMethodType.PATCH, HttpMethodType.POST,
            HttpMethodType.POST, HttpMethodType.POST, HttpMethodType.POST,
            HttpMethodType.GET, HttpMethodType.POST, HttpMethodType.POST,
            HttpMethodType.POST, HttpMethodType.GET,
        )
        assertEquals(expected, methods)
    }

    // ── URL parsing ────────────────────────────────────────────────────────────

    @Test
    fun sap_byd_first_request_url_uses_raw_field() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val firstReq = folder.children.first()
        // raw URL contains the Postman variable placeholder
        assertNotNull(firstReq.url)
        assertTrue(firstReq.url!!.contains("TenantHostname"), "URL should preserve {{TenantHostname}} variable")
        assertTrue(firstReq.url!!.startsWith("https://"), "URL should start with https://")
    }

    @Test
    fun sap_byd_get_metadata_url_contains_query_params() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val metadataReq = folder.children[1] // "Get metadata"
        assertEquals("Get metadata", metadataReq.name)
        assertNotNull(metadataReq.url)
        // raw URL: .../khcustomerquote/$metadata?sap-label=true&sap-language=en
        assertTrue(metadataReq.url!!.contains("metadata"), "URL should reference metadata endpoint")
        assertTrue(metadataReq.url!!.contains("sap-label"), "URL should include sap-label query param from raw field")
    }

    // ── Headers ────────────────────────────────────────────────────────────────

    @Test
    fun sap_byd_first_request_has_two_enabled_headers() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val firstReq = folder.children.first()
        // "Get xsrf-token" has: x-csrf-token=fetch AND Accept=application/json (both enabled)
        assertEquals(2, firstReq.userHeaders.size)
        assertTrue(firstReq.userHeaders.any { (k, _) -> k == "x-csrf-token" })
        assertTrue(firstReq.userHeaders.any { (k, _) -> k == "Accept" })
    }

    @Test
    fun sap_byd_get_metadata_skips_disabled_accept_header() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val metadataReq = folder.children[1] // "Get metadata" — Accept header is disabled
        assertEquals(1, metadataReq.userHeaders.size, "Disabled Accept header should be skipped")
        assertEquals("x-csrf-token", metadataReq.userHeaders[0].first)
    }

    @Test
    fun sap_byd_post_requests_have_three_headers() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val createQuote = folder.children[2] // "Create sales quote (prices are determined)"
        // Content-Type, x-csrf-token, Accept — all enabled
        assertEquals(3, createQuote.userHeaders.size)
        assertTrue(createQuote.userHeaders.any { (k, _) -> k == "Content-Type" })
        assertTrue(createQuote.userHeaders.any { (k, _) -> k == "x-csrf-token" })
    }

    // ── Body parsing ───────────────────────────────────────────────────────────

    @Test
    fun sap_byd_get_requests_have_no_body() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val firstReq = folder.children.first() // GET "Get xsrf-token"
        assertTrue(firstReq.bodyType == null, "GET request body type should be null")
        assertTrue(firstReq.bodyContent == null, "GET request body content should be null")
    }

    @Test
    fun sap_byd_create_quote_has_raw_body() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val createQuote = folder.children[2] // "Create sales quote (prices are determined)"
        // Body mode is "raw" with no language option → RAW_TEXT
        assertNotNull(createQuote.bodyType)
        assertNotNull(createQuote.bodyContent)
        assertTrue(
            createQuote.bodyContent!!.contains("ExternalReference"),
            "Body content should include JSON payload"
        )
    }

    // ── Auth ───────────────────────────────────────────────────────────────────

    @Test
    fun sap_byd_individual_requests_have_no_per_request_auth() {
        // Collection-level auth (basic) is on the root, not individual requests.
        // PostmanImporter only reads per-request auth → requests should have no auth type.
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val createQuote = folder.children[2]
        assertTrue(
            createQuote.authType == null,
            "Individual request should not have auth (auth is at collection level)"
        )
    }

    // ── Script conversion ──────────────────────────────────────────────────────

    @Test
    fun sap_byd_first_request_test_script_is_converted() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val firstReq = folder.children.first() // "Get xsrf-token"
        assertNotNull(firstReq.testScript, "Test script should not be null")
        val script = firstReq.testScript!!
        // pm.environment.set → reqlab.environment.set
        assertTrue(script.contains("reqlab.environment.set"), "pm.environment.set should be rewritten to reqlab.environment.set")
        // pm.test → reqlab.test
        assertTrue(script.contains("reqlab.test"), "pm.test should be rewritten to reqlab.test")
        // pm.response → reqlab.response
        assertTrue(script.contains("reqlab.response"), "pm.response should be rewritten to reqlab.response")
        // no pm.* calls should remain
        assertNoPmCalls(script)
    }

    @Test
    fun sap_byd_create_quote_test_script_converts_pm_expect() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val createQuote = folder.children[2] // "Create sales quote (prices are determined)"
        assertNotNull(createQuote.testScript)
        val script = createQuote.testScript!!
        assertTrue(script.contains("reqlab.test"), "pm.test → reqlab.test")
        assertTrue(script.contains("reqlab.expect"), "pm.expect → reqlab.expect")
        assertNoPmCalls(script)
    }

    @Test
    fun sap_byd_create_quote_prerequest_script_converts_pm_environment() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        val createQuote = folder.children[2] // has prerequest script with pm.environment.set
        assertNotNull(createQuote.preRequestScript)
        val script = createQuote.preRequestScript!!
        assertTrue(
            script.contains("reqlab.environment.set") || !script.contains("pm.environment.set"),
            "Pre-request pm.environment.set should be converted"
        )
    }

    @Test
    fun sap_byd_all_request_scripts_have_no_unconverted_pm_calls() {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        val folder = state.collections[0].children.first { it.isFolder }
        folder.children.filter { !it.isFolder }.forEach { req ->
            req.testScript?.let { assertNoPmCalls(it, context = req.name) }
            req.preRequestScript?.let { assertNoPmCalls(it, context = "${req.name} (prerequest)") }
        }
    }

    // ── Full roundtrip ─────────────────────────────────────────────────────────

    @Test
    fun sap_byd_exported_as_reqlab_format_can_be_reimported() {
        val state1 = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state1, fixture.readText())
        val exported = ImportExportRepository.exportCollectionToString(state1.collections[0])

        val state2 = AppState(openDefaultTab = false, withDemoData = false)
        val name2 = ImportExportRepository.importCollectionFromString(state2, exported)
        assertEquals("New Business", name2)
        assertEquals(1, state2.collections.size)
        val folder2 = state2.collections[0].children.first { it.isFolder }
        assertEquals("Create Sales Quote with net price components", folder2.name)
        assertEquals(14, folder2.children.filter { !it.isFolder }.size)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Asserts that the script contains no remaining unconverted `pm.*` calls
     * (ignoring `postman.*` which is a legacy Postman namespace we intentionally don't convert).
     */
    private fun assertNoPmCalls(script: String, context: String = "") {
        // Split into tokens so we don't accidentally flag "// pm.sendRequest" comments
        // or false positives within string literals.
        // We look for pm. followed by an identifier character, but not inside a comment.
        val nonCommentLines = script.lines().filterNot { it.trimStart().startsWith("//") }
        val remaining = nonCommentLines.filter { line ->
            Regex("""(?<!\w)pm\.[a-zA-Z]""").containsMatchIn(line)
        }
        assertTrue(
            remaining.isEmpty(),
            buildString {
                if (context.isNotEmpty()) append("[$context] ")
                append("Found unconverted pm.* calls:\n")
                remaining.forEach { append("  > $it\n") }
            }
        )
    }
}
