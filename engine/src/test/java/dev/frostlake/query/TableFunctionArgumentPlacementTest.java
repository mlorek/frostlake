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
 * A table function's argument that names nothing is refused at the name's place in the statement
 * (live-verified): FLATTEN written bare or inside TABLE(), with a named or a positional argument, LATERAL or
 * not, SPLIT_TO_TABLE and GENERATOR alike, on a later line, and inside a subquery, where the place still
 * counts from the statement's start rather than from the subquery's or the argument's own text.
 */
public class TableFunctionArgumentPlacementTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE full_t (a INT, b INT)");
        engine.execute("INSERT INTO full_t VALUES (1, 2), (3, 4)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return "ACCEPTED " + rs.getRowCount();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String badName(final int line, final int position, final String name) {
        return "SQL compilation error: error line " + line + " at position " + position
            + "|invalid identifier '" + name + "'";
    }

    /** At the top of the statement: every argument shape is placed at the name. */
    @Test
    public void aTopLevelArgumentIsPlacedAtTheName() {
        assertEquals(badName(1, 60, "FULL_T.NOSUCH"),
            answer("SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(FULL_T.nosuch, 5))), FULL_T"));
        assertEquals(badName(1, 53, "NOSUCH"), answer("SELECT * FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(nosuch)))"));
        assertEquals(badName(1, 61, "FULL_T.NOSUCH"),
            answer("SELECT * FROM FULL_T, TABLE(FLATTEN(input => ARRAY_CONSTRUCT(FULL_T.nosuch)))"));
        assertEquals(badName(1, 47, "FULL_T.NOSUCH"), answer("SELECT * FROM FULL_T, LATERAL FLATTEN(input => FULL_T.nosuch)"));
        assertEquals(badName(1, 43, "FULL_T.NOSUCH"), answer("SELECT * FROM FULL_T, TABLE(SPLIT_TO_TABLE(FULL_T.nosuch, ','))"));
        assertEquals(badName(1, 63, "NOSUCH"), answer("SELECT * FROM FULL_T, LATERAL FLATTEN(input => ARRAY_CONSTRUCT(nosuch))"));
        assertEquals(badName(1, 54, "F.NOSUCH"), answer("SELECT * FROM FULL_T f, TABLE(FLATTEN(ARRAY_CONSTRUCT(f.nosuch)))"));
        assertEquals(badName(1, 42, "NOSUCH"), answer("SELECT * FROM TABLE(GENERATOR(ROWCOUNT => nosuch))"));
    }

    /** A second named argument on a later line keeps its own line and column. */
    @Test
    public void aLaterLineKeepsItsPlace() {
        assertEquals(badName(2, 9, "NOSUCH"),
            answer("SELECT * FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(1),\n path => nosuch))"));
    }

    /** Inside a subquery the place counts from the statement, not from the subquery's own text. */
    @Test
    public void anArgumentInASubqueryIsPlacedInTheStatement() {
        assertEquals(badName(1, 68, "FULL_T.NOSUCH"), answer(
            "SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(FULL_T.nosuch, 5)))) FROM FULL_T"));
        assertEquals(badName(1, 68, "FULL_T.NOSUCH"), answer(
            "SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(FULL_T.nosuch, 5)))) FROM T"));
        assertEquals(badName(1, 85, "T.NOSUCH"), answer(
            "SELECT a FROM T WHERE a IN (SELECT value FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(T.nosuch))))"));
    }

    /** A name the FLATTEN's rows resolve is still placed where it is written. */
    @Test
    public void aNameOverTheOutputIsUnchanged() {
        assertEquals(badName(1, 81, "F.NOSUCH"), answer(
            "SELECT * FROM FULL_T, TABLE(FLATTEN(input => ARRAY_CONSTRUCT(FULL_T.a))) f WHERE f.nosuch = 1"));
    }
}
