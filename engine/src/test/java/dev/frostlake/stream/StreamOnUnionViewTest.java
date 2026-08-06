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

package dev.frostlake.stream;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE STREAM ON VIEW where the view is a {@code UNION ALL} over single-table branches. Snowflake's
 * change tracking supports projections, filters, and UNION ALL (each branch tracked independently on its
 * own base table); plain UNION, joins, and other set operators are not supported and stay rejected.
 */
public class StreamOnUnionViewTest extends BaseDatabaseTest {

    /**
     * A stream's {@code SELECT *} yields the source's data columns first, then METADATA$ACTION,
     * METADATA$ISUPDATE, METADATA$ROW_ID (Snowflake's order). The metadata columns are read by NAME so
     * these assertions survive a change to the number of data columns.
     */
    private String metadata(final ResultSet rs, final Row row, final String column) {
        return String.valueOf(row.getValue(rs.getColumnIndex(column)));
    }

    @Test
    public void testStreamStarPutsDataColumnsBeforeMetadata() {
        engine.execute("CREATE TABLE st_src (id INTEGER)");
        engine.execute("CREATE STREAM st ON TABLE st_src");
        engine.execute("INSERT INTO st_src VALUES (1)");

        final ResultSet rs = engine.executeQuery("SELECT * FROM st");
        assertEquals(4, rs.getColumns().size());
        assertEquals("ID", rs.getColumns().get(0).getName().toUpperCase());
        assertEquals("METADATA$ACTION", rs.getColumns().get(1).getName().toUpperCase());
        assertEquals("METADATA$ISUPDATE", rs.getColumns().get(2).getName().toUpperCase());
        assertEquals("METADATA$ROW_ID", rs.getColumns().get(3).getName().toUpperCase());
        // An explicit column list still resolves the metadata columns by name.
        final ResultSet explicit = engine.executeQuery("SELECT id, METADATA$ACTION FROM st");
        assertEquals(2, explicit.getColumns().size());
        assertEquals("INSERT", String.valueOf(explicit.getRows().get(0).getValue(1)));
    }

    @Test
    public void testStreamOnUnionAllViewCapturesBothBranches() {
        engine.execute("CREATE TABLE t1 (id INTEGER, name VARCHAR)");
        engine.execute("CREATE TABLE t2 (id INTEGER, name VARCHAR)");
        engine.execute("CREATE VIEW u AS SELECT id, name FROM t1 UNION ALL SELECT id, name FROM t2");
        engine.execute("CREATE STREAM s ON VIEW u");

        engine.execute("INSERT INTO t1 VALUES (1, 'a')");
        engine.execute("INSERT INTO t2 VALUES (2, 'b')");

        final ResultSet rs = engine.executeQuery("SELECT * FROM s ORDER BY id");
        assertEquals(2, rs.getRowCount(), "both UNION ALL branches should surface their inserts");

        final Row first = rs.getRows().get(0);
        assertEquals(1L, ((Number) first.getValue(0)).longValue());
        assertEquals("a", first.getValue(1));
        assertEquals("INSERT", metadata(rs, first, "METADATA$ACTION"));

        final Row second = rs.getRows().get(1);
        assertEquals(2L, ((Number) second.getValue(0)).longValue());
        assertEquals("b", second.getValue(1));
        assertEquals("INSERT", metadata(rs, second, "METADATA$ACTION"));
    }

    @Test
    public void testStreamOnUnionAllViewAppliesPerBranchFilter() {
        engine.execute("CREATE TABLE t1 (id INTEGER)");
        engine.execute("CREATE TABLE t2 (id INTEGER)");
        engine.execute("CREATE VIEW u AS SELECT id FROM t1 WHERE id > 10 UNION ALL SELECT id FROM t2 WHERE id < 100");
        engine.execute("CREATE STREAM s ON VIEW u");

        engine.execute("INSERT INTO t1 VALUES (5), (20)");    // only 20 passes t1's filter
        engine.execute("INSERT INTO t2 VALUES (50), (200)");  // only 50 passes t2's filter

        final ResultSet rs = engine.executeQuery("SELECT * FROM s ORDER BY id");
        assertEquals(2, rs.getRowCount(), "each branch filters its own base table's changes");
        assertEquals(20L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(50L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void testUnionAllBranchesDoNotCrossCancel() {
        // A row with identical values exists in both base tables. Deleting it from t2 (insert+delete in
        // the same window) must cancel only t2's change and leave t1's insert intact — the net-delta
        // consolidation is scoped per source table, so look-alike rows in different branches don't
        // cross-cancel. A literal per branch proves which branch's row survived.
        engine.execute("CREATE TABLE t1 (id INTEGER)");
        engine.execute("CREATE TABLE t2 (id INTEGER)");
        engine.execute("CREATE VIEW u AS "
            + "SELECT id, 'FROM_T1' AS src FROM t1 UNION ALL SELECT id, 'FROM_T2' AS src FROM t2");
        engine.execute("CREATE STREAM s ON VIEW u");

        engine.execute("INSERT INTO t1 VALUES (1)");
        engine.execute("INSERT INTO t2 VALUES (1)");
        engine.execute("DELETE FROM t2 WHERE id = 1");

        final ResultSet rs = engine.executeQuery("SELECT * FROM s");
        assertEquals(1, rs.getRowCount(), "t2's insert+delete cancels; t1's insert survives");
        final Row row = rs.getRows().get(0);
        assertEquals(1L, ((Number) row.getValue(0)).longValue());
        assertEquals("FROM_T1", row.getValue(1), "the surviving row must be t1's, not t2's");
        assertEquals("INSERT", metadata(rs, row, "METADATA$ACTION"));
    }

    @Test
    public void testPlainUnionViewIsRejected() {
        engine.execute("CREATE TABLE t1 (id INTEGER)");
        engine.execute("CREATE TABLE t2 (id INTEGER)");
        engine.execute("CREATE VIEW u AS SELECT id FROM t1 UNION SELECT id FROM t2");

        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE STREAM s ON VIEW u");
            }
        });
        assertTrue(ex.getMessage().contains("joins of type '[UNION]'"),
            "plain UNION (dedup) is unsupported, matching Snowflake: " + ex.getMessage());
    }

    @Test
    public void testJoinInsideUnionAllBranchIsRejected() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "matches FROSTLAKE's rejection wording (\"change tracking supports …\") for a join inside "
            + "a UNION ALL arm; single-branch inner-join views ARE supported (StreamOnJoinViewTest) "
            + "and this mixed shape is unmeasured on a real account");
        engine.execute("CREATE TABLE t1 (id INTEGER)");
        engine.execute("CREATE TABLE t2 (id INTEGER)");
        engine.execute("CREATE VIEW u AS SELECT t1.id FROM t1 JOIN t2 ON t1.id = t2.id"
            + " UNION ALL SELECT id FROM t2");

        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE STREAM s ON VIEW u");
            }
        });
        assertTrue(ex.getMessage().contains("change tracking supports"),
            "a join inside a UNION ALL branch is unsupported: " + ex.getMessage());
    }
}
