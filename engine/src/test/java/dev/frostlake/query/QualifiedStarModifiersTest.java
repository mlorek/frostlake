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
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A QUALIFIED star takes the same modifiers as a plain one — EXCLUDE, ILIKE, REPLACE and RENAME — applied to
 * the columns of the one relation its qualifier names, and refused in the same words when they name a column
 * that relation does not carry. Every projection path honours them: the plain select list, a derived table or
 * CTE, a grouped query, and a select list with a window call beside the star. Every cell is live-verified.
 */
public class QualifiedStarModifiersTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    /** The column names, then the rows: "A, B: 1, TRUE | 2, FALSE". */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final ResultSetColumn column : rs.getColumns()) {
            out.append(out.length() > 0 ? ", " : "").append(column.getName().toUpperCase(Locale.ROOT));
        }
        out.append(':');
        boolean firstRow = true;
        for (final Row row : rs.getRows()) {
            out.append(firstRow ? " " : " | ");
            firstRow = false;
            for (int c = 0; c < row.getValues().size(); c++) {
                if (c > 0) {
                    out.append(", ");
                }
                final String text = String.valueOf(row.getValue(c));
                out.append("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)
                    ? text.toUpperCase(Locale.ROOT) : text);
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), sql + " -> " + refused.getMessage());
    }

    /** EXCLUDE and ILIKE pick the named relation's columns. */
    @Test
    public void excludeAndIlikePickColumns() {
        assertEquals("ID: 5 | 7", answer("SELECT fz.* EXCLUDE b FROM fz AS fz ORDER BY 1"));
        assertEquals("ID: 5 | 7", answer("SELECT FZ.* EXCLUDE (b) FROM fz ORDER BY 1"));
        assertEquals("ID: 5 | 7", answer("SELECT f.* ILIKE 'i%' FROM fz f ORDER BY 1"));
        assertEquals("ID: 5 | 7", answer("SELECT t.* EXCLUDE b FROM (SELECT * FROM fz) t ORDER BY 1"));
        assertEquals("ID, AGAIN: 5, 5 | 7, 7", answer("SELECT fz.* EXCLUDE b, fz.id AS again FROM fz ORDER BY 1"));
        assertEquals("ID: 5", answer("SELECT fz.* EXCLUDE b FROM fz WHERE b ORDER BY 1"));
        assertEquals("ID: 5 | 7", answer("SELECT DISTINCT fz.* EXCLUDE b FROM fz ORDER BY 1"));
    }

    /** RENAME names the output column; REPLACE substitutes the value and types the column from it. */
    @Test
    public void renameAndReplaceRewriteTheColumns() {
        assertEquals("I, B: 5, TRUE | 7, FALSE", answer("SELECT fz.* RENAME id AS i FROM fz ORDER BY 1"));
        assertEquals("K, C: 5, TRUE | 7, FALSE", answer("SELECT fz.* RENAME (id AS k, b AS c) FROM fz ORDER BY 1"));
        assertEquals("B, B: 5, TRUE | 7, FALSE", answer("SELECT fz.* RENAME id AS b FROM fz ORDER BY 1"));
        assertEquals("ID, B: 6, TRUE | 8, FALSE", answer("SELECT fz.* REPLACE (id + 1 AS id) FROM fz ORDER BY 1"));
        assertEquals("ID, B: 5, x | 7, x", answer("SELECT fz.* REPLACE ('x' AS b) FROM fz ORDER BY 1"));
        final DataType replaced = engine.executeQuery("SELECT fz.* REPLACE ('x' AS b) FROM fz").getColumns()
            .get(1).getDataType();
        assertTrue(replaced instanceof StringType, String.valueOf(replaced));
        assertEquals(1, ((StringType) replaced).getMaxLength());
        assertEquals("ID, B: 5, 50", answer("SELECT a.* REPLACE (g.v AS b) FROM fz a JOIN g ON a.id = g.id"));
    }

    /** The modifiers combine in the one order the grammar takes them. */
    @Test
    public void theModifiersCombine() {
        assertEquals("K: 5 | 7", answer("SELECT fz.* EXCLUDE b RENAME id AS k FROM fz ORDER BY 1"));
        assertEquals("ID: 10 | 14", answer("SELECT fz.* EXCLUDE b REPLACE (id * 2 AS id) FROM fz ORDER BY 1"));
        assertEquals("K: 5 | 7", answer("SELECT fz.* ILIKE 'i%' RENAME id AS k FROM fz ORDER BY 1"));
        assertEquals("ID: 10 | 14", answer("SELECT fz.* ILIKE 'i%' REPLACE (id * 2 AS id) FROM fz ORDER BY 1"));
        assertEquals("K, B: 10, TRUE | 14, FALSE",
            answer("SELECT fz.* REPLACE (id * 2 AS id) RENAME id AS k FROM fz ORDER BY 1"));
    }

    /** Over a join each qualified star reads its own relation. */
    @Test
    public void eachStarReadsItsOwnRelation() {
        assertEquals("ID, ID, V: 5, 5, 50", answer("SELECT a.* EXCLUDE b, g.* FROM fz a JOIN g ON a.id = g.id"));
        assertEquals("B, V: TRUE, 50", answer("SELECT a.* EXCLUDE id, g.* EXCLUDE id FROM fz a JOIN g ON a.id = g.id"));
        assertEquals("ID, ID: 5, 5 | 7, null",
            answer("SELECT a.* EXCLUDE b, g.* EXCLUDE v FROM fz a LEFT JOIN g ON a.id = g.id ORDER BY 1"));
        assertEquals("ID: 5 | 5 | 6 | 7",
            answer("SELECT fz.* EXCLUDE b FROM fz UNION ALL SELECT g.* EXCLUDE v FROM g ORDER BY 1"));
    }

    /** A modifier naming a column the relation does not carry is refused, as for a plain star. */
    @Test
    public void aModifierNamingNoColumnIsRefused() {
        assertRefused("SELECT fz.* EXCLUDE nosuch FROM fz", "column 'NOSUCH' does not exist");
        assertRefused("SELECT a.* EXCLUDE v FROM fz a JOIN g ON a.id = g.id", "column 'V' does not exist");
        assertRefused("SELECT fz.* REPLACE (1 AS nosuch) FROM fz", "column 'NOSUCH' does not exist");
        assertRefused("SELECT fz.* RENAME nosuch AS k FROM fz", "column 'NOSUCH' does not exist");
        assertRefused("SELECT fz.* EXCLUDE (id, id) FROM fz", "duplicate column name 'ID'");
        assertRefused("SELECT {fz.* EXCLUDE nosuch} FROM fz", "column 'NOSUCH' does not exist");
        assertRefused("SELECT fz.* EXCLUDE (id, b) FROM fz", "SELECT with no columns");
        assertRefused("SELECT fz.* ILIKE 'zz%' FROM fz", "SELECT with no columns");
        assertRefused("SELECT x.* EXCLUDE b FROM fz", "Object 'X' does not exist or not authorized.");
    }

    /** A derived table, a CTE, a grouped query and a function argument see the modified columns. */
    @Test
    public void everyPathSeesTheModifiedColumns() {
        assertEquals("ID: 5 | 7", answer("SELECT * FROM (SELECT fz.* EXCLUDE b FROM fz) ORDER BY 1"));
        assertEquals("K: 5 | 7", answer("WITH c AS (SELECT fz.* RENAME id AS k FROM fz) SELECT k FROM c ORDER BY 1"));
        assertEquals("COUNT(*), ID, B: 1, 6, TRUE | 1, 8, FALSE",
            answer("SELECT COUNT(*), fz.* REPLACE (id + 1 AS id) FROM fz GROUP BY ALL ORDER BY 2"));
        assertEquals("HASH(FZ.* EXCLUDE B) = HASH(ID): TRUE | TRUE",
            answer("SELECT HASH(fz.* EXCLUDE b) = HASH(id) FROM fz ORDER BY 1"));
    }

    /** Beside a window call the star projects the same columns, and the window value keeps its own slot. */
    @Test
    public void aWindowBesideTheStarKeepsItsSlot() {
        assertEquals("ID, RN: 5, 2 | 7, 1",
            answer("SELECT fz.* EXCLUDE b, ROW_NUMBER() OVER (ORDER BY id DESC) AS rn FROM fz ORDER BY 1"));
        assertEquals("ID, B, RN: 6, TRUE, 2 | 8, FALSE, 1",
            answer("SELECT fz.* REPLACE (id + 1 AS id), ROW_NUMBER() OVER (ORDER BY id DESC) AS rn FROM fz ORDER BY 1"));
        assertEquals("K, B, RN: 5, TRUE, 1 | 7, FALSE, 2",
            answer("SELECT f.* RENAME id AS k, ROW_NUMBER() OVER (ORDER BY f.id) AS rn FROM fz f ORDER BY 1"));
        assertEquals("ID, V, RN: 5, 50, 1", answer("SELECT a.* EXCLUDE b, g.* EXCLUDE id,"
            + " ROW_NUMBER() OVER (ORDER BY a.id) AS rn FROM fz a JOIN g ON a.id = g.id"));
        assertEquals("ID, B, RN: 5, TRUE, 1",
            answer("SELECT a.*, ROW_NUMBER() OVER (ORDER BY a.id) AS rn FROM fz a JOIN g ON a.id = g.id"));
        assertEquals("ID, V, S: 5, 51, 5",
            answer("SELECT g.* REPLACE (v + 1 AS v), SUM(a.id) OVER () AS s FROM fz a JOIN g ON a.id = g.id"));
        // Each column keeps its own value even where two share a name.
        assertEquals("A, A, RN: 1, 2, 1",
            answer("SELECT t.*, ROW_NUMBER() OVER (ORDER BY 1) AS rn FROM (SELECT 1 a, 2 a) t"));
        assertEquals("X, X, RN: 1, 2, 1",
            answer("SELECT t.*, ROW_NUMBER() OVER (ORDER BY 1) AS rn FROM (SELECT 1 AS \"x\", 2 AS \"X\") t"));
    }
}
