package com.reqlab.core.model.json

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Json5Test {

    @Test
    fun line_and_block_comments_omit_commented_out_field() {
        val text = """
            {
              "name": "Ada",
              // "role": "admin",
              /* "debug": true, */
              "active": true,
            }
        """.trimIndent()
        val wire = Json5.toWireJson(text).getOrThrow()
        assertTrue(!wire.contains("//"), wire)
        assertTrue(!wire.contains("role"), wire)
        assertTrue(!wire.contains("debug"), wire)
        val obj = Json5.parseToJsonElement(text).getOrThrow().jsonObject
        assertEquals("Ada", obj["name"]?.jsonPrimitive?.content)
        assertEquals(true, obj["active"]?.jsonPrimitive?.boolean)
        assertEquals(2, obj.size)
    }

    @Test
    fun slash_slash_inside_string_stays_string() {
        val text = """{"url":"http://x","note":"a // b"}"""
        val obj = Json5.parseToJsonElement(text).getOrThrow().jsonObject
        assertEquals("http://x", obj["url"]?.jsonPrimitive?.content)
        assertEquals("a // b", obj["note"]?.jsonPrimitive?.content)
        assertEquals(text, Json5.toWireJson(text).getOrThrow())
    }

    @Test
    fun trailing_commas_unquoted_keys_single_quotes() {
        val text = """{ name: 'Ada', tags: ['api',], }"""
        val obj = Json5.parseToJsonElement(text).getOrThrow().jsonObject
        assertEquals("Ada", obj["name"]?.jsonPrimitive?.content)
        assertEquals("api", obj["tags"]?.jsonArray?.get(0)?.jsonPrimitive?.content)
    }

    @Test
    fun nested_objects_and_arrays() {
        val text = """{ user: { name: "Bob", }, items: [1, 2,], }"""
        val obj = Json5.parseToJsonElement(text).getOrThrow().jsonObject
        assertEquals("Bob", obj["user"]?.jsonObject?.get("name")?.jsonPrimitive?.content)
        assertEquals(2, obj["items"]?.jsonArray?.size)
    }

    @Test
    fun hex_and_leading_decimal_encode_as_json_numbers() {
        val text = """{ hex: 0xFF, frac: .5, plus: +2, trailing: 5. }"""
        val obj = Json5.parseToJsonElement(text).getOrThrow().jsonObject
        assertEquals(255, obj["hex"]?.jsonPrimitive?.int)
        val wire = Json5.toCanonicalJson(text).getOrThrow()
        assertTrue(wire.contains("255"), wire)
        assertTrue(wire.contains("0.5") || wire.contains("0.50"), wire)
        assertTrue(!wire.contains("0x"), wire)
    }

    @Test
    fun infinity_and_nan_are_rejected() {
        assertTrue(Json5.parseToJsonElement("Infinity").isFailure)
        assertTrue(Json5.parseToJsonElement("NaN").isFailure)
        assertTrue(Json5.parseToJsonElement("{ a: Infinity }").isFailure)
        assertTrue(Json5.toWireJson("{ a: NaN }").isFailure)
    }

    @Test
    fun leftover_garbage_fails() {
        assertTrue(Json5.parseToJsonElement("{ a: 1 } extra").isFailure)
        assertTrue(Json5.parseToJsonElement("{ a: }").isFailure)
    }

    @Test
    fun strict_compact_json_toWireJson_is_identity() {
        val compact = """{"name":"Alice","age":30}"""
        assertEquals(compact, Json5.toWireJson(compact).getOrThrow())
    }

    @Test
    fun variable_placeholder_inside_string_is_unchanged() {
        val text = """{"id":"{{n}}"}"""
        assertEquals(text, Json5.toWireJson(text).getOrThrow())
        assertEquals("{{n}}", Json5.parseToJsonElement(text).getOrThrow().jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun pretty_strict_json_is_also_identity_for_toWireJson() {
        val pretty = "{\n  \"a\": 1\n}"
        assertEquals(pretty, Json5.toWireJson(pretty).getOrThrow())
    }

    @Test
    fun canonical_json_is_object() {
        val text = """{ a: 1, }"""
        val canonical = Json5.toCanonicalJson(text).getOrThrow()
        assertTrue(canonical.contains("\"a\""), canonical)
        assertTrue(!canonical.trimEnd().endsWith(",}") && !canonical.contains(",\n}"), canonical)
    }

    @Test
    fun line_continuations_join_string() {
        assertEquals("abcd", Json5.parseToJsonElement("'ab\\\ncd'").getOrThrow().jsonPrimitive.content)
        assertEquals("abcd", Json5.parseToJsonElement("'ab\\\r\ncd'").getOrThrow().jsonPrimitive.content)
        assertEquals("abcd", Json5.parseToJsonElement("'ab\\\rcd'").getOrThrow().jsonPrimitive.content)
        assertEquals("abcd", Json5.parseToJsonElement("'ab\\\u2028cd'").getOrThrow().jsonPrimitive.content)
        assertEquals("abcd", Json5.parseToJsonElement("'ab\\\u2029cd'").getOrThrow().jsonPrimitive.content)
    }

    @Test
    fun named_and_hex_escapes() {
        val s = Json5.parseToJsonElement("'\\v\\0\\x0f'").getOrThrow().jsonPrimitive.content
        assertEquals("\u000B\u0000\u000F", s)
    }

    @Test
    fun unknown_escape_is_the_character_itself() {
        assertEquals("a", Json5.parseToJsonElement("'\\a'").getOrThrow().jsonPrimitive.content)
        assertEquals("/", Json5.parseToJsonElement("'\\/'").getOrThrow().jsonPrimitive.content)
    }

    @Test
    fun unescaped_newline_in_string_fails() {
        assertTrue(Json5.parseToJsonElement("\"hello\nworld\"").isFailure)
        assertTrue(Json5.parseToJsonElement("'hello\rworld'").isFailure)
    }

    @Test
    fun digit_escape_other_than_zero_fails() {
        assertTrue(Json5.parseToJsonElement("'\\1'").isFailure)
        assertTrue(Json5.parseToJsonElement("'\\01'").isFailure)
    }

    @Test
    fun zero_forms_are_valid_but_leading_zero_integers_are_not() {
        val ok = Json5.parseToJsonElement("[0,0.,0e0]").getOrThrow().jsonArray
        assertEquals(3, ok.size)
        assertTrue(Json5.parseToJsonElement("01").isFailure)
        assertTrue(Json5.parseToJsonElement("[01]").isFailure)
        assertTrue(Json5.parseToJsonElement("+01").isFailure)
    }

    @Test
    fun bom_before_object_is_whitespace() {
        val obj = Json5.parseToJsonElement("\uFEFF{a:1}").getOrThrow().jsonObject
        assertEquals(1, obj["a"]?.jsonPrimitive?.int)
    }
}
