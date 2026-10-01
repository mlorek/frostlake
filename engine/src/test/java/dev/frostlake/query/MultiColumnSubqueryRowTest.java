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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A scalar subquery of several columns is typed as the ROW of its items wherever a type is asked of it. A cast of it
 * is refused in the conversion's words, a call counts it as one argument, and an INSERT matches it against its column
 * like any other value. Frostlake refused its column count first in all three (live-verified).
 */
public class MultiColumnSubqueryRowTest extends BaseDatabaseTest {

    private static final String MISMATCH = "SQL compilation error:\nExpression type does not match column data type, "
        + "expecting ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE T (a INT, b INT)");
        engine.execute("CREATE TABLE S (v VARCHAR, d DATE)");
        engine.execute("INSERT INTO T VALUES (1, 2)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void aCastOfARowIsRefusedInTheConversionsWords() {
        assertEquals("SQL compilation error:\ninvalid type [CAST((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES (NULL)) DUAL) "
            + "AS NUMBER(38,0))] for parameter 'TO_NUMBER'", refusal("SELECT (SELECT 1, 2)::INT"));
        assertEquals("SQL compilation error:\ninvalid type [CAST((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES (NULL)) DUAL) "
            + "AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'", refusal("SELECT CAST((SELECT 1, 2) AS VARCHAR)"));
        assertEquals("SQL compilation error:\ninvalid type [CAST((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES (NULL)) DUAL) "
            + "AS DATE)] for parameter 'TO_DATE'", refusal("SELECT (SELECT 1, 2)::DATE"));
        assertEquals("SQL compilation error:\ninvalid type [TRY_CAST((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES (NULL)) "
            + "DUAL))] for parameter 'TO_NUMBER'", refusal("SELECT TRY_CAST((SELECT 1, 2) AS INT)"));
        assertEquals("SQL compilation error:\ninvalid type [CAST((SELECT T.A AS \"A\", T.B AS \"B\" FROM T AS T) AS "
            + "NUMBER(38,0))] for parameter 'TO_NUMBER'", refusal("SELECT (SELECT a, b FROM T)::INT FROM T"));
    }

    @Test
    public void aCallCountsTheRowAsOneArgument() {
        assertEquals("SQL compilation error: error line 1 at position 7\nnot enough arguments for function [IFF((SELECT "
            + "TRUE AS \"TRUE\", 1 AS \"1\", 0 AS \"0\" FROM (VALUES (null)) DUAL))], expected 3, got 1",
            refusal("SELECT IFF(SELECT TRUE, 1, 0)"));
        assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'IFF': "
            + "(ROW(BOOLEAN, NUMBER(1,0)), NUMBER(1,0), NUMBER(1,0))", refusal("SELECT IFF((SELECT TRUE, 1), 1, 0)"));
    }

    @Test
    public void anInsertMatchesTheRowAgainstItsColumn() {
        final String intoA = "NUMBER(38,0) but got ROW(NUMBER(1,0), NUMBER(1,0)) for column A";
        assertEquals(MISMATCH + intoA, refusal("INSERT INTO T (a) SELECT (SELECT 1, 2)"));
        assertEquals(MISMATCH + intoA, refusal("INSERT INTO T SELECT (SELECT 1, 2), 3"));
        assertEquals(MISMATCH + intoA, refusal("INSERT INTO T (a) SELECT (SELECT 1, 2) FROM T WHERE FALSE"));
        assertEquals(MISMATCH + intoA, refusal("INSERT INTO T (a) SELECT * FROM (SELECT (SELECT 1, 2))"));
        assertEquals(MISMATCH + intoA, refusal("INSERT OVERWRITE INTO T (a) SELECT (SELECT 1, 2)"));
        assertEquals(MISMATCH + "NUMBER(38,0) but got ROW(NUMBER(38,0), NUMBER(38,0)) for column A",
            refusal("INSERT INTO T (a) SELECT (SELECT t2.a, t2.b FROM T t2 WHERE t2.a = s.a) FROM T s"));
        assertEquals(MISMATCH + "VARCHAR(16777216) but got ROW(VARCHAR(1), VARCHAR(1)) for column V",
            refusal("INSERT INTO S (v) SELECT (SELECT 'x', 'y')"));
        assertEquals(MISMATCH + "DATE but got ROW(DATE, NUMBER(1,0)) for column D",
            refusal("INSERT INTO S (d) SELECT (SELECT CURRENT_DATE, 1)"));
        // In the table's column order, whatever the column list says.
        assertEquals(MISMATCH + "NUMBER(38,0) but got ROW(VARCHAR(1), VARCHAR(1)) for column A",
            refusal("INSERT INTO T (b, a) SELECT (SELECT 1, 2), (SELECT 'x', 'y')"));
        assertEquals(MISMATCH + "NUMBER(38,0) but got ROW(ANY) for column A", refusal("INSERT INTO T (a) VALUES ((SELECT 1, 2))"));
        assertEquals("SQL compilation error:\nInvalid data type [ROW(NUMBER(1,0), NUMBER(1,0))] in VALUES clause",
            refusal("INSERT INTO T (a) VALUES (1), ((SELECT 1, 2))"));
        // The source's names first, and a query that stores the row still refuses its column count.
        assertEquals("SQL compilation error: error line 1 at position 25\ninvalid identifier 'NOSUCH'",
            refusal("INSERT INTO T (a) SELECT nosuch, (SELECT 1, 2)"));
        assertEquals("SQL compilation error: error line 1 at position 26\nUnsupported: Scalar subquery with multi-column "
            + "SELECT clause.", refusal("CREATE TABLE C AS SELECT (SELECT 1, 2) AS r"));
    }
}
