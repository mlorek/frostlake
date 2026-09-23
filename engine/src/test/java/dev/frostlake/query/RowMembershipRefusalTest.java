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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A membership test or a quantified comparison against a subquery of several columns compares a value with
 * the ROW of its items, and is refused while the statement compiles, whatever the rows: {@code a IN (SELECT
 * 1, 2)} is {@code Invalid argument types for function '=': (NUMBER(38,0), ROW(NUMBER(1,0), NUMBER(1,0)))} at
 * the IN — '!=' at the NOT of NOT IN, and the written operator at the operator of ANY or ALL. A tuple against
 * a subquery of one column is the ROW on the other side, and against as many columns it compares. EXISTS
 * takes any width. Every cell is live-verified.
 */
public class RowMembershipRefusalTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE re (a INT)");
        engine.execute("CREATE OR REPLACE TABLE ft (b INT, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE full1 (a INT)");
        engine.execute("INSERT INTO full1 VALUES (1)");
    }

    /** The first row's first cell, "no row", or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String rowRefusal(final int position, final String operator, final String types) {
        return "SQL compilation error: error line 1 at position " + position + "|Invalid argument types for function '"
            + operator + "': (" + types + ")";
    }

    @Test
    public void aValueAgainstSeveralColumnsIsARowComparison() {
        final String twoLiterals = "NUMBER(38,0), ROW(NUMBER(1,0), NUMBER(1,0))";
        final String[][] cells = {
            {"SELECT 'b' IN (SELECT 'abc','x')", rowRefusal(11, "=", "VARCHAR(1), ROW(VARCHAR(3), VARCHAR(1))")},
            {"SELECT 'b' NOT IN (SELECT 'abc','x')", rowRefusal(11, "!=", "VARCHAR(1), ROW(VARCHAR(3), VARCHAR(1))")},
            {"SELECT UPPER('b' IN (SELECT 'abc','x'), 1)", rowRefusal(17, "=", "VARCHAR(1), ROW(VARCHAR(3), VARCHAR(1))")},
            {"SELECT 1 FROM re WHERE a IN (SELECT 1, 2 FROM ft)", rowRefusal(25, "=", twoLiterals)},
            {"SELECT 1 FROM full1 WHERE a IN (SELECT 1, 2 FROM full1)", rowRefusal(28, "=", twoLiterals)},
            {"SELECT 1 FROM re WHERE a IN (SELECT d, d FROM ft)", rowRefusal(25, "=", "NUMBER(38,0), ROW(DATE, DATE)")},
            {"SELECT 1 FROM re WHERE a NOT IN (SELECT 1, 2 FROM ft)", rowRefusal(25, "!=", twoLiterals)},
            {"SELECT 1 FROM re WHERE a IN (WITH w AS (SELECT 1 AS x, 2 AS y) SELECT * FROM w)", rowRefusal(25, "=", twoLiterals)},
            {"SELECT 1 FROM re WHERE a IN (SELECT * FROM full1, full1 f2)",
                rowRefusal(25, "=", "NUMBER(38,0), ROW(NUMBER(38,0), NUMBER(38,0))")},
            {"SELECT 1 FROM re WHERE a IN (SELECT 1, 2)", rowRefusal(25, "=", twoLiterals)},
            {"SELECT a IN (SELECT 1, 2) FROM full1", rowRefusal(9, "=", twoLiterals)},
            {"SELECT 1 FROM re WHERE a IN (SELECT 1, 'x' FROM ft)", rowRefusal(25, "=", "NUMBER(38,0), ROW(NUMBER(1,0), VARCHAR(1))")},
            {"SELECT 1 FROM re WHERE a IN (SELECT 1, 2 FROM ft UNION ALL SELECT 3, 4 FROM ft)", rowRefusal(25, "=", twoLiterals)},
            {"SELECT CASE WHEN 1 IN (SELECT 1, 2) THEN 1 END", rowRefusal(19, "=", "NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0))")},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aQuantifiedComparisonNamesItsOperator() {
        final String twoLiterals = "NUMBER(38,0), ROW(NUMBER(1,0), NUMBER(1,0))";
        assertEquals(rowRefusal(25, "=", twoLiterals), answer("SELECT 1 FROM re WHERE a = ANY (SELECT 1, 2 FROM ft)"));
        assertEquals(rowRefusal(25, "<>", twoLiterals), answer("SELECT 1 FROM re WHERE a <> ALL (SELECT 1, 2 FROM ft)"));
        assertEquals(rowRefusal(25, ">", twoLiterals), answer("SELECT 1 FROM re WHERE a > ANY (SELECT 1, 2 FROM ft)"));
    }

    @Test
    public void aTupleAgainstOneColumnIsTheRowOnTheLeft() {
        assertEquals(rowRefusal(30, "=", "ROW(NUMBER(38,0), NUMBER(38,0)), NUMBER(1,0)"),
            answer("SELECT 1 FROM re WHERE (a, a) IN (SELECT 1 FROM ft)"));
        assertEquals("no row", answer("SELECT 1 FROM re WHERE (a, a) IN (SELECT 1, 2 FROM ft)"));
        assertEquals("no row", answer("SELECT 1 FROM re WHERE EXISTS (SELECT 1, 2 FROM ft)"));
        assertEquals("no row", answer("SELECT 1 FROM re WHERE a IN (SELECT 1 FROM ft UNION ALL SELECT 2 FROM ft)"));
        assertEquals("1", answer("SELECT 1 FROM full1 WHERE a IN (SELECT a FROM full1)"));
    }
}
