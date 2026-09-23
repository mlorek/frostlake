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
 * A subquery selecting more than one column is typed as the ROW of its items where an operator or a
 * function takes it, and refused there in the argument-type sentence at the operator or the call: a
 * comparison, arithmetic, IS NULL, BETWEEN (as its two comparisons), a function, a two-argument COALESCE
 * (named IFNULL), inside AND, NOT and CASE too. An UPDATE's SET matches it against the column as a ROW. A
 * subquery standing alone, or on both sides of a comparison, keeps the multi-column sentence. Every cell is
 * live-verified.
 */
public class RowTypedSubqueryTest extends BaseDatabaseTest {

    /** The ROW type of a subquery selecting two one-digit numbers. */
    private static final String TWO_ONES = "ROW(NUMBER(1,0), NUMBER(1,0))";

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE DATABASE ROW_SUBQUERY_DB");
        engine.execute("CREATE OR REPLACE TABLE T (a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE FULL_T (a INT, b INT)");
        engine.execute("INSERT INTO FULL_T VALUES (1, 2)");
    }

    @AfterEach
    public void dropTables() {
        engine.execute("DROP DATABASE IF EXISTS ROW_SUBQUERY_DB");
    }

    /** The first row's first cell, empty for no rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String at(final int position, final String detail) {
        return "SQL compilation error: error line 1 at position " + position + "|" + detail;
    }

    @Test
    public void anOperatorRefusesTheRowWhereItStands() {
        assertEquals(at(21, "Invalid argument types for function '=': (" + TWO_ONES + ", NUMBER(1,0))"),
            answer("SELECT (SELECT 1, 2) = 5"));
        assertEquals(at(9, "Invalid argument types for function '=': (NUMBER(1,0), " + TWO_ONES + ")"),
            answer("SELECT 5 = (SELECT 1, 2)"));
        assertEquals(at(21, "Invalid argument types for function '<>': (" + TWO_ONES + ", NUMBER(1,0))"),
            answer("SELECT (SELECT 1, 2) <> 5"));
        assertEquals(at(21, "Invalid argument types for function '<': (" + TWO_ONES + ", NUMBER(1,0))"),
            answer("SELECT (SELECT 1, 2) < 5"));
        assertEquals(at(21, "Invalid argument types for function '>=': (" + TWO_ONES + ", NUMBER(1,0))"),
            answer("SELECT (SELECT 1, 2) >= 5"));
        assertEquals(at(9, "Invalid argument types for function '>=': (NUMBER(1,0), " + TWO_ONES + ")"),
            answer("SELECT 5 BETWEEN (SELECT 1, 2) AND 7"));
        assertEquals(at(21, "Invalid argument types for function '>=': (" + TWO_ONES + ", NUMBER(1,0))"),
            answer("SELECT (SELECT 1, 2) BETWEEN 1 AND 7"));
        assertEquals(at(21, "Invalid argument types for function 'IS NULL': (" + TWO_ONES + ")"),
            answer("SELECT (SELECT 1, 2) IS NULL"));
        assertEquals(at(21, "Invalid argument types for function '+': (" + TWO_ONES + ", NUMBER(1,0))"),
            answer("SELECT (SELECT 1, 2) + 1"));
        assertEquals(at(23, "Invalid argument types for function '=': (ROW(NUMBER(1,0), VARCHAR(1)), NUMBER(1,0))"),
            answer("SELECT (SELECT 1, 'x') = 5"));
        assertEquals(at(27, "Invalid argument types for function '=': (ROW(NULL, NULL), NUMBER(1,0))"),
            answer("SELECT (SELECT NULL, NULL) = 5"));
        assertEquals(at(27, "Invalid argument types for function '=': (ROW(NUMBER(2,1), VARCHAR(3)), NUMBER(1,0))"),
            answer("SELECT (SELECT 1.5, 'abc') = 5"));
        assertEquals(at(21, "Invalid argument types for function '=': (" + TWO_ONES + ", NUMBER(1,0))"),
            answer("SELECT (SELECT 1, 2) = 5 AND TRUE"));
        assertEquals(at(26, "Invalid argument types for function '=': (" + TWO_ONES + ", NUMBER(1,0))"),
            answer("SELECT NOT ((SELECT 1, 2) = 5)"));
        assertEquals(at(31, "Invalid argument types for function '=': (" + TWO_ONES + ", NUMBER(1,0))"),
            answer("SELECT CASE WHEN (SELECT 1, 2) = 5 THEN 1 END"));
        assertEquals(at(41, "Invalid argument types for function '=': (" + TWO_ONES + ", NUMBER(38,0))"),
            answer("SELECT a FROM FULL_T WHERE (SELECT 1, 2) = a"));
        assertEquals(at(24, "Invalid argument types for function '=': (NUMBER(38,0), ROW(NUMBER(38,0), NUMBER(38,0)))"),
            answer("SELECT a FROM T WHERE a = (SELECT a, b FROM FULL_T)"));
        assertEquals(at(33, "Invalid argument types for function '=': (ROW(NUMBER(38,0), NUMBER(38,0)), NUMBER(1,0))"),
            answer("SELECT (SELECT a, b FROM FULL_T) = 5"));
    }

    @Test
    public void aFunctionRefusesTheRowAtItsCall() {
        assertEquals(at(7, "Invalid argument types for function 'ABS': (" + TWO_ONES + ")"), answer("SELECT ABS(SELECT -1, 2)"));
        assertEquals(at(7, "Invalid argument types for function 'ABS': (" + TWO_ONES + ")"), answer("SELECT ABS((SELECT -1, 2))"));
        assertEquals(at(7, "Invalid argument types for function 'UPPER': (ROW(VARCHAR(1), VARCHAR(1)))"),
            answer("SELECT UPPER((SELECT 'a', 'b'))"));
        assertEquals(at(7, "Invalid argument types for function 'IFNULL': (" + TWO_ONES + ", NUMBER(1,0))"),
            answer("SELECT COALESCE((SELECT 1, 2), 5)"));
    }

    @Test
    public void aRowStandingAloneKeepsTheMultiColumnSentence() {
        assertEquals(at(8, "Unsupported: Scalar subquery with multi-column SELECT clause."), answer("SELECT (SELECT 1, 2)"));
        assertEquals(at(8, "Unsupported: Scalar subquery with multi-column SELECT clause."),
            answer("SELECT (SELECT 1, 2) = (SELECT 1, 2)"));
    }

    @Test
    public void anUpdateMatchesTheRowAgainstItsColumn() {
        assertEquals("SQL compilation error:|Expression type does not match column data type, expecting NUMBER(38,0) but got"
            + " ROW(NUMBER(38,0), NUMBER(38,0)) for column A", answer("UPDATE T SET a = (SELECT a, b FROM FULL_T)"));
        assertEquals("SQL compilation error:|Expression type does not match column data type, expecting NUMBER(38,0) but got"
            + " ROW(NUMBER(1,0), VARCHAR(1)) for column A", answer("UPDATE T SET a = (SELECT 1, 'x')"));
    }
}
