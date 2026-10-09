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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A set operation arm that projects a scalar subquery of several columns. The arms' types are compared before the
 * subquery's column count is refused: a ROW beside anything but a ROW of as many fields is an inconsistent pair,
 * "expected" the later arm and "got" the one before it, in every statement the set operation is part of. Two
 * ROWs of one width, and a subquery outside a set operation, keep the column-count refusal.
 */
public class SetOperationRowArmTest extends BaseDatabaseTest {

    private static final String INCONSISTENT =
        "SQL compilation error:\ninconsistent data type for result columns for set operator input branches, expected ";
    private static final String ROW2 = "ROW(NUMBER(1,0), NUMBER(1,0))";
    private static final String SEVERAL = "\nUnsupported: Scalar subquery with multi-column SELECT clause.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE T (a INT, b INT)");
        engine.execute("INSERT INTO T VALUES (1, 2)");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aRowBesideAnotherTypeIsAnInconsistentPair() {
        assertEquals(INCONSISTENT + "NUMBER(1,0), got " + ROW2, refusalOf("SELECT (SELECT 1, 2) UNION ALL SELECT 1"));
        assertEquals(INCONSISTENT + ROW2 + ", got NUMBER(1,0)", refusalOf("SELECT 1 UNION ALL SELECT (SELECT 1, 2)"));
        assertEquals(INCONSISTENT + ROW2 + ", got NUMBER(1,0)",
            refusalOf("SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT (SELECT 1, 2)"));
        assertEquals(INCONSISTENT + "NUMBER(1,0), got " + ROW2, refusalOf("SELECT (SELECT 1, 2) UNION SELECT 1"));
        assertEquals(INCONSISTENT + "NUMBER(1,0), got " + ROW2, refusalOf("SELECT (SELECT 1, 2) INTERSECT SELECT 1"));
        assertEquals(INCONSISTENT + "NUMBER(1,0), got " + ROW2, refusalOf("SELECT (SELECT 1, 2) EXCEPT SELECT 1"));
        assertEquals(INCONSISTENT + "VARCHAR(1), got ROW(NUMBER(1,0), VARCHAR(1))",
            refusalOf("SELECT (SELECT 1, 'a') UNION ALL SELECT 'x'"));
        assertEquals(INCONSISTENT + "NUMBER(1,0), got ROW(NUMBER(38,0), NUMBER(38,0))",
            refusalOf("SELECT (SELECT a, b FROM T) UNION ALL SELECT 1"));
        assertEquals(INCONSISTENT + "NUMBER(1,0), got " + ROW2,
            refusalOf("SELECT 1, (SELECT 1, 2) UNION ALL SELECT 1, 2"));
        assertEquals(INCONSISTENT + "ROW(NUMBER(1,0), NUMBER(1,0), NUMBER(1,0)), got " + ROW2,
            refusalOf("SELECT (SELECT 1, 2) UNION ALL SELECT (SELECT 3, 4, 5)"));
    }

    @Test
    public void theStatementAroundTheSetOperationAnswersTheSame() {
        assertEquals(INCONSISTENT + "NUMBER(1,0), got " + ROW2,
            refusalOf("INSERT INTO T (a) SELECT (SELECT 1, 2) UNION ALL SELECT 1"));
        assertEquals(INCONSISTENT + "NUMBER(1,0), got " + ROW2,
            refusalOf("SELECT * FROM (SELECT (SELECT 1, 2) UNION ALL SELECT 1)"));
        assertEquals(INCONSISTENT + "NUMBER(1,0), got " + ROW2,
            refusalOf("CREATE OR REPLACE TABLE ROT AS SELECT (SELECT 1, 2) AS c UNION ALL SELECT 1"));
        assertEquals(INCONSISTENT + ROW2 + ", got NUMBER(1,0)",
            refusalOf("SELECT 1 WHERE 1 IN (SELECT 1 UNION ALL SELECT (SELECT 1, 2))"));
    }

    @Test
    public void rowsOfOneWidthAndAPlainSubqueryKeepTheColumnCountRefusal() {
        assertEquals("SQL compilation error: error line 1 at position 8" + SEVERAL,
            refusalOf("SELECT (SELECT 1, 2) UNION ALL SELECT (SELECT 3, 4)"));
        assertEquals("SQL compilation error: error line 1 at position 8" + SEVERAL,
            refusalOf("SELECT (SELECT 1, 2) UNION ALL SELECT (SELECT 'a', 'b')"));
        assertEquals("SQL compilation error: error line 1 at position 8" + SEVERAL, refusalOf("SELECT (SELECT 1, 2)"));
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT 1 UNION ALL SELECT (SELECT 2) ORDER BY 1")
            .getRows().get(0).getValue(0)));
    }
}
