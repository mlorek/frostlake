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
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FLATTEN's THIS column is the CONTAINER a row came out of, as a VARIANT — not the input's JSON text.
 *
 * <p>Two things follow, both live-verified. A recursive walk moves THIS down with it: the rows a nested
 * container emits report that nested container, so {@code a.b} over {@code {"a":{"b":1}}} reports
 * {@code {"b":1}} rather than the root. And whatever consumes THIS sees a container rather than a
 * string holding one: ARRAY_AGG nests it, OBJECT_CONSTRUCT embeds it, and a table built over it keeps
 * an OBJECT.
 */
public class FlattenThisTest extends BaseDatabaseTest {

    /** Every row of a query, its cells joined with " | ", in the order the query returned them. */
    private List<String> rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> lines = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < rs.getColumns().size(); i++) {
                if (i > 0) {
                    line.append(" | ");
                }
                line.append(String.valueOf(row.getValue(i)));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** The same rows, sorted — for a query whose ORDER BY leaves ties. */
    private List<String> sortedRows(final String sql) {
        final List<String> lines = rows(sql);
        Collections.sort(lines);
        return lines;
    }

    @Test
    public void thisIsTheContainerItself() {
        assertEquals(Arrays.asList("{\"a\":1}"),
            rows("SELECT this FROM TABLE(FLATTEN(input => PARSE_JSON('{\"a\":1}')))"));
        assertEquals(Arrays.asList("[1,2]", "[1,2]"),
            rows("SELECT this FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(1, 2)))"));
        assertEquals(Arrays.asList("{\"a\":1,\"b\":\"x\"}", "{\"a\":1,\"b\":\"x\"}"),
            rows("SELECT this FROM TABLE(FLATTEN(input => OBJECT_CONSTRUCT('a', 1, 'b', 'x')))"));
        assertEquals(Arrays.asList("OBJECT"),
            rows("SELECT TYPEOF(this) FROM TABLE(FLATTEN(input => PARSE_JSON('{\"a\":1}')))"));
        assertEquals(Arrays.asList("ARRAY"),
            rows("SELECT TYPEOF(this) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(1)))"));
    }

    @Test
    public void aRecursiveWalkMovesThisDownWithIt() {
        assertEquals(Arrays.asList(
                "a | {\"a\":[1,{\"c\":2}],\"d\":3}",
                "a[0] | [1,{\"c\":2}]",
                "a[1] | [1,{\"c\":2}]",
                "a[1].c | {\"c\":2}",
                "d | {\"a\":[1,{\"c\":2}],\"d\":3}"),
            rows("""
                SELECT path, this FROM TABLE(FLATTEN(input => PARSE_JSON('{"a":[1,{"c":2}],"d":3}'),
                    recursive => TRUE)) ORDER BY path"""));
        assertEquals(Arrays.asList("a.b={\"b\":1}", "a={\"a\":{\"b\":1}}"),
            rows("""
                SELECT path || '=' || this::VARCHAR FROM TABLE(FLATTEN(input => PARSE_JSON('{"a":{"b":1}}'),
                    recursive => TRUE)) ORDER BY 1"""));
    }

    @Test
    public void theModeDecidesWhichContainersAreWalked() {
        assertEquals(Arrays.asList(
                "a | {\"a\":[1,[2,3]],\"b\":{\"c\":{\"d\":4}}}",
                "b | {\"a\":[1,[2,3]],\"b\":{\"c\":{\"d\":4}}}",
                "b.c | {\"c\":{\"d\":4}}",
                "b.c.d | {\"d\":4}"),
            rows("""
                SELECT path, this FROM TABLE(FLATTEN(input => PARSE_JSON('{"a":[1,[2,3]],"b":{"c":{"d":4}}}'),
                    recursive => TRUE, mode => 'OBJECT')) ORDER BY path"""));
        assertEquals(Arrays.asList(
                "[0] | [[1,2],[3]]", "[0][0] | [1,2]", "[0][1] | [1,2]", "[1] | [[1,2],[3]]", "[1][0] | [3]"),
            rows("""
                SELECT path, this FROM TABLE(FLATTEN(input => PARSE_JSON('[[1,2],[3]]'),
                    recursive => TRUE, mode => 'ARRAY')) ORDER BY path"""));
    }

    /** PATH selects the container that is walked, so THIS is the selected container, not the input. */
    @Test
    public void aPathSelectsTheContainerThisReports() {
        assertEquals(Arrays.asList("a.b[0] | [1,2]", "a.b[1] | [1,2]"),
            rows("""
                SELECT path, this FROM TABLE(FLATTEN(input => PARSE_JSON('{"a":{"b":[1,2]}}'), path => 'a.b'))
                ORDER BY path"""));
        assertEquals(Arrays.asList("a.b[0] | [1,{\"c\":2}]", "a.b[1] | [1,{\"c\":2}]", "a.b[1].c | {\"c\":2}"),
            rows("""
                SELECT path, this FROM TABLE(FLATTEN(input => PARSE_JSON('{"a":{"b":[1,{"c":2}]}}'),
                    path => 'a.b', recursive => TRUE)) ORDER BY path"""));
    }

    /**
     * OUTER's stand-in row for an empty container reports THAT container as THIS — the input's at the
     * top, a nested one's inside a recursive walk — and nothing at all for a scalar, a NULL or a path
     * that matched nothing.
     */
    @Test
    public void anOuterRowReportsTheEmptyContainer() {
        assertEquals(Arrays.asList(" | null | []"),
            rows("SELECT path, value, this FROM TABLE(FLATTEN(input => PARSE_JSON('[]'), outer => TRUE))"));
        assertEquals(Arrays.asList(" | null | {}"),
            rows("SELECT path, value, this FROM TABLE(FLATTEN(input => PARSE_JSON('{}'), outer => TRUE))"));
        assertEquals(Arrays.asList(
                "a | [] | {\"a\":[],\"b\":{}}", "a | null | []", "b | null | {}", "b | {} | {\"a\":[],\"b\":{}}"),
            sortedRows("""
                SELECT path, value, this FROM TABLE(FLATTEN(input => PARSE_JSON('{"a":[], "b":{}}'),
                    outer => TRUE, recursive => TRUE))"""));
        assertEquals(Arrays.asList("a.b | null | []"),
            rows("""
                SELECT path, value, this FROM TABLE(FLATTEN(input => PARSE_JSON('{"a":{"b":[]}}'),
                    path => 'a.b', outer => TRUE))"""));
        assertEquals(Arrays.asList("null | null"),
            rows("SELECT value, this FROM TABLE(FLATTEN(input => PARSE_JSON('\"abc\"'), outer => TRUE))"));
        assertEquals(Arrays.asList("null | null"),
            rows("SELECT value, this FROM TABLE(FLATTEN(input => NULL, outer => TRUE))"));
        assertEquals(Arrays.asList("null | null"),
            rows("""
                SELECT value, this FROM TABLE(FLATTEN(input => PARSE_JSON('{"a":{"b":1}}'), path => 'a.zz',
                    outer => TRUE))"""));
    }

    /** What consumes THIS gets a container: nested by ARRAY_AGG, embedded by OBJECT_CONSTRUCT. */
    @Test
    public void aConsumerSeesAContainerNotItsText() {
        assertEquals(Arrays.asList("[[1,2],[1,2]]"),
            rows("SELECT ARRAY_AGG(this) FROM TABLE(FLATTEN(input => PARSE_JSON('[1,2]')))"));
        assertEquals(Arrays.asList("{\"t\":[1]}"),
            rows("SELECT OBJECT_CONSTRUCT('t', this) FROM TABLE(FLATTEN(input => PARSE_JSON('[1]')))"));
        assertEquals(Arrays.asList("1", "1"),
            rows("SELECT this:a FROM TABLE(FLATTEN(input => PARSE_JSON('{\"a\":1,\"b\":2}'))) ORDER BY key"));
        engine.execute("CREATE TABLE ft AS SELECT this FROM TABLE(FLATTEN(input => PARSE_JSON('{\"a\":1}')))");
        assertEquals(Arrays.asList("{\"a\":1} | OBJECT"), rows("SELECT this, TYPEOF(this) FROM ft"));
    }

    /** The container keeps its members' own spellings: a JSON-looking string, a DOUBLE, a DATE. */
    @Test
    public void theContainerKeepsItsMembersSpellings() {
        assertEquals(Arrays.asList("{\"k\":\"{\\\"q\\\":1}\"}"),
            rows("""
                SELECT this FROM TABLE(FLATTEN(input => PARSE_JSON('{"k":"{\\\\"q\\\\":1}"}')))"""));
        assertEquals(Arrays.asList("[1.5,\"x\",true,undefined]", "[1.5,\"x\",true,undefined]",
                "[1.5,\"x\",true,undefined]"),
            rows("SELECT this FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(1.50, 'x', TRUE, NULL)))"));
        assertEquals(Arrays.asList("[\"2020-01-01\",\"2020-01-01 10:00:00.000\"]",
                "[\"2020-01-01\",\"2020-01-01 10:00:00.000\"]"),
            rows("""
                SELECT this FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(TO_DATE('2020-01-01'),
                    TO_TIMESTAMP_NTZ('2020-01-01 10:00:00'))))"""));
    }

    /** A LATERAL FLATTEN over a column reports each input row's own containers. */
    @Test
    public void aLateralFlattenReportsEachRowsContainers() {
        engine.execute("CREATE TABLE j (id INT, v VARIANT)");
        engine.execute("""
            INSERT INTO j SELECT 1, PARSE_JSON('{"x":[1,2]}') UNION ALL SELECT 2, PARSE_JSON('{"x":[3]}')""");
        assertEquals(Arrays.asList("1 | [0] | [1,2]", "1 | [1] | [1,2]", "2 | [0] | [3]"),
            rows("SELECT j.id, f.path, f.this FROM j, LATERAL FLATTEN(input => j.v:x) f ORDER BY 1, 2"));
        assertEquals(Arrays.asList(
                "1 | x | {\"x\":[1,2]}", "1 | x[0] | [1,2]", "1 | x[1] | [1,2]",
                "2 | x | {\"x\":[3]}", "2 | x[0] | [3]"),
            rows("""
                SELECT j.id, f.path, f.this FROM j, LATERAL FLATTEN(input => j.v, recursive => TRUE) f
                ORDER BY 1, 2"""));
    }
}
