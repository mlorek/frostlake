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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FLATTEN hands a container's members back with the types they were built with. A LATERAL item's argument reads the
 * row's columns with their declared types, so FLATTEN(input => ARRAY_CONSTRUCT(u)) over a UUID column holds a UUID
 * member, as it does over a derived table; and every typed member — a UUID, a DATE, a BINARY, a TIMESTAMP, a TIME —
 * comes out of FLATTEN's VALUE still typed, rendering, comparing and re-aggregating as that type. Every cell is
 * live-verified.
 */
public class FlattenMemberTypeTest extends BaseDatabaseTest {

    private static final String UUID = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ut (u UUID, s VARCHAR, d DATE, n NUMBER(10,2), b BINARY, ts TIMESTAMP_NTZ,"
            + " tm TIME, bo BOOLEAN, f FLOAT)");
        engine.execute("INSERT INTO ut SELECT '" + UUID + "', 'x', '2024-01-15', 1.50, TO_BINARY('ABCD', 'HEX'),"
            + " '2024-01-15 10:00:00', '10:00:00', TRUE, 1.5");
    }

    /** The first row's cells, joined by ", ". */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final StringBuilder out = new StringBuilder();
            for (final Object value : row.getValues()) {
                out.append(out.length() > 0 ? ", " : "").append(value);
            }
            return out.toString();
        }
        return "no row";
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aLateralArgumentReadsTheColumnsDeclaredType() {
        assertCells(new String[][] {
            {"SELECT TYPEOF(value) FROM ut, TABLE(FLATTEN(ARRAY_CONSTRUCT(u)))", "UUID"},
            {"SELECT TYPEOF(f.value) FROM ut, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(u)) f", "UUID"},
            {"SELECT TYPEOF(f.value) FROM ut JOIN LATERAL FLATTEN(input => ARRAY_CONSTRUCT(u)) f", "UUID"},
            {"SELECT TYPEOF(f.value) FROM ut x, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(x.u)) f", "UUID"},
            {"SELECT TYPEOF(f.value) FROM ut, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(NVL(u, u))) f", "UUID"},
            {"SELECT TYPEOF(f.value) FROM ut, LATERAL FLATTEN(input => OBJECT_CONSTRUCT('k', u)) f", "UUID"},
            {"WITH c AS (SELECT u FROM ut) SELECT TYPEOF(f.value) FROM c, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(c.u)) f",
                "UUID"},
            {"SELECT HASH(f.value) = HASH(TO_VARIANT(ut.u)), HASH(f.value) = HASH(TO_VARIANT(ut.s))"
                + " FROM ut, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(u)) f", "true, false"},
        });
    }

    @Test
    public void everyTypedMemberComesOutTyped() {
        final String members = "UUID,VARCHAR,DATE,DECIMAL,BINARY,TIMESTAMP_NTZ,TIME,BOOLEAN,DOUBLE";
        assertCells(new String[][] {
            {"SELECT LISTAGG(TYPEOF(f.value), ',') WITHIN GROUP (ORDER BY f.index) FROM ut,"
                + " LATERAL FLATTEN(input => ARRAY_CONSTRUCT(u, s, d, n, b, ts, tm, bo, f)) f", members},
            {"SELECT LISTAGG(TYPEOF(f.value), ',') WITHIN GROUP (ORDER BY f.index)"
                + " FROM (SELECT ARRAY_CONSTRUCT(u, s, d, n, b, ts, tm, bo, f) a FROM ut), LATERAL FLATTEN(input => a) f",
                members},
            {"SELECT TYPEOF(f.value), f.key FROM ut, LATERAL FLATTEN(input => OBJECT_CONSTRUCT('b', b)) f", "BINARY, b"},
            {"SELECT TYPEOF(f.value) FROM ut, LATERAL FLATTEN(input => OBJECT_CONSTRUCT('t', tm)) f", "TIME"},
        });
    }

    @Test
    public void aTypedMemberRendersComparesAndReaggregatesAsItsType() {
        assertCells(new String[][] {
            {"SELECT LISTAGG(f.value::VARCHAR, '|') WITHIN GROUP (ORDER BY f.index) FROM ut,"
                + " LATERAL FLATTEN(input => ARRAY_CONSTRUCT(u, s, d, n, b, ts, tm, bo, f)) f",
                UUID + "|x|2024-01-15|1.5|ABCD|2024-01-15 10:00:00.000|10:00:00|true|1.5"},
            {"SELECT f.value = '2024-01-15', IS_DATE(f.value) FROM ut, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(d)) f",
                "true, true"},
            {"SELECT TO_JSON(ARRAY_AGG(f.value)) FROM ut, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(d, ts, b)) f",
                "[\"2024-01-15\",\"2024-01-15 10:00:00.000\",\"ABCD\"]"},
            {"SELECT TYPEOF(ARRAY_AGG(f.value)[0]) FROM ut, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(d)) f", "DATE"},
        });
    }
}
