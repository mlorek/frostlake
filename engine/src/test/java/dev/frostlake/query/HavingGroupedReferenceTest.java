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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HAVING holds its own columns to the grouping as the select list does: a column that is neither a
 * grouping key nor inside an aggregate is "[OWNER.COLUMN] is not a valid group by expression", with or
 * without a GROUP BY, inside CASE, AND and OR, and in a correlated subquery. A select alias, a grouped
 * expression and an alias- or ordinal-grouped column are legal there. Every cell is live-verified.
 */
public class HavingGroupedReferenceTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    /** A grouped query's HAVING, and an implicitly aggregated one's. */
    @Test
    public void havingColumnsMustBeGroupedOrAggregated() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P450_DB");
            engine.execute("CREATE OR REPLACE TABLE P450_DB.PUBLIC.G (id INT, v INT)");
            engine.execute("INSERT INTO P450_DB.PUBLIC.G VALUES (5, 50), (6, 60)");
            engine.execute("CREATE OR REPLACE TABLE P450_DB.PUBLIC.FZ (z INT)");
            engine.execute("INSERT INTO P450_DB.PUBLIC.FZ VALUES (1)");
            assertRefused("SELECT SUM(v) FROM G HAVING SUM(v) > id",
                "[G.ID] is not a valid group by expression");
            assertRefused("SELECT (SELECT SUM(v) FROM G HAVING SUM(v) > id) FROM FZ",
                "[G.ID] is not a valid group by expression");
            assertRefused("SELECT z FROM FZ WHERE EXISTS (SELECT 1 FROM G HAVING SUM(v) > id)",
                "[G.ID] is not a valid group by expression");
            assertEquals("6, 60",
                rows("SELECT id, SUM(v) FROM G GROUP BY id HAVING id > 5"));
            assertRefused("SELECT id FROM G GROUP BY id HAVING v > 1",
                "[G.V] is not a valid group by expression");
            assertEquals("110",
                rows("SELECT SUM(v) FROM G HAVING SUM(v) > 0"));
            assertRefused("SELECT COUNT(*) FROM G HAVING CASE WHEN id > 0 THEN 1 END = 1",
                "[G.ID] is not a valid group by expression");
            assertEquals("110",
                rows("SELECT SUM(v) AS s FROM G HAVING s > 0"));
            assertRefused("SELECT SUM(v) FROM G HAVING SUM(v) > G.id",
                "[G.ID] is not a valid group by expression");
            assertRefused("SELECT SUM(v) FROM G HAVING v > id",
                "[G.V] is not a valid group by expression");
            assertEquals("6",
                rows("SELECT id AS k FROM G GROUP BY k HAVING id > 5"));
            assertRefused("SELECT id FROM G GROUP BY id HAVING SUM(v) > v",
                "[G.V] is not a valid group by expression");
            assertRefused("SELECT SUM(v) FROM G HAVING MAX(id) > 0 AND id > 0",
                "[G.ID] is not a valid group by expression");
            assertRefused("SELECT SUM(v) FROM G HAVING nosuchcol > 0",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("SELECT SUM(v) FROM G HAVING id > 0 AND nosuchfn(1) = 1",
                "Unknown function NOSUCHFN.");
            assertRefused("SELECT SUM(v) FROM G HAVING COUNT(*) > 1 OR id IS NULL",
                "[G.ID] is not a valid group by expression");
            assertRefused("SELECT SUM(v) FROM G g2 HAVING SUM(v) > g2.id",
                "[G2.ID] is not a valid group by expression");
            assertEquals("6",
                rows("SELECT id FROM G GROUP BY 1 HAVING id > 5"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P450_DB");
        }
    }
}
