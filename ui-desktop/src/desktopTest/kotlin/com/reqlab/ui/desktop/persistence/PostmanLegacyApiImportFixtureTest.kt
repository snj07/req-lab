package com.reqlab.ui.shared.persistence

import com.reqlab.ui.shared.state.AppState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test that imports the "legacy-postman-api" fixture collection —
 * a collection that uses the *old* `postman.*` sandbox API (pre-pm era) and the
 * legacy global aliases `responseBody` / `responseCode`.
 *
 * Validates that every legacy call is converted to its `reqlab.*` equivalent and
 * that no `postman.*` / old global identifiers survive into the imported scripts.
 *
 * Fixture: qa-tests/fixtures/legacy-postman-api.postman_collection.json
 */
class PostmanLegacyApiImportFixtureTest {

    private val fixture by lazy {
        val candidates = listOf(
            File("qa-tests/fixtures/legacy-postman-api.postman_collection.json"),
            File("../qa-tests/fixtures/legacy-postman-api.postman_collection.json"),
        )
        candidates.firstOrNull { it.exists() }
            ?: error("Fixture legacy-postman-api.postman_collection.json not found")
    }

    private fun buildState(): AppState {
        val state = AppState(openDefaultTab = false, withDemoData = false)
        ImportExportRepository.importCollectionFromString(state, fixture.readText())
        return state
    }

    private fun assertNoPmOrPostmanCalls(script: String, context: String) {
        // Only check non-commented lines — intentionally commented-out calls are fine.
        val executableLines = script.lines()
            .filter { !it.trim().startsWith("//") }
            .joinToString("\n")
        assertFalse(Regex("""\bpm\.""").containsMatchIn(executableLines),
            "$context: raw pm.* call found after conversion")
        assertFalse(Regex("""\bpostman\.""").containsMatchIn(executableLines),
            "$context: raw postman.* call found after conversion")
        assertFalse(Regex("""\bresponseCode\b""").containsMatchIn(executableLines),
            "$context: legacy responseCode found after conversion")
        assertFalse(Regex("""\bresponseBody\b""").containsMatchIn(executableLines),
            "$context: legacy standalone responseBody found after conversion")
    }

    // ── Collection detection & structure ──────────────────────────────────────

    @Test
    fun fixture_is_imported_successfully() {
        val state = buildState()
        assertTrue(state.collections.isNotEmpty(), "Collection should be imported")
        assertTrue(state.collections[0].children.isNotEmpty(),
            "Collection should contain requests")
    }

    // ── Login request test-script conversion ──────────────────────────────────

    @Test
    fun login_test_script_converts_postman_setEnvironmentVariable() {
        val state = buildState()
        val login = state.collections[0].children.firstOrNull { it.name == "Login" }
            ?: state.collections[0].children.first()
        val script = login.testScript
        assertNotNull(script, "Login should have a test script")
        assertTrue(script.contains("reqlab.environment.set"),
            "postman.setEnvironmentVariable should be converted to reqlab.environment.set")
    }

    @Test
    fun login_test_script_converts_postman_getEnvironmentVariable() {
        val state = buildState()
        val login = state.collections[0].children.firstOrNull { it.name == "Login" }
            ?: state.collections[0].children.first()
        val script = login.testScript!!
        assertTrue(script.contains("reqlab.environment.get"),
            "postman.getEnvironmentVariable should be converted to reqlab.environment.get")
    }

    @Test
    fun login_test_script_converts_responseCode_code() {
        val state = buildState()
        val login = state.collections[0].children.firstOrNull { it.name == "Login" }
            ?: state.collections[0].children.first()
        val script = login.testScript!!
        assertTrue(script.contains("response.code"),
            "responseCode.code should be converted to response.code")
    }

    @Test
    fun login_test_script_converts_responseBody() {
        val state = buildState()
        val login = state.collections[0].children.firstOrNull { it.name == "Login" }
            ?: state.collections[0].children.first()
        val script = login.testScript!!
        assertFalse(Regex("""\bresponseBody\b""").containsMatchIn(script),
            "standalone responseBody should be converted to response.text()")
        assertTrue(script.contains("response.text()"),
            "responseBody should be converted to response.text()")
    }

    @Test
    fun login_test_script_comments_out_setNextRequest() {
        val state = buildState()
        val login = state.collections[0].children.firstOrNull { it.name == "Login" }
            ?: state.collections[0].children.first()
        val script = login.testScript!!
        assertTrue(script.contains("// postman.setNextRequest is not supported in ReqLab"),
            "postman.setNextRequest should be commented out")
    }

    @Test
    fun login_test_script_has_no_remaining_legacy_calls() {
        val state = buildState()
        val login = state.collections[0].children.firstOrNull { it.name == "Login" }
            ?: state.collections[0].children.first()
        assertNoPmOrPostmanCalls(login.testScript!!, "Login test script")
    }

    // ── GetProfile request conversion ─────────────────────────────────────────

    @Test
    fun get_profile_prerequest_converts_postman_get_and_setGlobalVariable() {
        val state = buildState()
        val profile = state.collections[0].children.firstOrNull { it.name == "Get Profile" }
            ?: state.collections[0].children.last()
        val preScript = profile.preRequestScript
        assertNotNull(preScript, "Get Profile should have a pre-request script")
        assertTrue(preScript.contains("reqlab.environment.get"),
            "postman.getGlobalVariable should be converted to reqlab.environment.get")
        assertTrue(preScript.contains("reqlab.environment.set"),
            "postman.setGlobalVariable should be converted to reqlab.environment.set")
        assertNoPmOrPostmanCalls(preScript, "Get Profile pre-request script")
    }

    @Test
    fun get_profile_test_script_converts_mixed_pm_and_postman_apis() {
        val state = buildState()
        val profile = state.collections[0].children.firstOrNull { it.name == "Get Profile" }
            ?: state.collections[0].children.last()
        val script = profile.testScript
        assertNotNull(script, "Get Profile should have a test script")
        assertTrue(script.contains("reqlab.test"), "pm.test should become reqlab.test")
        assertTrue(script.contains("reqlab.environment.set"),
            "postman.setEnvironmentVariable should be converted to reqlab.environment.set")
        assertNoPmOrPostmanCalls(script, "Get Profile test script")
    }
}
