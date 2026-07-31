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
 * XMLGET(xml, tag [, instance]): the 0-based instance-th direct child element with the given tag,
 * NULL when absent / out of range / negative / not an XML value; matching is case-sensitive — all
 * live-verified against Snowflake.
 */
public class XmlGetTest extends BaseDatabaseTest {

    private static final String DOC = "PARSE_XML('<a><b>1</b><c>2</c><b>3</b></a>')";

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private String text(final String sql) {
        final Object v = scalar(sql);
        return v == null ? null : v.toString();
    }

    @Test
    public void returnsTheFirstMatchByDefaultAndByInstance() {
        assertEquals("<b>1</b>", text("SELECT XMLGET(" + DOC + ", 'b')"));
        assertEquals("<b>3</b>", text("SELECT XMLGET(" + DOC + ", 'b', 1)"));
        assertNull(scalar("SELECT XMLGET(" + DOC + ", 'b', 2)"));
        assertEquals("XML", text("SELECT TYPEOF(XMLGET(" + DOC + ", 'b'))"));
    }

    @Test
    public void missingTagCaseMismatchAndNegativeInstanceAreNull() {
        assertNull(scalar("SELECT XMLGET(" + DOC + ", 'zz')"));
        assertNull(scalar("SELECT XMLGET(PARSE_XML('<a><B>1</B></a>'), 'b')"),
            "tag matching is case-sensitive");
        assertNull(scalar("SELECT XMLGET(PARSE_XML('<a><b>1</b></a>'), 'b', -1)"));
        assertNull(scalar("SELECT XMLGET(PARSE_XML('<a>txt</a>'), 'b')"),
            "scalar content has no child elements");
    }

    @Test
    public void nonXmlAndNullInputsAreNull() {
        assertNull(scalar("SELECT XMLGET(NULL, 'b')"));
        assertNull(scalar("SELECT XMLGET(PARSE_JSON('{\"b\":1}'), 'b')"),
            "a plain JSON object is not an XML element");
    }

    @Test
    public void singleChildAttributesMixedContentAndNesting() {
        assertEquals("<b>1</b>", text("SELECT XMLGET(PARSE_XML('<a><b>1</b></a>'), 'b')"));
        assertEquals("<b x=\"9\">1</b>", text("SELECT XMLGET(PARSE_XML('<a><b x=\"9\">1</b></a>'), 'b')"));
        assertEquals("<b>1</b>", text("SELECT XMLGET(PARSE_XML('<a>x<b>1</b>y</a>'), 'b')"));
        assertEquals("<c>5</c>",
            text("SELECT XMLGET(XMLGET(PARSE_XML('<a><b><c>5</c></b></a>'), 'b'), 'c')"));
    }

    @Test
    public void contentExtractsThroughThePathSyntax() {
        assertEquals("1", text("SELECT XMLGET(PARSE_XML('<a><b>1</b></a>'), 'b'):\"$\""));
    }

    @Test
    public void varcharFirstArgumentIsRejectedAtCompileTime() {
        final RuntimeException rejected = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT XMLGET('<a><b>1</b></a>', 'b')");
            }
        });
        assertTrue(rejected.getMessage().contains("Invalid argument types for function 'XMLGET'"),
            rejected.getMessage());
    }
}
