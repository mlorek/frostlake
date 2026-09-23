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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * An alias hides its relation's own name, so inside {@code (SELECT MAX(f2.id) - fz.id FROM fz f2)} the
 * qualifier FZ reaches no relation of the subquery and reads the row around it. Frostlake took it for the
 * aliased relation's own column and refused the item as ungrouped (live-verified).
 */
public class AliasedRelationOuterReferenceTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED =
        "SQL compilation error:\nUnsupported subquery type cannot be evaluated at line 1, position 12";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    /** Each row's cells joined by commas, rows by bars. */
    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "");
            for (int c = 0; c < result.getColumnCount(); c++) {
                text.append(c > 0 ? ", " : "").append(result.getRows().get(r).getValue(c));
            }
        }
        return text.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void theTablesOwnNameReadsTheOuterRowWhereAnAliasHidesIt() {
        assertEquals("5, 2 | 7, 0", rows("SELECT id, (SELECT MAX(f2.id) - fz.id FROM fz f2) FROM fz ORDER BY id"));
        assertEquals("5, 2 | 7, 0",
            rows("SELECT id, (SELECT MAX(f2.id) - fz.id FROM fz f2) FROM fz GROUP BY id ORDER BY id"));
        assertEquals("5, 35 | 7, 49", rows("SELECT id, (SELECT MAX(f2.id) * fz.id FROM fz f2) FROM fz ORDER BY id"));
        assertEquals("", rows("SELECT id FROM fz WHERE id = (SELECT MAX(f2.id) - fz.id FROM fz f2) ORDER BY id"));
        // The same shape over another relation, and the subquery's own alias, are unchanged.
        assertEquals("5, 55 | 7, 53", rows("SELECT id, (SELECT MAX(g.v) - fz.id FROM g) FROM fz ORDER BY id"));
        assertEquals("5, 7 | 7, null",
            rows("SELECT id, (SELECT MAX(f2.id) FROM fz f2 WHERE f2.id > fz.id) FROM fz ORDER BY id"));
        assertEquals("5, 1 | 7, 0",
            rows("SELECT id, (SELECT COUNT(*) FROM fz f2 WHERE f2.id > fz.id) FROM fz ORDER BY id"));
        assertEquals("5, 2 | 7, 0", rows("SELECT id, (SELECT MAX(f2.id) FROM fz f2) - id FROM fz ORDER BY id"));
    }

    @Test
    public void whatStaysRefusedKeepsItsOwnSentence() {
        // The aliased relation's own column, ungrouped, is still the grouped sentence.
        assertEquals("SQL compilation error:\n[F2.ID] is not a valid group by expression",
            refusal("SELECT id, (SELECT MAX(f2.id) - f2.id FROM fz f2) FROM fz ORDER BY id"));
        // An outer name beside the aggregates is answered only in the shapes live can plan.
        assertEquals(UNSUPPORTED, refusal("SELECT id, (SELECT MAX(fz.id) FROM fz f2) FROM fz ORDER BY id"));
        assertEquals(UNSUPPORTED, refusal("SELECT id, (SELECT fz.id FROM fz f2 LIMIT 1) FROM fz ORDER BY id"));
        assertEquals(UNSUPPORTED,
            refusal("SELECT id, (SELECT MAX(f2.id) - fz.id FROM fz AS f2 WHERE f2.b) FROM fz ORDER BY id"));
        assertEquals(UNSUPPORTED, refusal("SELECT id, (SELECT SUM(f2.id) + fz.id FROM fz f2) FROM fz ORDER BY id"));
    }
}
