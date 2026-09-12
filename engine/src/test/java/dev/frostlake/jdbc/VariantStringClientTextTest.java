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

package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.http.ResultSetData;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A semi-structured cell reaches a client as its JSON text, as the account's driver hands it back: a
 * string inside a VARIANT keeps its quotes, whether the VARIANT is the whole value or a path, an element
 * or a FLATTEN value read out of one, while a cast to VARCHAR reads bare and a container keeps the
 * session's JSON_INDENT layout. The in-process driver and the HTTP wire agree. Every cell is live-verified.
 */
public class VariantStringClientTextTest extends BaseJdbcTest {

    /** Every cell of the first row through getString, a comma between cells. */
    private String firstRow(final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            final StringBuilder out = new StringBuilder();
            if (rs.next()) {
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    if (i > 1) {
                        out.append(", ");
                    }
                    out.append(rs.getString(i));
                }
            }
            return out.toString();
        }
    }

    @Test
    public void aVariantStringKeepsItsQuotes() throws SQLException {
        assertEquals("\"abc\"", firstRow("SELECT TO_VARIANT('abc')"));
        assertEquals("\"a\\\"b\"", firstRow("SELECT TO_VARIANT('a\"b')"));
        assertEquals("\"\"", firstRow("SELECT TO_VARIANT('')"));
        assertEquals("\"x\"", firstRow("SELECT PARSE_JSON('\"x\"')"));
        assertEquals("\"ü\"", firstRow("SELECT TO_VARIANT('ü')"));
        assertEquals("1, 1.5, true, null",
            firstRow("SELECT TO_VARIANT(1), TO_VARIANT(1.5), TO_VARIANT(TRUE), PARSE_JSON('null')"));
    }

    @Test
    public void aStringReadOutOfAVariantKeepsItsQuotesToo() throws SQLException {
        assertEquals("\"v\"", firstRow("SELECT OBJECT_CONSTRUCT('k', 'v'):k"));
        assertEquals("\"a\"", firstRow("SELECT GET(ARRAY_CONSTRUCT('a'), 0)"));
        assertEquals("\"q\"", firstRow("SELECT value FROM TABLE(FLATTEN(PARSE_JSON('[\"q\"]')))"));
        assertEquals("\"a\", {\"n\":\"x\"}",
            firstRow("SELECT ARRAY_CONSTRUCT('a')[0], OBJECT_CONSTRUCT('k', OBJECT_CONSTRUCT('n', 'x')):k"));
        assertEquals("abc, v", firstRow("SELECT TO_VARIANT('abc')::VARCHAR, OBJECT_CONSTRUCT('k', 'v'):k::VARCHAR"));
    }

    @Test
    public void columnsAndPathsOverThemReadAsJsonText() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE t (v VARIANT, o OBJECT, a ARRAY)");
        statement.execute("INSERT INTO t SELECT TO_VARIANT('abc'), OBJECT_CONSTRUCT('k', 'v'), ARRAY_CONSTRUCT('a', 1)");
        assertEquals("\"abc\", {\"k\":\"v\"}, [\"a\",1]", firstRow("SELECT v, o, a FROM t"));
        assertEquals("\"v\", \"a\", 1", firstRow("SELECT o:k, a[0], a[1] FROM t"));
        try (ResultSet rs = statement.executeQuery("SELECT o:k FROM t")) {
            rs.next();
            assertEquals("\"v\"", rs.getObject(1));
        }
    }

    /** A container keeps the session's JSON_INDENT layout, which a string's quotes do not touch. */
    @Test
    public void aContainerFollowsTheSessionIndent() throws SQLException {
        try {
            statement.execute("ALTER SESSION SET JSON_INDENT = 2");
            assertEquals("[\n  1,\n  2\n]", firstRow("SELECT ARRAY_CONSTRUCT(1, 2)"));
            assertEquals("{\n  \"k\": \"v\"\n}", firstRow("SELECT OBJECT_CONSTRUCT('k', 'v')"));
            assertEquals("\"abc\", \"v\"", firstRow("SELECT TO_VARIANT('abc'), OBJECT_CONSTRUCT('k', 'v'):k"));
        } finally {
            statement.execute("ALTER SESSION SET JSON_INDENT = 0");
        }
    }

    /** The HTTP wire carries the same text for a path result, where it used to send the bare content. */
    @Test
    public void theHttpWireCarriesTheSameText() {
        assumeFalse(LiveSnowflake.enabled(), "the engine's own wire has no live counterpart");
        final ResultSetData data = ResultSetData.from(sharedEngine.executeQuery(
            "SELECT OBJECT_CONSTRUCT('k', 'v'):k, TO_VARIANT('abc'), PARSE_JSON('null'), TO_VARIANT('abc')::VARCHAR"));
        assertEquals("\"v\"", data.getRows().get(0).get(0));
        assertEquals("\"abc\"", data.getRows().get(0).get(1));
        assertEquals("null", data.getRows().get(0).get(2));
        assertEquals("abc", data.getRows().get(0).get(3));
    }
}
