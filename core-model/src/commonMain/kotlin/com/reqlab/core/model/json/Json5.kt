package com.reqlab.core.model.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * JSON5 authoring helper: parse comments, trailing commas, unquoted keys, and
 * related JSON5 syntax into [JsonElement], then emit RFC 8259 JSON for the wire.
 *
 * [Infinity] / [NaN] are rejected — they are not JSON.
 */
object Json5 {

    private val strictJson = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalSerializationApi::class)
    private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun parseToJsonElement(text: String): Result<JsonElement> =
        runCatching { Json5Parser(text).parse() }

    /**
     * If [text] is already valid RFC 8259 JSON, return it unchanged.
     * Otherwise parse JSON5 and pretty-print strict JSON.
     */
    fun toWireJson(text: String): Result<String> {
        if (text.isBlank()) return Result.success(text)
        if (runCatching { strictJson.parseToJsonElement(text) }.isSuccess) return Result.success(text)
        return toCanonicalJson(text)
    }

    /** Always re-encode as pretty 2-space JSON (comments and JSON5 syntax dropped). */
    fun toCanonicalJson(text: String): Result<String> =
        parseToJsonElement(text).map { prettyJson.encodeToString(JsonElement.serializer(), it) }
}

class Json5ParseException(message: String, val offset: Int) : IllegalArgumentException(message)

@OptIn(ExperimentalSerializationApi::class)
private class Json5Parser(private val text: String) {
    private var i = 0

    fun parse(): JsonElement {
        skip()
        if (i >= text.length) throw error("Empty JSON5")
        val value = parseValue()
        skip()
        if (i < text.length) throw error("Unexpected trailing input")
        return value
    }

    private fun parseValue(): JsonElement {
        skip()
        if (i >= text.length) throw error("Unexpected end of input")
        return when (val c = text[i]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"', '\'' -> JsonPrimitive(parseQuotedString())
            't' -> { expectWord("true"); JsonPrimitive(true) }
            'f' -> { expectWord("false"); JsonPrimitive(false) }
            'n' -> {
                if (text.startsWith("null", i)) { expectWord("null"); JsonNull }
                else if (text.startsWith("NaN", i)) throw error("NaN is not valid JSON")
                else throw error("Unexpected token")
            }
            'I' -> {
                if (text.startsWith("Infinity", i)) throw error("Infinity is not valid JSON")
                else throw error("Unexpected token")
            }
            '+', '-', '.', in '0'..'9' -> parseNumber()
            else -> throw error("Unexpected character '$c'")
        }
    }

    private fun parseObject(): JsonObject {
        expect('{')
        skip()
        val map = linkedMapOf<String, JsonElement>()
        if (peek('}')) {
            expect('}')
            return JsonObject(map)
        }
        while (true) {
            skip()
            val key = when {
                i >= text.length -> throw error("Unterminated object")
                text[i] == '"' || text[i] == '\'' -> parseQuotedString()
                else -> parseIdentifier()
            }
            skip()
            expect(':')
            skip()
            map[key] = parseValue()
            skip()
            when {
                peek(',') -> {
                    expect(',')
                    skip()
                    if (peek('}')) {
                        expect('}')
                        return JsonObject(map)
                    }
                }
                peek('}') -> {
                    expect('}')
                    return JsonObject(map)
                }
                else -> throw error("Expected ',' or '}' in object")
            }
        }
    }

    private fun parseArray(): JsonArray {
        expect('[')
        skip()
        val items = mutableListOf<JsonElement>()
        if (peek(']')) {
            expect(']')
            return JsonArray(items)
        }
        while (true) {
            skip()
            items.add(parseValue())
            skip()
            when {
                peek(',') -> {
                    expect(',')
                    skip()
                    if (peek(']')) {
                        expect(']')
                        return JsonArray(items)
                    }
                }
                peek(']') -> {
                    expect(']')
                    return JsonArray(items)
                }
                else -> throw error("Expected ',' or ']' in array")
            }
        }
    }

    private fun parseIdentifier(): String {
        if (i >= text.length) throw error("Expected identifier")
        val start = i
        val c = text[i]
        if (!(c.isLetter() || c == '_' || c == '$')) throw error("Expected property name")
        i++
        while (i < text.length) {
            val n = text[i]
            if (n.isLetterOrDigit() || n == '_' || n == '$') i++ else break
        }
        val id = text.substring(start, i)
        if (id == "Infinity" || id == "NaN") throw error("$id is not valid JSON")
        return id
    }

    private fun parseQuotedString(): String {
        val quote = text[i]
        if (quote != '"' && quote != '\'') throw error("Expected string")
        i++
        val sb = StringBuilder()
        while (i < text.length) {
            when (val c = text[i]) {
                quote -> { i++; return sb.toString() }
                '\\' -> parseStringEscape(sb)
                '\n', '\r' -> throw error("Unescaped line terminator in string")
                else -> { sb.append(c); i++ }
            }
        }
        throw error("Unterminated string")
    }

    /** JSON5 spec 5.1: named escapes, \xHH, \uHHHH, \0, line continuation; reject \1–\9. */
    private fun parseStringEscape(sb: StringBuilder) {
        i++
        if (i >= text.length) throw error("Unterminated string escape")
        when (val e = text[i]) {
            '"', '\'', '\\', '/' -> { sb.append(e); i++ }
            'b' -> { sb.append('\b'); i++ }
            'f' -> { sb.append('\u000c'); i++ }
            'n' -> { sb.append('\n'); i++ }
            'r' -> { sb.append('\r'); i++ }
            't' -> { sb.append('\t'); i++ }
            'v' -> { sb.append('\u000B'); i++ }
            '0' -> {
                i++
                if (i < text.length && text[i].isDigit()) throw error("Invalid octal escape")
                sb.append('\u0000')
            }
            in '1'..'9' -> throw error("Invalid escape")
            'x' -> {
                i++
                if (i + 2 > text.length) throw error("Invalid hex escape")
                val hex = text.substring(i, i + 2)
                val cp = hex.toIntOrNull(16) ?: throw error("Invalid hex escape")
                sb.append(cp.toChar())
                i += 2
            }
            'u' -> {
                i++
                if (i + 4 > text.length) throw error("Invalid unicode escape")
                val hex = text.substring(i, i + 4)
                val cp = hex.toIntOrNull(16) ?: throw error("Invalid unicode escape")
                sb.append(cp.toChar())
                i += 4
            }
            '\n' -> i++
            '\r' -> {
                i++
                if (i < text.length && text[i] == '\n') i++
            }
            '\u2028', '\u2029' -> i++
            else -> { sb.append(e); i++ }
        }
    }

    private fun parseNumber(): JsonPrimitive {
        val start = i
        if (peek('+') || peek('-')) {
            if (text[i] == '+') i++ else i++
        }
        if (text.startsWith("Infinity", i)) throw error("Infinity is not valid JSON")
        if (text.startsWith("NaN", i)) throw error("NaN is not valid JSON")

        if (i < text.length && (text[i] == '0') && i + 1 < text.length && (text[i + 1] == 'x' || text[i + 1] == 'X')) {
            val signStart = start
            i += 2
            val hexStart = i
            while (i < text.length && text[i].isHexDigit()) i++
            if (i == hexStart) throw error("Invalid hex number")
            val hex = text.substring(hexStart, i)
            val mag = hex.toLongOrNull(16) ?: throw error("Hex number out of range")
            val neg = text[signStart] == '-'
            return JsonUnquotedLiteral(if (neg) (-mag).toString() else mag.toString())
        }

        if (i < text.length && text[i] == '.') {
            i++
            if (i >= text.length || !text[i].isDigit()) throw error("Invalid number")
            while (i < text.length && text[i].isDigit()) i++
        } else {
            if (i >= text.length || !text[i].isDigit()) throw error("Invalid number")
            val first = text[i]
            i++
            if (first == '0' && i < text.length && text[i].isDigit()) {
                throw error("Leading zero is not valid")
            }
            while (i < text.length && text[i].isDigit()) i++
            if (i < text.length && text[i] == '.') {
                i++
                while (i < text.length && text[i].isDigit()) i++
            }
        }
        if (i < text.length && (text[i] == 'e' || text[i] == 'E')) {
            i++
            if (i < text.length && (text[i] == '+' || text[i] == '-')) i++
            if (i >= text.length || !text[i].isDigit()) throw error("Invalid exponent")
            while (i < text.length && text[i].isDigit()) i++
        }
        var raw = text.substring(start, i)
        if (raw.startsWith("+")) raw = raw.substring(1)
        if (raw.startsWith(".")) raw = "0$raw"
        if (raw.startsWith("-.")) raw = "-0${raw.substring(1)}"
        if (raw.endsWith(".") && !raw.contains('e', ignoreCase = true)) raw = raw.dropLast(1)
        return JsonUnquotedLiteral(raw)
    }

    private fun skip() {
        while (i < text.length) {
            when {
                text[i].isWhitespace() || text[i] == '\uFEFF' -> i++
                text.startsWith("//", i) -> {
                    i += 2
                    while (i < text.length && text[i] != '\n' && text[i] != '\r') i++
                }
                text.startsWith("/*", i) -> {
                    i += 2
                    val end = text.indexOf("*/", i)
                    if (end < 0) throw error("Unterminated block comment")
                    i = end + 2
                }
                else -> return
            }
        }
    }

    private fun expect(c: Char) {
        skip()
        if (i >= text.length || text[i] != c) throw error("Expected '$c'")
        i++
    }

    private fun expectWord(word: String) {
        if (!text.startsWith(word, i)) throw error("Expected $word")
        i += word.length
        if (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '$')) {
            throw error("Unexpected identifier after $word")
        }
    }

    private fun peek(c: Char): Boolean {
        skip()
        return i < text.length && text[i] == c
    }

    private fun error(msg: String) = Json5ParseException("$msg at offset $i", i)

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
