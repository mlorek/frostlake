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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Two ROW constructors compare element by element. The grammar had a row constructor only before IN,
 * so every one of these was a syntax error at the operator — accepting too little, which is the
 * direction that loses working SQL.
 *
 * <p>Equality reads the WHOLE row: one pair that differs makes it FALSE however many NULLs stand
 * beside it, and a NULL decides only when nothing else has. The ordering operators read it
 * LEXICOGRAPHICALLY and stop at the first pair that differs, so a NULL after that pair never matters.
 */
public class RowConstructorComparisonTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
    }

    /** The first column of the first row, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** Equality over the whole row, with NULL deciding only when nothing else does. */
    @Test
    public void equalityReadsTheWholeRow() {
        assertEquals("true", answer("SELECT (1, 2) = (1, 2)"));
        assertEquals("false", answer("SELECT (1, 2) = (1, 3)"));
        assertEquals("null", answer("SELECT (1, NULL) = (1, 2)"), "nothing else decided it");
        assertEquals("false", answer("SELECT (1, NULL) = (2, 2)"), "the first pair did");
    }

    /** Inequality is its mirror, NULL included. */
    @Test
    public void inequalityMirrorsIt() {
        assertEquals("false", answer("SELECT (1, 2) <> (1, 2)"));
        assertEquals("true", answer("SELECT (1, 2) != (1, 3)"));
        assertEquals("null", answer("SELECT (1, NULL) <> (1, 2)"));
        assertEquals("true", answer("SELECT (1, NULL) <> (2, 2)"));
    }

    /** The ordering operators read the row left to right and stop at the first difference. */
    @Test
    public void orderingIsLexicographic() {
        assertEquals("true", answer("SELECT (1, 2) < (1, 3)"));
        assertEquals("true", answer("SELECT (1, 2) < (2, 1)"), "the first pair decides");
        assertEquals("false", answer("SELECT (1, 2) < (1, 2)"));
        assertEquals("false", answer("SELECT (2, 1) < (1, 5)"));
        assertEquals("true", answer("SELECT (1, 2) <= (1, 2)"));
        assertEquals("true", answer("SELECT (1, 2) > (1, 1)"));
        assertEquals("false", answer("SELECT (1, 2) >= (1, 3)"));
    }

    /** A NULL past the deciding pair never matters; one AT it makes the answer unknown. */
    @Test
    public void aNullMattersOnlyWhereItDecides() {
        assertEquals("true", answer("SELECT (1, NULL) < (2, 1)"));
        assertEquals("null", answer("SELECT (1, NULL) < (1, 1)"));
        assertEquals("null", answer("SELECT (NULL, 1) < (1, 2)"));
    }

    /** The refusal a comparison raises, its lines joined by a bar. */
    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage().replace('\n', '|');
    }

    /**
     * A row opposite a scalar parses and is refused by TYPE at the operator; rows of two widths are refused at
     * the right one, naming both ROW types. A parenthesised single value is a scalar, not a row of one.
     */
    @Test
    public void rowsThatCannotBeComparedAreRefusedByType() {
        assertEquals("SQL compilation error: error line 1 at position 14|Invalid argument types for function '=': "
            + "(ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))", refusal("SELECT (1, 2) = 1"));
        assertEquals("SQL compilation error: error line 1 at position 9|Invalid argument types for function '=': "
            + "(NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))", refusal("SELECT 1 = (1, 2)"));
        assertEquals("SQL compilation error: error line 1 at position 14|Invalid argument types for function '=': "
            + "(ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))", refusal("SELECT (1, 2) = (1)"));
        assertEquals("SQL compilation error: error line 1 at position 11|Invalid argument types for function '=': "
            + "(NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))", refusal("SELECT (1) = (1, 2)"));
        assertEquals("SQL compilation error: error line 1 at position 11|Invalid argument types for function '<': "
            + "(VARCHAR(1), ROW(VARCHAR(1), VARCHAR(1)))", refusal("SELECT 'a' < ('a', 'b')"));
        assertEquals("SQL compilation error:|Can not convert parameter 'ROW(1, 2, 3)' of type [ROW(NUMBER(1,0), "
            + "NUMBER(1,0), NUMBER(1,0))] into expected type [ROW(NUMBER(1,0), NUMBER(1,0))]",
            refusal("SELECT (1, 2) = (1, 2, 3)"));
        assertEquals("SQL compilation error:|Can not convert parameter 'ROW(1, 2)' of type [ROW(NUMBER(1,0), "
            + "NUMBER(1,0))] into expected type [ROW(NUMBER(1,0), NUMBER(1,0), NUMBER(1,0))]",
            refusal("SELECT (1, 2, 3) = (1, 2)"));
        // Refused while the statement compiles: an empty table reads no row to evaluate it over.
        engine.execute("CREATE OR REPLACE TABLE rz (a INT, b VARCHAR)");
        assertEquals("SQL compilation error: error line 1 at position 30|Invalid argument types for function '=': "
            + "(ROW(NUMBER(38,0), VARCHAR(16777216)), NUMBER(38,0))", refusal("SELECT a FROM rz WHERE (a, b) = a"));
        assertEquals("true", answer("SELECT (1) = (1)"), "two parenthesised scalars compare as scalars");
    }

    /** A row comparison is a predicate of the plan, which SYSTEM$TYPEOF tags as it tags every comparison. */
    @Test
    public void itIsTypedAsAPredicate() {
        assertEquals("BOOLEAN[ROWINDEX]", answer("SELECT SYSTEM$TYPEOF((1, 2) = (1, 2))"));
    }

    /** It is an ordinary predicate: it filters, negates, parenthesises and nests. */
    @Test
    public void itIsAnOrdinaryPredicate() {
        assertEquals("5", answer("SELECT id FROM fz WHERE (id, b) = (5, TRUE)"));
        assertEquals("7", answer("SELECT id FROM fz WHERE (id, b) <> (5, TRUE)"));
        assertEquals("5", answer("SELECT id FROM fz WHERE (id, b) < (6, TRUE)"));
        assertEquals("7", answer("SELECT id FROM fz WHERE NOT (id, 1) = (5, 1)"));
        assertEquals("5", answer("SELECT id FROM fz WHERE ((id, 1) = (5, 1))"));
        assertEquals("true", answer("SELECT (1 + 1, 2) = (2, 2)"), "an element may be an expression");
        assertEquals("y", answer("SELECT CASE WHEN (1, 2) = (1, 2) THEN 'y' ELSE 'n' END"));
    }
}
