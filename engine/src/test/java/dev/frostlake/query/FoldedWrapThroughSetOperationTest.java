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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A number or boolean constant wrapped into a VARIANT still converts to a sized VARCHAR unchecked when it is
 * read through a set operation whose every arm projects that same constant, and through FIRST_VALUE or
 * LAST_VALUE over it. Arms holding different constants, LAG, LEAD, NTH_VALUE and the aggregates stay checked,
 * and a scalar subquery folds only a single projection: a set operator or an ORDER BY at its top, or a LIMIT,
 * FETCH or TOP over a FROM, keeps it checked. Every cell is live-verified.
 */
public class FoldedWrapThroughSetOperationTest extends BaseDatabaseTest {

    private static final String TOO_LONG = "String '123' is too long and would be truncated";

    private static final String WRAP = "WITH c AS (SELECT TO_VARIANT(123) AS v) ";

    @AfterEach
    public void dropView() {
        engine.execute("DROP VIEW IF EXISTS fwso_view");
    }

    /** Every row's first cell, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                if (out.length() > 0) {
                    out.append('|');
                }
                out.append(row.getValue(0));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            final String message = String.valueOf(refused.getMessage());
            return message.contains(TOO_LONG) ? TOO_LONG : message.replace('\n', '|');
        }
    }

    @Test
    public void armsProjectingOneWrapConvertUnchecked() {
        assertEquals("123|123", answer(WRAP
            + ", d AS (SELECT v FROM c UNION ALL SELECT v FROM c) SELECT v::VARCHAR(1) FROM d"));
        assertEquals("123", answer(WRAP + "SELECT v::VARCHAR(1) FROM (SELECT v FROM c UNION SELECT v FROM c)"));
        assertEquals("123|123|123", answer(WRAP
            + "SELECT v::VARCHAR(1) FROM (SELECT v FROM c UNION ALL SELECT v FROM c UNION ALL SELECT v FROM c)"));
        assertEquals("123", answer(WRAP + "SELECT v::VARCHAR(1) FROM (SELECT v FROM c INTERSECT SELECT v FROM c)"));
        assertEquals("123|123", answer(WRAP
            + "SELECT v::VARCHAR(1) FROM (SELECT v FROM c UNION ALL SELECT TO_VARIANT(123))"));
        assertEquals("123|123", answer("WITH c1 AS (SELECT TO_VARIANT(123) AS v), c2 AS (SELECT TO_VARIANT(123) AS v)"
            + " SELECT v::VARCHAR(1) FROM (SELECT v FROM c1 UNION ALL SELECT v FROM c2)"));
        assertEquals("123|123", answer("SELECT v::VARCHAR(1) FROM (SELECT TO_VARIANT(123) AS v UNION ALL SELECT TO_VARIANT(123))"));
        assertEquals("123|123", answer("SELECT v::VARCHAR(1) FROM (SELECT TO_VARIANT(123) AS v UNION ALL SELECT 123::VARIANT)"));
        assertEquals("123|123", answer("SELECT v::VARCHAR(1) FROM (SELECT 123::VARIANT AS v UNION ALL SELECT TO_VARIANT(123))"));
        assertEquals("1.5|1.5", answer("SELECT v::VARCHAR(1) FROM (SELECT TO_VARIANT(1.5) AS v UNION ALL SELECT TO_VARIANT(1.5))"));
        assertEquals("true|true", answer("WITH c AS (SELECT TO_VARIANT(TRUE) AS v)"
            + " SELECT v::VARCHAR(1) FROM (SELECT v FROM c UNION ALL SELECT v FROM c)"));
        assertEquals("123|123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v, 1 AS k)"
            + " SELECT v::VARCHAR(1) FROM (SELECT v, k FROM c UNION ALL SELECT v, k FROM c)"));
        assertEquals("123", answer("SELECT v::VARCHAR(1) FROM (SELECT TO_VARIANT(123) AS v UNION ALL SELECT TO_VARIANT(123)) LIMIT 1"));
        assertEquals("123", answer("SELECT v::VARCHAR(1) FROM (SELECT v FROM (SELECT TO_VARIANT(123) AS v"
            + " UNION ALL SELECT TO_VARIANT(123)) LIMIT 1)"));
        engine.execute("CREATE OR REPLACE VIEW fwso_view AS SELECT TO_VARIANT(123) AS v UNION ALL SELECT TO_VARIANT(123)");
        assertEquals("123|123", answer("SELECT v::VARCHAR(1) FROM fwso_view"));
        assertEquals("", answer(WRAP + "SELECT v::VARCHAR(1) FROM (SELECT v FROM c EXCEPT SELECT v FROM c)"));
    }

    @Test
    public void armsHoldingDifferentConstantsStayChecked() {
        assertEquals(TOO_LONG, answer("SELECT v::VARCHAR(1) FROM (SELECT TO_VARIANT(123) AS v UNION ALL SELECT TO_VARIANT(456))"));
        assertEquals(TOO_LONG, answer("SELECT v::VARCHAR(1) FROM (SELECT TO_VARIANT(123) AS v UNION ALL SELECT TO_VARIANT(123)"
            + " UNION ALL SELECT TO_VARIANT(456))"));
    }

    @Test
    public void firstAndLastValueReadTheWrap() {
        assertEquals("123", answer(WRAP + "SELECT FIRST_VALUE(v) OVER (ORDER BY 1)::VARCHAR(1) FROM c"));
        assertEquals("123", answer(WRAP + "SELECT LAST_VALUE(v) OVER (ORDER BY 1)::VARCHAR(1) FROM c"));
        assertEquals("123", answer(WRAP + "SELECT FIRST_VALUE(v) OVER (PARTITION BY 1 ORDER BY 1)::VARCHAR(1) FROM c"));
        assertEquals("123", answer(WRAP + "SELECT LAST_VALUE(v) OVER (ORDER BY 1 ROWS BETWEEN UNBOUNDED PRECEDING"
            + " AND UNBOUNDED FOLLOWING)::VARCHAR(1) FROM c"));
        assertEquals("123", answer(WRAP + "SELECT FIRST_VALUE(v) IGNORE NULLS OVER (ORDER BY 1)::VARCHAR(1) FROM c"));
        assertEquals("123", answer(WRAP + "SELECT x::VARCHAR(1) FROM (SELECT FIRST_VALUE(v) OVER (ORDER BY 1) AS x FROM c)"));
        assertEquals("123|123", answer(WRAP + "SELECT FIRST_VALUE(v) OVER (ORDER BY 1)::VARCHAR(1)"
            + " FROM (SELECT v FROM c UNION ALL SELECT v FROM c)"));
        assertEquals(TOO_LONG, answer("SELECT FIRST_VALUE(v) OVER (ORDER BY 1)::VARCHAR(1)"
            + " FROM (SELECT TO_VARIANT(123) AS v UNION ALL SELECT TO_VARIANT(456))"));
    }

    @Test
    public void otherWindowsAndAggregatesStayChecked() {
        assertEquals(TOO_LONG, answer(WRAP + "SELECT NTH_VALUE(v, 1) OVER (ORDER BY 1)::VARCHAR(1) FROM c"));
        assertEquals(TOO_LONG, answer("WITH c AS (SELECT TO_VARIANT(123) AS v UNION ALL SELECT TO_VARIANT(123))"
            + " SELECT LEAD(v) OVER (ORDER BY 1)::VARCHAR(1) FROM c"));
        assertEquals(TOO_LONG, answer(WRAP + "SELECT ANY_VALUE(v)::VARCHAR(1) FROM c"));
        assertEquals(TOO_LONG, answer(WRAP + "SELECT MIN(v)::VARCHAR(1) FROM c"));
    }

    @Test
    public void aScalarSubqueryFoldsOnlyASingleProjection() {
        assertEquals("123", answer(WRAP + "SELECT (SELECT v FROM c)::VARCHAR(1)"));
        assertEquals("123", answer("SELECT (SELECT TO_VARIANT(123) AS v LIMIT 1)::VARCHAR(1)"));
        assertEquals("123", answer("SELECT (SELECT TOP 1 TO_VARIANT(123) AS v)::VARCHAR(1)"));
        assertEquals("123", answer("SELECT (SELECT TO_VARIANT(123) AS v FETCH FIRST 1 ROW ONLY)::VARCHAR(1)"));
        assertEquals("123", answer("SELECT (SELECT v FROM (SELECT TO_VARIANT(123) AS v))::VARCHAR(1)"));
        assertEquals(TOO_LONG, answer(WRAP + "SELECT (SELECT v FROM c LIMIT 1)::VARCHAR(1)"));
        assertEquals(TOO_LONG, answer(WRAP + "SELECT (SELECT v FROM c ORDER BY 1)::VARCHAR(1)"));
        assertEquals(TOO_LONG, answer("SELECT (SELECT TO_VARIANT(123) AS v ORDER BY 1)::VARCHAR(1)"));
        assertEquals(TOO_LONG, answer(WRAP + "SELECT (SELECT TO_VARIANT(123) FROM c LIMIT 1)::VARCHAR(1)"));
        assertEquals(TOO_LONG, answer("SELECT (SELECT v FROM (SELECT TO_VARIANT(123) AS v) LIMIT 1)::VARCHAR(1)"));
        assertEquals(TOO_LONG, answer("SELECT (SELECT TO_VARIANT(123) UNION SELECT TO_VARIANT(123))::VARCHAR(1)"));
        assertEquals(TOO_LONG, answer(WRAP + "SELECT (SELECT v FROM c UNION SELECT v FROM c)::VARCHAR(1)"));
    }
}
