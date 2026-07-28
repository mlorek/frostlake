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

package dev.frostlake.features;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NestedObjectAccessTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testDeepNestedObjectColonDotAccess() {
        engine.execute("CREATE TABLE obj3 (d VARIANT)");
        engine.execute("INSERT INTO obj3 VALUES ('{\"a\": {\"b\": {\"c\": 1}}}')");
        ResultSet rs = engine.executeQuery("SELECT d:a.b.c AS e FROM obj3");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        Object val = rs.getRows().get(0).getValue(0);
        assertNotNull(val, "d:a.b.c should return 1");
        assertEquals(1L, Long.parseLong(val.toString()));
    }

    @Test
    public void testSingleColonAccess() {
        ResultSet rs = engine.executeQuery(
            "WITH t AS (SELECT {'x': 42} AS d) SELECT d:x AS v FROM t");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(42L, Long.parseLong(rs.getRows().get(0).getValue(0).toString()));
    }

    @Test
    public void testMixedColonDotPaths() {
        engine.execute("CREATE TABLE obj_mixed (d VARIANT)");
        engine.execute("INSERT INTO obj_mixed VALUES ('{\"a\": {\"b\": {\"c\": 1}}}')");
        // d:a.b.c, d:a:b:c, d:a.b:c should all return 1
        ResultSet rs = engine.executeQuery("SELECT d:a.b.c AS e1, d:a:b:c AS e2, d:a.b:c AS e3 FROM obj_mixed");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, Long.parseLong(rs.getRows().get(0).getValue(0).toString()));
        assertEquals(1L, Long.parseLong(rs.getRows().get(0).getValue(1).toString()));
        assertEquals(1L, Long.parseLong(rs.getRows().get(0).getValue(2).toString()));
    }

    @Test
    public void testSingleColonAccessFromTable() {
        engine.execute("CREATE TABLE jt (d VARIANT)");
        engine.execute("INSERT INTO jt VALUES ('{\"x\": 42}')");
        ResultSet rs = engine.executeQuery("SELECT d:x AS v FROM jt");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(42L, Long.parseLong(rs.getRows().get(0).getValue(0).toString()));
    }

    @Test
    public void testDotChainFromTable() {
        engine.execute("CREATE TABLE jt2 (d VARIANT)");
        engine.execute("INSERT INTO jt2 VALUES ('{\"a\": {\"b\": \"hello\"}}')");
        ResultSet rs = engine.executeQuery("SELECT d:a.b AS v FROM jt2");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("hello", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void testCteRawValue() {
        // Verify CTE stores the JSON object
        ResultSet rs = engine.executeQuery("WITH t AS (SELECT {'a': {'b': 'hello'}} AS d) SELECT d FROM t");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertNotNull(rs.getRows().get(0).getValue(0), "CTE JSON column should not be null");
    }

    @Test
    public void testTwoLevelDotAccess() {
        // Use a real table instead of CTE to avoid CTE projection issues
        engine.execute("CREATE TABLE obj2 (d VARIANT)");
        engine.execute("INSERT INTO obj2 VALUES ('{\"a\": {\"b\": \"hello\"}}')");
        ResultSet rs = engine.executeQuery("SELECT d:a.b AS v FROM obj2");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("hello", rs.getRows().get(0).getValue(0).toString());
    }

    // ---- an object-returning VALUE inside an object/array literal must NEST, not stringify ----
    // Previously {'k': OBJECT_CONSTRUCT(...)} serialized the value as an unescaped quoted string
    // ({"k": "{"a":1}"}), producing malformed JSON so any downstream OBJECT_* / path access returned NULL.

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void objectLiteralNestsObjectConstructValue() {
        assertEquals("{\"result\":{\"a\":1},\"x\":2}",
            String.valueOf(scalar("SELECT {'result': OBJECT_CONSTRUCT('a', 1), 'x': 2}")));
    }

    @Test
    public void objectLiteralNestsParseJsonValue() {
        assertEquals("{\"result\":{\"a\":1},\"x\":2}",
            String.valueOf(scalar("SELECT {'result': PARSE_JSON('{\"a\":1}'), 'x': 2}")));
    }

    @Test
    public void nestedObjectValueIsPathAccessible() {
        // The nested object is real JSON, so a variant path into it resolves (was NULL when malformed).
        assertEquals("1", String.valueOf(scalar("SELECT ({'result': OBJECT_CONSTRUCT('a', 1)}):result:a")));
    }

    @Test
    public void objectInsertOverNestedObjectValueIsNotNull() {
        // OBJECT_INSERT over an object whose value was an OBJECT_CONSTRUCT must succeed (used to return NULL
        // because the object was malformed) — this is the exact shape that nulled the loader stats object.
        final Object v = scalar(
            "SELECT OBJECT_INSERT({'result': OBJECT_CONSTRUCT('a', 1), 'p': null}, 'p', 5)");
        assertNotNull(v);
        assertTrue(String.valueOf(v).contains("\"result\":{\"a\":1}"), String.valueOf(v));
        assertTrue(String.valueOf(v).contains("\"p\":5"), String.valueOf(v));
    }

    @Test
    public void arrayLiteralNestsObjectValues() {
        assertEquals("[{\"a\":1},{\"b\":2}]",
            String.valueOf(scalar("SELECT [OBJECT_CONSTRUCT('a', 1), OBJECT_CONSTRUCT('b', 2)]")));
    }

    // ---- quoted path segments: x:"source".y names the field `source` ----
    // The surrounding quotes were kept in the lookup key, so the whole path silently resolved to NULL
    // (a loader concatenated x:value:"source".ApiInfo.kind into a NOT NULL column and died).

    @Test
    public void quotedFirstSegmentEqualsUnquoted() {
        engine.execute("CREATE TABLE qseg (d VARIANT)");
        engine.execute("INSERT INTO qseg VALUES ('{\"source\": {\"a\": 7}}')");
        final ResultSet rs = engine.executeQuery("SELECT d:\"source\".a AS q, d:source.a AS u FROM qseg");
        assertEquals("7", String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals("7", String.valueOf(rs.getRows().get(0).getValue(1)));
    }

    @Test
    public void quotedMiddleSegmentInColonDotChain() {
        // The loader shape: src:value:"source".ApiInfo.kind::VARCHAR.
        engine.execute("CREATE TABLE qmid (src VARIANT)");
        engine.execute("INSERT INTO qmid VALUES ('{\"value\": {\"source\": {\"ApiInfo\": {\"kind\": \"AGENT\"}}}}')");
        assertEquals("AGENT",
            String.valueOf(scalar("SELECT src:value:\"source\".ApiInfo.kind::VARCHAR FROM qmid")));
    }

    @Test
    public void quotedSegmentWithEmbeddedDot() {
        // Quoting exists so a key containing a dot is one segment, not a two-level path.
        engine.execute("CREATE TABLE qdot (d VARIANT)");
        engine.execute("INSERT INTO qdot VALUES ('{\"a.b\": 3, \"a\": {\"b\": 9}}')");
        assertEquals("3", String.valueOf(scalar("SELECT d:\"a.b\" FROM qdot")));
        assertEquals("9", String.valueOf(scalar("SELECT d:a.b FROM qdot")));
    }

    @Test
    public void quotedSegmentPreservesCase() {
        engine.execute("CREATE TABLE qcase (d VARIANT)");
        engine.execute("INSERT INTO qcase VALUES ('{\"Key\": 1}')");
        assertEquals("1", String.valueOf(scalar("SELECT d:\"Key\" FROM qcase")));
    }

    @Test
    public void statsObjectThreadedThroughProcStaysNonNull() {
        // The loader flow in miniature: a UDF builds a stats object with a nested object-returning value
        // ('result'), a proc OBJECT_INSERTs into it, and the caller threads it via ':= (CALL ...)'. The
        // stats object must survive non-null (it went NULL because the UDF's object was malformed JSON).
        engine.execute("CREATE OR REPLACE FUNCTION errmsg() RETURNS OBJECT AS $$ OBJECT_CONSTRUCT('err_code', 0) $$");
        engine.execute("CREATE OR REPLACE FUNCTION statinit() RETURNS OBJECT AS $$ {'procedure_arguments': null, 'result': errmsg()} $$");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE add_args(stats OBJECT, proc_args ARRAY) RETURNS OBJECT LANGUAGE SQL AS $$
            BEGIN RETURN (OBJECT_INSERT(:stats, 'procedure_arguments', :proc_args)); END $$""");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE run_flow() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE stats OBJECT;
            BEGIN
              stats := (statinit());
              stats := (CALL add_args(:stats, [ {'name': 'source', 'value': 'stream'} ]));
              RETURN IFF(:stats IS NULL, 'NULL', 'OK:' || :stats:procedure_arguments[0]:name::VARCHAR);
            END $$""");
        assertEquals("OK:source", String.valueOf(scalar("CALL run_flow()")));
    }
}
