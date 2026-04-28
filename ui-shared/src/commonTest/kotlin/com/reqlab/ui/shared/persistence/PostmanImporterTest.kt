package com.reqlab.ui.shared.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class PostmanImporterTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun parse(raw: String) = json.parseToJsonElement(raw).jsonObject

    // ── isPostmanCollection ────────────────────────────────────────────────────

    @Test
    fun `isPostmanCollection returns true for v2 schema`() {
        val root = parse("""{"info":{"schema":"https://schema.getpostman.com/json/collection/v2.0.0/collection.json"},"item":[]}""")
        assertTrue(PostmanImporter.isPostmanCollection(root))
    }

    @Test
    fun `isPostmanCollection returns true for v2_1 schema`() {
        val root = parse("""{"info":{"schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},"item":[]}""")
        assertTrue(PostmanImporter.isPostmanCollection(root))
    }

    @Test
    fun `isPostmanCollection returns true for wrapped collection`() {
        val root = parse("""{"collection":{"info":{"schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},"item":[]}}""")
        assertTrue(PostmanImporter.isPostmanCollection(root))
    }

    @Test
    fun `isPostmanCollection returns false for reqLab collection`() {
        val root = parse("""{"type":"reqLabCollection","version":"1.0","name":"Test","folders":[],"requests":[]}""")
        assertFalse(PostmanImporter.isPostmanCollection(root))
    }

    @Test
    fun `isPostmanCollection returns false for random JSON`() {
        val root = parse("""{"foo":"bar"}""")
        assertFalse(PostmanImporter.isPostmanCollection(root))
    }

    // ── isPostmanEnvironment ───────────────────────────────────────────────────

    @Test
    fun `isPostmanEnvironment returns true for valid postman env`() {
        val root = parse("""{"name":"My Env","values":[{"key":"base_url","value":"http://localhost","enabled":true}]}""")
        assertTrue(PostmanImporter.isPostmanEnvironment(root))
    }

    @Test
    fun `isPostmanEnvironment returns false for reqLab environment`() {
        val root = parse("""{"type":"reqLabEnvironment","name":"Env","variables":{}}""")
        assertFalse(PostmanImporter.isPostmanEnvironment(root))
    }

    @Test
    fun `isPostmanEnvironment returns false when values is missing`() {
        val root = parse("""{"name":"My Env"}""")
        assertFalse(PostmanImporter.isPostmanEnvironment(root))
    }

    // ── importCollection – minimal ─────────────────────────────────────────────

    @Test
    fun `importCollection parses minimal collection`() {
        val root = parse("""
        {
          "info":{"name":"My API","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[]
        }
        """.trimIndent())
        val dto = PostmanImporter.importCollection(root)
        assertEquals("My API", dto.name)
        assertTrue(dto.folders.isEmpty())
        assertTrue(dto.requests.isEmpty())
    }

    @Test
    fun `importCollection parses flat requests`() {
        val root = parse("""
        {
          "info":{"name":"Flat","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[
            {
              "name":"Get Users",
              "request":{"method":"GET","url":{"raw":"https://api.example.com/users"},"header":[],"body":null}
            }
          ]
        }
        """.trimIndent())
        val dto = PostmanImporter.importCollection(root)
        assertEquals("Flat", dto.name)
        assertEquals(1, dto.requests.size)
        assertEquals("Get Users", dto.requests[0].name)
        assertEquals("GET", dto.requests[0].method)
        assertEquals("https://api.example.com/users", dto.requests[0].url)
    }

    @Test
    fun `importCollection parses nested folder structure`() {
        val root = parse("""
        {
          "info":{"name":"Nested","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[
            {
              "name":"Users",
              "item":[
                {"name":"List","request":{"method":"GET","url":"https://api.example.com/users","header":[]}},
                {"name":"Create","request":{"method":"POST","url":"https://api.example.com/users","header":[]}}
              ]
            }
          ]
        }
        """.trimIndent())
        val dto = PostmanImporter.importCollection(root)
        assertEquals(1, dto.folders.size)
        assertEquals("Users", dto.folders[0].name)
        assertEquals(2, dto.folders[0].requests.size)
    }

    @Test
    fun `importCollection throws when info is missing`() {
        val root = parse("""{"item":[]}""")
        assertFailsWith<ImportExportException> { PostmanImporter.importCollection(root) }
    }

    // ── URL parsing ────────────────────────────────────────────────────────────

    @Test
    fun `importCollection uses raw URL when present`() {
        val root = parse("""
        {
          "info":{"name":"URL","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[
            {"name":"R","request":{"method":"GET","url":{"raw":"https://example.com/path?q=1"}}}
          ]
        }
        """.trimIndent())
        assertEquals("https://example.com/path?q=1", PostmanImporter.importCollection(root).requests[0].url)
    }

    @Test
    fun `importCollection accepts string URL`() {
        val root = parse("""
        {
          "info":{"name":"StrURL","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[
            {"name":"R","request":{"method":"GET","url":"https://example.com/foo"}}
          ]
        }
        """.trimIndent())
        assertEquals("https://example.com/foo", PostmanImporter.importCollection(root).requests[0].url)
    }

    // ── Header parsing ─────────────────────────────────────────────────────────

    @Test
    fun `importCollection skips headers with empty or blank keys`() {
        // Bug 2: Headers with empty keys were imported, polluting the request config.
        // BEFORE FIX: all 3 headers (including the 2 blank-key ones) were returned.
        // AFTER FIX:  only the header with a non-blank key is returned.
        val root = parse("""
        {
          "info":{"name":"EmptyKey","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[
            {"name":"R","request":{
              "method":"GET","url":"https://example.com",
              "header":[
                {"key":"","value":"should-be-skipped"},
                {"key":"   ","value":"also-skipped"},
                {"key":"X-Token","value":"abc123"}
              ]
            }}
          ]
        }
        """.trimIndent())
        val headers = PostmanImporter.importCollection(root).requests[0].userHeaders
        assertEquals(1, headers.size, "Blank-key headers must be skipped during import")
        assertEquals("X-Token" to "abc123", headers[0])
    }

    @Test
    fun `importCollection parses headers and skips disabled ones`() {
        val root = parse("""
        {
          "info":{"name":"H","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[
            {"name":"R","request":{
              "method":"GET","url":"https://example.com",
              "header":[
                {"key":"X-Token","value":"abc123","disabled":false},
                {"key":"X-Skip","value":"skip","disabled":true}
              ]
            }}
          ]
        }
        """.trimIndent())
        val headers = PostmanImporter.importCollection(root).requests[0].userHeaders
        assertEquals(1, headers.size)
        assertEquals("X-Token" to "abc123", headers[0])
    }

    // ── Body parsing ───────────────────────────────────────────────────────────

    @Test
    fun `importCollection parses raw JSON body`() {
        val root = parse("""
        {
          "info":{"name":"B","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[
            {"name":"R","request":{
              "method":"POST","url":"https://example.com",
              "body":{"mode":"raw","raw":"{\"key\":\"value\"}","options":{"raw":{"language":"json"}}}
            }}
          ]
        }
        """.trimIndent())
        val req = PostmanImporter.importCollection(root).requests[0]
        assertEquals("JSON", req.bodyType)
        assertEquals("{\"key\":\"value\"}", req.bodyContent)
    }

    @Test
    fun `importCollection parses x-www-form-urlencoded body`() {
        val root = parse("""
        {
          "info":{"name":"UE","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[
            {"name":"R","request":{
              "method":"POST","url":"https://example.com",
              "body":{"mode":"urlencoded","urlencoded":[{"key":"a","value":"1"},{"key":"b","value":"2"}]}
            }}
          ]
        }
        """.trimIndent())
        val req = PostmanImporter.importCollection(root).requests[0]
        assertEquals("X_WWW_FORM_URLENCODED", req.bodyType)
        assertEquals("a=1&b=2", req.bodyContent)
    }

    // ── Auth parsing ───────────────────────────────────────────────────────────

    @Test
    fun `importCollection parses bearer auth`() {
        val root = parse("""
        {
          "info":{"name":"Auth","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[
            {"name":"R","request":{
              "method":"GET","url":"https://example.com",
              "auth":{"type":"bearer","bearer":[{"key":"token","value":"mytoken123"}]}
            }}
          ]
        }
        """.trimIndent())
        val req = PostmanImporter.importCollection(root).requests[0]
        assertEquals("BEARER", req.authType)
        assertEquals("mytoken123", req.authToken)
    }

    @Test
    fun `importCollection parses basic auth`() {
        val root = parse("""
        {
          "info":{"name":"Auth","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[
            {"name":"R","request":{
              "method":"GET","url":"https://example.com",
              "auth":{"type":"basic","basic":[{"key":"username","value":"user"},{"key":"password","value":"pass"}]}
            }}
          ]
        }
        """.trimIndent())
        val req = PostmanImporter.importCollection(root).requests[0]
        assertEquals("BASIC", req.authType)
        assertEquals("user", req.authUsername)
        assertEquals("pass", req.authPassword)
    }

    @Test
    fun `importCollection parses apikey auth`() {
        val root = parse("""
        {
          "info":{"name":"Auth","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[
            {"name":"R","request":{
              "method":"GET","url":"https://example.com",
              "auth":{"type":"apikey","apikey":[{"key":"key","value":"X-API-KEY"},{"key":"value","value":"secret"}]}
            }}
          ]
        }
        """.trimIndent())
        val req = PostmanImporter.importCollection(root).requests[0]
        assertEquals("API_KEY", req.authType)
        assertEquals("X-API-KEY", req.authApiKey)
        assertEquals("secret", req.authApiValue)
    }

    // ── Script parsing ─────────────────────────────────────────────────────────

    @Test
    fun `importCollection parses pre-request and test scripts`() {
        val root = parse("""
        {
          "info":{"name":"Scripts","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
          "item":[
            {
              "name":"R",
              "request":{"method":"GET","url":"https://api.example.com"},
              "event":[
                {"listen":"prerequest","script":{"type":"text/javascript","exec":["pm.environment.set('x','1');"]}},
                {"listen":"test","script":{"type":"text/javascript","exec":["pm.test('OK', () => pm.response.to.have.status(200));"]}}
              ]
            }
          ]
        }
        """.trimIndent())
        val req = PostmanImporter.importCollection(root).requests[0]
        assertNotNull(req.preRequestScript)
        assertTrue(req.preRequestScript!!.contains("reqlab.environment.set"))
        assertNotNull(req.testScript)
        assertTrue(req.testScript!!.contains("reqlab.test"))
        assertTrue(req.testScript!!.contains("reqlab.response"))
    }

    // ── convertScript ──────────────────────────────────────────────────────────

    @Test
    fun `convertScript replaces pm_test`() {
        assertEquals("reqlab.test('OK', fn)", PostmanImporter.convertScript("pm.test('OK', fn)"))
    }

    @Test
    fun `convertScript replaces pm_expect`() {
        assertEquals("reqlab.expect(x).to.equal(1)", PostmanImporter.convertScript("pm.expect(x).to.equal(1)"))
    }

    @Test
    fun `convertScript replaces pm_response`() {
        assertEquals("reqlab.response.status", PostmanImporter.convertScript("pm.response.status"))
    }

    @Test
    fun `convertScript replaces pm_environment`() {
        val result = PostmanImporter.convertScript("pm.environment.get('key')")
        assertEquals("reqlab.environment.get('key')", result)
    }

    @Test
    fun `convertScript replaces pm_globals with environment`() {
        val result = PostmanImporter.convertScript("pm.globals.set('k','v')")
        assertEquals("reqlab.environment.set('k','v')", result)
    }

    @Test
    fun `convertScript comments out pm_sendRequest`() {
        val result = PostmanImporter.convertScript("pm.sendRequest('url', cb)")
        assertTrue(result.contains("// pm.sendRequest is not supported in ReqLab"))
    }

    @Test
    fun `convertScript leaves blank script unchanged`() {
        assertEquals("", PostmanImporter.convertScript(""))
    }

    // ── convertScript – legacy postman.* namespace ─────────────────────────────

    @Test
    fun `convertScript replaces postman_setEnvironmentVariable`() {
        assertEquals(
            "reqlab.environment.set('SESSION', jsonData.sessionId);",
            PostmanImporter.convertScript("postman.setEnvironmentVariable('SESSION', jsonData.sessionId);")
        )
    }

    @Test
    fun `convertScript replaces postman_getEnvironmentVariable`() {
        assertEquals(
            "reqlab.environment.get('USERNAME')",
            PostmanImporter.convertScript("postman.getEnvironmentVariable('USERNAME')")
        )
    }

    @Test
    fun `convertScript replaces postman_clearEnvironmentVariable`() {
        assertEquals(
            "reqlab.environment.unset('key')",
            PostmanImporter.convertScript("postman.clearEnvironmentVariable('key')")
        )
    }

    @Test
    fun `convertScript replaces postman_setGlobalVariable`() {
        assertEquals(
            "reqlab.environment.set('g','v')",
            PostmanImporter.convertScript("postman.setGlobalVariable('g','v')")
        )
    }

    @Test
    fun `convertScript replaces postman_getGlobalVariable`() {
        assertEquals(
            "reqlab.environment.get('g')",
            PostmanImporter.convertScript("postman.getGlobalVariable('g')")
        )
    }

    @Test
    fun `convertScript comments out postman_setNextRequest`() {
        val result = PostmanImporter.convertScript("postman.setNextRequest(null);")
        assertTrue(result.contains("// postman.setNextRequest is not supported in ReqLab"))
    }

    @Test
    fun `convertScript replaces responseCode_code`() {
        assertEquals("response.code", PostmanImporter.convertScript("responseCode.code"))
    }

    @Test
    fun `convertScript replaces responseCode_name`() {
        assertEquals("response.status", PostmanImporter.convertScript("responseCode.name"))
    }

    @Test
    fun `convertScript replaces standalone responseBody`() {
        assertEquals(
            "JSON.parse(response.text())",
            PostmanImporter.convertScript("JSON.parse(responseBody)")
        )
    }

    @Test
    fun `convertScript does not replace responseBody inside longer identifier`() {
        // e.g. a user variable named responseBodyData should not be touched
        val input = "var responseBodyData = 1;"
        assertEquals(input, PostmanImporter.convertScript(input))
    }

    @Test
    fun `convertScript handles full legacy login script`() {
        val input = """
            var jsonData = JSON.parse(responseBody);
            if (responseCode.code === 200) {
                postman.setEnvironmentVariable('SESSION_ID', jsonData.sessionId);
                postman.setEnvironmentVariable('ORG_ID', jsonData.orgId);
                console.log("Logged in as " + postman.getEnvironmentVariable('USERNAME'));
            } else {
                postman.setNextRequest(null);
                console.log("Login failed");
            }
        """.trimIndent()
        val result = PostmanImporter.convertScript(input)
        // All postman.* and old globals must be gone
        assertFalse(result.contains("postman.setEnvironmentVariable"), "should not contain old setEnvVar")
        assertFalse(result.contains("postman.getEnvironmentVariable"), "should not contain old getEnvVar")
        assertFalse(result.contains("responseCode.code"), "should not contain responseCode.code")
        assertFalse(Regex("""\bresponseBody\b""").containsMatchIn(result), "should not contain standalone responseBody")
        // Converted calls must be present
        assertTrue(result.contains("reqlab.environment.set"), "set converted")
        assertTrue(result.contains("reqlab.environment.get"), "get converted")
        assertTrue(result.contains("response.code"), "responseCode.code converted")
        assertTrue(result.contains("response.text()"), "responseBody converted")
        assertTrue(result.contains("// postman.setNextRequest is not supported in ReqLab"), "setNextRequest commented")
    }



    @Test
    fun `importEnvironment parses name and variables`() {
        val root = parse("""
        {
          "name":"Staging",
          "values":[
            {"key":"base_url","value":"https://staging.example.com","enabled":true},
            {"key":"api_key","value":"sk_test_123","enabled":true}
          ]
        }
        """.trimIndent())
        val dto = PostmanImporter.importEnvironment(root)
        assertEquals("Staging", dto.name)
        assertEquals("https://staging.example.com", dto.variables["base_url"])
        assertEquals("sk_test_123", dto.variables["api_key"])
    }

    @Test
    fun `importEnvironment skips disabled variables`() {
        val root = parse("""
        {
          "name":"Env",
          "values":[
            {"key":"active","value":"yes","enabled":true},
            {"key":"inactive","value":"no","enabled":false}
          ]
        }
        """.trimIndent())
        val dto = PostmanImporter.importEnvironment(root)
        assertEquals(1, dto.variables.size)
        assertTrue(dto.variables.containsKey("active"))
        assertFalse(dto.variables.containsKey("inactive"))
    }

    @Test
    fun `importEnvironment skips entries with blank keys`() {
        val root = parse("""
        {
          "name":"Env",
          "values":[
            {"key":"","value":"oops","enabled":true},
            {"key":"valid","value":"ok","enabled":true}
          ]
        }
        """.trimIndent())
        val dto = PostmanImporter.importEnvironment(root)
        assertEquals(1, dto.variables.size)
        assertTrue(dto.variables.containsKey("valid"))
    }

    @Test
    fun `importEnvironment throws when name is missing`() {
        val root = parse("""{"values":[]}""")
        assertFailsWith<ImportExportException> { PostmanImporter.importEnvironment(root) }
    }
}
