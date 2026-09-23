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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A number or boolean constant wrapped into a VARIANT converts to a sized VARCHAR unchecked when it is read
 * through a relation, as the wrap written in place does: live folds it through a CTE, a view, a derived table
 * and a scalar subquery, across a WHERE, a join, a LIMIT, an ORDER BY, a GROUP BY and a column list. An
 * aggregate over it, a union of two wraps, the side an outer join extends, a computed wrap and a stored table
 * are checked. Every cell is live-verified.
 */
public class FoldedVariantWrapCastTest extends BaseDatabaseTest {

    private static final String TOO_LONG = "String '123' is too long and would be truncated";

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE OR REPLACE VIEW wrap_view AS SELECT TO_VARIANT(123) AS v");
        engine.execute("CREATE OR REPLACE TABLE wrap_table AS SELECT TO_VARIANT(123) AS v");
    }

    @AfterEach
    public void dropRelations() {
        engine.execute("DROP VIEW IF EXISTS wrap_view");
        engine.execute("DROP TABLE IF EXISTS wrap_table");
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
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void aWrapReadThroughARelationConvertsUnchecked() {
        assertEquals("123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v) SELECT v::VARCHAR(1) FROM c"));
        assertEquals("123", answer("SELECT (SELECT TO_VARIANT(123))::VARCHAR(1)"));
        assertEquals("123", answer("SELECT v::VARCHAR(1) FROM wrap_view"));
        assertEquals("123", answer("SELECT v::VARCHAR(1) FROM (SELECT TO_VARIANT(123) AS v)"));
        assertEquals("123|123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v FROM (VALUES (1),(2))) SELECT v::VARCHAR(1) FROM c"));
        assertEquals("123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v), d AS (SELECT v FROM c) SELECT v::VARCHAR(1) FROM d"));
        assertEquals("123", answer("WITH c AS (SELECT 123::VARIANT AS v) SELECT CAST(v AS VARCHAR(1)) FROM c"));
        assertEquals("1234.5", answer("WITH c AS (SELECT TO_VARIANT(1234.5) AS v) SELECT v::VARCHAR(2) FROM c"));
        assertEquals("true", answer("WITH c AS (SELECT TO_VARIANT(TRUE) AS v) SELECT v::VARCHAR(1) FROM c"));
        assertEquals("-5", answer("WITH c AS (SELECT TO_VARIANT(-5) AS v) SELECT v::VARCHAR(1) FROM c"));
        assertEquals("123", answer("SELECT (SELECT TO_VARIANT(123) AS v LIMIT 1)::VARCHAR(1)"));
        assertEquals("123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v) SELECT (SELECT v FROM c)::VARCHAR(1)"));
        assertEquals("123", answer("WITH c AS (SELECT IFF(TRUE, TO_VARIANT(123), NULL) AS v) SELECT v::VARCHAR(1) FROM c"));
    }

    @Test
    public void theFoldHoldsAcrossTheQueryAroundIt() {
        assertEquals("123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v, 1 AS k) SELECT v::VARCHAR(1) FROM c WHERE k = 1"));
        assertEquals("123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v) SELECT c.v::VARCHAR(1) FROM c, c c2"));
        assertEquals("123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v) SELECT v::VARCHAR(1) FROM c GROUP BY v"));
        assertEquals("123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v) SELECT DISTINCT v::VARCHAR(1) FROM c"));
        assertEquals("123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v) SELECT v::VARCHAR(1) FROM c ORDER BY 1"));
        assertEquals("123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v) SELECT v::VARCHAR(1) FROM c LEFT JOIN (SELECT 1 AS k) d ON FALSE"));
        assertEquals("123", answer("WITH c (w) AS (SELECT TO_VARIANT(123)) SELECT w::VARCHAR(1) FROM c"));
        assertEquals("123", answer("SELECT v::VARCHAR(1) FROM (SELECT TO_VARIANT(123) AS v) AS d(v)"));
        assertEquals("123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v) SELECT IFF(TRUE, v, NULL)::VARCHAR(1) FROM c"));
        assertEquals("123", answer("WITH c AS (SELECT TO_VARIANT(123) AS v) SELECT TO_VARIANT(v)::VARCHAR(1) FROM c"));
    }

    @Test
    public void anythingThatIsNotTheFoldedWrapIsChecked() {
        assertEquals(TOO_LONG, answer("WITH c AS (SELECT TO_VARIANT(123) AS v) SELECT MAX(v)::VARCHAR(1) FROM c"));
        assertEquals(TOO_LONG, answer("WITH c AS (SELECT TO_VARIANT(123) AS v UNION ALL SELECT TO_VARIANT(456)) SELECT v::VARCHAR(1) FROM c"));
        assertEquals(TOO_LONG, answer("WITH c AS (SELECT TO_VARIANT(123) AS v) SELECT d.v::VARCHAR(1) FROM (SELECT 1 AS k) x LEFT JOIN c d ON TRUE"));
        assertEquals(TOO_LONG, answer("WITH c AS (SELECT TO_VARIANT(123::NUMBER(10,2)) AS v) SELECT v::VARCHAR(1) FROM c"));
        assertEquals(TOO_LONG, answer("WITH c AS (SELECT TO_VARIANT(123) AS v) SELECT (SELECT MAX(v) FROM c)::VARCHAR(1)"));
        assertEquals(TOO_LONG, answer("SELECT v::VARCHAR(1) FROM wrap_table"));
        assertEquals("String '1.414213562' is too long and would be truncated",
            answer("SELECT (SELECT TO_VARIANT(SQRT(2)))::VARCHAR(3)"));
        assertEquals("String 'abcdef' is too long and would be truncated",
            answer("WITH c AS (SELECT TO_VARIANT('abcdef') AS v) SELECT v::VARCHAR(1) FROM c"));
    }
}
