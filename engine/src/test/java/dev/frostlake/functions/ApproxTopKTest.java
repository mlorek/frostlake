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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * APPROX_TOP_K and its accumulate / combine / estimate forms: the Space-Saving ranking (count
 * descending, the most recently counted first among equals, the evicted count inherited), the
 * [value, count] rendering that keeps each value's own type, the serialised state with its declared
 * datatype and width, the merge and its trimming, the compile-time limit rules, the arity sentences
 * and the whole-partition window family. Every expectation is live-verified.
 */
public class ApproxTopKTest extends BaseDatabaseTest {

    private static final String TOP_K_STATE_OF_WF =
        "{\"counters\":10,\"datatype\":\"FIXED\",\"precision\":10,\"scale\":2,\"state\":[[3.5,2],[2.5,1],[1.5,1]],"
            + "\"type\":\"approx_top_k\"}";

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE wf (n NUMBER(10,2), s VARCHAR, k VARCHAR)");
        engine.execute("INSERT INTO wf SELECT 1.5, 'a', 'x' UNION ALL SELECT 2.5, 'b', 'x' UNION ALL SELECT 3.5, 'b', 'y' "
            + "UNION ALL SELECT 3.5, 'c', 'y' UNION ALL SELECT NULL, NULL, 'y'");
        engine.execute("CREATE TABLE wt (s VARCHAR, n NUMBER(10,2), f FLOAT, i INTEGER, d DATE, b BOOLEAN, v VARIANT)");
        engine.execute("INSERT INTO wt SELECT 'c', 3.5, 0.1, 7, '2024-01-03', TRUE, PARSE_JSON('{\"a\":1}') "
            + "UNION ALL SELECT 'b', 1.5, 0.2, 3, '2024-01-01', FALSE, PARSE_JSON('\"x\"') "
            + "UNION ALL SELECT 'a', 2.5, 0.1, 3, '2024-01-02', TRUE, PARSE_JSON('[1]') "
            + "UNION ALL SELECT 'b', 1.5, 0.3, 9, '2024-01-01', TRUE, PARSE_JSON('{\"a\":1}')");
        engine.execute("CREATE TABLE wr (n NUMBER(10,2), b NUMBER(38,0), f FLOAT, s VARCHAR)");
        engine.execute("INSERT INTO wr SELECT 1.10, 12345678901234567890123456789, 1e20, 'a' "
            + "UNION ALL SELECT 2.00, 12345678901234567890123456789, 0.5, 'a' "
            + "UNION ALL SELECT 1.10, 7, 1e20, NULL");
        engine.execute("CREATE TABLE wc (g INTEGER, x INTEGER)");
        engine.execute("INSERT INTO wc SELECT 1, 1 UNION ALL SELECT 1, 1 UNION ALL SELECT 1, 1 "
            + "UNION ALL SELECT 2, 2 UNION ALL SELECT 2, 2 UNION ALL SELECT 2, 3");
    }

    private List<Row> rows(final String sql) {
        return engine.executeQuery(sql).getRows();
    }

    private Row row(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        assertEquals(1, result.getRowCount(), sql);
        return result.getRows().get(0);
    }

    private String text(final String sql) {
        return String.valueOf(row(sql).getValue(0));
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private void assertRefusal(final String sql, final String... fragments) {
        final String message = refusal(sql);
        for (final String fragment : fragments) {
            assertTrue(message.contains(fragment), sql + " -> " + message);
        }
    }

    @Test
    public void defaultsToTheSingleMostFrequentValue() {
        assertEquals("[[3.5,2]]", text("SELECT APPROX_TOP_K(n) FROM wf"));
        assertEquals("[[\"b\",2]]", text("SELECT APPROX_TOP_K(s) FROM wt"));
        assertEquals("[[3.5,2],[2.5,1]]", text("SELECT APPROX_TOP_K(n, 2) FROM wf"));
        assertEquals("[[3.5,2],[2.5,1],[1.5,1]]", text("SELECT APPROX_TOP_K(n, 3) FROM wf"));
        assertEquals("[[3.5,2],[2.5,1],[1.5,1]]", text("SELECT APPROX_TOP_K(n, 10, 100) FROM wf"));
        assertEquals("[[\"b\",2],[\"c\",1],[\"a\",1]]", text("SELECT APPROX_TOP_K(s, 5) FROM wf"));
        assertEquals("[[3.5,2],[2.5,1]]", text("SELECT APPROX_TOP_K(TO_VARIANT(n), 2) FROM wf"));
    }

    @Test
    public void ranksByCountThenByTheMostRecentlyCountedValue() {
        assertEquals("[[\"b\",2],[\"a\",1],[\"c\",1]]", text("SELECT APPROX_TOP_K(s, 5) FROM wt"));
        assertEquals("[[1.5,2],[2.5,1],[3.5,1]]", text("SELECT APPROX_TOP_K(n, 5) FROM wt"));
        // A DOUBLE inside a pair takes the fifteen-decimal form, in a result cell and under a text
        // conversion alike.
        assertEquals("[[1.000000000000000e-01,2],[3.000000000000000e-01,1],[2.000000000000000e-01,1]]",
            text("SELECT APPROX_TOP_K(f, 5) FROM wt"));
        assertEquals("[[1.000000000000000e-01,2],[3.000000000000000e-01,1],[2.000000000000000e-01,1]]",
            text("SELECT TO_VARCHAR(APPROX_TOP_K(f, 5)) FROM wt"));
        assertEquals("[[3,2],[9,1],[7,1]]", text("SELECT APPROX_TOP_K(i, 5) FROM wt"));
        assertEquals("[[\"2024-01-01\",2],[\"2024-01-02\",1],[\"2024-01-03\",1]]", text("SELECT APPROX_TOP_K(d, 5) FROM wt"));
        assertEquals("[[true,3],[false,1]]", text("SELECT APPROX_TOP_K(b, 5) FROM wt"));
        assertEquals("[[{\"a\":1},2],[[1],1],[\"x\",1]]", text("SELECT APPROX_TOP_K(v, 5) FROM wt"));
    }

    @Test
    public void eachValueRendersInItsOwnType() {
        assertEquals("[[1.1,2],[2,1]]", text("SELECT APPROX_TOP_K(n, 5) FROM wr"));
        assertEquals("[[12345678901234567890123456789,2],[7,1]]", text("SELECT APPROX_TOP_K(b, 5) FROM wr"));
        assertEquals("[[1.000000000000000e+20,2],[5.000000000000000e-01,1]]", text("SELECT APPROX_TOP_K(f, 5) FROM wr"));
        final Row pair = row("SELECT APPROX_TOP_K(n, 2)[0], APPROX_TOP_K(n, 2)[0][0], APPROX_TOP_K(n, 2)[0][1], "
            + "TYPEOF(APPROX_TOP_K(n, 2)[0][0]), TYPEOF(APPROX_TOP_K(s, 2)[0][0]), TYPEOF(APPROX_TOP_K(n, 2)[0][1]) FROM wt");
        assertEquals("[1.5,2]", String.valueOf(pair.getValue(0)));
        assertEquals("1.5", String.valueOf(pair.getValue(1)));
        assertEquals("2", String.valueOf(pair.getValue(2)));
        assertEquals("DECIMAL", String.valueOf(pair.getValue(3)));
        assertEquals("VARCHAR", String.valueOf(pair.getValue(4)));
        assertEquals("INTEGER", String.valueOf(pair.getValue(5)));
    }

    @Test
    public void aFullSummaryHandsTheEvictedCountOn() {
        assertEquals("[[3.5,4]]", text("SELECT APPROX_TOP_K(n, 2, 1) FROM wf"));
        assertEquals("[[1.5,2],[2.5,2]]", text("SELECT APPROX_TOP_K(n, 2, 2) FROM wt"));
        assertEquals("[[1,25],[0,25],[6,25]]",
            text("SELECT APPROX_TOP_K(SEQ4() % 7, 3, 4) FROM TABLE(GENERATOR(ROWCOUNT => 100))"));
    }

    @Test
    public void nothingCountedIsAnEmptyArray() {
        assertEquals("[]", text("SELECT APPROX_TOP_K(n) FROM wf WHERE n > 100"));
        assertEquals("[]", text("SELECT APPROX_TOP_K(s) FROM wt WHERE s IS NULL"));
        assertEquals("[]", text("SELECT APPROX_TOP_K(NULL, 2) FROM wf"));
    }

    @Test
    public void distinctCountsEveryValueOnce() {
        final String ranked = text("SELECT APPROX_TOP_K(DISTINCT n, 5) FROM wt");
        assertTrue(ranked.contains("[1.5,1]") && ranked.contains("[2.5,1]") && ranked.contains("[3.5,1]"), ranked);
        assertEquals("[[1.5,1],[2.5,1],[3.5,1]]".length(), ranked.length(), ranked);
    }

    @Test
    public void groupedAndWholePartitionWindowForms() {
        final List<Row> grouped = rows("SELECT APPROX_TOP_K(n, 2) FROM wf GROUP BY k ORDER BY k");
        assertEquals(2, grouped.size());
        assertEquals("[[2.5,1],[1.5,1]]", String.valueOf(grouped.get(0).getValue(0)));
        assertEquals("[[3.5,2]]", String.valueOf(grouped.get(1).getValue(0)));
        assertEquals("[[3.5,2],[2.5,1]]", text("SELECT APPROX_TOP_K(n, 2) OVER () FROM wf LIMIT 1"));
        final List<Row> partitioned = rows("SELECT APPROX_TOP_K(n, 2) OVER (PARTITION BY k) FROM wf ORDER BY k LIMIT 2");
        assertEquals("[[2.5,1],[1.5,1]]", String.valueOf(partitioned.get(0).getValue(0)));
        assertEquals("[[2.5,1],[1.5,1]]", String.valueOf(partitioned.get(1).getValue(0)));
        assertEquals("[[3.5,2],[2.5,1]]", text("SELECT APPROX_TOP_K(n, 2) OVER (ORDER BY n ROWS BETWEEN UNBOUNDED PRECEDING "
            + "AND UNBOUNDED FOLLOWING) FROM wf LIMIT 1"));
        assertEquals("[[\"b\",1]]", text("SELECT APPROX_TOP_K(s, 5) OVER (PARTITION BY b ORDER BY n ROWS BETWEEN UNBOUNDED "
            + "PRECEDING AND UNBOUNDED FOLLOWING) FROM wt ORDER BY b, n LIMIT 1"));
        final List<Row> overGroups = rows("SELECT APPROX_TOP_K(s, 5), APPROX_TOP_K(NULL, 2), APPROX_TOP_K(s) OVER () "
            + "FROM wr GROUP BY s ORDER BY s");
        assertEquals(2, overGroups.size());
        assertEquals("[[\"a\",2]]", String.valueOf(overGroups.get(0).getValue(0)));
        assertEquals("[]", String.valueOf(overGroups.get(0).getValue(1)));
        assertEquals("[[\"a\",1]]", String.valueOf(overGroups.get(0).getValue(2)));
        assertEquals("[]", String.valueOf(overGroups.get(1).getValue(0)));
        assertEquals("[[\"a\",1]]", String.valueOf(overGroups.get(1).getValue(2)));
    }

    @Test
    public void orderedFramesAreRefusedAsForTheWholePartitionFamily() {
        assertRefusal("SELECT APPROX_TOP_K(n, 2) OVER (ORDER BY n) FROM wf LIMIT 1",
            "error line 1 at position 26", "Cumulative window frame unsupported for function APPROX_TOP_K");
        assertRefusal("SELECT APPROX_TOP_K(n, 2) OVER (ORDER BY n ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM wf LIMIT 1",
            "error line 1 at position 43", "Sliding window frame unsupported for function APPROX_TOP_K");
        assertRefusal("SELECT APPROX_TOP_K(n, 2) OVER (ORDER BY n RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) "
            + "FROM wf LIMIT 1",
            "Aggregate window function with order by clause is not supported: "
                + "[APPROX_TOP_K(WF.N, 2) OVER (ORDER BY WF.N ASC NULLS LAST)].");
        assertRefusal("SELECT APPROX_TOP_K_ACCUMULATE(n, 10) OVER (ORDER BY n) FROM wf",
            "error line 1 at position 38", "Cumulative window frame unsupported for function APPROX_TOP_K_ACCUMULATE");
        assertRefusal("SELECT APPROX_TOP_K_COMBINE(st, 10) OVER (ORDER BY st) FROM "
            + "(SELECT APPROX_TOP_K_ACCUMULATE(n, 10) AS st FROM wf GROUP BY k)",
            "Cumulative window frame unsupported for function APPROX_TOP_K_COMBINE");
        assertEquals(TOP_K_STATE_OF_WF, text("SELECT APPROX_TOP_K_ACCUMULATE(n, 10) OVER () FROM wf LIMIT 1"));
        assertEquals(TOP_K_STATE_OF_WF, text("SELECT APPROX_TOP_K_COMBINE(st) OVER () FROM "
            + "(SELECT APPROX_TOP_K_ACCUMULATE(n, 10) AS st FROM wf GROUP BY k) LIMIT 1"));
    }

    @Test
    public void theLimitsAreConstantsJudgedAtCompileTime() {
        assertRefusal("SELECT APPROX_TOP_K(n, 0) FROM wf", "SQL compilation error:\n"
            + "Invalid value [0] for function 'APPROX_TOP_K', parameter 1: Number of items must be a positive integer");
        assertRefusal("SELECT APPROX_TOP_K(n, -1) FROM wf",
            "Invalid value [-1] for function 'APPROX_TOP_K', parameter 1: Number of items must be a positive integer");
        assertRefusal("SELECT APPROX_TOP_K(n, 100001) FROM wf",
            "Invalid value [100001] for function 'APPROX_TOP_K', parameter 1: Number of items cannot be larger than 100000");
        assertRefusal("SELECT APPROX_TOP_K(n, NULL) FROM wf",
            "Invalid value [null] for function 'APPROX_TOP_K', parameter 1: Number of items must be a positive integer");
        assertRefusal("SELECT APPROX_TOP_K(n, 2, 0) FROM wf",
            "Invalid value [0] for function 'APPROX_TOP_K', parameter 2: Number of counters must be a positive integer");
        assertRefusal("SELECT APPROX_TOP_K(n, 2, 100001) FROM wf",
            "Invalid value [100001] for function 'APPROX_TOP_K', parameter 2: Number of counters cannot be larger than 100000");
        assertRefusal("SELECT APPROX_TOP_K(n, 1.5) FROM wf",
            "Invalid value [CAST(1.5 AS NUMBER(18,0))] for function '{1}', parameter {2}: {3}");
        assertRefusal("SELECT APPROX_TOP_K(n, 'x') FROM wf",
            "Invalid value [TO_NUMBER('x', 18, 0)] for function '{1}', parameter {2}: {3}");
        assertRefusal("SELECT APPROX_TOP_K(n, i) FROM wt",
            "SQL compilation error:\nargument 2 to function APPROX_TOP_K needs to be constant, found 'WT.I'");
        assertRefusal("SELECT APPROX_TOP_K(n, 2, i) FROM wt",
            "argument 3 to function APPROX_TOP_K needs to be constant, found 'WT.I'");
        assertRefusal("SELECT APPROX_TOP_K_ACCUMULATE(n, i) FROM wt",
            "argument 2 to function APPROX_TOP_K_ACCUMULATE needs to be constant, found 'WT.I'");
        assertRefusal("SELECT APPROX_TOP_K_ACCUMULATE(n, 0) FROM wf",
            "Invalid value [0] for function 'APPROX_TOP_K_ACCUMULATE', parameter 1: Number of counters must be a positive integer");
        assertRefusal("SELECT APPROX_TOP_K_ACCUMULATE(n, 100001) FROM wf",
            "Invalid value [100001] for function 'APPROX_TOP_K_ACCUMULATE', parameter 1: "
                + "Number of counters cannot be larger than 100000");
        assertRefusal("SELECT APPROX_TOP_K_COMBINE(st, 0) FROM (SELECT APPROX_TOP_K_ACCUMULATE(n, 10) AS st FROM wf)",
            "Invalid value [0] for function 'APPROX_TOP_K_COMBINE', parameter 1: Number of counters must be a positive integer");
        assertRefusal("SELECT APPROX_TOP_K_COMBINE(st, 100001) FROM (SELECT APPROX_TOP_K_ACCUMULATE(n, 10) AS st FROM wf)",
            "Invalid value [100001] for function 'APPROX_TOP_K_COMBINE', parameter 1: Number of counters cannot be larger than 100000");
        assertRefusal("SELECT APPROX_TOP_K_ESTIMATE(APPROX_TOP_K_ACCUMULATE(n, 10), 0) FROM wf",
            "Invalid value [0] for function 'APPROX_TOP_K_ESTIMATE', parameter 1: Number of counters must be a positive integer");
        assertRefusal("SELECT APPROX_TOP_K_ESTIMATE(APPROX_TOP_K_ACCUMULATE(n, 10), 100001) FROM wf",
            "Invalid value [100001] for function 'APPROX_TOP_K_ESTIMATE', parameter 1: Number of counters cannot be larger than 100000");
        assertRefusal("SELECT APPROX_TOP_K_ESTIMATE(APPROX_TOP_K_ACCUMULATE(n, 10), NULL) FROM wf",
            "Invalid value [null] for function 'APPROX_TOP_K_ESTIMATE', parameter 1: Number of counters must be a positive integer");
        assertRefusal("SELECT APPROX_TOP_K_ESTIMATE(APPROX_TOP_K_ACCUMULATE(n, 10), 1.5) FROM wf",
            "Invalid value [CAST(1.5 AS NUMBER(18,0))] for function '{1}', parameter {2}: {3}");
        assertRefusal("SELECT APPROX_TOP_K_ESTIMATE(APPROX_TOP_K_ACCUMULATE(n, 10), i) FROM wt GROUP BY i",
            "Invalid value [WT.I] for function '{1}', parameter {2}: {3}");
        // Every integral spelling of a constant passes.
        assertEquals("[[3.5,2],[2.5,1]]", text("SELECT APPROX_TOP_K(n, '2') FROM wf"));
        assertEquals("[[3.5,2],[2.5,1]]", text("SELECT APPROX_TOP_K(n, 2.0) FROM wf"));
        assertEquals("[[3.5,2],[2.5,1],[1.5,1]]", text("SELECT APPROX_TOP_K(n, 1e1) FROM wf"));
        assertEquals("[[3.5,2],[2.5,1]]", text("SELECT APPROX_TOP_K(n, (2)) FROM wf"));
        assertEquals("[[3.5,2],[2.5,1]]", text("SELECT APPROX_TOP_K(n, +2) FROM wf"));
        assertEquals("[[3.5,2]]", text("SELECT APPROX_TOP_K_ESTIMATE(APPROX_TOP_K_ACCUMULATE(n, 10), '1') FROM wf"));
    }

    @Test
    public void arityIsJudgedWithThePlanEcho() {
        assertRefusal("SELECT APPROX_TOP_K() FROM wf", "error line 1 at position 7",
            "not enough arguments for function [APPROX_TOP_K()], expected 1, got 0");
        assertRefusal("SELECT APPROX_TOP_K(n, 1, 2, 3) FROM wt", "error line 1 at position 7",
            "too many arguments for function [APPROX_TOP_K(WT.N, 1, 2, 3)] expected 3, got 4");
        assertRefusal("SELECT APPROX_TOP_K_ACCUMULATE(n) FROM wt", "error line 1 at position 7",
            "not enough arguments for function [APPROX_TOP_K_ACCUMULATE(WT.N)], expected 2, got 1");
    }

    @Test
    public void theStateNamesTheDeclaredFamilyAndWidth() {
        assertEquals(TOP_K_STATE_OF_WF, text("SELECT APPROX_TOP_K_ACCUMULATE(n, 10) FROM wf"));
        assertEquals("{\"counters\":10,\"datatype\":\"TEXT\",\"precision\":38,\"scale\":0,\"state\":[[\"b\",2],[\"a\",1],[\"c\",1]],"
            + "\"type\":\"approx_top_k\"}", text("SELECT APPROX_TOP_K_ACCUMULATE(s, 10) FROM wt"));
        assertEquals("{\"counters\":10,\"datatype\":\"REAL\",\"precision\":38,\"scale\":0,\"state\":[[1.000000000000000e-01,2],[3.000000000000000e-01,1],"
            + "[2.000000000000000e-01,1]],"
            + "\"type\":\"approx_top_k\"}", text("SELECT APPROX_TOP_K_ACCUMULATE(f, 10) FROM wt"));
        assertEquals("{\"counters\":10,\"datatype\":\"FIXED\",\"precision\":38,\"scale\":0,\"state\":[[3,2],[9,1],[7,1]],"
            + "\"type\":\"approx_top_k\"}", text("SELECT APPROX_TOP_K_ACCUMULATE(i, 10) FROM wt"));
        assertEquals("{\"counters\":10,\"datatype\":\"DATE\",\"precision\":38,\"scale\":0,\"state\":[[\"2024-01-01\",2],"
            + "[\"2024-01-02\",1],[\"2024-01-03\",1]],\"type\":\"approx_top_k\"}",
            text("SELECT APPROX_TOP_K_ACCUMULATE(d, 10) FROM wt"));
        assertEquals("{\"counters\":10,\"datatype\":\"BOOLEAN\",\"precision\":38,\"scale\":0,\"state\":[[true,3],[false,1]],"
            + "\"type\":\"approx_top_k\"}", text("SELECT APPROX_TOP_K_ACCUMULATE(b, 10) FROM wt"));
        assertEquals("{\"counters\":10,\"datatype\":\"VARIANT\",\"precision\":38,\"scale\":0,\"state\":[[{\"a\":1},2],[[1],1],"
            + "[\"x\",1]],\"type\":\"approx_top_k\"}", text("SELECT APPROX_TOP_K_ACCUMULATE(v, 10) FROM wt"));
        // An expression's declared type, a literal's own width, a concatenation's TEXT.
        final Row typed = row("SELECT APPROX_TOP_K(n + 1, 5), APPROX_TOP_K_ACCUMULATE(n + 1, 10), APPROX_TOP_K_ACCUMULATE(1.5, 10), "
            + "APPROX_TOP_K_ACCUMULATE(s || 'x', 10) FROM wr");
        assertEquals("[[2.1,2],[3,1]]", String.valueOf(typed.getValue(0)));
        assertEquals("{\"counters\":10,\"datatype\":\"FIXED\",\"precision\":11,\"scale\":2,\"state\":[[2.1,2],[3,1]],"
            + "\"type\":\"approx_top_k\"}", String.valueOf(typed.getValue(1)));
        assertEquals("{\"counters\":10,\"datatype\":\"FIXED\",\"precision\":2,\"scale\":1,\"state\":[[1.5,3]],"
            + "\"type\":\"approx_top_k\"}", String.valueOf(typed.getValue(2)));
        assertEquals("{\"counters\":10,\"datatype\":\"TEXT\",\"precision\":38,\"scale\":0,\"state\":[[\"ax\",2]],"
            + "\"type\":\"approx_top_k\"}", String.valueOf(typed.getValue(3)));
        // A state that saw nothing: one counter and the 38/0 width, whatever was asked.
        final Row empty = row("SELECT APPROX_TOP_K_ACCUMULATE(n, 10), APPROX_TOP_K_ACCUMULATE(s, 10) FROM wf WHERE n > 100");
        assertEquals("{\"counters\":1,\"datatype\":\"FIXED\",\"precision\":38,\"scale\":0,\"state\":[],\"type\":\"approx_top_k\"}",
            String.valueOf(empty.getValue(0)));
        assertEquals("{\"counters\":1,\"datatype\":\"TEXT\",\"precision\":38,\"scale\":0,\"state\":[],\"type\":\"approx_top_k\"}",
            String.valueOf(empty.getValue(1)));
        final Row typeof = row("SELECT SYSTEM$TYPEOF(APPROX_TOP_K(n)), SYSTEM$TYPEOF(APPROX_TOP_K_ACCUMULATE(n, 10)), "
            + "TYPEOF(APPROX_TOP_K(n)), TYPEOF(APPROX_TOP_K_ACCUMULATE(n, 10)), "
            + "SYSTEM$TYPEOF(APPROX_TOP_K_ESTIMATE(APPROX_TOP_K_ACCUMULATE(n, 10), 2)) FROM wf");
        assertEquals("ARRAY[LOB]", String.valueOf(typeof.getValue(0)));
        assertEquals("OBJECT[LOB]", String.valueOf(typeof.getValue(1)));
        assertEquals("ARRAY", String.valueOf(typeof.getValue(2)));
        assertEquals("OBJECT", String.valueOf(typeof.getValue(3)));
        assertEquals("ARRAY[LOB]", String.valueOf(typeof.getValue(4)));
    }

    @Test
    public void estimateRanksAState() {
        assertEquals("[[3.5,2],[2.5,1]]", text("SELECT APPROX_TOP_K_ESTIMATE(APPROX_TOP_K_ACCUMULATE(n, 10), 2) FROM wf"));
        assertEquals("[[1.5,2]]", text("SELECT APPROX_TOP_K_ESTIMATE(APPROX_TOP_K_ACCUMULATE(n, 10)) FROM wt"));
        assertEquals("[[1.5,2],[2.5,1],[3.5,1]]", text("SELECT APPROX_TOP_K_ESTIMATE(APPROX_TOP_K_ACCUMULATE(n, 10), 100) FROM wt"));
        assertRefusal("SELECT APPROX_TOP_K_ESTIMATE(PARSE_JSON('{\"a\":1}'), 2) FROM wf",
            "Invalid parameter value: ApproxTopK state. Reason: invalid type");
        assertNull(row("SELECT APPROX_TOP_K_ESTIMATE(NULL, 2) FROM wf LIMIT 1").getValue(0));
    }

    @Test
    public void combineAddsCountsThenTrims() {
        assertEquals(TOP_K_STATE_OF_WF,
            text("SELECT APPROX_TOP_K_COMBINE(st, 10) FROM (SELECT APPROX_TOP_K_ACCUMULATE(n, 10) AS st FROM wf GROUP BY k)"));
        assertEquals("{\"counters\":10,\"datatype\":\"FIXED\",\"precision\":38,\"scale\":0,\"state\":[[1,3],[2,2],[3,1]],"
            + "\"type\":\"approx_top_k\"}",
            text("SELECT APPROX_TOP_K_COMBINE(st) FROM (SELECT APPROX_TOP_K_ACCUMULATE(x, 10) AS st FROM wc GROUP BY g)"));
        assertEquals("{\"counters\":2,\"datatype\":\"FIXED\",\"precision\":38,\"scale\":0,\"state\":[[1,3],[2,2]],"
            + "\"type\":\"approx_top_k\"}",
            text("SELECT APPROX_TOP_K_COMBINE(st, 2) FROM (SELECT APPROX_TOP_K_ACCUMULATE(x, 10) AS st FROM wc GROUP BY g)"));
        assertEquals("[[1,3],[2,2],[3,1]]", text("SELECT APPROX_TOP_K_ESTIMATE(APPROX_TOP_K_COMBINE(st, 3), 5) FROM "
            + "(SELECT APPROX_TOP_K_ACCUMULATE(x, 10) AS st FROM wc GROUP BY g)"));
        // A constant state is a state per row: six rows of [1.5, 2] add up.
        assertEquals("{\"counters\":10,\"datatype\":\"FIXED\",\"precision\":10,\"scale\":2,\"state\":[[1.5,12]],"
            + "\"type\":\"approx_top_k\"}",
            text("SELECT APPROX_TOP_K_COMBINE(PARSE_JSON('{\"counters\":10,\"datatype\":\"FIXED\",\"precision\":10,\"scale\":2,"
                + "\"state\":[[1.5,2]],\"type\":\"approx_top_k\"}')) FROM wc"));
        final String missing = "{\"counters\":1,\"datatype\":\"MISSING\",\"precision\":38,\"scale\":0,\"state\":[],"
            + "\"type\":\"approx_top_k\"}";
        assertEquals(missing, text("SELECT APPROX_TOP_K_COMBINE(NULL) FROM wf"));
        assertEquals(missing, text("SELECT APPROX_TOP_K_COMBINE(st) FROM "
            + "(SELECT APPROX_TOP_K_ACCUMULATE(n, 10) AS st FROM wf WHERE n > 100 GROUP BY k)"));
        assertRefusal("SELECT APPROX_TOP_K_COMBINE(st) FROM (SELECT APPROX_TOP_K_ACCUMULATE(x, 3) AS st FROM wc GROUP BY g "
            + "UNION ALL SELECT APPROX_TOP_K_ACCUMULATE(x, 5) FROM wc)",
            "Invalid parameter value: ApproxTopK state. Reason: combining states with different numbers of counters");
        assertRefusal("SELECT APPROX_TOP_K_COMBINE(PARSE_JSON('{\"a\":1}')) FROM wf",
            "Invalid parameter value: ApproxTopK state. Reason: invalid type");
    }
}
