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
 * The WHERE's argument types, and the aggregate and window placement rules of WHERE and ON, wait until every
 * clause's names and the ORDER BY position are settled: an out-of-range ORDER BY position, and a bad name in
 * HAVING, QUALIFY or GROUP BY, are refused first. A multi-column subquery compared in the WHERE is typed there too,
 * so the position outranks its ROW type (all live-verified).
 */
public class ClauseNamesBeforeWhereTypesTest extends BaseDatabaseTest {

    private static final String ORDINAL = "SQL compilation error:\n[9] is not a valid order by expression";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT, b INT)");
        engine.execute("CREATE TABLE full_t (a INT, b INT)");
        engine.execute("INSERT INTO full_t VALUES (1, 2)");
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String at(final int position, final String sentence) {
        return "SQL compilation error: error line 1 at position " + position + "\n" + sentence;
    }

    @Test
    public void theOrderByPositionOutranksTheWhere() {
        assertEquals(ORDINAL, refusal("SELECT a FROM T WHERE 'a' + TRUE = 1 ORDER BY 9"));
        assertEquals(ORDINAL, refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a WHERE 'a' + TRUE = 1 ORDER BY 9"));
        assertEquals(ORDINAL, refusal("SELECT a FROM T WHERE 'a' + TRUE = 1 ORDER BY a, 9"));
        assertEquals(ORDINAL, refusal("SELECT a FROM T WHERE SUM(a) > 1 ORDER BY 9"));
        assertEquals(ORDINAL, refusal("SELECT f.a FROM FULL_T f JOIN T ON SUM(T.a) > 0 ORDER BY 9"));
        assertEquals(ORDINAL, refusal("SELECT a FROM T WHERE ROW_NUMBER() OVER (ORDER BY a) = 1 ORDER BY 9"));
        assertEquals(ORDINAL, refusal("SELECT a FROM T WHERE 'a' + TRUE = 1 AND a = (SELECT a FROM T ORDER BY 9)"));
    }

    @Test
    public void laterClausesNamesOutrankTheWhere() {
        assertEquals(at(44, "invalid identifier 'NOSUCH9'"), refusal("SELECT a FROM T WHERE 'a' + TRUE = 1 HAVING nosuch9 = 1"));
        assertEquals(at(45, "invalid identifier 'NOSUCH8'"), refusal("SELECT a FROM T WHERE 'a' + TRUE = 1 QUALIFY nosuch8 = 1"));
        assertEquals(at(46, "invalid identifier 'NOSUCH10'"), refusal("SELECT a FROM T WHERE 'a' + TRUE = 1 GROUP BY nosuch10"));
        assertEquals(at(75, "invalid identifier 'NOSUCH10'"),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a WHERE 'a' + TRUE = 1 GROUP BY nosuch10"));
        assertEquals(at(40, "invalid identifier 'NOSUCH9'"), refusal("SELECT a FROM T WHERE SUM(a) > 1 HAVING nosuch9 = 1"));
        assertEquals(at(41, "invalid identifier 'NOSUCH'"), refusal("SELECT a FROM T WHERE SUM(a) > 1 QUALIFY nosuch = 1"));
        assertEquals(at(53, "invalid identifier 'NOSUCH'"),
            refusal("SELECT a FROM T WHERE 'a' + TRUE = 1 AND a = (SELECT nosuch FROM T)"));
    }

    @Test
    public void theWhereStillComesBeforePlacementAndTheQualifyRule() {
        assertEquals(at(41, "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"),
            refusal("SELECT a FROM T WHERE SUM(a) > 1 AND 'a' + TRUE = 1"));
        assertEquals(at(31, "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"),
            refusal("SELECT SUM(a) FROM T WHERE 'a' + TRUE = 1 QUALIFY 1 = 1"));
    }

    @Test
    public void aRowComparedInTheWhereWaitsForThePositionAndTheNames() {
        assertEquals(ORDINAL, refusal("SELECT id FROM FZ WHERE id = (SELECT id, v FROM G) ORDER BY 9"));
        assertEquals(ORDINAL, refusal("SELECT id FROM FZ WHERE (SELECT id, v FROM G) = 1 ORDER BY 9"));
        assertEquals(ORDINAL, refusal("SELECT id FROM FZ WHERE id IN (SELECT id, v FROM G) ORDER BY 9"));
        assertEquals(ORDINAL, refusal("SELECT id, (SELECT id, v FROM G) FROM FZ ORDER BY 9"));
        assertEquals(at(63, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ WHERE id = (SELECT id, v FROM G) AND (SELECT nosuch FROM G) = 1"));
        assertEquals(at(46, "Invalid argument types for function '=': (ROW(NUMBER(38,0), NUMBER(38,0)), NUMBER(1,0))"),
            refusal("SELECT id FROM FZ WHERE (SELECT id, v FROM G) = 1 AND 'a' + TRUE = 1"));
        // The ROW type is judged in the order written, among the predicate's other types.
        assertEquals(at(28, "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"),
            refusal("SELECT id FROM FZ WHERE 'a' + TRUE = 1 AND (SELECT id, v FROM G) = 1"));
    }
}
