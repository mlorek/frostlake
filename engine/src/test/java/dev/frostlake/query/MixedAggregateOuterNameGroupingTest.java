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
 * An aggregate inside a grouped query's subquery that reads the subquery's own columns beside the enclosing query's
 * is the subquery's aggregate, so the enclosing names it reads are held to the enclosing query's grouping: in the
 * select list "'FZ.ID' in select clause is neither an aggregate nor in the group by clause." at the name, in HAVING
 * and under an implicit grouping "[FZ.ID] is not a valid group by expression". An aggregate over the enclosing names
 * alone stays the enclosing query's value, and a grouped name passes to the correlation rules (all live-verified).
 */
public class MixedAggregateOuterNameGroupingTest extends BaseDatabaseTest {

    private static final String UNGROUPED_ID = "'FZ.ID' in select clause is neither an aggregate nor in the group by clause.";
    private static final String NOT_A_GROUP_KEY = "SQL compilation error:\n[FZ.ID] is not a valid group by expression";

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
    public void inTheSelectListTheNameIsRefusedWhereItStands() {
        assertEquals(at(22, UNGROUPED_ID), refusal("SELECT b, (SELECT MAX(fz.id + g.v) FROM G g) FROM FZ GROUP BY b"));
        assertEquals(at(22, UNGROUPED_ID), refusal("SELECT b, (SELECT SUM(fz.id + g.v) FROM G g) FROM FZ GROUP BY b"));
        assertEquals(at(24, UNGROUPED_ID), refusal("SELECT b, (SELECT COUNT(fz.id + g.v) FROM G g) FROM FZ GROUP BY b"));
        assertEquals(at(28, UNGROUPED_ID), refusal("SELECT b, (SELECT MAX(g.v + fz.id) FROM G g) FROM FZ GROUP BY b"));
        assertEquals(at(22, UNGROUPED_ID), refusal("SELECT b, (SELECT MAX(fz.id + g.v) FROM G g), id FROM FZ GROUP BY b"));
        assertEquals(at(22, UNGROUPED_ID),
            refusal("SELECT b, (SELECT MAX(fz.id + g.v) FROM G g WHERE g.v > 0) FROM FZ GROUP BY b"));
        assertEquals(at(33, UNGROUPED_ID), refusal("SELECT b, (SELECT SUM(g.v) + MAX(fz.id + g.v) FROM G g) FROM FZ GROUP BY b"));
        assertEquals(at(22, UNGROUPED_ID), refusal("SELECT b, (SELECT MAX(fz.id + g.v) FROM G g) FROM FZ GROUP BY b ORDER BY b"));
        assertEquals(at(22, UNGROUPED_ID),
            refusal("SELECT b, (SELECT MAX(fz.id + g.v) FROM G g) FROM FZ GROUP BY b QUALIFY 1 = 1"));
        assertEquals(at(22, "'X.ID' in select clause is neither an aggregate nor in the group by clause."),
            refusal("SELECT b, (SELECT MAX(x.id + g.v) FROM G g) FROM FZ x GROUP BY b"));
    }

    @Test
    public void inHavingAndUnderAnImplicitGroupingTheNameIsNoGroupKey() {
        assertEquals(NOT_A_GROUP_KEY, refusal("SELECT b FROM FZ GROUP BY b HAVING (SELECT MAX(fz.id + g.v) FROM G g) > 55"));
        assertEquals(NOT_A_GROUP_KEY,
            refusal("SELECT b FROM FZ GROUP BY b HAVING (SELECT 1 FROM G g HAVING MAX(fz.id + g.v) > 0) > 55"));
        assertEquals(NOT_A_GROUP_KEY,
            refusal("SELECT b FROM FZ GROUP BY b HAVING (SELECT MAX(g.v) FROM G g WHERE g.id < MAX(fz.id + g.v)) > 55"));
        assertEquals(NOT_A_GROUP_KEY, refusal("SELECT MAX(id), (SELECT MAX(fz.id + g.v) FROM G g) FROM FZ"));
        assertEquals(NOT_A_GROUP_KEY, refusal("SELECT COUNT(*), (SELECT MAX(fz.id + g.v) FROM G g) FROM FZ"));
        assertEquals(NOT_A_GROUP_KEY, refusal("SELECT MAX(id), (SELECT MAX(fz.id + g.v) FROM G g), id FROM FZ"));
    }

    @Test
    public void namesAndTypesComeFirstAndAGroupedNamePassesOn() {
        assertEquals(at(37, "invalid identifier 'NOSUCH'"),
            refusal("SELECT b, (SELECT MAX(fz.id + g.v) + nosuch FROM G g) FROM FZ GROUP BY b"));
        assertEquals(at(46, "invalid identifier 'NOSUCH'"),
            refusal("SELECT b, (SELECT MAX(fz.id + g.v) FROM G g), nosuch FROM FZ GROUP BY b"));
        assertEquals(at(54, "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"),
            refusal("SELECT b, (SELECT MAX(fz.id + g.v) FROM G g WHERE 'x' + TRUE = 1) FROM FZ GROUP BY b"));
        assertEquals(at(50, "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"),
            refusal("SELECT b, (SELECT MAX(fz.id + g.v) FROM G g), 'x' + TRUE FROM FZ GROUP BY b"));
        assertEquals(at(79, "invalid identifier 'NOSUCH'"),
            refusal("SELECT b FROM FZ GROUP BY b HAVING (SELECT MAX(fz.id + g.v) FROM G g) > 55 AND nosuch = 1"));
        assertEquals("SQL compilation error:\nUnsupported subquery type cannot be evaluated at line 1, position 12",
            refusal("SELECT id, (SELECT MAX(fz.id + g.v) FROM G g) FROM FZ GROUP BY id"));
        assertEquals("SQL compilation error:\nUnsupported subquery type cannot be evaluated at line 1, position 11",
            refusal("SELECT b, (SELECT MAX(fz.id * 2) FROM G g) FROM FZ GROUP BY b"));
        // In the select list an aggregate in the subquery's WHERE is the subquery's own refusal.
        assertEquals("SQL compilation error:\nInvalid aggregate function in where clause [MAX(G.V)]",
            refusal("SELECT b, (SELECT MAX(g.v) FROM G g WHERE g.id < MAX(g.v)) FROM FZ GROUP BY b"));
    }
}
