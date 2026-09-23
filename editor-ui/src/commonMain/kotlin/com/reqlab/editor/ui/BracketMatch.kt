package com.reqlab.editor.ui

private val BRACKET_OPEN = mapOf('(' to ')', '[' to ']', '{' to '}')
private val BRACKET_CLOSE = mapOf(')' to '(', ']' to '[', '}' to '{')

/** Scan at most this many chars from the caret so a 10 MB one-liner cannot hitch. */
internal const val BRACKET_SCAN_LIMIT = 65_536

/**
 * Offsets of the bracket under/before [cursor] and its match, or null.
 * Skips quoted strings. XML tags are not matched (brace pairs only).
 */
internal fun matchingBracketOffsets(
    text: String,
    cursor: Int,
    maxScan: Int = BRACKET_SCAN_LIMIT,
): Pair<Int, Int>? {
    if (text.isEmpty()) return null
    val len = text.length
    val c = cursor.coerceIn(0, len)
    val idx = when {
        c < len && isBracket(text[c]) -> c
        c > 0 && isBracket(text[c - 1]) -> c - 1
        else -> return null
    }
    val ch = text[idx]
    val closer = BRACKET_OPEN[ch]
    if (closer != null) {
        val found = scanBracket(text, idx, 1, ch, closer, maxScan) ?: return null
        return idx to found
    }
    val opener = BRACKET_CLOSE[ch] ?: return null
    val found = scanBracket(text, idx, -1, ch, opener, maxScan) ?: return null
    return found to idx
}

private fun isBracket(ch: Char) = ch in BRACKET_OPEN || ch in BRACKET_CLOSE

private fun scanBracket(
    text: String,
    from: Int,
    dir: Int,
    origin: Char,
    target: Char,
    maxScan: Int,
): Int? {
    var depth = 1
    var i = from + dir
    var scanned = 0
    var inString = false
    var stringDelim = '\u0000'
    var escape = false
    while (i in text.indices && scanned < maxScan) {
        val ch = text[i]
        if (inString) {
            when {
                escape -> escape = false
                ch == '\\' -> escape = true
                ch == stringDelim -> inString = false
            }
        } else {
            when (ch) {
                '"', '\'' -> {
                    inString = true
                    stringDelim = ch
                }
                origin -> depth++
                target -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        i += dir
        scanned++
    }
    return null
}

/** Columns (0-based) at which to draw indent guides for [line]. */
internal fun indentGuideColumns(line: String, tabWidth: Int = 2): List<Int> {
    if (tabWidth <= 0) return emptyList()
    val n = line.indexOfFirst { it != ' ' }.let { if (it < 0) line.length else it }
    if (n < tabWidth) return emptyList()
    return (tabWidth until n step tabWidth).toList()
}
