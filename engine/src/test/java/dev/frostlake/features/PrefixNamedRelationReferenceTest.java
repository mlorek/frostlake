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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A column reference qualified by a relation's database and schema reads THAT relation, even when another
 * joined relation's name begins with its name — {@code A} beside {@code AB}, {@code T} beside {@code T2} — in
 * the select list, an ON, a WHERE, a GROUP BY and an ORDER BY alike. Live-verified.
 */
public class PrefixNamedRelationReferenceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE DATABASE P547_DB");
        engine.execute("CREATE OR REPLACE TABLE P547_DB.PUBLIC.T (x INT)");
        engine.execute("CREATE OR REPLACE TABLE P547_DB.PUBLIC.T2 (x INT)");
        engine.execute("INSERT INTO P547_DB.PUBLIC.T VALUES (5)");
        engine.execute("INSERT INTO P547_DB.PUBLIC.T2 VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE P547_DB.PUBLIC.A (x INT, v VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE P547_DB.PUBLIC.AB (x INT, v VARCHAR)");
        engine.execute("INSERT INTO P547_DB.PUBLIC.A VALUES (1, 'a1'), (2, 'a2')");
        engine.execute("INSERT INTO P547_DB.PUBLIC.AB VALUES (2, 'ab2'), (3, 'ab3')");
    }

    @Override
    protected void teardownTest() {
        engine.execute("DROP DATABASE IF EXISTS P547_DB");
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
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

    @Test
    public void anOnConditionReadsEachRelationItNames() {
        assertEquals("0", rows("SELECT COUNT(*) FROM P547_DB.PUBLIC.T JOIN P547_DB.PUBLIC.T2"
            + " ON P547_DB.PUBLIC.T.x = P547_DB.PUBLIC.T2.x"));
        assertEquals("0", rows("SELECT COUNT(*) FROM P547_DB.PUBLIC.T2 JOIN P547_DB.PUBLIC.T"
            + " ON P547_DB.PUBLIC.T.x = P547_DB.PUBLIC.T2.x"));
        assertEquals("0", rows("SELECT COUNT(*) FROM P547_DB..T JOIN P547_DB..T2 ON P547_DB..T.x = P547_DB..T2.x"));
        assertEquals("0", rows("SELECT COUNT(*) FROM P547_DB.PUBLIC.T JOIN P547_DB.PUBLIC.T2 ON T.x = T2.x"));
        assertEquals("1", rows("SELECT COUNT(*) FROM P547_DB.PUBLIC.A JOIN P547_DB.PUBLIC.AB ON PUBLIC.A.x = PUBLIC.AB.x"));
    }

    @Test
    public void theSelectListAndWhereReadTheirOwnRelation() {
        assertEquals("a2, ab2", rows("SELECT P547_DB.PUBLIC.A.v, P547_DB.PUBLIC.AB.v FROM P547_DB.PUBLIC.A"
            + " JOIN P547_DB.PUBLIC.AB ON P547_DB.PUBLIC.A.x = P547_DB.PUBLIC.AB.x"));
        assertEquals("a2", rows("SELECT P547_DB.PUBLIC.A.v FROM P547_DB.PUBLIC.AB JOIN P547_DB.PUBLIC.A"
            + " ON P547_DB.PUBLIC.A.x = P547_DB.PUBLIC.AB.x"));
        assertEquals("a2", rows("SELECT P547_DB.PUBLIC.A.v FROM P547_DB.PUBLIC.A, P547_DB.PUBLIC.AB"
            + " WHERE P547_DB.PUBLIC.A.x = P547_DB.PUBLIC.AB.x"));
        assertEquals("a2", rows("SELECT PUBLIC.A.v FROM P547_DB.PUBLIC.A JOIN P547_DB.PUBLIC.AB ON PUBLIC.A.x = PUBLIC.AB.x"));
        assertEquals("ab3", rows("SELECT P547_DB.PUBLIC.AB.v FROM P547_DB.PUBLIC.A JOIN P547_DB.PUBLIC.AB ON TRUE"
            + " ORDER BY P547_DB.PUBLIC.AB.v DESC LIMIT 1"));
    }

    @Test
    public void aGroupedSelectListReadsTheQualifiedKey() {
        assertEquals("1, 2 | 2, 2", rows("SELECT P547_DB.PUBLIC.A.x, COUNT(*) FROM P547_DB.PUBLIC.A JOIN P547_DB.PUBLIC.AB"
            + " ON TRUE GROUP BY P547_DB.PUBLIC.A.x ORDER BY P547_DB.PUBLIC.A.x"));
        assertEquals("1, 1 | 2, 1", rows("SELECT P547_DB.PUBLIC.A.x, COUNT(*) FROM P547_DB.PUBLIC.A"
            + " GROUP BY P547_DB.PUBLIC.A.x ORDER BY 1"));
        assertEquals("1, 1 | 2, 1", rows("SELECT PUBLIC.A.x, COUNT(*) FROM P547_DB.PUBLIC.A GROUP BY PUBLIC.A.x ORDER BY 1"));
        assertEquals("1, 1 | 2, 1", rows("SELECT A.x, COUNT(*) FROM P547_DB.PUBLIC.A GROUP BY P547_DB.PUBLIC.A.x ORDER BY 1"));
        assertEquals("1, 1 | 2, 1", rows("SELECT P547_DB.PUBLIC.A.x, COUNT(*) FROM P547_DB.PUBLIC.A GROUP BY A.x ORDER BY 1"));
        assertEquals("1, 1 | 2, 1", rows("SELECT P547_DB.PUBLIC.A.x, COUNT(*) FROM P547_DB.PUBLIC.A GROUP BY x ORDER BY 1"));
        assertEquals("1, 1 | 2, 1", rows("SELECT x, COUNT(*) FROM P547_DB.PUBLIC.A GROUP BY P547_DB.PUBLIC.A.x ORDER BY 1"));
        assertEquals("2, 1 | 3, 1", rows("SELECT P547_DB.PUBLIC.A.x + 1, COUNT(*) FROM P547_DB.PUBLIC.A"
            + " GROUP BY P547_DB.PUBLIC.A.x ORDER BY 1"));
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT P547_DB.PUBLIC.A.v, COUNT(*) FROM P547_DB.PUBLIC.A GROUP BY P547_DB.PUBLIC.A.x");
            }
        });
        assertEquals("SQL compilation error: error line 1 at position 7\n'P547_DB.PUBLIC.A.V' in select clause is"
            + " neither an aggregate nor in the group by clause.", refused.getMessage());
    }
}
