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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A view is inlined into the query that reads it: a column the query never reads is never computed, and a
 * block that keeps no row reads none, so a value that would fault there is never produced. Each view below
 * holds one row whose {@code CAST(s AS VARCHAR(5))} faults ({@code String 'abcdefgh' is too long and would
 * be truncated}); reading that column refuses, and reading around it answers. A view that must compute every
 * column to produce its rows (DISTINCT, an ORDER BY on the column) refuses whatever is read. Every cell is
 * live-verified.
 */
public class UnreadViewColumnTest extends BaseDatabaseTest {

    private static final String TOO_LONG = "String 'abcdefgh' is too long and would be truncated";
    private static final String NO_ROWS = "<no rows>";
    private static final String ACCEPTED = "<accepted>";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE nc (s VARCHAR, n INT)");
        engine.execute("INSERT INTO nc VALUES ('abcdefgh', 1)");
        engine.execute("CREATE TABLE nc2 (n INT)");
        engine.execute("CREATE VIEW nc_v AS SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc");
        engine.execute("CREATE VIEW nc_u AS SELECT CAST(s AS VARCHAR(5)) AS c FROM nc UNION ALL SELECT 'y'");
        engine.execute("CREATE VIEW nc_g AS SELECT n, MAX(CAST(s AS VARCHAR(5))) AS m FROM nc GROUP BY n");
        engine.execute("CREATE VIEW nc_d AS SELECT DISTINCT CAST(s AS VARCHAR(5)) AS c, n FROM nc");
        engine.execute("CREATE VIEW nc_o AS SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc ORDER BY c");
        engine.execute("CREATE VIEW nc_l AS SELECT CAST(s AS VARCHAR(5)) AS c, n FROM nc LIMIT 1");
        engine.execute("CREATE VIEW nc_win AS SELECT CAST(s AS VARCHAR(5)) AS c, ROW_NUMBER() OVER (ORDER BY n) AS r FROM nc");
        engine.execute("CREATE VIEW nc_vv AS SELECT c, n FROM nc_v");
        engine.execute("CREATE VIEW nc_x AS SELECT 1 / (n - 1) AS z, n FROM nc");
        engine.execute("CREATE VIEW nc_e (x, y) AS SELECT CAST(s AS VARCHAR(5)), n FROM nc");
        engine.execute("CREATE VIEW nc_la AS SELECT CAST(s AS VARCHAR(5)) AS c, LENGTH(c) AS lc, n FROM nc");
    }

    /** The first row's values, "<no rows>", or the refusal with newlines shown as '|'. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            if (rs.getRows().isEmpty()) {
                return NO_ROWS;
            }
            final Row row = rs.getRows().get(0);
            final StringBuilder sb = new StringBuilder();
            for (final Object value : row.getValues()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(value);
            }
            return sb.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String accepted(final String sql) {
        try {
            engine.executeQuery(sql);
            return ACCEPTED;
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Each {statement, answer} pair, every mismatch reported at once. */
    private void assertAnswers(final String[][] cells) {
        final List<String> wrong = new ArrayList<String>();
        for (final String[] cell : cells) {
            final String got = ACCEPTED.equals(cell[1]) ? accepted(cell[0]) : answer(cell[0]);
            if (!cell[1].equals(got)) {
                wrong.add(cell[0] + "\n    expected: " + cell[1] + "\n    got:      " + got);
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    /** A column the query never names is never computed. */
    @Test
    public void anUnreadColumnIsNeverComputed() {
        assertAnswers(new String[][] {
            {"SELECT COUNT(*) FROM nc_v", "1"},
            {"SELECT n FROM nc_v", "1"},
            {"SELECT 1 FROM nc_v", "1"},
            {"SELECT n FROM nc_v GROUP BY n", "1"},
            {"SELECT DISTINCT n FROM nc_v", "1"},
            {"SELECT n FROM nc_v WHERE n = 2", NO_ROWS},
            {"SELECT COUNT(*) FROM nc_v v JOIN nc ON v.n = nc.n", "1"},
            {"SELECT COUNT(*) FROM nc_v, nc_v v2", "1"},
            {"SELECT n FROM nc_v v1 WHERE EXISTS (SELECT 1 FROM nc_v v2 WHERE v2.n = v1.n)", "1"},
            {"SELECT COUNT(*) FROM (SELECT n FROM nc_v UNION ALL SELECT 2)", "2"},
            {"SELECT COUNT(*) FROM (SELECT n FROM nc_v)", "1"},
            {"SELECT COUNT(*) FROM nc_x", "1"},
            {"SELECT n FROM nc_x", "1"},
            {"INSERT INTO nc2 SELECT n FROM nc_v", ACCEPTED},
            {"CREATE TABLE nc3 AS SELECT n FROM nc_v", ACCEPTED},
        });
    }

    /** The view's own shape does not matter: a union, a group, a limit, a window, another view. */
    @Test
    public void everyShapeOfViewPrunesTheSame() {
        assertAnswers(new String[][] {
            {"SELECT COUNT(*) FROM nc_u", "2"},
            {"SELECT n FROM nc_g", "1"},
            {"SELECT n FROM nc_l", "1"},
            {"SELECT r FROM nc_win", "1"},
            {"SELECT n FROM nc_vv", "1"},
            {"SELECT y FROM nc_e", "1"},
            {"SELECT n FROM nc_la", "1"},
        });
    }

    /** A block that keeps no row reads none. */
    @Test
    public void aBlockThatKeepsNoRowReadsNone() {
        assertAnswers(new String[][] {
            {"SELECT c FROM nc_v WHERE 1=0", NO_ROWS},
            {"SELECT c FROM nc_v WHERE FALSE", NO_ROWS},
            {"SELECT c FROM nc_v WHERE NULL", NO_ROWS},
            {"SELECT COUNT(*) FROM nc_v WHERE FALSE", "0"},
            {"SELECT COUNT(*) FROM nc_v WHERE n > 0 AND 1 = 0", "0"},
            {"SELECT c FROM nc_v LIMIT 0", NO_ROWS},
        });
    }

    /** Reading the column, or a view that must compute it to produce its rows, refuses. */
    @Test
    public void readingTheColumnStillRefuses() {
        assertAnswers(new String[][] {
            {"SELECT * FROM nc_v", TOO_LONG},
            {"SELECT COUNT(c) FROM nc_v", TOO_LONG},
            {"SELECT n FROM nc_v WHERE c IS NOT NULL", TOO_LONG},
            {"SELECT n FROM nc_v WHERE c = c", TOO_LONG},
            {"SELECT n FROM nc_v QUALIFY ROW_NUMBER() OVER (ORDER BY c) = 1", TOO_LONG},
            {"SELECT MAX(n) FROM nc_v HAVING MAX(c) IS NOT NULL", TOO_LONG},
            {"SELECT x FROM nc_e", TOO_LONG},
            {"SELECT lc FROM nc_la", TOO_LONG},
            {"SELECT z FROM nc_x", "Division by zero"},
            {"SELECT COUNT(*) FROM nc_d", TOO_LONG},
            {"SELECT n FROM nc_o", TOO_LONG},
        });
    }
}
