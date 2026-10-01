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
 * A subquery of more than one column compared by an operator is typed as the ROW of its items, and refused in
 * the argument-type sentence — but only once the names are settled: a name the subquery cannot resolve, and every
 * name of the predicate around it, are refused first, wherever each stands. The same holds in HAVING and QUALIFY,
 * and in a FROM-less select's WHERE and HAVING (all live-verified).
 */
public class RowSubqueryNamesFirstTest extends BaseDatabaseTest {

    private static final String ROW_EQ = "Invalid argument types for function '=': (NUMBER(38,0), ROW(NUMBER(38,0), NUMBER(38,0)))";

    @Override
    protected void setupTest() {
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
    public void theSubquerysNamesComeBeforeItsRowType() {
        assertEquals(at(59, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ WHERE id = (SELECT fz.id, 1 FROM G WHERE nosuch = 1)"));
        assertEquals(at(62, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ WHERE id = (SELECT fz.id, 1 FROM FZ g WHERE nosuch = 1)"));
        assertEquals(at(37, "invalid identifier 'NOSUCH'"), refusal("SELECT id FROM FZ WHERE id = (SELECT nosuch, 1 FROM G)"));
        assertEquals(at(56, "invalid identifier 'G.NOSUCH'"),
            refusal("SELECT id FROM FZ WHERE id = (SELECT id, v FROM G WHERE g.nosuch = 1)"));
        assertEquals(at(56, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ WHERE id > (SELECT id, v FROM G WHERE nosuch = 1)"));
        assertEquals(at(51, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ WHERE (SELECT id, v FROM G WHERE nosuch = 1) = id"));
        assertEquals(at(56, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ WHERE id + (SELECT id, v FROM G WHERE nosuch = 1) = 1"));
        assertEquals(at(60, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ WHERE id = ANY (SELECT id, v FROM G WHERE nosuch = 1)"));
        assertEquals(at(57, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ WHERE id IN (SELECT id, v FROM G WHERE nosuch = 1)"));
        // Without a bad name, the ROW type is refused as before.
        assertEquals(at(27, ROW_EQ), refusal("SELECT id FROM FZ WHERE id = (SELECT id, v FROM G WHERE id = 5)"));
    }

    @Test
    public void everyNameOfTheWhereComesBeforeItsTypes() {
        assertEquals(at(55, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ WHERE id = (SELECT id, v FROM G) AND nosuch = 1"));
        assertEquals(at(24, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ WHERE nosuch = 1 AND id = (SELECT id, v FROM G)"));
        assertEquals(at(43, "invalid identifier 'NOSUCH'"), refusal("SELECT id FROM FZ WHERE 'a' + TRUE = 1 AND nosuch = 1"));
        assertEquals(at(24, "invalid identifier 'NOSUCH'"), refusal("SELECT id FROM FZ WHERE nosuch = 1 AND 'a' + TRUE = 1"));
        assertEquals(at(75, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ WHERE 'a' + TRUE = 1 AND id = (SELECT id, v FROM G WHERE nosuch = 1)"));
    }

    @Test
    public void havingQualifyAndFromlessClausesAgree() {
        assertEquals(at(28, ROW_EQ), refusal("SELECT id FROM FZ HAVING id = (SELECT id, v FROM G)"));
        assertEquals(at(57, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ HAVING id = (SELECT id, v FROM G WHERE nosuch = 1)"));
        assertEquals(at(74, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ GROUP BY id HAVING MAX(id) = (SELECT id, v FROM G WHERE nosuch = 1)"));
        assertEquals(at(47, "Invalid argument types for function 'IS NULL': (ROW(NUMBER(38,0), NUMBER(38,0)))"),
            refusal("SELECT id FROM FZ HAVING (SELECT id, v FROM G) IS NULL"));
        assertEquals(at(58, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id FROM FZ QUALIFY id = (SELECT id, v FROM G WHERE nosuch = 1)"));
        assertEquals(at(22, "Invalid argument types for function '=': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))"),
            refusal("SELECT 1 AS x WHERE x = (SELECT 1, 2)"));
        assertEquals(at(32, "invalid identifier 'NOSUCH'"), refusal("SELECT 1 AS x WHERE x = (SELECT nosuch, 2 FROM g)"));
        assertEquals(at(23, "Invalid argument types for function '=': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))"),
            refusal("SELECT 1 AS x HAVING x = (SELECT 1, 2)"));
    }
}
