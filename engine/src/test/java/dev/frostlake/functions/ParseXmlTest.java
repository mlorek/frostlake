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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PARSE_XML against the live-verified Snowflake XML-in-VARIANT model: the value is an ordinary
 * variant OBJECT shaped {@code {"$": content, "@": tag, "@attr": value, "childname": index}} whose
 * XML-ness is structural and TYPEOF reports XML. Stringification renders leaf elements on one line
 * and PRETTY-prints elements that contain child elements (newline + 2-space indent per level).
 */
public class ParseXmlTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private String text(final String sql) {
        final Object v = scalar(sql);
        return v == null ? null : v.toString();
    }

    @Test
    public void rendersXmlTextAndReportsTypeXml() {
        // A leaf element stays on one line; an element with child elements pretty-prints.
        assertEquals("<test>22</test>", text("SELECT PARSE_XML('<test>22</test>')"));
        assertEquals("XML", text("SELECT TYPEOF(PARSE_XML('<test>22</test>'))"));
        // XML renders COMPACT — Snowflake's indentation follows JSON_INDENT, and the engine has
        // no such parameter, so the compact form is its canonical rendering (matches TO_XML).
        assertEquals("<a><b>1</b></a>", text("SELECT PARSE_XML('<a><b>1</b></a>')::VARCHAR"));
    }

    @Test
    public void modelKeepsDollarAtAndAttributeMembers() {
        assertEquals("{\"$\":\"x\",\"@\":\"a\",\"@attr1\":\"v1\",\"@attr2\":2}",
            text("SELECT TO_JSON(PARSE_XML('<a attr1=\"v1\" attr2=\"2\">x</a>'))"),
            "attributes are @-prefixed members and auto-convert to numbers");
        assertEquals("{\"$\":\"\",\"@\":\"a\"}", text("SELECT TO_JSON(PARSE_XML('<a/>'))"));
        assertEquals("<a></a>", text("SELECT PARSE_XML('<a></a>')"));
    }

    @Test
    public void childElementsCarryFirstOccurrenceIndexMembers() {
        assertEquals("{\"$\":{\"$\":1,\"@\":\"b\"},\"@\":\"a\",\"b\":0}",
            text("SELECT TO_JSON(PARSE_XML('<a><b>1</b></a>'))"));
        assertEquals("{\"$\":[{\"$\":1,\"@\":\"b\"},{\"$\":2,\"@\":\"c\"}],\"@\":\"a\",\"b\":0,\"c\":1}",
            text("SELECT TO_JSON(PARSE_XML('<a><b>1</b><c>2</c></a>'))"));
        assertEquals("{\"$\":[\"text\",{\"$\":1,\"@\":\"b\"},\"tail\"],\"@\":\"a\",\"b\":1}",
            text("SELECT TO_JSON(PARSE_XML('<a>text<b>1</b>tail</a>'))"),
            "the index counts text pieces too");
    }

    @Test
    public void autoConvertFollowsSnowflakeRules() {
        assertEquals("{\"$\":22,\"@\":\"a\"}", text("SELECT TO_JSON(PARSE_XML('<a> 22 </a>'))"),
            "text is trimmed before conversion");
        assertEquals("INTEGER", text("SELECT TYPEOF(PARSE_XML('<a>22</a>'):\"$\")"));
        assertEquals("{\"$\":\"007\",\"@\":\"a\"}", text("SELECT TO_JSON(PARSE_XML('<a>007</a>'))"),
            "leading zeros keep the text");
        assertEquals("{\"$\":\".5\",\"@\":\"a\"}", text("SELECT TO_JSON(PARSE_XML('<a>.5</a>'))"));
        assertEquals("{\"$\":\"+7\",\"@\":\"a\"}", text("SELECT TO_JSON(PARSE_XML('<a>+7</a>'))"));
        assertEquals("{\"$\":-0.5,\"@\":\"a\"}", text("SELECT TO_JSON(PARSE_XML('<a>-0.5</a>'))"));
        assertEquals("{\"$\":true,\"@\":\"a\"}", text("SELECT TO_JSON(PARSE_XML('<a>true</a>'))"),
            "boolean text is sniffed");
        assertEquals("{\"$\":99999999999999999999999999,\"@\":\"a\"}",
            text("SELECT TO_JSON(PARSE_XML('<a>99999999999999999999999999</a>'))"));
        // DECIMAL keeps its scale in the model (Snowflake renders the XML display as 1.0 too), and
        // node-based path extraction preserves it: TYPEOF of the extracted member is DECIMAL,
        // exactly as live Snowflake reports.
        assertEquals("<a>1.0</a>", text("SELECT PARSE_XML('<a>1.0</a>')"));
        assertEquals("DECIMAL", text("SELECT TYPEOF(PARSE_XML('<a>1.0</a>'):\"$\")"));
        // Exponent forms become DOUBLE (Snowflake renders its own scientific display text; the
        // engine renders the double's plain text — same value, same TYPEOF).
        assertEquals("DOUBLE", text("SELECT TYPEOF(PARSE_XML('<a>1e5</a>'):\"$\")"));
    }

    @Test
    public void disableAutoConvertKeepsText() {
        assertEquals("{\"$\":\"22\",\"@\":\"a\"}", text("SELECT TO_JSON(PARSE_XML('<a>22</a>', TRUE))"));
        assertEquals("VARCHAR", text("SELECT TYPEOF(PARSE_XML('<a>22</a>', TRUE):\"$\")"));
        assertEquals("{\"$\":22,\"@\":\"a\"}", text("SELECT TO_JSON(PARSE_XML('<a>22</a>', FALSE))"));
    }

    @Test
    public void escapesEntitiesAndCdata() {
        assertEquals("<a>&lt;&amp;&gt;</a>", text("SELECT PARSE_XML('<a>&lt;&amp;&gt;</a>')"));
        assertEquals("<a>hi &lt;b&gt; &amp;amp;</a>",
            text("SELECT PARSE_XML('<a><![CDATA[hi <b> &amp;]]></a>')"),
            "CDATA content is literal text and re-escapes on render");
    }

    @Test
    public void prologCommentsDoctypeAndNamespacesAreHandled() {
        assertEquals("<a>1</a>", text("SELECT PARSE_XML('<?xml version=\"1.0\" encoding=\"UTF-8\"?><a>1</a>')"));
        assertEquals("{\"$\":{\"$\":1,\"@\":\"b\"},\"@\":\"a\",\"b\":0}",
            text("SELECT TO_JSON(PARSE_XML('<a><!-- comment --><b>1</b></a>'))"));
        assertEquals("<a>1</a>", text("SELECT PARSE_XML('<!DOCTYPE a []><a>1</a>')"));
        assertEquals("{\"$\":1,\"@\":\"x:a\",\"@xmlns:x\":\"urn:foo\"}",
            text("SELECT TO_JSON(PARSE_XML('<x:a xmlns:x=\"urn:foo\">1</x:a>'))"),
            "prefixes and xmlns stay verbatim (no namespace processing)");
    }

    @Test
    public void nullAndEmptyInputsYieldSqlNull() {
        assertNull(scalar("SELECT PARSE_XML(NULL)"));
        assertNull(scalar("SELECT PARSE_XML('')"));
        assertNull(scalar("SELECT PARSE_XML('   ')"));
    }

    @Test
    public void malformedXmlRaisesParseError() {
        final RuntimeException notXml = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_XML('not xml')");
            }
        });
        assertTrue(notXml.getMessage().contains("Error parsing XML"), notXml.getMessage());
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_XML('<a><b></a>')");
            }
        });
    }

    @Test
    public void xmlShapeIsStructuralEvenFromParseJson() {
        assertEquals("<b>1</b>", text("SELECT PARSE_JSON('{\"$\":1,\"@\":\"b\"}')"));
        assertEquals("XML", text("SELECT TYPEOF(PARSE_JSON('{\"$\":1,\"@\":\"b\"}'))"));
    }

    @Test
    public void pathAccessSeesTheModel() {
        // Path extraction yields VARIANT values; ::VARCHAR unwraps a variant string to its bare
        // text (the SF-portable form — an uncast variant string displays quoted).
        assertEquals("x", text("SELECT PARSE_XML('<a>x</a>'):\"$\"::VARCHAR"));
        assertEquals("a", text("SELECT GET(PARSE_XML('<a>x</a>'), '@')::VARCHAR"));
        assertEquals("<b>1</b>", text("SELECT GET(PARSE_XML('<a><b>1</b></a>'), '$')"),
            "a single element child is itself XML-shaped and renders as XML");
    }

    @Test
    public void xmlValuesRoundTripThroughTableStorage() {
        engine.execute("CREATE TABLE xml_t (v VARIANT)");
        engine.execute("INSERT INTO xml_t SELECT PARSE_XML('<r a=\"1\"><b>2</b></r>')");
        assertEquals("<r a=\"1\"><b>2</b></r>", text("SELECT v FROM xml_t"));
        assertEquals("XML", text("SELECT TYPEOF(v) FROM xml_t"));
    }
}
