/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What is wrong with an XML document, in Snowflake's words — and the ONE document it accepts that a
 * strict parser will not.
 *
 * <p>★ TWO SURFACES, ONE VOCABULARY. CHECK_XML RETURNS the sentence and NULL for a sound document;
 * PARSE_XML RAISES the same sentence behind {@code Error parsing XML:}. Every cell below was measured
 * on both and they agree, so one reader serves both — which is also why a fault must be decided
 * before any parse is attempted rather than translated from whatever a parser happened to say.
 *
 * <p>★ THE ANCHOR IS PER-SENTENCE, and no two are alike: an unclosed document reports at its last
 * character, a mismatched closer at its own end, an orphan closer at the SLASH — {@code </a>} and
 * {@code </abcdef>} both say pos 2, so the tag's length plays no part — and content before any
 * element always says pos 1. Two whole-document faults carry no position at all.
 *
 * <p>★ A CLOSER THAT MATCHES SOMETHING DEEPER IS NOT AN ORPHAN. {@code <a><b></a>} does not complain
 * about {@code </a>}; it reports that {@code </b>} went missing. Only a name open nowhere gets the
 * orphan sentence, and telling the two apart is the whole content of that pair.
 *
 * <p>★ AND ONE ACCEPTANCE, not a wording change at all: an UNQUOTED attribute value is legal, and the
 * document comes back with the quotes supplied.
 */
public class XmlFaultVocabularyTest extends BaseDatabaseTest {

    /** A document as a SQL string literal: its own quotes doubled, so the document reaches the reader. */
    private String literal(final String document) {
        return document.replace("'", "''");
    }

    /** CHECK_XML's answer: the sentence, or null for a sound document. */
    private String checked(final String document) {
        final ResultSet rs = engine.executeQuery("SELECT CHECK_XML('" + literal(document) + "')");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** PARSE_XML's answer: the document's model, or its refusal. */
    private String parsed(final String document) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT PARSE_XML('" + literal(document) + "')");
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ The same sentence on both surfaces — the pairing that makes one reader enough. */
    @Test
    public void bothsurfacesSpeakOneVocabulary() {
        assertEquals("missing closing tags: </a>, pos 3", checked("<a>"));
        assertEquals("Error parsing XML: missing closing tags: </a>, pos 3", parsed("<a>"),
            "PARSE_XML is the same sentence behind its own prefix");
        assertEquals("null", checked("<a></a>"), "and a sound document is NULL, not a sentence");
    }

    /** ★ An unclosed document: reported at its last character, every open tag listed innermost first. */
    @Test
    public void anunclosedDocumentListsItsOpenTags() {
        assertEquals("missing closing tags: </a>, pos 4", checked("<a>x"));
        assertEquals("missing closing tags: </a>, pos 9", checked("<a b=\'1\'>"));
        assertEquals("missing closing tags: </a>, pos 5", checked("  <a>"),
            "leading whitespace is COUNTED, as it is in the JSON reader");
        assertEquals("missing closing tags: </c></b></a>, pos 9", checked("<a><b><c>"),
            "★ innermost first — the order is the stack's, not the document's");
    }

    /** ★ A closer matching something deeper reports the tags ABOVE it, not itself. */
    @Test
    public void amismatchedCloserNamesWhatWentUnclosed() {
        assertEquals("missing closing tags: </b>, pos 10", checked("<a><b></a></b>"),
            "★ </a> is not the complaint — </b> is what never closed");
        assertEquals("no opening tag for </b>, pos 7", checked("<a></b>"),
            "and a name open NOWHERE is the orphan instead");
    }

    /** ★ The orphan closer anchors on the SLASH, whatever the tag's length. */
    @Test
    public void anorphanCloserAnchorsOnTheSlash() {
        assertEquals("closing tag with no opening tags, pos 2", checked("</a>"));
        assertEquals("closing tag with no opening tags, pos 2", checked("</abcdef>"),
            "★ the same position for a longer name — the tag's end plays no part");
        assertEquals("closing tag with no opening tags, pos 4", checked("  </a>"));
        assertEquals("closing tag with no opening tags, pos 9", checked("<a></a></b>"));
    }

    /** The malformed-tag pair, and content that is no element at all. */
    @Test
    public void themalformedTagSentences() {
        assertEquals("missing tag name after <, pos 2", checked("<>"));
        assertEquals("missing tag name after <, pos 4", checked("  <>"));
        assertEquals("prematurely terminated XML document in a tag, pos 1", checked("<"));
        assertEquals("prematurely terminated XML document in a tag, pos 3", checked("  <"));
        assertEquals("not an XML element, pos 1", checked("abc"));
        assertEquals("not an XML element, pos 1", checked("x<a></a>"),
            "★ always pos 1, wherever the content ends");
    }

    /** ★ The two whole-document faults, which carry NO position. */
    @Test
    public void thepositionlessSentences() {
        assertEquals("more than one document in the input", checked("<a/><b/>"));
        assertEquals("garbage after valid input document", checked("<a/>junk"));
    }

    /** ★ A multi-line document gets a line clause, exactly as the JSON reader gives one. */
    @Test
    public void amultiLineDocumentNamesItsLine() {
        assertEquals("missing closing tags: </b></a>, line 2, pos 3", checked("<a>\n<b>"));
        assertEquals("missing closing tags: </b>, line 2, pos 7", checked("<a>\n<b></a></b>"));
        assertEquals("no opening tag for </b>, line 2, pos 4", checked("<a>\n</b>"));
        assertEquals("garbage after valid input document", checked("<a/>\njunk"),
            "and the positionless sentence stays positionless across lines");
    }

    /** ★ AN UNQUOTED ATTRIBUTE VALUE IS LEGAL — an acceptance, not a wording. */
    @Test
    public void anunquotedAttributeValueIsAccepted() {
        assertEquals("null", checked("<a b=1></a>"));
        assertEquals("<a b=\"1\"></a>", parsed("<a b=1></a>"),
            "★ and the quotes are SUPPLIED — the document comes back quoted");
        assertEquals("<a b=\"1\" c=\"2\"></a>", parsed("<a b=1 c=2></a>"));
        assertEquals("<a b=\"1\"></a>", parsed("<a b=1/>"),
            "a self-closing tag with one is expanded the same way");
    }

    /** The attribute faults, each with its own sentence. */
    @Test
    public void theattributeFaults() {
        assertEquals("bad character in attribute name: '2', pos 8", checked("<a b=1 2></a>"),
            "a second attribute whose name cannot start one");
        assertEquals("missing attribute value, pos 5", checked("<a b></a>"));
        assertEquals("missing attribute value, pos 6", checked("<a b=></a>"));
    }

    /** An entity the document never declared, reported at its semicolon. */
    @Test
    public void anunknownEntityIsNamed() {
        assertEquals("unknown entity &nope;, pos 9", checked("<a>&nope;</a>"));
        assertEquals("unknown entity &nope;, pos 11", checked("<a>zz&nope;</a>"));
        assertEquals("null", checked("<a>&amp;</a>"), "a known one passes");
    }

    /** The sound documents, unchanged — comments, CDATA and entities all still parse as before. */
    @Test
    public void thesoundDocumentsAreUntouched() {
        assertEquals("<a><b>1</b></a>", parsed("<a><b>1</b></a>"));
        assertEquals("<a></a>", parsed("<!-- c --><a></a>"));
        assertEquals("<a>x</a>", parsed("<a><![CDATA[x]]></a>"));
        assertEquals("<a>&amp;</a>", parsed("<a>&amp;</a>"));
    }
}
