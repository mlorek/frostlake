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
 * TO_XML: compact XML for XML-shaped variants, Snowflake's {@code <SnowflakeData type="...">}
 * scheme for everything else (all live-verified), and compile-time rejection of VARCHAR arguments.
 */
public class ToXmlTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private String text(final String sql) {
        final Object v = scalar(sql);
        return v == null ? null : v.toString();
    }

    @Test
    public void xmlVariantsRenderCompactXml() {
        assertEquals("<a attr=\"1\"><b>x</b><c>2</c></a>",
            text("SELECT TO_XML(PARSE_XML('<a attr=\"1\"><b>x</b><c>2</c></a>'))"));
        assertEquals("<a>text<b>1</b>tail</a>",
            text("SELECT TO_XML(PARSE_XML('<a>text<b>1</b>tail</a>'))"));
        assertEquals("<a></a>", text("SELECT TO_XML(PARSE_XML('<a/>'))"));
        assertEquals("<a b=\"2\" z=\"1\"></a>", text("SELECT TO_XML(PARSE_XML('<a z=\"1\" b=\"2\"/>'))"),
            "attributes render sorted by name");
        assertEquals("<a>&lt;&amp;&gt;</a>", text("SELECT TO_XML(PARSE_XML('<a>&lt;&amp;&gt;</a>'))"));
    }

    @Test
    public void jsonVariantsUseTheSnowflakeDataScheme() {
        assertEquals("<SnowflakeData type=\"OBJECT\">"
                + "<a type=\"INTEGER\">1</a>"
                + "<b type=\"ARRAY\"><e type=\"INTEGER\">1</e><e type=\"INTEGER\">2</e></b>"
                + "<c type=\"VARCHAR\">x</c>"
                + "</SnowflakeData>",
            text("SELECT TO_XML(PARSE_JSON('{\"a\":1,\"b\":[1,2],\"c\":\"x\"}'))"));
        assertEquals("<SnowflakeData type=\"ARRAY\"><e type=\"INTEGER\">1</e><e type=\"INTEGER\">2</e></SnowflakeData>",
            text("SELECT TO_XML(PARSE_JSON('[1,2]'))"));
        assertEquals("<SnowflakeData type=\"VARCHAR\">str</SnowflakeData>",
            text("SELECT TO_XML(PARSE_JSON('\"str\"'))"));
        assertEquals("<SnowflakeData type=\"INTEGER\">1</SnowflakeData>",
            text("SELECT TO_XML(PARSE_JSON('1'))"));
        assertEquals("<SnowflakeData type=\"NULL_VALUE\" xsi:nil=\"true\"/>",
            text("SELECT TO_XML(PARSE_JSON('null'))"));
        assertEquals("<SnowflakeData type=\"DECIMAL\">3.14</SnowflakeData>",
            text("SELECT TO_XML(TO_VARIANT(3.14))"));
        assertEquals("<SnowflakeData type=\"BOOLEAN\">true</SnowflakeData>",
            text("SELECT TO_XML(TO_VARIANT(TRUE))"));
        assertEquals("<SnowflakeData type=\"OBJECT\"><a type=\"OBJECT\"><b type=\"INTEGER\">1</b></a></SnowflakeData>",
            text("SELECT TO_XML(PARSE_JSON('{\"a\":{\"b\":1}}'))"));
        assertEquals("<SnowflakeData type=\"OBJECT\"><a type=\"NULL_VALUE\" xsi:nil=\"true\"/></SnowflakeData>",
            text("SELECT TO_XML(PARSE_JSON('{\"a\":null}'))"));
    }

    @Test
    public void xmlNestedInsideJsonRendersWithTypeXml() {
        assertEquals("<SnowflakeData type=\"OBJECT\"><k type=\"XML\"><a>1</a></k></SnowflakeData>",
            text("SELECT TO_XML(OBJECT_CONSTRUCT('k', PARSE_XML('<a>1</a>')))"));
    }

    @Test
    public void nullInAndVarcharRejection() {
        assertNull(scalar("SELECT TO_XML(NULL)"));
        final RuntimeException rejected = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_XML('plain')");
            }
        });
        assertTrue(rejected.getMessage().contains("Invalid argument types for function 'TO_XML'"),
            rejected.getMessage());
    }
}
