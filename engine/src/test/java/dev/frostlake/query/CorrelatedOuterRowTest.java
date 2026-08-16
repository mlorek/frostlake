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
 * A correlated subquery reads the outer row wherever it looks: in its HAVING, beside its own grouping keys,
 * and in a FROM-less select list, afresh for every outer row. A scalar subquery of more than one column is
 * refused before any row is read, correlated or not and a set operation included, after any name it cannot
 * resolve. HAVING reads a grouped expression, and a grouping key the select list leaves out, as the group's
 * value. Every cell is live-verified.
 */
public class CorrelatedOuterRowTest extends BaseDatabaseTest {

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

    /** A correlated subquery's HAVING reads the outer row beside its own group keys, and a FROM-less one reads it too. */
    @Test
    public void correlatedHavingAndFromLessSubqueriesReadTheOuterRow() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P451_DB");
            engine.execute("CREATE OR REPLACE TABLE P451_DB.PUBLIC.FZ (id INT, b BOOLEAN)");
            engine.execute("INSERT INTO P451_DB.PUBLIC.FZ VALUES (5, TRUE), (7, FALSE)");
            engine.execute("CREATE OR REPLACE TABLE P451_DB.PUBLIC.G (id INT, v INT)");
            engine.execute("INSERT INTO P451_DB.PUBLIC.G VALUES (5, 50), (6, 60)");
            assertEquals("5, 110 | 7, 110",
                rows("SELECT id, (SELECT SUM(v) FROM G HAVING SUM(v) > fz.id) AS x FROM FZ ORDER BY id"));
            assertEquals("5, 50 | 7, 50",
                rows("SELECT id, (SELECT SUM(v) FROM G WHERE G.id = 5 HAVING SUM(v) > fz.id) AS x FROM FZ ORDER BY id"));
            assertEquals("5, 50 | 7, 50",
                rows("SELECT id, (SELECT SUM(v) FROM G WHERE G.id = 5 GROUP BY id HAVING SUM(v) > id) AS x FROM FZ ORDER BY id"));
            assertEquals("5, 6 | 7, 8",
                rows("SELECT id, (SELECT fz.id + 1) AS x FROM FZ ORDER BY id"));
            assertEquals("5, true | 7, false",
                rows("SELECT id, (SELECT fz.b) AS x FROM FZ ORDER BY id"));
            assertEquals("5, 0 | 7, 1",
                rows("SELECT id, (SELECT COUNT(*) WHERE fz.id > 5) AS x FROM FZ ORDER BY id"));
            assertEquals("5",
                rows("SELECT id FROM FZ WHERE (SELECT fz.id + 1) = 6"));
            assertEquals("5, 7 | 7, 9",
                rows("SELECT id, (SELECT (SELECT fz.id + 2)) AS x FROM FZ ORDER BY id"));
            assertEquals("5, null | 7, 60",
                rows("SELECT id, (SELECT MAX(v) FROM G WHERE G.id < fz.id) AS x FROM FZ ORDER BY id"));
            assertEquals("5, 50 | 7, 70",
                rows("SELECT id, (SELECT fz.id * 10 AS y) AS x FROM FZ ORDER BY id"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P451_DB");
        }
    }

    /** A bare outer reference in a FROM-less subquery is read afresh for every outer row, qualified or not. */
    @Test
    public void fromLessOuterReferencesAnswerPerRow() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P451B_DB");
            engine.execute("CREATE OR REPLACE TABLE P451B_DB.PUBLIC.FZ (id INT, b BOOLEAN, s VARCHAR)");
            engine.execute("INSERT INTO P451B_DB.PUBLIC.FZ VALUES (5, TRUE, 'x'), (7, FALSE, 'y')");
            assertEquals("5, true | 7, false",
                rows("SELECT id, (SELECT fz.b) AS x FROM P451B_DB.PUBLIC.FZ ORDER BY id"));
            assertEquals("5, 5 | 7, 7",
                rows("SELECT id, (SELECT fz.id) AS x FROM P451B_DB.PUBLIC.FZ ORDER BY id"));
            assertEquals("5, x | 7, y",
                rows("SELECT id, (SELECT fz.s) AS x FROM P451B_DB.PUBLIC.FZ ORDER BY id"));
            assertEquals("5, true | 7, false",
                rows("SELECT id, (SELECT b) AS x FROM P451B_DB.PUBLIC.FZ ORDER BY id"));
            assertEquals("5, true | 7, false",
                rows("SELECT id, (SELECT (fz.b)) AS x FROM P451B_DB.PUBLIC.FZ ORDER BY id"));
            assertEquals("5, false | 7, true",
                rows("SELECT id, (SELECT NOT fz.b) AS x FROM P451B_DB.PUBLIC.FZ ORDER BY id"));
            assertEquals("5, true | 7, false",
                rows("SELECT id, (SELECT fz.b AS q) AS x FROM P451B_DB.PUBLIC.FZ ORDER BY id"));
            assertEquals("5, 5 | 7, 7",
                rows("SELECT id, (SELECT id) AS x FROM P451B_DB.PUBLIC.FZ ORDER BY id"));
            assertEquals("5, x | 7, y",
                rows("SELECT id, (SELECT s) AS x FROM P451B_DB.PUBLIC.FZ f ORDER BY id"));
            assertEquals("5, x | 7, y",
                rows("SELECT id, (SELECT f.s) AS x FROM P451B_DB.PUBLIC.FZ f ORDER BY id"));
            assertEquals("5",
                rows("SELECT id FROM P451B_DB.PUBLIC.FZ WHERE (SELECT fz.b) ORDER BY id"));
            assertEquals("5, 10 | 7, 14",
                rows("SELECT id, (SELECT fz.id + fz.id) AS x FROM P451B_DB.PUBLIC.FZ ORDER BY id"));
            assertRefused("SELECT id, (SELECT fz.b, 1) AS x FROM P451B_DB.PUBLIC.FZ ORDER BY id",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
            assertRefused("SELECT id, (SELECT 1 AS k, k + fz.id AS m) AS x FROM P451B_DB.PUBLIC.FZ ORDER BY id",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P451B_DB");
        }
    }

    /** More than one column in a scalar subquery is refused, correlated or not, and over no rows at all. */
    @Test
    public void multiColumnScalarSubqueryIsRefusedCorrelatedOrNot() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P451C_DB");
            engine.execute("CREATE OR REPLACE TABLE P451C_DB.PUBLIC.FZ (id INT, b BOOLEAN)");
            engine.execute("INSERT INTO P451C_DB.PUBLIC.FZ VALUES (5, TRUE), (7, FALSE)");
            engine.execute("CREATE OR REPLACE TABLE P451C_DB.PUBLIC.G (id INT, v INT)");
            engine.execute("INSERT INTO P451C_DB.PUBLIC.G VALUES (5, 50), (6, 60)");
            assertRefused("SELECT id, (SELECT g.v, fz.id FROM P451C_DB.PUBLIC.G g WHERE g.id = 5) AS x FROM P451C_DB.PUBLIC.FZ ORDER BY id",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
            assertRefused("SELECT id, (SELECT g.v, g.id FROM P451C_DB.PUBLIC.G g WHERE g.id = fz.id) AS x FROM P451C_DB.PUBLIC.FZ ORDER BY id",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
            assertRefused("SELECT id, (SELECT 1, 2) AS x FROM P451C_DB.PUBLIC.FZ ORDER BY id",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
            assertRefused("SELECT id, (SELECT fz.id AS a, a + 1) AS x FROM P451C_DB.PUBLIC.FZ ORDER BY id",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
            assertRefused("SELECT id, (SELECT * FROM P451C_DB.PUBLIC.G g WHERE g.id = fz.id) AS x FROM P451C_DB.PUBLIC.FZ ORDER BY id",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
            assertRefused("SELECT id, (SELECT fz.id, fz.b) AS x FROM P451C_DB.PUBLIC.FZ WHERE FALSE",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P451C_DB");
        }
    }

    /** A name the subquery cannot resolve is reported ahead of its column count. */
    @Test
    public void anUnresolvedNameIsReportedBeforeTheColumnCount() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P451D_DB");
            engine.execute("CREATE OR REPLACE TABLE P451D_DB.PUBLIC.FZ (id INT, b BOOLEAN)");
            engine.execute("INSERT INTO P451D_DB.PUBLIC.FZ VALUES (5, TRUE), (7, FALSE)");
            engine.execute("CREATE OR REPLACE TABLE P451D_DB.PUBLIC.G (id INT, v INT)");
            engine.execute("INSERT INTO P451D_DB.PUBLIC.G VALUES (5, 50), (6, 60)");
            assertRefused("SELECT id, (SELECT nosuch, 1) AS x FROM P451D_DB.PUBLIC.FZ ORDER BY id",
                "invalid identifier 'NOSUCH'");
            assertRefused("SELECT id, (SELECT 1, nosuch) AS x FROM P451D_DB.PUBLIC.FZ ORDER BY id",
                "invalid identifier 'NOSUCH'");
            assertRefused("SELECT id, (SELECT nosuch, 1 FROM P451D_DB.PUBLIC.G) AS x FROM P451D_DB.PUBLIC.FZ ORDER BY id",
                "invalid identifier 'NOSUCH'");
            assertRefused("SELECT id, (SELECT fz.id, nosuch) AS x FROM P451D_DB.PUBLIC.FZ ORDER BY id",
                "invalid identifier 'NOSUCH'");
            assertRefused("SELECT id, (SELECT fz.id, 1 UNION ALL SELECT 2, 3) AS x FROM P451D_DB.PUBLIC.FZ ORDER BY id",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
            assertRefused("SELECT id, (SELECT nosuchfn(1), 2) AS x FROM P451D_DB.PUBLIC.FZ ORDER BY id",
                "Unknown function NOSUCHFN.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P451D_DB");
        }
    }

    /** A set operation of several columns is the same refusal, not the single-row fault its rows would raise. */
    @Test
    public void aSetOperationOfSeveralColumnsIsRefusedToo() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P451F_DB");
            engine.execute("CREATE OR REPLACE TABLE P451F_DB.PUBLIC.FZ (id INT, b BOOLEAN)");
            engine.execute("INSERT INTO P451F_DB.PUBLIC.FZ VALUES (5, TRUE), (7, FALSE)");
            engine.execute("CREATE OR REPLACE TABLE P451F_DB.PUBLIC.G (id INT, v INT)");
            engine.execute("INSERT INTO P451F_DB.PUBLIC.G VALUES (5, 50), (6, 60)");
            assertRefused("SELECT id, (SELECT 1, 2 UNION ALL SELECT 3, 4) AS x FROM P451F_DB.PUBLIC.FZ ORDER BY id",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
            assertRefused("SELECT (SELECT 1, 2 UNION ALL SELECT 3, 4) AS x",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
            assertRefused("SELECT id, (SELECT fz.id, 1 UNION ALL SELECT 2, 3) AS x FROM P451F_DB.PUBLIC.FZ WHERE FALSE",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
            assertRefused("SELECT id, (SELECT g.id, g.v FROM P451F_DB.PUBLIC.G g UNION ALL SELECT 1, 2) AS x FROM P451F_DB.PUBLIC.FZ ORDER BY id",
                "Unsupported: Scalar subquery with multi-column SELECT clause.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P451F_DB");
        }
    }

    /** HAVING reads a grouped expression and a grouping key the select list does not project as the group's value. */
    @Test
    public void havingReadsGroupedExpressionsAndUnprojectedKeys() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P451F_DB");
            engine.execute("CREATE OR REPLACE TABLE P451F_DB.PUBLIC.FZ (id INT, b BOOLEAN)");
            engine.execute("INSERT INTO P451F_DB.PUBLIC.FZ VALUES (5, TRUE), (7, FALSE)");
            engine.execute("CREATE OR REPLACE TABLE P451F_DB.PUBLIC.G (id INT, v INT)");
            engine.execute("INSERT INTO P451F_DB.PUBLIC.G VALUES (5, 50), (6, 60)");
            assertEquals("6 | 7",
                rows("SELECT id + 1 AS k FROM P451F_DB.PUBLIC.G GROUP BY id + 1 HAVING id + 1 > 5 ORDER BY k"));
            assertEquals("60",
                rows("SELECT SUM(v) FROM P451F_DB.PUBLIC.G GROUP BY id HAVING id > 5"));
            assertEquals("60",
                rows("SELECT SUM(v) FROM P451F_DB.PUBLIC.G GROUP BY id HAVING id + 1 > 6"));
            assertEquals("1",
                rows("SELECT COUNT(*) FROM P451F_DB.PUBLIC.G GROUP BY id, v HAVING v > 55"));
            assertRefused("SELECT id FROM P451F_DB.PUBLIC.G GROUP BY id HAVING id * 10 = v",
                "[G.V] is not a valid group by expression");
            assertEquals("60",
                rows("SELECT SUM(v) FROM P451F_DB.PUBLIC.G GROUP BY id + 1 HAVING id + 1 > 6"));
            assertEquals("60",
                rows("SELECT SUM(v) AS s FROM P451F_DB.PUBLIC.G GROUP BY id HAVING MAX(v) > 55 AND id > 5"));
            assertEquals("7, 1",
                rows("SELECT g.id + 1 AS k, COUNT(*) FROM P451F_DB.PUBLIC.G g GROUP BY g.id + 1 HAVING g.id + 1 = 7"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P451F_DB");
        }
    }
}
