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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A recursive term may hold no aggregate function (Snowflake's WITH documentation), so one written in its HAVING is
 * refused as one in its select list is, for a CTE and a RECURSIVE view alike, once the select list has been judged
 * against the grouping the aggregate makes: a bare column beside it is the grouping refusal first (live-verified). A
 * HAVING that writes no aggregate is a filter, as in any select without a GROUP BY, and a window in the QUALIFY stays
 * the window refusal.
 */
public class RecursiveTermAggregateClausesTest {

    private static final String AGGREGATE = "REFUSED SQL compilation error:\nAggregate functions are not allowed in a "
        + "CTEs recursive term.";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE DATABASE d");
        engine.execute("USE SCHEMA PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** What a statement answers: its rows, or its refusal. */
    private String answer(final String sql) {
        final ResultSet rs;
        try {
            rs = engine.executeQuery(sql);
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            out.append(out.length() == 0 ? "" : " ;; ").append(row.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void anAggregateInTheTermsHavingIsRefused() {
        assertEquals(AGGREGATE, answer("WITH r(n) AS (SELECT 1 UNION ALL SELECT 2 FROM r WHERE n < 3 "
            + "HAVING COUNT(*) > 0) SELECT * FROM r"));
        assertEquals(AGGREGATE, answer("WITH r(n) AS (SELECT 1 WHERE FALSE UNION ALL SELECT 2 FROM r WHERE n < 3 "
            + "HAVING COUNT(*) > 0) SELECT * FROM r"));
        assertEquals(AGGREGATE, answer("CREATE RECURSIVE VIEW rv9 (n) AS SELECT 1 UNION ALL SELECT 2 FROM rv9 "
            + "WHERE n < 3 HAVING COUNT(*) > 0"));
    }

    @Test
    public void aBareColumnBesideTheAggregateIsTheGroupingRefusal() {
        assertEquals("REFUSED SQL compilation error:\n[R.N] is not a valid group by expression",
            answer("WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 HAVING COUNT(*) > 0) "
                + "SELECT * FROM r"));
    }

    @Test
    public void aHavingWithNoAggregateFiltersTheStep() {
        assertEquals("1 ;; 10 ;; 11 ;; 12", answer("WITH r(n) AS (SELECT column1 FROM VALUES (1), (10) UNION ALL "
            + "SELECT n + 1 FROM r WHERE n < 12 HAVING n > 5) SELECT * FROM r ORDER BY n"));
        assertEquals("REFUSED SQL compilation error:\nWindow functions are not allowed in a CTEs recursive term.",
            answer("WITH r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 "
                + "QUALIFY ROW_NUMBER() OVER (ORDER BY n) = 1) SELECT * FROM r"));
    }
}
