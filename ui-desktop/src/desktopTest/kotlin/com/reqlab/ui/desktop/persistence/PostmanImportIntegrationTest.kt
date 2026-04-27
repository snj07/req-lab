package com.reqlab.ui.shared.persistence

import com.reqlab.ui.shared.state.AppState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests that exercise [ImportExportRepository] end-to-end for Postman-format inputs.
 * Runs on the JVM (desktopTest source set).
 */
class PostmanImportIntegrationTest {

    // ── collection import ──────────────────────────────────────────────────────

    @Test
    fun importCollectionFromString_acceptsPostmanV2Collection() {
        val state = AppState(withDemoData = false)
        val postmanJson = """
        {
          "info": {
            "name": "Pet Store",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
          },
          "item": [
            {
              "name": "Get Pets",
              "request": {
                "method": "GET",
                "url": { "raw": "https://petstore.example.com/pets" },
                "header": [{ "key": "Accept", "value": "application/json" }]
              }
            }
          ]
        }
        """.trimIndent()

        val name = ImportExportRepository.importCollectionFromString(state, postmanJson)
        assertEquals("Pet Store", name)
        assertEquals(1, state.collections.size)
        val col = state.collections[0]
        assertEquals("Pet Store", col.name)
        assertEquals(1, col.children.size)
        val req = col.children[0]
        assertEquals("Get Pets", req.name)
        assertEquals("GET", req.method?.name)
        assertEquals("https://petstore.example.com/pets", req.url)
        assertEquals(1, req.userHeaders.size)
        assertEquals("Accept", req.userHeaders[0].first)
        assertEquals("application/json", req.userHeaders[0].second)
    }

    @Test
    fun importCollectionFromString_preservesFolderStructure() {
        val state = AppState(withDemoData = false)
        val postmanJson = """
        {
          "info": {
            "name": "Nested API",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
          },
          "item": [
            {
              "name": "Users",
              "item": [
                { "name": "List", "request": { "method": "GET",  "url": "https://api.example.com/users" } },
                { "name": "Create","request": { "method": "POST", "url": "https://api.example.com/users" } }
              ]
            },
            { "name": "Health", "request": { "method": "GET", "url": "https://api.example.com/health" } }
          ]
        }
        """.trimIndent()

        ImportExportRepository.importCollectionFromString(state, postmanJson)
        val col = state.collections[0]
        // One folder + one top-level request
        val folder = col.children.firstOrNull { it.isFolder }
        assertNotNull(folder)
        assertEquals("Users", folder!!.name)
        assertEquals(2, folder.children.size)
        val topReq = col.children.firstOrNull { !it.isFolder }
        assertNotNull(topReq)
        assertEquals("Health", topReq!!.name)
    }

    @Test
    fun importCollectionFromString_deduplicatesCollectionName() {
        val state = AppState(withDemoData = false)
        val postmanJson = """
        {
          "info": {"name": "My API","schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item": []
        }
        """.trimIndent()
        ImportExportRepository.importCollectionFromString(state, postmanJson)
        val name2 = ImportExportRepository.importCollectionFromString(state, postmanJson)
        assertEquals("My API (1)", name2)
        assertEquals(2, state.collections.size)
    }

    @Test
    fun importCollectionFromString_throwsForUnrecognizedFormat() {
        val state = AppState(withDemoData = false)
        assertFailsWith<ImportExportException> {
            ImportExportRepository.importCollectionFromString(state, """{"foo":"bar"}""")
        }
    }

    @Test
    fun importCollectionFromString_stillAcceptsReqLabFormat() {
        val state = AppState(withDemoData = false)
        val reqLabJson = """
        {
          "type": "reqLabCollection",
          "version": "1.0",
          "name": "ReqLab Native",
          "folders": [],
          "requests": []
        }
        """.trimIndent()
        val name = ImportExportRepository.importCollectionFromString(state, reqLabJson)
        assertEquals("ReqLab Native", name)
    }

    // ── environment import ─────────────────────────────────────────────────────

    @Test
    fun importEnvironmentFromString_acceptsPostmanEnvironment() {
        val state = AppState(withDemoData = false)
        val envJson = """
        {
          "name": "Production",
          "values": [
            { "key": "base_url", "value": "https://api.prod.example.com", "enabled": true },
            { "key": "timeout",  "value": "30000", "enabled": true },
            { "key": "debug",    "value": "false",  "enabled": false }
          ]
        }
        """.trimIndent()

        val name = ImportExportRepository.importEnvironmentFromString(state, envJson)
        assertEquals("Production", name)
        assertEquals(1, state.environments.size)
        val env = state.environments[0]
        assertEquals("Production", env.name)
        val baseUrl = env.variables.firstOrNull { it.key == "base_url" }
        assertNotNull(baseUrl)
        assertEquals("https://api.prod.example.com", baseUrl!!.value)
        val timeout = env.variables.firstOrNull { it.key == "timeout" }
        assertNotNull(timeout)
        // disabled variable "debug" must be absent
        assertTrue(env.variables.none { it.key == "debug" })
    }

    @Test
    fun importEnvironmentFromString_deduplicatesName() {
        val state = AppState(withDemoData = false)
        val envJson = """{"name":"Dev","values":[{"key":"x","value":"1","enabled":true}]}"""
        ImportExportRepository.importEnvironmentFromString(state, envJson)
        val name2 = ImportExportRepository.importEnvironmentFromString(state, envJson)
        assertEquals("Dev (1)", name2)
        assertEquals(2, state.environments.size)
    }

    @Test
    fun importEnvironmentFromString_stillAcceptsReqLabFormat() {
        val state = AppState(withDemoData = false)
        val reqLabEnvJson = """
        {
          "type": "reqLabEnvironment",
          "name": "Local",
          "variables": { "host": "localhost" }
        }
        """.trimIndent()
        val name = ImportExportRepository.importEnvironmentFromString(state, reqLabEnvJson)
        assertEquals("Local", name)
    }

    // ── wrapped Postman collection (exported by Postman app) ───────────────────

    @Test
    fun importCollectionFromString_acceptsWrappedPostmanExport() {
        val state = AppState(withDemoData = false)
        val wrapped = """
        {
          "collection": {
            "info": {
              "name": "Wrapped",
              "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
            },
            "item": [
              { "name": "Ping", "request": { "method": "GET", "url": "https://api.example.com/ping" } }
            ]
          }
        }
        """.trimIndent()
        val name = ImportExportRepository.importCollectionFromString(state, wrapped)
        assertEquals("Wrapped", name)
        assertEquals(1, state.collections[0].children.size)
    }

    // ── script conversion ──────────────────────────────────────────────────────

    @Test
    fun importCollectionFromString_convertsScripts() {
        val state = AppState(withDemoData = false)
        val postmanJson = """
        {
          "info": {"name":"Scripts","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item": [{
            "name": "Scripted",
            "request": {"method":"GET","url":"https://api.example.com"},
            "event": [
              {
                "listen": "test",
                "script": {"exec": ["pm.test('status 200', () => { pm.expect(pm.response.code).to.equal(200); });"]}
              }
            ]
          }]
        }
        """.trimIndent()
        ImportExportRepository.importCollectionFromString(state, postmanJson)
        val testScript = state.collections[0].children[0].testScript
        assertNotNull(testScript)
        assertTrue(testScript!!.contains("reqlab.test"), "Expected reqlab.test but got: $testScript")
        assertTrue(testScript.contains("reqlab.expect"), "Expected reqlab.expect but got: $testScript")
        assertTrue(testScript.contains("reqlab.response"), "Expected reqlab.response but got: $testScript")
    }

    @Test
    fun importCollectionFromString_infersRawJsonFromContentTypeHeader() {
        val state = AppState(withDemoData = false)
        val postmanJson = """
        {
          "info": {"name":"BodyType Header","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item": [{
            "name": "Create",
            "request": {
              "method": "POST",
              "url": "https://api.example.com/items",
              "header": [{"key":"Content-Type","value":"application/json"}],
              "body": {
                "mode": "raw",
                "raw": "{\"name\":\"Alice\"}"
              }
            }
          }]
        }
        """.trimIndent()

        ImportExportRepository.importCollectionFromString(state, postmanJson)
        val req = state.collections[0].children[0]
        assertEquals("JSON", req.bodyType?.name)
        assertEquals("{\"name\":\"Alice\"}", req.bodyContent)
    }

    @Test
    fun importCollectionFromString_infersRawJsonFromBodyShapeWhenLanguageMissing() {
        val state = AppState(withDemoData = false)
        val postmanJson = """
        {
          "info": {"name":"BodyType Shape","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item": [{
            "name": "Update",
            "request": {
              "method": "PUT",
              "url": "https://api.example.com/items/1",
              "body": {
                "mode": "raw",
                "raw": "  [1,2,3]  "
              }
            }
          }]
        }
        """.trimIndent()

        ImportExportRepository.importCollectionFromString(state, postmanJson)
        val req = state.collections[0].children[0]
        assertEquals("JSON", req.bodyType?.name)
        assertEquals("  [1,2,3]  ", req.bodyContent)
    }
}
