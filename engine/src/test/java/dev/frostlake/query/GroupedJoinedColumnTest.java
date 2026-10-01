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
 * A grouped query over two joined relations holding a same-named column groups the column of the relation its key
 * names, and nothing else: a reference qualified by the other relation is ungrouped in the select list, in HAVING and
 * in ORDER BY, where Frostlake read it as grouped. A column a USING join merges, the relation's own alias or table
 * name, and an ordinal or output alias of the grouped column stay grouped (live-verified).
 */
public class GroupedJoinedColumnTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE A (x INT, v VARCHAR)");
        engine.execute("CREATE TABLE AB (x INT, v VARCHAR)");
        engine.execute("INSERT INTO A VALUES (1, 'a1'), (2, 'a2')");
        engine.execute("INSERT INTO AB VALUES (2, 'ab2'), (3, 'ab3')");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String neither(final String column) {
        return "SQL compilation error: error line 1 at position 7\n'" + column
            + "' in select clause is neither an aggregate nor in the group by clause.";
    }

    private int rows(final String sql) {
        return engine.executeQuery(sql).getRowCount();
    }

    @Test
    public void theOtherRelationsColumnIsNotGrouped() {
        assertEquals(neither("AB.X"), refusal("SELECT AB.x, COUNT(*) FROM A JOIN AB ON TRUE GROUP BY A.x"));
        assertEquals(neither("A.X"), refusal("SELECT a.x, COUNT(*) FROM A a JOIN AB b ON a.x = b.x GROUP BY b.x"));
        assertEquals(neither("B.V"), refusal("SELECT b.v, COUNT(*) FROM A a JOIN AB b ON TRUE GROUP BY a.v"));
        assertEquals(neither("B.X"), refusal("SELECT b.x + 1, COUNT(*) FROM A a JOIN AB b ON TRUE GROUP BY a.x"));
        assertEquals(neither("B.X"), refusal("SELECT b.x AS x, COUNT(*) FROM A a JOIN AB b ON TRUE GROUP BY a.x"));
        assertEquals("SQL compilation error:\n[B.X] is not a valid group by expression",
            refusal("SELECT COUNT(*) FROM A a JOIN AB b ON TRUE GROUP BY a.x HAVING b.x > 0"));
        assertEquals("SQL compilation error:\n[B.X] is not a valid order by expression",
            refusal("SELECT COUNT(*) FROM A a JOIN AB b ON TRUE GROUP BY a.x ORDER BY b.x"));
    }

    @Test
    public void theGroupedRelationsColumnStaysGrouped() {
        assertEquals(4, rows("SELECT a.x, b.x, COUNT(*) FROM A a JOIN AB b ON TRUE GROUP BY a.x, b.x"));
        assertEquals(2, rows("SELECT a.x, COUNT(*) FROM A a JOIN AB b ON TRUE GROUP BY a.x"));
        assertEquals(2, rows("SELECT a.x, COUNT(*) FROM A a JOIN AB b ON TRUE GROUP BY A.x"));
        assertEquals(2, rows("SELECT A.x, COUNT(*) FROM A a JOIN AB b ON TRUE GROUP BY a.x"));
        assertEquals(2, rows("SELECT a.x, COUNT(*) FROM A a JOIN AB b ON TRUE GROUP BY 1"));
        assertEquals(2, rows("SELECT a.x AS k, COUNT(*) FROM A a JOIN AB b ON TRUE GROUP BY k"));
        assertEquals(2, rows("SELECT AB.x, COUNT(*) FROM A JOIN AB ON TRUE GROUP BY AB.x"));
        assertEquals(1, rows("SELECT b.x, COUNT(*) FROM A a JOIN AB b USING (x) GROUP BY a.x"));
        assertEquals(1, rows("SELECT x, COUNT(*) FROM A a JOIN AB b USING (x) GROUP BY a.x"));
        assertEquals(2, rows("SELECT COUNT(*) FROM A a JOIN AB b ON TRUE GROUP BY a.x HAVING a.x > 0 ORDER BY a.x"));
    }
}
