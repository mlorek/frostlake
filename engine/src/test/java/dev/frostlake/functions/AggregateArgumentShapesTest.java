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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Six aggregate argument shapes decided as the account decides them: COUNT_IF refuses a non-boolean
 * (in IFF's name) and DISTINCT; OBJECT_AGG refuses a VARCHAR, temporal or BINARY value; the boolean
 * aggregates refuse a FLOAT, temporal or BINARY at compile time in TO_BOOLEAN's name, read a text or
 * a VARIANT strictly at row time, and BOOLXOR_AGG is TRUE for exactly one true; LISTAGG refuses a
 * BINARY value or delimiter; APPROX_COUNT_DISTINCT counts distinct tuples over several arguments or a lone star's columns,
 * skipping a tuple with any NULL. Every expectation is live-verified.
 */
public class AggregateArgumentShapesTest extends BaseDatabaseTest {

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE aw (n102 NUMBER(10,2), t VARCHAR(10), f FLOAT, d DATE, b BOOLEAN, n380 NUMBER(38,0), "
            + "v VARIANT, g VARCHAR(10), bin BINARY)");
        engine.execute("INSERT INTO aw SELECT 1.5, 'a', 1.5, '2020-01-01', TRUE, 7, TO_VARIANT(1), 'x', TO_BINARY('ab') "
            + "UNION ALL SELECT 2.5, 'b', 0, '2020-01-02', FALSE, 7, TO_VARIANT(0), 'true', TO_BINARY('cd') "
            + "UNION ALL SELECT 1.5, 'a', 1.5, '2020-01-01', TRUE, 8, TO_VARIANT(1), 'x', TO_BINARY('ab')");
    }

    private Row row(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        assertEquals(1, result.getRowCount(), sql);
        return result.getRows().get(0);
    }

    private String text(final String sql) {
        return String.valueOf(row(sql).getValue(0));
    }

    private static String cell(final Row row, final int index) {
        return String.valueOf(row.getValue(index));
    }

    private static String bool(final Row row, final int index) {
        return String.valueOf(row.getValue(index)).toLowerCase();
    }

    private void assertRefusal(final String sql, final String... fragments) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        for (final String fragment : fragments) {
            assertTrue(refused.getMessage().contains(fragment), sql + " -> " + refused.getMessage());
        }
    }

    @Test
    public void countIfTakesOnlyABooleanAndNoDistinct() {
        final String[][] refused = {
            {"n102", "NUMBER(10,2)"}, {"t", "VARCHAR(10)"}, {"v", "VARIANT"}, {"n380", "NUMBER(38,0)"}, {"f", "FLOAT"}, {"d", "DATE"}};
        for (final String[] shape : refused) {
            assertRefusal("SELECT COUNT_IF(" + shape[0] + ") FROM aw", "SQL compilation error: error line 1 at position 7\n"
                + "Invalid argument types for function 'IFF': (" + shape[1] + ", NUMBER(1,0), NUMBER(1,0))");
        }
        assertRefusal("SELECT COUNT_IF(DISTINCT b) FROM aw", "SQL compilation error:\n"
            + "invalid use of 'distinct' for function 'COUNT_IF(DISTINCT AW.B)'");
        final Row counted = row("SELECT COUNT_IF(b), COUNT_IF(n102 > 2), COUNT_IF(NOT b) FROM aw");
        assertEquals("2", cell(counted, 0));
        assertEquals("1", cell(counted, 1));
        assertEquals("1", cell(counted, 2));
    }

    @Test
    public void objectAggTakesANumberBooleanFloatOrVariantValue() {
        assertRefusal("SELECT OBJECT_AGG(t, t) FROM aw", "SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'OBJECT_AGG': (VARCHAR(10), VARCHAR(10))");
        assertRefusal("SELECT OBJECT_AGG(t, d) FROM aw", "error line 1 at position 7",
            "Invalid argument types for function 'OBJECT_AGG': (VARCHAR(10), DATE)");
        assertRefusal("SELECT OBJECT_AGG(t, bin) FROM aw", "error line 1 at position 7",
            "Invalid argument types for function 'OBJECT_AGG': (VARCHAR(10), BINARY(8388608))");
        final Row taken = row("SELECT OBJECT_AGG(t, n102), OBJECT_AGG(t, b), OBJECT_AGG(t, v), OBJECT_AGG(t, f), OBJECT_AGG(t, n380), "
            + "OBJECT_AGG(t, TO_VARIANT(t)), OBJECT_AGG(t, d::VARIANT), OBJECT_AGG(t, t::VARIANT) FROM aw WHERE n380 = 7");
        assertEquals("{\"a\":1.5,\"b\":2.5}", cell(taken, 0));
        assertEquals("{\"a\":true,\"b\":false}", cell(taken, 1));
        assertEquals("{\"a\":1,\"b\":0}", cell(taken, 2));
        assertEquals("{\"a\":1.500000000000000e+00,\"b\":0.000000000000000e+00}", cell(taken, 3));
        assertEquals("{\"a\":7,\"b\":7}", cell(taken, 4));
        assertEquals("{\"a\":\"a\",\"b\":\"b\"}", cell(taken, 5));
        assertEquals("{\"a\":\"2020-01-01\",\"b\":\"2020-01-02\"}", cell(taken, 6));
        assertEquals("{\"a\":\"a\",\"b\":\"b\"}", cell(taken, 7));
        // The KEY takes any family, spelled as text.
        final Row keys = row("SELECT OBJECT_AGG(n102, n102), OBJECT_AGG(d, n102), OBJECT_AGG(b, n102), OBJECT_AGG(v, n102) FROM aw WHERE n380 = 7");
        assertEquals("{\"1.50\":1.5,\"2.50\":2.5}", cell(keys, 0));
        assertEquals("{\"2020-01-01\":1.5,\"2020-01-02\":2.5}", cell(keys, 1));
        assertEquals("{\"false\":2.5,\"true\":1.5}", cell(keys, 2));
        assertEquals("{\"0\":2.5,\"1\":1.5}", cell(keys, 3));
        assertEquals("OBJECT[LOB]", text("SELECT SYSTEM$TYPEOF(OBJECT_AGG(t, n102)) FROM aw LIMIT 1"));
    }

    @Test
    public void booleanAggregatesConvertAsToBooleanDoes() {
        for (final String name : new String[] {"BOOLOR_AGG", "BOOLAND_AGG", "BOOLXOR_AGG"}) {
            assertRefusal("SELECT " + name + "(f) FROM aw", "SQL compilation error:\n"
                + "invalid type [TO_BOOLEAN(AW.F)] for parameter 'TO_BOOLEAN'");
        }
        assertRefusal("SELECT BOOLOR_AGG(d) FROM aw", "invalid type [TO_BOOLEAN(AW.D)] for parameter 'TO_BOOLEAN'");
        assertRefusal("SELECT BOOLOR_AGG(bin) FROM aw", "invalid type [TO_BOOLEAN(AW.BIN)] for parameter 'TO_BOOLEAN'");
        assertRefusal("SELECT BOOLOR_AGG(f) OVER () FROM aw LIMIT 1", "invalid type [TO_BOOLEAN(AW.F)] for parameter 'TO_BOOLEAN'");
        // An exact NUMBER reads as x <> 0; BOOLXOR_AGG is exactly-one, not a parity.
        final Row numbers = row("SELECT BOOLOR_AGG(n102), BOOLAND_AGG(n102), BOOLXOR_AGG(n102), BOOLOR_AGG(n380) FROM aw");
        assertEquals("true", bool(numbers, 0));
        assertEquals("true", bool(numbers, 1));
        assertEquals("false", bool(numbers, 2));
        assertEquals("true", bool(numbers, 3));
        final Row booleans = row("SELECT BOOLOR_AGG(b), BOOLAND_AGG(b), BOOLXOR_AGG(b), BOOLOR_AGG(NULL) FROM aw");
        assertEquals("true", bool(booleans, 0));
        assertEquals("false", bool(booleans, 1));
        assertEquals("false", bool(booleans, 2));
        assertNull(booleans.getValue(3));
        assertEquals("true", text("SELECT BOOLXOR_AGG(b) FROM aw WHERE n380 = 7").toLowerCase());
        // A text or a VARIANT is read at row time, strictly.
        assertRefusal("SELECT BOOLOR_AGG(t) FROM aw", "Boolean value 'a' is not recognized");
        assertRefusal("SELECT BOOLOR_AGG(g) FROM aw", "Boolean value 'x' is not recognized");
        assertRefusal("SELECT BOOLOR_AGG(v) FROM aw", "Failed to cast variant value 1 to BOOLEAN");
        assertEquals("true", text("SELECT BOOLOR_AGG(g) FROM aw WHERE g = 'true'").toLowerCase());
    }

    @Test
    public void listAggRefusesABinaryValueOrDelimiter() {
        assertRefusal("SELECT LISTAGG(bin, ',') FROM aw", "SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'LISTAGG': (BINARY(8388608), VARCHAR(1))");
        assertRefusal("SELECT LISTAGG(bin) FROM aw", "Invalid argument types for function 'LISTAGG': (BINARY(8388608))");
        assertRefusal("SELECT LISTAGG(bin, ',') OVER () FROM aw LIMIT 1",
            "Invalid argument types for function 'LISTAGG': (BINARY(8388608), VARCHAR(1))");
        assertRefusal("SELECT LISTAGG(DISTINCT bin, ',') FROM aw",
            "Invalid argument types for function 'LISTAGG': (BINARY(8388608), VARCHAR(1))");
        final Row listed = row("SELECT LISTAGG(d, ','), LISTAGG(n102, ','), LISTAGG(b, ','), LISTAGG(v, ','), LISTAGG(f, ','), LISTAGG(t, 1) FROM aw");
        assertEquals("2020-01-01,2020-01-02,2020-01-01", cell(listed, 0));
        assertEquals("1.50,2.50,1.50", cell(listed, 1));
        assertEquals("true,false,true", cell(listed, 2));
        assertEquals("1,0,1", cell(listed, 3));
        assertEquals("1.5,0,1.5", cell(listed, 4));
        assertEquals("a1b1a", cell(listed, 5));
    }

    @Test
    public void approxCountDistinctCountsTuples() {
        assertEquals("3", text("SELECT APPROX_COUNT_DISTINCT(n102, n380) FROM aw"));
        final Row tuples = row("SELECT APPROX_COUNT_DISTINCT(n102, t), APPROX_COUNT_DISTINCT(n102, n380, t), APPROX_COUNT_DISTINCT(t, d, b, v), "
            + "APPROX_COUNT_DISTINCT(DISTINCT n102, n380), HLL(n102, n380), APPROXIMATE_COUNT_DISTINCT(n102, n380), "
            + "APPROX_COUNT_DISTINCT(n102, bin), APPROX_COUNT_DISTINCT(bin, bin) FROM aw");
        final String[] expected = {"2", "3", "2", "3", "3", "3", "2", "2"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], cell(tuples, i), "column " + i);
        }
        final Row typed = row("SELECT SYSTEM$TYPEOF(APPROX_COUNT_DISTINCT(n102, n380)), TYPEOF(TO_VARIANT(APPROX_COUNT_DISTINCT(n102, n380))) FROM aw");
        assertEquals("NUMBER(18,0)[SB8]", cell(typed, 0));
        assertEquals("INTEGER", cell(typed, 1));
        assertEquals("0", text("SELECT APPROX_COUNT_DISTINCT(n102, n380) FROM aw WHERE 1 = 0"));
        // A tuple with any NULL member is not counted.
        assertEquals("1", text("SELECT APPROX_COUNT_DISTINCT(n102, n380) FROM (SELECT 1.5 n102, 7 n380 UNION ALL SELECT 1.5, NULL "
            + "UNION ALL SELECT NULL, 7 UNION ALL SELECT NULL, NULL UNION ALL SELECT 1.5, 7)"));
        assertRefusal("SELECT APPROX_COUNT_DISTINCT() FROM aw", "error line 1 at position 7",
            "not enough arguments for function [APPROX_COUNT_DISTINCT()], expected 1, got 0");
    }

    @Test
    public void approxCountDistinctTakesAStarAsItsColumnList() {
        engine.execute("CREATE TABLE sa (a NUMBER(5,0), b NUMBER(5,0), c NUMBER(5,0))");
        engine.execute("INSERT INTO sa VALUES (1, 2, 3), (4, NULL, 6), (NULL, NULL, NULL)");
        final Row lone = row("SELECT APPROX_COUNT_DISTINCT(sa.*), HLL(sa.*), APPROXIMATE_COUNT_DISTINCT(sa.*), "
            + "APPROX_COUNT_DISTINCT(*), APPROX_COUNT_DISTINCT(DISTINCT sa.*) FROM sa");
        for (int i = 0; i < 5; i++) {
            assertEquals("1", cell(lone, i), "column " + i);
        }
        assertEquals("1", text("SELECT APPROX_COUNT_DISTINCT(s.*) FROM sa s"));
        assertEquals("NUMBER(18,0)[SB8]", text("SELECT SYSTEM$TYPEOF(APPROX_COUNT_DISTINCT(sa.*)) FROM sa LIMIT 1"));
        final ResultSet windowed = engine.executeQuery("SELECT APPROX_COUNT_DISTINCT(sa.*) OVER () FROM sa");
        assertEquals(3, windowed.getRowCount());
        for (int i = 0; i < 3; i++) {
            assertEquals("1", cell(windowed.getRows().get(i), 0), "row " + i);
        }
        // Per group: only the group holding (1, 2, 3) has a tuple without a NULL.
        final ResultSet grouped = engine.executeQuery("SELECT a, APPROX_COUNT_DISTINCT(sa.*) FROM sa GROUP BY a ORDER BY a");
        assertEquals(3, grouped.getRowCount());
        assertEquals("1", cell(grouped.getRows().get(0), 0));
        assertEquals("1", cell(grouped.getRows().get(0), 1));
        assertEquals("4", cell(grouped.getRows().get(1), 0));
        assertEquals("0", cell(grouped.getRows().get(1), 1));
        assertNull(grouped.getRows().get(2).getValue(0));
        assertEquals("0", cell(grouped.getRows().get(2), 1));
        engine.execute("INSERT INTO sa VALUES (1, 2, 3), (7, 8, 9)");
        final Row counted = row("SELECT APPROX_COUNT_DISTINCT(sa.*), COUNT(DISTINCT sa.*), "
            + "APPROX_COUNT_DISTINCT(sa.* EXCLUDE c) FROM sa");
        assertEquals("2", cell(counted, 0));
        assertEquals("2", cell(counted, 1));
        assertEquals("2", cell(counted, 2));
    }
}
