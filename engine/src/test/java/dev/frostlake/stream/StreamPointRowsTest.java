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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a stream started at an {@code AT | BEFORE} point holds. The changes since the point are kept row by row: a
 * row rewritten since is an update pair, a row deleted and inserted again is a delete and an insert. A view's
 * stream starts at the version of each table the view reads, and a point is refused when a table changed after it
 * while change tracking was off. A SHOW_INITIAL_ROWS stream on a view first reports the view's rows. Every
 * assertion holds on the embedded engine and on a real account alike.
 */
public class StreamPointRowsTest extends BaseDatabaseTest {

    private static final String TIME_TRAVEL_REFUSAL = "Time travel data is not available for table %s. The "
        + "requested time is either beyond the allowed time travel period or before the object creation time.";

    private String refusalOf(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return error.getMessage();
    }

    /** The rows of a query, each row's cells joined by commas, the rows by bars. */
    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (final Row row : result.getRows()) {
            if (text.length() > 0) {
                text.append('|');
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    text.append(',');
                }
                text.append(row.getValue(i));
            }
        }
        return text.toString();
    }

    @Test
    public void aRowRewrittenSinceThePointIsAnUpdatePair() {
        engine.execute("CREATE TABLE k (id INT, v VARCHAR) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO k VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        engine.execute("SET qk = LAST_QUERY_ID()");
        engine.execute("UPDATE k SET v = 'x' WHERE id = 1");
        engine.execute("UPDATE k SET v = 'a' WHERE id = 1");
        engine.execute("UPDATE k SET v = 'y' WHERE id = 2");
        engine.execute("DELETE FROM k WHERE id = 3");
        engine.execute("INSERT INTO k VALUES (3, 'c')");
        engine.execute("CREATE STREAM sk ON TABLE k AT (STATEMENT => $qk)");
        assertEquals("2,b,DELETE,true|2,y,INSERT,true|3,c,DELETE,false|3,c,INSERT,false",
            rows("SELECT id, v, METADATA$ACTION, METADATA$ISUPDATE FROM sk ORDER BY id, METADATA$ACTION"));
        engine.execute("CREATE STREAM sk2 ON TABLE k AT (STATEMENT => $qk) APPEND_ONLY = TRUE");
        assertEquals("3,c,INSERT,false",
            rows("SELECT id, v, METADATA$ACTION, METADATA$ISUPDATE FROM sk2 ORDER BY id, METADATA$ACTION"));
    }

    @Test
    public void changesMadeWhileTrackingWasOffRefuseThePoint() {
        engine.execute("CREATE TABLE x (id INT)");
        engine.execute("INSERT INTO x VALUES (1)");
        engine.execute("SET qx = LAST_QUERY_ID()");
        engine.execute("INSERT INTO x VALUES (2)");
        assertEquals("SQL compilation error: Change tracking is not enabled or has been missing for the time range "
            + "requested on table 'X'.", refusalOf("CREATE STREAM sx ON TABLE x AT (STATEMENT => $qx)"));
        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE 'X'");
        assertEquals("ON", cell(tables, soleRowWhere(tables, "name", "X"), "change_tracking"));
    }

    @Test
    public void aViewsStreamStartsAtEachTablesVersion() {
        engine.execute("CREATE TABLE t (id INT, v VARCHAR)");
        engine.execute("CREATE STREAM sa ON TABLE t");
        engine.execute("INSERT INTO t VALUES (1, 'a')");
        engine.execute("INSERT INTO t VALUES (2, 'b')");
        engine.execute("SET q2 = LAST_QUERY_ID()");
        engine.execute("UPDATE t SET v = 'z' WHERE id = 1");
        engine.execute("CREATE VIEW vw AS SELECT id, v FROM t");
        engine.execute("CREATE VIEW vf AS SELECT id FROM t WHERE id > 1");
        assertEquals(String.format(TIME_TRAVEL_REFUSAL, "T"),
            refusalOf("CREATE STREAM sv0 ON VIEW vw AT (OFFSET => -3600)"));
        engine.execute("CREATE STREAM sv1 ON VIEW vw AT (STATEMENT => $q2)");
        assertEquals("1,a,DELETE,true|1,z,INSERT,true",
            rows("SELECT id, v, METADATA$ACTION, METADATA$ISUPDATE FROM sv1 ORDER BY id, METADATA$ACTION"));
        engine.execute("CREATE STREAM sv2 ON VIEW vw AT (STREAM => 'sa')");
        assertEquals("1,z,INSERT,false|2,b,INSERT,false",
            rows("SELECT id, v, METADATA$ACTION, METADATA$ISUPDATE FROM sv2 ORDER BY id, METADATA$ACTION"));
        engine.execute("CREATE STREAM sv3 ON VIEW vf BEFORE (STATEMENT => $q2)");
        assertEquals("2,INSERT,false", rows("SELECT id, METADATA$ACTION, METADATA$ISUPDATE FROM sv3 ORDER BY id"));
        engine.execute("CREATE STREAM sv4 ON VIEW vw AT (STATEMENT => $q2) SHOW_INITIAL_ROWS = TRUE");
        assertEquals("1,a,INSERT|2,b,INSERT", rows("SELECT id, v, METADATA$ACTION FROM sv4 ORDER BY id"));
    }

    @Test
    public void aViewsStreamFirstReportsTheViewsRows() {
        engine.execute("CREATE TABLE x (id INT)");
        engine.execute("CREATE TABLE y (id INT)");
        engine.execute("INSERT INTO x VALUES (1), (2)");
        engine.execute("INSERT INTO y VALUES (1), (2)");
        engine.execute("CREATE VIEW vy AS SELECT id FROM y");
        engine.execute("CREATE VIEW vu AS SELECT id FROM y UNION ALL SELECT id FROM x");
        engine.execute("CREATE STREAM svi ON VIEW vy SHOW_INITIAL_ROWS = TRUE");
        assertEquals("1,INSERT,false|2,INSERT,false",
            rows("SELECT id, METADATA$ACTION, METADATA$ISUPDATE FROM svi ORDER BY id"));
        engine.execute("CREATE STREAM svu ON VIEW vu SHOW_INITIAL_ROWS = TRUE");
        assertEquals("1,INSERT|1,INSERT|2,INSERT|2,INSERT", rows("SELECT id, METADATA$ACTION FROM svu ORDER BY id"));
        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE 'Y'");
        assertEquals("ON", cell(tables, soleRowWhere(tables, "name", "Y"), "change_tracking"));
    }

    @Test
    public void aNullPointIsRefusedPerKind() {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE VIEW vw AS SELECT id FROM t");
        assertEquals("Time travel AT(STREAM => _) expected a stream name, got NULL.",
            refusalOf("CREATE STREAM s1 ON TABLE t AT (STREAM => NULL)"));
        assertEquals("Time travel AT(STREAM => _) expected a stream name, got NULL.",
            refusalOf("CREATE STREAM s2 ON TABLE t BEFORE (STREAM => NULL)"));
        assertEquals("Time travel AT(STREAM => _) expected a stream name, got NULL.",
            refusalOf("CREATE STREAM s3 ON VIEW vw AT (STREAM => NULL)"));
        assertEquals(String.format(TIME_TRAVEL_REFUSAL, "T"),
            refusalOf("CREATE STREAM s4 ON TABLE t AT (TIMESTAMP => NULL)"));
        assertEquals("SQL compilation error:\nInvalid data type [null] in AT(OFFSET => null)",
            refusalOf("CREATE STREAM s5 ON TABLE t AT (OFFSET => NULL)"));
        assertEquals("Statement null not found", refusalOf("CREATE STREAM s6 ON TABLE t AT (STATEMENT => NULL)"));
    }

    @Test
    public void aDynamicTablesStreamStartsNoEarlierThanTheTable() {
        engine.execute("CREATE TABLE u (id INT)");
        engine.execute("CREATE STREAM su ON TABLE u");
        engine.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = DOWNSTREAM WAREHOUSE = COMPUTE_WH "
            + "INITIALIZE = ON_SCHEDULE AS SELECT id FROM u");
        assertEquals(String.format(TIME_TRAVEL_REFUSAL, "DT"),
            refusalOf("CREATE STREAM sd1 ON DYNAMIC TABLE dt AT (OFFSET => -3600)"));
        assertEquals(String.format(TIME_TRAVEL_REFUSAL, "DT"),
            refusalOf("CREATE STREAM sd2 ON DYNAMIC TABLE dt AT (STREAM => 'su')"));
        engine.execute("CREATE STREAM sda ON DYNAMIC TABLE dt");
        engine.execute("CREATE STREAM sd3 ON DYNAMIC TABLE dt AT (STREAM => 'sda')");
    }
}
