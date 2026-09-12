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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ranking and positional window functions REQUIRE an ORDER BY in their window specification.
 * Frostlake answered instead — {@code ROW_NUMBER() OVER ()} returned 1,2,3 and
 * {@code RANK() OVER ()} returned 1,1,1 — numbers with no ordering to mean anything by.
 *
 * <p>Three things the measurement settled, none of them guessable:
 *
 * <pre>
 *   the list is PER FUNCTION      eleven demand it; the aggregates used as windows do not
 *   PARTITION BY does not satisfy it   OVER (PARTITION BY a) is refused exactly like OVER ()
 *   the refusal is COMPILE-time   an empty table refuses too, so a view over one cannot be created
 * </pre>
 */
public class WindowOrderByRequiredTest extends BaseDatabaseTest {

    /** Every function that demands an ORDER BY, with an argument list that parses. */
    private static final String[] REQUIRING = {
        "ROW_NUMBER()", "RANK()", "DENSE_RANK()", "PERCENT_RANK()", "CUME_DIST()",
        "NTILE(2)", "LAG(a)", "LEAD(a)", "FIRST_VALUE(a)", "LAST_VALUE(a)", "NTH_VALUE(a, 1)",
    };

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (a INT)");
        engine.execute("INSERT INTO gw VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE gwempty (a INT)");
    }

    /** The rows joined, or the message of the refusal. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder();
            while (rs.next()) {
                if (all.length() > 0) {
                    all.append(",");
                }
                all.append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** The name in the brackets is the function's own, on all eleven. */
    private String required(final String call) {
        return "SQL compilation error: Window function type ["
            + call.substring(0, call.indexOf('(')).toUpperCase()
            + "] requires ORDER BY in window specification.";
    }

    /** A bare OVER () is refused, naming the function. */
    @Test
    public void aBareWindowIsRefused() {
        for (final String call : REQUIRING) {
            assertEquals(required(call), outcome("SELECT " + call + " OVER () AS x FROM gw"), call);
        }
    }

    /** And PARTITION BY does not satisfy it — the shape most likely written by mistake. */
    @Test
    public void aPartitionOnlyWindowIsRefusedToo() {
        for (final String call : REQUIRING) {
            assertEquals(required(call),
                outcome("SELECT " + call + " OVER (PARTITION BY a) AS x FROM gw"), call);
        }
    }

    /** With an ORDER BY they all answer. */
    @Test
    public void anOrderedWindowAnswers() {
        assertEquals("1,2,3", outcome("SELECT ROW_NUMBER() OVER (ORDER BY a) AS x FROM gw"));
        assertEquals("1,2,3", outcome("SELECT RANK() OVER (ORDER BY a) AS x FROM gw"));
        assertEquals("1,2,3", outcome("SELECT DENSE_RANK() OVER (ORDER BY a) AS x FROM gw"));
        assertEquals("null,1,2", outcome("SELECT LAG(a) OVER (ORDER BY a) AS x FROM gw"));
        assertEquals("1,1,1", outcome("SELECT FIRST_VALUE(a) OVER (ORDER BY a) AS x FROM gw"));
    }

    /** The aggregates used as windows need no ORDER BY, and must keep answering without one. */
    @Test
    public void theAggregateWindowsAreUnaffected() {
        assertEquals("6,6,6", outcome("SELECT SUM(a) OVER () AS x FROM gw"));
        assertEquals("3,3,3", outcome("SELECT COUNT(*) OVER () AS x FROM gw"));
        assertEquals("3,3,3", outcome("SELECT MAX(a) OVER () AS x FROM gw"));
        assertEquals("1,1,1", outcome("SELECT MIN(a) OVER () AS x FROM gw"));
        assertEquals("1,2,3", outcome("SELECT SUM(a) OVER (PARTITION BY a) AS x FROM gw ORDER BY a"));
    }

    /** The refusal is decided at PLAN time, so an EMPTY input refuses just the same. */
    @Test
    public void anEmptyInputRefusesJustTheSame() {
        assertEquals(required("ROW_NUMBER()"),
            outcome("SELECT ROW_NUMBER() OVER () AS x FROM gwempty"));
        assertEquals(required("RANK()"), outcome("SELECT RANK() OVER () AS x FROM gwempty"));
        assertEquals(required("ROW_NUMBER()"),
            outcome("SELECT ROW_NUMBER() OVER () AS x FROM gw WHERE 1 = 0"),
            "no row survives the filter, and it still refuses");
    }

    /** And the grouped shape the defect was first seen in. */
    @Test
    public void theGroupedShapeIsRefused() {
        assertEquals(required("ROW_NUMBER()"),
            outcome("SELECT ROW_NUMBER() OVER () AS x FROM gw GROUP BY a"));
    }
}
