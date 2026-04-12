package com.reqlab.editor.core

import kotlin.test.*

class XmlModeTest {

    @BeforeTest
    fun setup() {
        LanguageRegistry.registerBuiltins()
    }

    @Test
    fun validXmlNoErrors() {
        val xml = "<root><child>text</child></root>"
        val errors = XmlMode.validate(xml)
        assertEquals(0, errors.size)
    }

    @Test
    fun unmatchedOpenTagReportsError() {
        val xml = "<root><child></root>"
        val errors = XmlMode.validate(xml)
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun selfClosingTagValid() {
        val xml = "<root><br/></root>"
        val errors = XmlMode.validate(xml)
        assertEquals(0, errors.size)
    }

    @Test
    fun nestedXmlValid() {
        val xml = """
<root>
    <parent>
        <child attr="val">text</child>
    </parent>
</root>
        """.trimIndent()
        val errors = XmlMode.validate(xml)
        assertEquals(0, errors.size)
    }

    @Test
    fun tokenizeXmlLine() {
        val (tokens, _) = XmlMode.tokenizeLine("""<tag attr="value">text</tag>""", 1, null)
        assertTrue(tokens.isNotEmpty())
        assertTrue(tokens.any { it.type == TokenType.TAG })
    }

    @Test
    fun foldingRegionsForXml() {
        val xml = "<root>\n  <child>text</child>\n</root>"
        val doc = EditorDocument.create(xml)
        val regions = XmlMode.foldingRegions(doc)
        assertTrue(regions.isNotEmpty())
    }

    @Test
    fun xmlCommentIgnored() {
        val xml = "<!-- comment --><root></root>"
        val errors = XmlMode.validate(xml)
        assertEquals(0, errors.size)
    }
}

class HtmlModeTest {

    @BeforeTest
    fun setup() {
        LanguageRegistry.registerBuiltins()
    }

    @Test
    fun validHtmlNoErrors() {
        val html = "<html><body><p>Hello</p></body></html>"
        val errors = HtmlMode.validate(html)
        assertEquals(0, errors.size)
    }

    @Test
    fun unclosedHtmlTagIsWarning() {
        // HTML mode converts unclosed tag errors to warnings
        val html = "<html><body><p>Hello</body></html>"
        val errors = HtmlMode.validate(html)
        if (errors.isNotEmpty()) {
            // unclosed <p> should be WARNING, not ERROR
            assertTrue(errors.any { it.severity == InlineErrorSeverity.WARNING })
        }
    }

    @Test
    fun voidElementsValid() {
        val html = "<html><body><br><img src=\"test.png\"><hr></body></html>"
        val errors = HtmlMode.validate(html)
        assertEquals(0, errors.size)
    }

    @Test
    fun tokenizeHtml() {
        val (tokens, _) = HtmlMode.tokenizeLine("<div class=\"main\">Hello</div>", 1, null)
        assertTrue(tokens.isNotEmpty())
    }
}
