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
 * A derived table or a CTE is planned into the query that reads it, so a column the statement never reads
 * is never computed: a value it would fault on (a cast to a narrower VARCHAR, 1 / 0, an unplannable
 * correlated subquery) is never produced, through a column alias list too. The body still compiles whole,
 * so an unknown name or function in an unread item is refused where it stands; and a column the body
 * itself uses (DISTINCT, GROUP BY, ORDER BY) or another arm reads is computed. Every cell is live-verified.
 */
public class UnreadDerivedColumnTest extends BaseDatabaseTest {

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

    @Test
    public void anUnreadColumnOfADerivedTableOrCteIsNeverComputed() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P449_DB");
            engine.execute("CREATE OR REPLACE TABLE nc (s VARCHAR, n INT)");
            engine.execute("INSERT INTO nc VALUES ('abcdefgh', 1)");
            engine.execute("CREATE OR REPLACE VIEW nc_v AS SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc");
            assertEquals("1",
                rows("SELECT COUNT(*) FROM (SELECT CAST(s AS VARCHAR(5)) AS c FROM nc)"));
            assertEquals("1",
                rows("WITH x AS (SELECT CAST(s AS VARCHAR(5)) AS c FROM nc) SELECT COUNT(*) FROM x"));
            assertEquals("1",
                rows("SELECT n FROM (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) t WHERE n = 1"));
            assertRefused("SELECT COUNT(*) FROM (SELECT nosuchcol AS c FROM nc)",
                "SQL compilation error: error line 1 at position 29\ninvalid identifier 'NOSUCHCOL'");
            assertRefused("WITH x AS (SELECT nosuchcol AS c FROM nc) SELECT COUNT(*) FROM x",
                "SQL compilation error: error line 1 at position 18\ninvalid identifier 'NOSUCHCOL'");
            assertRefused("SELECT COUNT(*) FROM (SELECT NOSUCHFN(s) AS c FROM nc)",
                "SQL compilation error:\nUnknown function NOSUCHFN.");
            assertEquals("1",
                rows("WITH x AS (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) SELECT n FROM x WHERE n = 1"));
            assertEquals("1",
                rows("WITH x AS (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) SELECT x.n FROM x, x AS y"));
            assertEquals("1",
                rows("SELECT t.n FROM (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) t"));
            assertRefused("SELECT COUNT(c) FROM (SELECT CAST(s AS VARCHAR(5)) AS c FROM nc)",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM (SELECT DISTINCT CAST(s AS VARCHAR(5)) AS c FROM nc)",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("SELECT COUNT(*) FROM (SELECT CAST(s AS VARCHAR(5)) AS c FROM nc GROUP BY c)",
                "String 'abcdefgh' is too long and would be truncated");
            assertEquals("2",
                rows("SELECT COUNT(*) FROM (SELECT CAST(s AS VARCHAR(5)) AS c FROM nc UNION ALL SELECT 'x')"));
            assertRefused("SELECT COUNT(*) FROM (SELECT CAST(s AS VARCHAR(5)) AS c FROM nc ORDER BY c)",
                "String 'abcdefgh' is too long and would be truncated");
            assertRefused("WITH x AS (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) SELECT n FROM x UNION ALL SELECT LENGTH(c) FROM x",
                "String 'abcdefgh' is too long and would be truncated");
            assertEquals("1",
                rows("SELECT COUNT(*) FROM (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) t WHERE t.n = 1"));
            assertEquals("1",
                rows("SELECT COUNT(*) FROM (SELECT CAST(s AS VARCHAR(5)) AS c FROM nc LIMIT 1)"));
            assertEquals("1",
                rows("SELECT COUNT(*) FROM (SELECT CAST(s AS VARCHAR(5)) AS c, COUNT(*) OVER () AS w FROM nc)"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P449_DB");
        }
    }

    @Test
    public void aRenamedOrCorrelatedUnreadColumnIsNeverComputed() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P449B_DB");
            engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
            engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
            engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
            engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
            engine.execute("CREATE OR REPLACE TABLE nc (s VARCHAR, n INT)");
            engine.execute("INSERT INTO nc VALUES ('abcdefgh', 1)");
            assertEquals("2",
                rows("WITH c AS (SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz) SELECT COUNT(*) FROM c"));
            assertEquals("2",
                rows("SELECT COUNT(*) FROM (SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz)"));
            assertEquals("5 | 7",
                rows("SELECT id FROM (SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz) ORDER BY id"));
            assertEquals("1",
                rows("SELECT COUNT(*) FROM (SELECT CAST(s AS VARCHAR(5)) AS c FROM nc) t (z)"));
            assertRefused("SELECT t.z FROM (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) t (z, m)",
                "String 'abcdefgh' is too long and would be truncated");
            assertEquals("1",
                rows("SELECT m FROM (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) t (z, m)"));
            assertEquals("1",
                rows("WITH x (z, m) AS (SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc) SELECT m FROM x"));
            assertEquals("1",
                rows("SELECT COUNT(*) FROM (SELECT CAST(s AS VARCHAR(5)) AS c FROM nc) WHERE 1 = 1"));
            assertEquals("1",
                rows("SELECT COUNT(*) FROM (SELECT CAST(s AS VARCHAR(5)) AS c, 1 / 0 AS d FROM nc)"));
            assertEquals("1",
                rows("SELECT COUNT(*) FROM (SELECT (SELECT CAST(s AS VARCHAR(5)) FROM nc) AS c)"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P449B_DB");
        }
    }
}
