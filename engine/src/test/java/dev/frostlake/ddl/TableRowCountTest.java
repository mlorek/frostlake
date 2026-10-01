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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A table's row count, as SHOW TABLES and SHOW OBJECTS print it in {@code rows} and
 * INFORMATION_SCHEMA.TABLES in {@code ROW_COUNT}: the rows committed to the table right now, whatever
 * wrote them — INSERT, DELETE, TRUNCATE, MERGE, CTAS, CLONE, INSERT OVERWRITE, a RENAME or a SWAP.
 * These read 0 for every table while nothing kept the count.
 *
 * <p>A transaction's own uncommitted writes are NOT counted, inside the transaction included, and a
 * permanent table a temporary one hides keeps its own count. A view has no count: NULL in
 * INFORMATION_SCHEMA, 0 in SHOW OBJECTS. The neighbouring {@code bytes} / {@code BYTES} figure is
 * live's compressed micro-partition size and is not asserted here.
 */
public class TableRowCountTest extends BaseDatabaseTest {

    /** SHOW TABLES' {@code rows} cell for one table. */
    private String showTablesRows(final String name) {
        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE '" + name + "'");
        return cell(rs, soleRowWhere(rs, "name", name), "rows");
    }

    /** SHOW OBJECTS' {@code rows} cell for one object. */
    private String showObjectsRows(final String name) {
        final ResultSet rs = engine.executeQuery("SHOW OBJECTS LIKE '" + name + "'");
        return cell(rs, soleRowWhere(rs, "name", name), "rows");
    }

    /** INFORMATION_SCHEMA.TABLES' ROW_COUNT for every entry of one name, lowest first. */
    private List<String> infoSchemaRows(final String name) {
        final ResultSet rs = engine.executeQuery("SELECT row_count FROM information_schema.tables"
            + " WHERE table_schema = CURRENT_SCHEMA() AND table_name = '" + name + "'"
            + " ORDER BY row_count");
        final List<String> counts = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            counts.add(String.valueOf(row.getValue(0)));
        }
        return counts;
    }

    /** All three surfaces agree on one count. */
    private void assertRows(final String name, final long expected) {
        assertEquals(String.valueOf(expected), showTablesRows(name), "SHOW TABLES rows of " + name);
        assertEquals(String.valueOf(expected), showObjectsRows(name), "SHOW OBJECTS rows of " + name);
        assertEquals(Arrays.asList(String.valueOf(expected)), infoSchemaRows(name),
            "INFORMATION_SCHEMA.TABLES ROW_COUNT of " + name);
    }

    @Test
    public void everyWritePathMovesTheCount() {
        engine.execute("CREATE TABLE rc0 (a INT)");
        assertRows("RC0", 0);

        engine.execute("CREATE TABLE rc (a INT)");
        engine.execute("INSERT INTO rc VALUES (1), (2), (3)");
        assertRows("RC", 3);
        engine.execute("INSERT INTO rc VALUES (4)");
        assertRows("RC", 4);
        engine.execute("DELETE FROM rc WHERE a = 2");
        assertRows("RC", 3);
        engine.execute("UPDATE rc SET a = a + 10");
        assertRows("RC", 3);
        engine.execute("TRUNCATE TABLE rc");
        assertRows("RC", 0);

        engine.execute("CREATE TABLE rcm (a INT)");
        engine.execute("MERGE INTO rcm USING (SELECT 1 AS a UNION ALL SELECT 2) s ON rcm.a = s.a"
            + " WHEN NOT MATCHED THEN INSERT (a) VALUES (s.a)");
        assertRows("RCM", 2);

        engine.execute("CREATE TABLE rcc AS SELECT 1 AS a UNION ALL SELECT 2");
        assertRows("RCC", 2);
        engine.execute("CREATE TABLE rcl CLONE rcm");
        assertRows("RCL", 2);

        engine.execute("CREATE TABLE rco (a INT)");
        engine.execute("INSERT INTO rco VALUES (1), (2), (3)");
        engine.execute("INSERT OVERWRITE INTO rco VALUES (7)");
        assertRows("RCO", 1);
    }

    @Test
    public void everyTableKindIsCounted() {
        engine.execute("CREATE TEMPORARY TABLE rtemp (a INT)");
        engine.execute("INSERT INTO rtemp VALUES (1), (2)");
        assertRows("RTEMP", 2);
        engine.execute("CREATE TRANSIENT TABLE rtrans (a INT)");
        engine.execute("INSERT INTO rtrans VALUES (1), (2), (3), (4)");
        assertRows("RTRANS", 4);
        engine.execute("CREATE TABLE \"lower rc\" (a INT)");
        engine.execute("INSERT INTO \"lower rc\" VALUES (1), (2)");
        assertRows("lower rc", 2);
    }

    @Test
    public void aRenameOrASwapCarriesTheRows() {
        engine.execute("CREATE TABLE rn (a INT)");
        engine.execute("INSERT INTO rn VALUES (1), (2)");
        engine.execute("ALTER TABLE rn RENAME TO rn2");
        assertRows("RN2", 2);

        engine.execute("CREATE TABLE sw1 (a INT)");
        engine.execute("INSERT INTO sw1 VALUES (1)");
        engine.execute("CREATE TABLE sw2 (a INT)");
        engine.execute("INSERT INTO sw2 VALUES (1), (2), (3), (4), (5)");
        engine.execute("ALTER TABLE sw1 SWAP WITH sw2");
        assertRows("SW1", 5);
        assertRows("SW2", 1);
    }

    /**
     * A temporary table may take a permanent table's name. SHOW lists the temporary one; the
     * INFORMATION_SCHEMA lists both, each with its own count, and dropping the temporary table brings
     * the permanent one's count back.
     */
    @Test
    public void aHiddenPermanentTableKeepsItsOwnCount() {
        engine.execute("CREATE TABLE sh (a INT)");
        engine.execute("INSERT INTO sh VALUES (1), (2), (3)");
        engine.execute("CREATE TEMPORARY TABLE sh (a INT)");
        engine.execute("INSERT INTO sh VALUES (9)");
        assertEquals("1", showTablesRows("SH"));
        assertEquals("1", showObjectsRows("SH"));
        assertEquals(Arrays.asList("1", "3"), infoSchemaRows("SH"));
        engine.execute("DROP TABLE sh");
        assertRows("SH", 3);
    }

    /** Uncommitted rows are not counted — not even by the session that wrote them. */
    @Test
    public void uncommittedRowsAreNotCounted() {
        engine.execute("CREATE TABLE rtx (a INT)");
        engine.execute("INSERT INTO rtx VALUES (1)");
        engine.execute("CREATE TABLE rtx2 (a INT)");
        engine.execute("INSERT INTO rtx2 VALUES (1), (2)");
        engine.execute("BEGIN");
        try {
            engine.execute("INSERT INTO rtx VALUES (5), (6)");
            engine.execute("DELETE FROM rtx2 WHERE a = 1");
            assertRows("RTX", 1);
            assertRows("RTX2", 2);
        } finally {
            engine.execute("ROLLBACK");
        }
        assertRows("RTX", 1);

        engine.execute("BEGIN");
        try {
            engine.execute("INSERT INTO rtx VALUES (5), (6)");
        } finally {
            engine.execute("COMMIT");
        }
        assertRows("RTX", 3);
    }

    @Test
    public void aViewHasNoCount() {
        engine.execute("CREATE TABLE vb (a INT)");
        engine.execute("INSERT INTO vb VALUES (1), (2)");
        engine.execute("CREATE VIEW vv AS SELECT * FROM vb");
        assertEquals("0", showObjectsRows("VV"));
        final ResultSet rs = engine.executeQuery("SELECT row_count, bytes FROM information_schema.tables"
            + " WHERE table_schema = CURRENT_SCHEMA() AND table_name = 'VV'");
        assertEquals(1, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(0));
        assertNull(rs.getRows().get(0).getValue(1));
    }
}
