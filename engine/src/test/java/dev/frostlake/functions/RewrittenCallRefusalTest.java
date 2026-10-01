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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The text functions the account plans as other calls are refused in the words of the call the plan holds:
 * LEFT as SUBSTR over (x, 1, n), RIGHT as RIGHT2, BIT_LENGTH as OCTET_LENGTH, RTRIMMED_LENGTH as RTRIM, REPEAT's
 * text as LENGTH, INSERT by the part that reads the refused argument, DIV0NULL as DIV0 or ZEROIFNULL, CONCAT_WS as
 * CONCAT over its values and separators interleaved, and the RLIKE and REGEXP operators under their own names at
 * the operator. A BOOLEAN value is refused where a number is read and read as its text elsewhere, and a text count
 * converts on the row. Every cell is live-verified.
 */
public class RewrittenCallRefusalTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE rt (o OBJECT, n5 NUMBER(5,0), g VARCHAR(10), f FLOAT, b BOOLEAN)");
    }

    /** Every row's first cell, lower-cased and joined by a bar. */
    private String answer(final String sql) {
        final StringBuilder answer = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            answer.append(String.valueOf(row.getValue(0)).toLowerCase());
        }
        return answer.toString();
    }

    @Test
    public void aRewrittenCallIsRefusedInTheWordsOfItsPlan() {
        for (final String[] cell : new String[][] {
            {"SELECT LEFT([1,2,3]::VECTOR(FLOAT,3), 2)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (VECTOR(FLOAT, 3), NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT LEFT([1,2,3]::VECTOR(FLOAT,3), 20)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (VECTOR(FLOAT, 3), NUMBER(1,0), NUMBER(2,0))"},
            {"SELECT RIGHT([1,2,3]::VECTOR(FLOAT,3), 20)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RIGHT2': (VECTOR(FLOAT, 3), NUMBER(2,0))"},
            {"SELECT BIT_LENGTH([1,2,3]::VECTOR(FLOAT,3))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'OCTET_LENGTH': (VECTOR(FLOAT, 3))"},
            {"SELECT LEFT(OBJECT_CONSTRUCT(), 2)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (OBJECT, NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT BIT_LENGTH(OBJECT_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'OCTET_LENGTH': (OBJECT)"},
            {"SELECT LEFT((1 = 1), 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (BOOLEAN, NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT LEFT('abc', (1 = 1))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (VARCHAR(3), NUMBER(1,0), BOOLEAN)"},
            {"SELECT LEFT('abc', TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (VARCHAR(3), NUMBER(1,0), BOOLEAN)"},
            {"SELECT RIGHT((1 = 1), 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RIGHT2': (BOOLEAN, NUMBER(1,0))"},
            {"SELECT BIT_LENGTH((1 = 1))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'OCTET_LENGTH': (BOOLEAN)"},
            {"SELECT RTRIMMED_LENGTH((1 = 1))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RTRIM': (BOOLEAN)"},
            {"SELECT REPEAT((1 = 1), 2)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'LENGTH': (BOOLEAN)"},
            {"SELECT INSERT((1 = 1), 1, 1, 'x')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (BOOLEAN, NUMBER(1,0), NUMBER(2,0))"},
            {"SELECT INSERT('abc', 1, 1, (1 = 1))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function '||': (VARCHAR(3), BOOLEAN, VARCHAR(3))"},
            {"SELECT CONCAT_WS(',', (1 = 1))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'CONCAT': (BOOLEAN)"},
            {"SELECT CONCAT_WS(',', 'a', (1 = 1))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'CONCAT': (VARCHAR(1), VARCHAR(1), BOOLEAN)"},
            {"SELECT (1 = 1) RLIKE 'x'", "SQL compilation error: error line 1 at position 15\nInvalid argument types for function 'RLIKE': (BOOLEAN, VARCHAR(1))"},
            {"SELECT REPEAT('x', (1 = 1))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function '*': (BOOLEAN, NUMBER(18,0))"},
            {"SELECT SPACE((1 = 1))", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'LPAD': (VARCHAR(1), BOOLEAN, VARCHAR(1))"},
            {"SELECT LEFT(ARRAY_CONSTRUCT(), 2)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (ARRAY, NUMBER(1,0), NUMBER(1,0))"},
            {"SELECT RIGHT(ARRAY_CONSTRUCT(), 2)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RIGHT2': (ARRAY, NUMBER(1,0))"},
            {"SELECT BIT_LENGTH(ARRAY_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'OCTET_LENGTH': (ARRAY)"},
            {"SELECT RTRIMMED_LENGTH(ARRAY_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RTRIM': (ARRAY)"},
            {"SELECT DIV0NULL(ARRAY_CONSTRUCT(), 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DIV0': (ARRAY, NUMBER(2,0))"},
            {"SELECT DIV0NULL(1, ARRAY_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ZEROIFNULL': (ARRAY)"},
            {"SELECT RTRIMMED_LENGTH(X'00')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RTRIM': (BINARY(1))"},
            {"SELECT LEFT('abc', 'x')", "Numeric value 'x' is not recognized"},
            {"SELECT RIGHT('abc', ARRAY_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RIGHT2': (VARCHAR(3), ARRAY)"},
            {"SELECT LEFT('abc', 1, 2)", "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [LEFT('abc', 1, 2)] expected 2, got 3"},
            {"SELECT RIGHT('abc')", "SQL compilation error: error line 1 at position 7\nnot enough arguments for function [RIGHT('abc')], expected 2, got 1"},
            {"SELECT RTRIMMED_LENGTH(OBJECT_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RTRIM': (OBJECT)"},
            {"SELECT REPEAT(OBJECT_CONSTRUCT(), 2)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'LENGTH': (OBJECT)"},
            {"SELECT INSERT(OBJECT_CONSTRUCT(), 1, 1, 'x')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (OBJECT, NUMBER(1,0), NUMBER(2,0))"},
            {"SELECT DIV0NULL(OBJECT_CONSTRUCT(), 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DIV0': (OBJECT, NUMBER(2,0))"},
            {"SELECT DIV0(OBJECT_CONSTRUCT(), 1)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DIV0': (OBJECT, NUMBER(1,0))"},
            {"SELECT RIGHT([1,2,3]::VECTOR(FLOAT,3), 2)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RIGHT2': (VECTOR(FLOAT, 3), NUMBER(1,0))"},
            {"SELECT BIT_LENGTH('abc', 1)", "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [BIT_LENGTH('abc', 1)] expected 1, got 2"},
            {"SELECT RTRIMMED_LENGTH('abc', 1)", "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [RTRIMMED_LENGTH('abc', 1)] expected 1, got 2"},
            {"SELECT LEFT(NULL, OBJECT_CONSTRUCT())", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (NULL, NUMBER(1,0), OBJECT)"},
            {"SELECT DIV0NULL(o, n5) FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DIV0': (OBJECT, NUMBER(5,0))"},
            {"SELECT DIV0NULL(o, 2.5) FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DIV0': (OBJECT, NUMBER(3,1))"},
            {"SELECT DIV0NULL(o, f) FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'DIV0': (OBJECT, FLOAT)"},
            {"SELECT DIV0NULL(n5, o) FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ZEROIFNULL': (OBJECT)"},
            {"SELECT DIV0NULL(o, o) FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ZEROIFNULL': (OBJECT)"},
            {"SELECT INSERT(o, n5, 1, 'x') FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (OBJECT, NUMBER(1,0), NUMBER(6,0))"},
            {"SELECT INSERT(o, 10, 1, 'x') FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (OBJECT, NUMBER(1,0), NUMBER(3,0))"},
            {"SELECT INSERT(g, 1, 1, o) FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function '||': (VARCHAR(10), OBJECT, VARCHAR(10))"},
            {"SELECT INSERT('abc', n5, 1, o) FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function '||': (VARCHAR(3), OBJECT, VARCHAR(3))"},
            {"SELECT INSERT(o, 1, n5, 'x') FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (OBJECT, NUMBER(1,0), NUMBER(2,0))"},
            {"SELECT INSERT('abc', o, 1, 'x') FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function '-': (OBJECT, NUMBER(1,0))"},
            {"SELECT INSERT('abc', 1, o, 'x') FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function '+': (NUMBER(1,0), OBJECT)"},
            {"SELECT (1 = 1) RLIKE 'x'", "SQL compilation error: error line 1 at position 15\nInvalid argument types for function 'RLIKE': (BOOLEAN, VARCHAR(1))"},
            {"SELECT o RLIKE 'x' FROM rt", "SQL compilation error: error line 1 at position 9\nInvalid argument types for function 'RLIKE': (OBJECT, VARCHAR(1))"},
            {"SELECT 'x' RLIKE o FROM rt", "SQL compilation error: error line 1 at position 11\nInvalid argument types for function 'RLIKE': (VARCHAR(1), OBJECT)"},
            {"SELECT o NOT RLIKE 'x' FROM rt", "SQL compilation error: error line 0 at position -1\nInvalid argument types for function 'RLIKE': (OBJECT, VARCHAR(1))"},
            {"SELECT o REGEXP 'x' FROM rt", "SQL compilation error: error line 1 at position 9\nInvalid argument types for function 'REGEXP': (OBJECT, VARCHAR(1))"},
            {"SELECT RLIKE(o, 'x') FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RLIKE': (OBJECT, VARCHAR(1))"},
            {"SELECT REGEXP_LIKE(o, 'x') FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'REGEXP_LIKE': (OBJECT, VARCHAR(1))"},
            {"SELECT REPEAT(o, n5) FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'LENGTH': (OBJECT)"},
            {"SELECT RIGHT(g, o) FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RIGHT2': (VARCHAR(10), OBJECT)"},
            {"SELECT LEFT(g, o) FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (VARCHAR(10), NUMBER(1,0), OBJECT)"},
            {"SELECT LEFT(o, n5) FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUBSTR': (OBJECT, NUMBER(1,0), NUMBER(5,0))"},
            {"SELECT CONCAT_WS(g, o) FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'CONCAT': (OBJECT)"},
            {"SELECT CONCAT_WS(',', o, 'a') FROM rt", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'CONCAT': (OBJECT, VARCHAR(1), VARCHAR(1))"},
            {"SELECT LEFT('abc', 'x')", "Numeric value 'x' is not recognized"},
            {"SELECT RIGHT('abc', TRUE)", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RIGHT2': (VARCHAR(3), BOOLEAN)"},
            {"SELECT INSERT('abc', TRUE, 1, 'x')", "SQL compilation error: error line 1 at position 7\nInvalid argument types for function '-': (BOOLEAN, NUMBER(1,0))"},
        }) {
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(cell[0]);
                }
            }, cell[0]);
            assertTrue(String.valueOf(refused.getMessage()).contains(cell[1]),
                cell[0] + " should be refused with [" + cell[1] + "] but read: " + refused.getMessage());
        }
    }

    @Test
    public void whatTheRewriteTakesStillAnswers() {
        for (final String[] cell : new String[][] {
            {"SELECT BIT_LENGTH(X'00')", "8"},
            {"SELECT LEFT(TO_DATE('2020-01-01'), 2)", "20"},
            {"SELECT SYSTEM$TYPEOF(ZEROIFNULL(1))", "number(2,0)[sb1]"},
            {"SELECT INSERT(g, 1, 1, b) FROM rt", ""},
            {"SELECT CONCAT_WS(o, g) FROM rt", ""},
            {"SELECT RTRIMMED_LENGTH(b) FROM rt", ""},
            {"SELECT BIT_LENGTH(b) FROM rt", ""},
            {"SELECT LEFT('abc', '2')", "ab"},
            {"SELECT RIGHT('abc', '2')", "bc"},
            {"SELECT LEFT(TRUE, 1)", "t"},
            {"SELECT RIGHT(TRUE, 1)", "e"},
            {"SELECT BIT_LENGTH(TRUE)", "32"},
            {"SELECT RTRIMMED_LENGTH(TRUE)", "4"},
            {"SELECT REPEAT(TRUE, 2)", "truetrue"},
        }) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }
}
