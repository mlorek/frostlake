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
 * A column of the query around a subquery carries its declared type while the subquery compiles, so an operator
 * or a call over it is refused for its argument types as one over the subquery's own columns is, at its place in
 * the statement, and ahead of the enclosing query's own types. That holds however deep the subquery is nested,
 * and through a subquery with no FROM of its own; and a query with no FROM ranks its subqueries' type refusals
 * ahead of its own items' as any query does. A name is still refused first, and a subquery's placement refusal
 * last (all live-verified).
 */
public class OuterColumnArgumentTypeTest extends BaseDatabaseTest {

    private static final String NUMBER_PLUS_BOOLEAN = "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)";
    private static final String VARCHAR_PLUS_BOOLEAN = "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private static String at(final int position, final String sentence) {
        return "SQL compilation error: error line 1 at position " + position + "\n" + sentence;
    }

    private static String plain(final String sentence) {
        return "SQL compilation error:\n" + sentence;
    }

    @Test
    public void anOperatorOverAnOuterColumnIsRefusedForItsArgumentTypes() {
        assertEquals(at(56, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id FROM fz WHERE id = (SELECT 1 FROM g WHERE g.v + fz.b = 1)"));
        assertEquals(at(23, NUMBER_PLUS_BOOLEAN), refusal("SELECT id, (SELECT g.v + fz.b FROM g LIMIT 1) FROM fz"));
        assertEquals(at(80, NUMBER_PLUS_BOOLEAN), refusal(
            "SELECT b FROM fz GROUP BY b HAVING (SELECT MAX(g.v) FROM g WHERE g.id < MAX(g.v + fz.b)) > 55"));
        assertEquals(at(54, "too many arguments for function [UPPER(CORRELATION(FZ.ID), 1)] expected 1, got 2"),
            refusal("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE UPPER(fz.id, 1) = 'x')"));
        assertEquals(at(23, "Invalid argument types for function '-': (NUMBER(38,0), BOOLEAN)"),
            refusal("SELECT id, (SELECT g.v - fz.b FROM g LIMIT 1) FROM fz"));
        assertEquals(at(24, "Invalid argument types for function '*': (BOOLEAN, NUMBER(1,0))"),
            refusal("SELECT id, (SELECT fz.b * 2 FROM g LIMIT 1) FROM fz"));
        assertEquals(at(19, "Invalid argument types for function 'NEGATE': (BOOLEAN)"),
            refusal("SELECT id, (SELECT -fz.b FROM g LIMIT 1) FROM fz"));
        assertEquals(at(19, "Invalid argument types for function 'SQRT': (BOOLEAN)"),
            refusal("SELECT id, (SELECT SQRT(fz.b) FROM g LIMIT 1) FROM fz"));
    }

    @Test
    public void everyRelationShapeAndStatementKindTypesItsOuterColumns() {
        assertEquals(at(74, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id FROM (SELECT * FROM fz) d WHERE id = (SELECT 1 FROM g WHERE g.v + d.b = 1)"));
        assertEquals(at(84, NUMBER_PLUS_BOOLEAN), refusal(
            "WITH c AS (SELECT * FROM fz) SELECT id FROM c WHERE id = (SELECT 1 FROM g WHERE g.v + c.b = 1)"));
        assertEquals(at(58, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id FROM fz f WHERE id = (SELECT 1 FROM g WHERE g.v + f.b = 1)"));
        assertEquals(at(56, NUMBER_PLUS_BOOLEAN), refusal("SELECT id FROM fz WHERE id = (SELECT 1 FROM g WHERE g.v + b = 1)"));
        assertEquals(at(42, NUMBER_PLUS_BOOLEAN), refusal("SELECT id FROM fz WHERE id IN (SELECT g.v + fz.b FROM g)"));
        assertEquals(at(43, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id, (SELECT g.v FROM g ORDER BY g.v + fz.b LIMIT 1) FROM fz"));
        assertEquals(at(39, NUMBER_PLUS_BOOLEAN), refusal("SELECT id FROM fz, LATERAL (SELECT g.v + fz.b AS z FROM g) l"));
        assertEquals(at(31, NUMBER_PLUS_BOOLEAN), refusal("UPDATE fz SET id = (SELECT g.v + fz.b FROM g LIMIT 1)"));
        assertEquals(at(38, NUMBER_PLUS_BOOLEAN), refusal("DELETE FROM fz WHERE id = (SELECT g.v + fz.b FROM g LIMIT 1)"));
        assertEquals(at(37, NUMBER_PLUS_BOOLEAN),
            refusal("INSERT INTO g SELECT id, (SELECT g.v + fz.b FROM g LIMIT 1) FROM fz"));
    }

    @Test
    public void theSubquerysArgumentTypeRanksAheadOfTheEnclosingQuerys() {
        assertEquals(at(75, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id FROM fz WHERE 'o' + TRUE = 1 AND id = (SELECT 1 FROM g WHERE g.v + fz.b = 1)"));
        assertEquals(at(31, NUMBER_PLUS_BOOLEAN), refusal("SELECT 'o' + TRUE, (SELECT g.v + fz.b FROM g LIMIT 1) FROM fz"));
        assertEquals(at(99, NUMBER_PLUS_BOOLEAN), refusal("SELECT b FROM fz GROUP BY b HAVING 'o' + TRUE = 1 AND "
            + "(SELECT MAX(g.v) FROM g WHERE g.id < MAX(g.v + fz.b)) > 55"));
        assertEquals(at(77, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id FROM fz WHERE 'o' + TRUE = 1 AND EXISTS (SELECT 1 FROM g WHERE g.v + fz.b = 1)"));
        assertEquals(at(61, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id FROM fz WHERE 'o' + TRUE = 1 AND id IN (SELECT g.v + fz.b FROM g)"));
    }

    /**
     * A subquery nested in one with no FROM is compiled with it, so its argument type is refused before the
     * shape of its correlation is judged, and ahead of what the FROM-less subquery around it computes.
     */
    @Test
    public void aSubqueryInsideAFromlessOneIsRefusedForItsTypesFirst() {
        assertEquals(at(31, NUMBER_PLUS_BOOLEAN), refusal("SELECT id, (SELECT (SELECT g.v + fz.b FROM g LIMIT 1)) FROM fz"));
        assertEquals(at(31, NUMBER_PLUS_BOOLEAN), refusal("SELECT id, (SELECT (SELECT g.v + fz.b FROM g)) FROM fz"));
        assertEquals(at(39, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id, (SELECT (SELECT (SELECT g.v + fz.b FROM g LIMIT 1))) FROM fz"));
        assertEquals(at(48, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id, (SELECT (SELECT g.v FROM g WHERE g.v + fz.b = 1 LIMIT 1)) FROM fz"));
        assertEquals(at(39, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id, (SELECT fz.id + (SELECT g.v + fz.b FROM g LIMIT 1)) FROM fz"));
        assertEquals(at(49, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id FROM fz WHERE id = (SELECT (SELECT g.v + fz.b FROM g LIMIT 1))"));
        assertEquals(at(51, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id FROM fz WHERE EXISTS (SELECT (SELECT g.v + fz.b FROM g LIMIT 1))"));
        assertEquals(at(31, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id, (SELECT (SELECT g.v + fz.b FROM g LIMIT 1) WHERE FALSE) FROM fz"));
        assertEquals(at(32, NUMBER_PLUS_BOOLEAN), refusal("SELECT 1/0, (SELECT (SELECT g.v + fz.b FROM g LIMIT 1)) FROM fz"));
        assertEquals(at(31, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id, (SELECT (SELECT g.v + fz.b FROM g LIMIT 1) + ('o' + TRUE)) FROM fz"));
        assertEquals(at(46, NUMBER_PLUS_BOOLEAN),
            refusal("SELECT id, (SELECT ('o' + TRUE) + (SELECT g.v + fz.b FROM g LIMIT 1)) FROM fz"));
        assertEquals(at(39, NUMBER_PLUS_BOOLEAN), refusal("UPDATE fz SET id = (SELECT (SELECT g.v + fz.b FROM g LIMIT 1))"));
    }

    @Test
    public void aFromlessQuerysSubqueryTypesRankAheadOfItsOwnTypes() {
        assertEquals(at(31, VARCHAR_PLUS_BOOLEAN), refusal("SELECT 'o' + TRUE, (SELECT 'x' + TRUE)"));
        assertEquals(at(36, VARCHAR_PLUS_BOOLEAN), refusal("SELECT 'o' + TRUE WHERE (SELECT 'x' + TRUE) = 1"));
        assertEquals(at(34, VARCHAR_PLUS_BOOLEAN), refusal("SELECT UPPER('a', 1), (SELECT 'x' + TRUE)"));
        assertEquals(at(38, VARCHAR_PLUS_BOOLEAN), refusal("SELECT 'o' + TRUE, EXISTS (SELECT 'x' + TRUE)"));
        assertEquals(at(36, VARCHAR_PLUS_BOOLEAN), refusal("SELECT 'o' + TRUE, 1 IN (SELECT 'x' + TRUE)"));
        assertEquals(at(27, "too many arguments for function [UPPER('a', 1)] expected 1, got 2"),
            refusal("SELECT 'o' + TRUE, (SELECT UPPER('a', 1))"));
        assertEquals(plain("Invalid data type [NUMBER(38,0)] for predicate [G.V]"),
            refusal("SELECT 'o' + TRUE, (SELECT 1 FROM g WHERE v)"));
        assertEquals(plain("Invalid data type [NUMBER(38,0)] for predicate [G.V]"),
            refusal("SELECT 'o' + TRUE WHERE EXISTS (SELECT 1 FROM g WHERE v)"));
        assertEquals(plain("Invalid data type [NUMBER(38,0)] for predicate [G.V]"),
            refusal("SELECT 'o' + TRUE, (SELECT 1 FROM g WHERE v) WHERE FALSE"));
        assertEquals(plain("[5] is not a valid order by expression"), refusal("SELECT 'o' + TRUE, (SELECT v FROM g ORDER BY 5)"));
        assertEquals(plain("[9] is not a valid group by expression"), refusal("SELECT 'o' + TRUE, (SELECT v FROM g GROUP BY 9)"));
        assertEquals(plain("argument 1 to function RANDOM needs to be constant, found 'G.V'"),
            refusal("SELECT 'o' + TRUE, (SELECT RANDOM(v) FROM g)"));
        assertEquals(plain("invalid number of result columns for set operator input branches, expected 1, got 2 in branch 2"),
            refusal("SELECT 'o' + TRUE, (SELECT v FROM g UNION SELECT id, v FROM g)"));
    }

    @Test
    public void aNameStillComesFirstAndAPlacementLast() {
        assertEquals(at(7, "invalid identifier 'NOSUCH'"), refusal("SELECT nosuch, (SELECT 'x' + TRUE)"));
        assertEquals(plain("Unknown function NOSUCHFN."), refusal("SELECT nosuchfn(1), (SELECT 'x' + TRUE)"));
        assertEquals(at(19, "invalid identifier 'NOSUCH'"),
            refusal("SELECT id, (SELECT nosuch + (SELECT g.v + fz.b FROM g LIMIT 1)) FROM fz"));
        assertEquals(at(11, VARCHAR_PLUS_BOOLEAN), refusal("SELECT 'o' + TRUE, (SELECT 1 FROM g WHERE MAX(v) > 0)"));
        assertEquals(at(11, VARCHAR_PLUS_BOOLEAN), refusal("SELECT 'o' + TRUE, (SELECT v FROM g QUALIFY v > 0)"));
        assertEquals(at(11, VARCHAR_PLUS_BOOLEAN), refusal("SELECT 'o' + TRUE, (SELECT v FROM g GROUP BY id)"));
        assertEquals(plain("Invalid aggregate function in where clause [MAX(G.V)]"),
            refusal("SELECT 1/0, (SELECT 1 FROM g WHERE MAX(v) > 0)"));
        // The FROM-less subquery's own type comes before the shape of the correlated one it holds.
        assertEquals(at(65, VARCHAR_PLUS_BOOLEAN),
            refusal("SELECT id, (SELECT (SELECT g.v FROM g WHERE g.id = fz.id) + ('o' + TRUE)) FROM fz"));
        assertEquals(plain("Unsupported subquery type cannot be evaluated at line 1, position 20"),
            refusal("SELECT id, (SELECT (SELECT g.v FROM g WHERE g.id = fz.id)) FROM fz"));
    }
}
