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
 * A SELECT with no FROM reads DUAL, one row whose one column COLUMN1 holds NULL: a plain star expands to it,
 * its modifiers are judged against it, and COLUMN1, DUAL.COLUMN1 and $1 read it. A star's EXCLUDE, RENAME and
 * REPLACE refuse a name no column carries over any relation. Frostlake refused every such star and name
 * (live-verified).
 */
public class FromlessDualTest extends BaseDatabaseTest {

    private static final String COMPILATION = "SQL compilation error:\n";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
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

    /** The result's column names joined by commas. */
    private String names(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int c = 0; c < result.getColumnCount(); c++) {
            text.append(c > 0 ? ", " : "").append(result.getColumns().get(c).getName());
        }
        return text.toString();
    }

    @Test
    public void aPlainStarReadsDualsOneNullColumn() {
        assertEquals("COLUMN1", names("SELECT *"));
        assertEquals("null", rows("SELECT *"));
        assertEquals("null, null", rows("SELECT *, *"));
        assertEquals("", rows("SELECT * WHERE 1 = 0"));
        assertEquals("null", rows("SELECT * ORDER BY 1"));
        assertEquals("null", rows("SELECT DISTINCT *"));
        assertEquals("", rows("SELECT * LIMIT 0"));
        assertEquals("null", rows("SELECT * GROUP BY ALL"));
        assertEquals("null, 1", rows("SELECT *, 1 AS x"));
        assertEquals("5 | 7", rows("SELECT id FROM FZ WHERE EXISTS (SELECT *) ORDER BY 1"));
        assertEquals("abc | null", rows("SELECT * UNION ALL SELECT 'abc' ORDER BY 1"));
        assertEquals("null", rows("SELECT * FROM (SELECT *)"));
        assertEquals("null", rows("WITH c AS (SELECT *) SELECT * FROM c"));
        assertEquals("K", names("SELECT * FROM (SELECT *) AS s (k)"));
        engine.execute("CREATE TABLE CT AS SELECT *");
        assertEquals("COLUMN1, VARCHAR(16777216)", rows("DESCRIBE TABLE CT ->> SELECT \"name\", \"type\" FROM $1"));
        // Its modifiers are judged against COLUMN1, and a star they leave empty projects nothing beside the rest.
        assertEquals("Z", names("SELECT * RENAME column1 AS z"));
        assertEquals("x", rows("SELECT * REPLACE ('x' AS column1)"));
        assertEquals("COLUMN1", names("SELECT * ILIKE 'col%'"));
        assertEquals("X", names("SELECT 1 AS x, * ILIKE 'a%'"));
    }

    @Test
    public void dualsColumnIsReadByName() {
        assertEquals("null", rows("SELECT column1"));
        assertEquals("null", rows("SELECT dual.column1"));
        assertEquals("null", rows("SELECT \"DUAL\".column1"));
        assertEquals("null", rows("SELECT dual.*"));
        assertEquals("null", rows("SELECT \"DUAL\".*"));
        assertEquals("null", rows("SELECT $1"));
        assertEquals("1, null", rows("SELECT 1 AS x, column1"));
        assertEquals("0, 1", rows("SELECT COUNT(column1), COUNT(*)"));
        assertEquals("null, 1", rows("SELECT *, COUNT(*) GROUP BY ALL"));
        assertEquals("", rows("SELECT column1 WHERE FALSE"));
        // DUAL's column comes before an item of the same name in WHERE and in the items after it, not in ORDER BY.
        assertEquals("", rows("SELECT 5 AS column1 WHERE column1 = 5"));
        assertEquals("5, null", rows("SELECT 5 AS column1, column1 + 1 AS y"));
        assertEquals("5", rows("SELECT 5 AS column1 ORDER BY column1"));
        // A FROM-less subquery reads its own DUAL before the query around it, which still lends its other names.
        assertEquals("null", rows("SELECT (SELECT column1 + 1) FROM (VALUES (5))"));
        assertEquals("", rows("SELECT column1 FROM (VALUES (5)) WHERE EXISTS (SELECT 1 WHERE column1 = 5)"));
        assertEquals("5, 5 | 7, 7", rows("SELECT id, (SELECT id) FROM FZ ORDER BY 1"));
        // A star argument sees nothing of DUAL.
        assertEquals("null, 0", rows("SELECT *, ARRAY_SIZE(ARRAY_CONSTRUCT(*))"));
    }

    @Test
    public void aFromlessStarAndDualsNamesAreRefusedLikeARelations() {
        final String noColumns = "SQL compilation error: error line 1 at position ";
        assertEquals(COMPILATION + "column 'A' does not exist", refusal("SELECT * EXCLUDE (a)"));
        assertEquals(COMPILATION + "column 'A' does not exist", refusal("SELECT * RENAME a AS b"));
        assertEquals(COMPILATION + "column 'A' does not exist", refusal("SELECT * REPLACE (1 AS a)"));
        assertEquals(COMPILATION + "column 'COLUMN1' does not exist",
            refusal("SELECT * EXCLUDE column1 RENAME column1 AS z"));
        assertEquals(COMPILATION + "duplicate column name 'COLUMN1'", refusal("SELECT * EXCLUDE (column1, column1)"));
        assertEquals(COMPILATION + "duplicate column name 'COLUMN1'",
            refusal("SELECT * RENAME (column1 AS a, column1 AS b)"));
        assertEquals(noColumns + "0\nSELECT with no columns", refusal("SELECT * EXCLUDE (column1)"));
        assertEquals(noColumns + "0\nSELECT with no columns", refusal("SELECT * ILIKE 'a%'"));
        assertEquals(noColumns + "2\nSELECT with no columns", refusal("  SELECT * ILIKE 'a%'"));
        assertEquals(noColumns + "32\nSELECT with no columns",
            refusal("SELECT id FROM FZ WHERE EXISTS (SELECT * ILIKE 'a%')"));
        assertEquals(COMPILATION + "[DUAL.COLUMN1] is not a valid group by expression", refusal("SELECT *, COUNT(*)"));
        assertEquals(COMPILATION + "[DUAL.COLUMN1] is not a valid group by expression", refusal("SELECT COUNT(*), *"));
        assertEquals(COMPILATION + "[DUAL.COLUMN1] is not a valid group by expression",
            refusal("SELECT * HAVING COUNT(*) = 1"));
        assertEquals(noColumns + "7\ninvalid identifier 'COLUMN2'", refusal("SELECT column2"));
        assertEquals(noColumns + "7\ninvalid identifier '\"column1\"'", refusal("SELECT \"column1\""));
        assertEquals(noColumns + "7\ninvalid identifier 'DUAL.NOSUCH'", refusal("SELECT dual.nosuch"));
        assertEquals(noColumns + "7\ninvalid identifier '$2'", refusal("SELECT $2"));
        assertEquals(noColumns + "10\ninvalid identifier 'NOSUCH'", refusal("SELECT *, nosuch"));
        assertEquals(COMPILATION + "Object '\"dual\"' does not exist or not authorized.", refusal("SELECT \"dual\".*"));
        assertEquals(COMPILATION + "Object 'PUBLIC.DUAL' does not exist or not authorized.",
            refusal("SELECT public.dual.*"));
        assertEquals(COMPILATION + "Object 'FZ' does not exist or not authorized.", refusal("SELECT fz.*, *"));
    }

    @Test
    public void aStarsModifiersRefuseANameNoColumnCarries() {
        assertEquals(COMPILATION + "column 'A' does not exist", refusal("SELECT * EXCLUDE (a) FROM FZ"));
        assertEquals(COMPILATION + "column 'A' does not exist", refusal("SELECT * RENAME a AS q FROM FZ"));
        assertEquals(COMPILATION + "column 'A' does not exist", refusal("SELECT * REPLACE (1 AS a) FROM FZ"));
        assertEquals(COMPILATION + "column 'id' does not exist", refusal("SELECT * EXCLUDE (\"id\") FROM FZ"));
        assertEquals(COMPILATION + "column 'A' does not exist", refusal("SELECT {* EXCLUDE (a)} FROM FZ"));
        // In written order: an EXCLUDE or an ILIKE takes its columns away from the modifiers after it.
        assertEquals(COMPILATION + "column 'ID' does not exist", refusal("SELECT * EXCLUDE id RENAME id AS z FROM FZ"));
        assertEquals(COMPILATION + "column 'ID' does not exist", refusal("SELECT * ILIKE 'b' RENAME id AS k FROM FZ"));
        assertEquals(COMPILATION + "column 'A' does not exist", refusal("SELECT * REPLACE (1 AS a) RENAME a AS z FROM FZ"));
        // A list's own duplicates come first.
        assertEquals(COMPILATION + "duplicate column name 'A'", refusal("SELECT * EXCLUDE (id, a, a) FROM FZ"));
        assertEquals(COMPILATION + "duplicate column name 'ID'", refusal("SELECT * RENAME (id AS a, id AS c) FROM FZ"));
        assertEquals(COMPILATION + "duplicate column name 'ID'", refusal("SELECT * REPLACE (1 AS id, 2 AS id) FROM FZ"));
        // Ahead of the query's other names, inside a derived table too; over a join, a name both sides carry.
        assertEquals(COMPILATION + "column 'A' does not exist", refusal("SELECT nosuch, * EXCLUDE (a) FROM FZ"));
        assertEquals(COMPILATION + "column 'A' does not exist",
            refusal("SELECT COUNT(*) FROM (SELECT * EXCLUDE (a) FROM FZ)"));
        assertEquals(COMPILATION + "ambiguous column name 'B'",
            refusal("SELECT * EXCLUDE (b) FROM FZ JOIN FZ AS f2 ON FZ.id = f2.id"));
        assertEquals("B, B", names("SELECT * EXCLUDE (id) FROM FZ JOIN FZ AS f2 USING (id)"));
        assertEquals("SQL compilation error: error line 1 at position 0\nSELECT with no columns",
            refusal("SELECT * ILIKE 'zz%' FROM FZ"));
        assertEquals("SQL compilation error: error line 1 at position 0\nSELECT with no columns",
            refusal("SELECT * EXCLUDE (id, b) FROM FZ"));
        assertEquals("5 | 7", rows("SELECT id, * ILIKE 'zz%' FROM FZ ORDER BY 1"));
    }
}
