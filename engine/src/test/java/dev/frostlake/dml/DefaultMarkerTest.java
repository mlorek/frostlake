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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bare word {@code DEFAULT} as a DML value writes the column's DECLARED default. Frostlake had no
 * such marker at all — every one of these statements was refused, which is the direction that matters:
 * valid SQL the account runs.
 *
 * <pre>
 *   a INT DEFAULT 9   VALUES (DEFAULT)   writes 9
 *   b INT             VALUES (DEFAULT)   writes NULL — a column with no default HAS none, and that is
 *                                        not an error
 *   c INT NOT NULL    VALUES (DEFAULT)   "NULL result in a non-nullable column" — the NULL above,
 *                                        refused by the constraint rather than by the marker
 *   id INT AUTOINCREMENT                 takes the next sequence value
 * </pre>
 *
 * <p>The word is a marker only where it STANDS ALONE as the value; in any larger expression it is an
 * ordinary name that resolves to nothing, and {@code SET c = DEFAULT + 1} is "invalid identifier
 * 'DEFAULT'" on both engines.
 */
public class DefaultMarkerTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE dm (a INT DEFAULT 9, b INT, c INT NOT NULL,"
            + " s VARCHAR(5) DEFAULT 'x')");
    }

    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        while (rs.next()) {
            if (text.length() > 0) {
                text.append(" | ");
            }
            for (int i = 0; i < rs.getColumns().size(); i++) {
                text.append(i > 0 ? "," : "").append(String.valueOf(rs.getValue(i)));
            }
        }
        return text.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    /** An INSERT writes the declared default, and NULL for a column that has none. */
    @Test
    public void insertWritesTheDeclaredDefault() {
        engine.execute("INSERT INTO dm (a, b, c, s) VALUES (DEFAULT, DEFAULT, 1, DEFAULT)");
        assertEquals("9,null,1,x", rows("SELECT a, b, c, s FROM dm"));
    }

    /** A NOT NULL column has no default, so the NULL it would write is refused by the constraint. */
    @Test
    public void aNotNullColumnRefusesTheNull() {
        assertTrue(refusal("INSERT INTO dm (a, b, c, s) VALUES (1, 2, DEFAULT, 'y')")
            .contains("failed on column C with error: NULL result in a non-nullable column"),
            refusal("INSERT INTO dm (a, b, c, s) VALUES (1, 2, DEFAULT, 'y')"));
    }

    /** It works positionally too, and per row of a multi-row VALUES. */
    @Test
    public void itWorksPositionallyAndPerRow() {
        engine.execute("INSERT INTO dm VALUES (DEFAULT, 5, 6, 'z')");
        engine.execute("INSERT INTO dm (a, b, c, s) VALUES (DEFAULT, 1, 7, 'p'), (2, DEFAULT, 8, 'q')");
        assertEquals("9,5,6,z | 9,1,7,p | 2,null,8,q",
            rows("SELECT a, b, c, s FROM dm ORDER BY c"));
    }

    /** An UPDATE takes it the same way, for a column with a default and for one without. */
    @Test
    public void updateTakesItToo() {
        engine.execute("INSERT INTO dm VALUES (1, 2, 6, 'z')");
        engine.execute("UPDATE dm SET a = DEFAULT WHERE c = 6");
        engine.execute("UPDATE dm SET b = DEFAULT WHERE c = 6");
        assertEquals("9,null,6,z", rows("SELECT a, b, c, s FROM dm WHERE c = 6"));
    }

    /** An AUTOINCREMENT column takes its next value. */
    @Test
    public void anAutoincrementColumnTakesItsNextValue() {
        engine.execute("CREATE OR REPLACE TABLE dmi (id INT AUTOINCREMENT, v INT DEFAULT 4)");
        engine.execute("INSERT INTO dmi (id, v) VALUES (DEFAULT, DEFAULT)");
        assertEquals("1,4", rows("SELECT id, v FROM dmi"));
    }

    /** Both MERGE clauses take it. */
    @Test
    public void bothMergeClausesTakeIt() {
        engine.execute("CREATE OR REPLACE TABLE dms (k INT, v INT DEFAULT 3)");
        engine.execute("MERGE INTO dms t USING (SELECT 1 AS k) s ON t.k = s.k"
            + " WHEN NOT MATCHED THEN INSERT (k, v) VALUES (s.k, DEFAULT)");
        assertEquals("1,3", rows("SELECT k, v FROM dms"));
        engine.execute("MERGE INTO dms t USING (SELECT 1 AS k) s ON t.k = s.k"
            + " WHEN MATCHED THEN UPDATE SET t.v = DEFAULT");
        assertEquals("1,3", rows("SELECT k, v FROM dms"));
    }

    /** But only where it STANDS ALONE — in a larger expression it is a name, and resolves to nothing. */
    @Test
    public void itIsAMarkerOnlyWhenItStandsAlone() {
        engine.execute("INSERT INTO dm VALUES (1, 2, 6, 'z')");
        assertTrue(refusal("UPDATE dm SET a = DEFAULT + 1 WHERE c = 6")
            .contains("invalid identifier 'DEFAULT'"),
            refusal("UPDATE dm SET a = DEFAULT + 1 WHERE c = 6"));
    }
}
