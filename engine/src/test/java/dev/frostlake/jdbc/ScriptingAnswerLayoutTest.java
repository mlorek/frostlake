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

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A Snowflake Scripting RETURN's semi-structured answer — a SQL procedure's CALL, an anonymous block run directly or
 * by EXECUTE IMMEDIATE — reads laid out across lines through the driver at the session's JSON_INDENT, at width zero
 * too, where a query's, a function's and a table procedure's values read compact; getObject reads the text getString
 * reads, at any width. Every cell is live-verified.
 */
public class ScriptingAnswerLayoutTest extends BaseJdbcTest {

    private static final String PAIR = "{\n\"a\": 1,\n\"b\": [\n1,\n2\n]\n}";
    private static final String PAIR_AT_FOUR = "{\n    \"a\": 1,\n    \"b\": [\n        1,\n        2\n    ]\n}";
    private static final String COMPACT_PAIR = "{\"a\":1,\"b\":[1,2]}";
    private static final String QUERY = "SELECT PARSE_JSON('{\"a\":1,\"b\":[1,2]}')";
    private static final String BLOCK = "EXECUTE IMMEDIATE $$ BEGIN RETURN PARSE_JSON('{\"a\":1}'); END $$";

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("CREATE OR REPLACE PROCEDURE pv() RETURNS VARIANT LANGUAGE SQL AS"
            + " $$ BEGIN RETURN PARSE_JSON('{\"a\":1,\"b\":[1,2]}'); END $$");
        statement.execute("CREATE OR REPLACE PROCEDURE po() RETURNS OBJECT LANGUAGE SQL AS"
            + " $$ BEGIN RETURN OBJECT_CONSTRUCT('a', 1); END $$");
        statement.execute("CREATE OR REPLACE PROCEDURE pa() RETURNS ARRAY LANGUAGE SQL AS"
            + " $$ BEGIN RETURN ARRAY_CONSTRUCT(1, 2); END $$");
        statement.execute("CREATE OR REPLACE PROCEDURE ps() RETURNS VARIANT LANGUAGE SQL AS"
            + " $$ BEGIN RETURN TO_VARIANT(5); END $$");
        statement.execute("CREATE OR REPLACE PROCEDURE pe() RETURNS VARIANT LANGUAGE SQL AS"
            + " $$ BEGIN RETURN PARSE_JSON('{\"a\":{},\"b\":[],\"c\":\"x\"}'); END $$");
        statement.execute("CREATE OR REPLACE PROCEDURE pstr() RETURNS VARIANT LANGUAGE SQL AS"
            + " $$ BEGIN RETURN TO_VARIANT('x'); END $$");
        statement.execute("CREATE OR REPLACE PROCEDURE pt() RETURNS TABLE (v VARIANT) LANGUAGE SQL AS"
            + " $$ DECLARE r RESULTSET DEFAULT (SELECT PARSE_JSON('{\"a\":1}') AS v); BEGIN RETURN TABLE(r); END $$");
        statement.execute("CREATE OR REPLACE FUNCTION fv() RETURNS VARIANT AS 'PARSE_JSON(''{\"a\":1}'')'");
    }

    /** The first cell through getString and getObject, which must read alike. */
    private String cell(final ResultSet rs, final String sql) throws SQLException {
        rs.next();
        final String text = rs.getString(1);
        assertEquals(text, rs.getObject(1), "getObject of " + sql);
        return text;
    }

    private String read(final String sql) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return cell(rs, sql);
        }
    }

    private String prepared(final String sql) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement(sql); ResultSet rs = p.executeQuery()) {
            return cell(rs, sql);
        }
    }

    private String called(final String sql) throws SQLException {
        try (CallableStatement c = connection.prepareCall(sql)) {
            c.execute();
            try (ResultSet rs = c.getResultSet()) {
                return cell(rs, sql);
            }
        }
    }

    @Test
    public void aScriptingReturnIsLaidOutAtWidthZero() throws SQLException {
        assertEquals(PAIR, read("CALL pv()"));
        assertEquals(PAIR, prepared("CALL pv()"));
        assertEquals(PAIR, called("CALL pv()"));
        assertEquals("{\n\"a\": 1\n}", read("CALL po()"));
        assertEquals("[\n1,\n2\n]", read("CALL pa()"));
        assertEquals("{\n\"a\": {},\n\"b\": [],\n\"c\": \"x\"\n}", read("CALL pe()"));
        assertEquals("5", read("CALL ps()"));
        assertEquals("\"x\"", read("CALL pstr()"));
        assertEquals("{\n\"a\": 1\n}", read("BEGIN RETURN PARSE_JSON('{\"a\":1}'); END"));
        assertEquals("{\n\"a\": 1\n}", read(BLOCK));
        assertEquals(PAIR, read("EXECUTE IMMEDIATE 'CALL pv()'"));
        assertEquals(PAIR, read("DECLARE v VARIANT; BEGIN CALL pv() INTO :v; RETURN v; END"));
        assertEquals("[\n{\n\"k\": 1.500000000000000e+00\n},\n2\n]",
            read("BEGIN RETURN ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('k', 1.5::FLOAT), 2); END"));
    }

    @Test
    public void queriesFunctionsAndReturnedTablesStayCompact() throws SQLException {
        assertEquals(COMPACT_PAIR, read(QUERY));
        assertEquals(COMPACT_PAIR, prepared(QUERY));
        assertEquals(COMPACT_PAIR, called(QUERY));
        assertEquals("{\"a\":1}", read("SELECT fv()"));
        assertEquals("{\"a\":1}", read("CALL pt()"));
        assertEquals("{\"a\":1}", read("SELECT OBJECT_CONSTRUCT('a', 1), ARRAY_CONSTRUCT(1, 2)"));
        assertEquals("{\"a\":1}", read("EXECUTE IMMEDIATE 'SELECT PARSE_JSON(''{\"a\":1}'')'"));
        assertEquals(PAIR, read("CALL pv()"));
        assertEquals(COMPACT_PAIR, read("SELECT \"PV\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
    }

    @Test
    public void aWidthLaysOutEveryValueAndGetObjectReadsIt() throws SQLException {
        try {
            statement.execute("ALTER SESSION SET JSON_INDENT = 4");
            assertEquals(PAIR_AT_FOUR, read("CALL pv()"));
            assertEquals(PAIR_AT_FOUR, called("CALL pv()"));
            assertEquals(PAIR_AT_FOUR, read(QUERY));
            assertEquals("{\n    \"a\": 1\n}", read(BLOCK));
            assertEquals("{\n    \"a\": 1\n}", read("SELECT fv()"));
            assertEquals("{\n    \"a\": 1\n}", read("CALL pt()"));
        } finally {
            statement.execute("ALTER SESSION SET JSON_INDENT = 0");
        }
    }

    /** The HTTP wire carries a scripting answer laid out as the in-process driver reads it. */
    @Test
    public void theHttpWireCarriesTheLaidOutAnswer() {
        assumeFalse(LiveSnowflake.enabled(), "the engine's own wire has no live counterpart");
        assertEquals(PAIR,
            ResultSetData.from(sharedEngine.executeQuery("CALL test_db.PUBLIC.pv()")).getRows().get(0).get(0));
        assertEquals(COMPACT_PAIR, ResultSetData.from(sharedEngine.executeQuery(QUERY)).getRows().get(0).get(0));
    }
}
