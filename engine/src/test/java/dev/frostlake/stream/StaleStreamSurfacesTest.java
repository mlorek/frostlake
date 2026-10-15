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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * What a stream shows once its table is replaced under the same name: SHOW STREAMS names no table for a table
 * stream and counts the tables a view stream lost, a read is refused in the account's words, SYSTEM$STREAM_HAS_DATA
 * refuses, and GET_DDL quotes the table's full name; a healthy view stream lists the tables its view reads
 * (live-verified).
 */
public class StaleStreamSurfacesTest extends BaseDatabaseTest {

    private static final String SHOWN = "SELECT \"name\", \"table_name\", \"base_tables\", \"stale\" FROM"
        + " TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1";

    @Override
    protected void setupTest() {
        engine.execute("CREATE SCHEMA pg");
        engine.execute("CREATE TABLE pg.raw (id INT, v STRING)");
        engine.execute("INSERT INTO pg.raw VALUES (1, 'seed')");
        engine.execute("CREATE SCHEMA ps");
        engine.execute("CREATE VIEW ps.rawv AS SELECT id, v FROM pg.raw");
        engine.execute("CREATE STREAM ps.st_view ON VIEW ps.rawv SHOW_INITIAL_ROWS = TRUE");
        engine.execute("CREATE STREAM ps.st_tab ON TABLE pg.raw");
        engine.execute("CREATE STREAM ps.st_app ON TABLE pg.raw APPEND_ONLY = TRUE COMMENT = 'cross'");
    }

    private String answer(final String sql) {
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? ", " : "").append(value);
                }
                rows.append(rows.length() > 0 ? " | " : "").append(cells);
            }
            return rows.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void aHealthyViewStreamListsTheTablesItsViewReads() {
        engine.execute("SHOW STREAMS IN SCHEMA ps");
        assertEquals("ST_APP, TEST_DB.PG.RAW, TEST_DB.PG.RAW, false | ST_TAB, TEST_DB.PG.RAW, TEST_DB.PG.RAW, false"
            + " | ST_VIEW, TEST_DB.PS.RAWV, TEST_DB.PG.RAW, false", answer(SHOWN));
    }

    @Test
    public void aReplacedTableLeavesItsStreamsStale() {
        engine.execute("DROP TABLE pg.raw");
        engine.execute("CREATE TABLE pg.raw (id INT, v STRING)");
        engine.execute("SHOW STREAMS IN SCHEMA ps");
        assertEquals("ST_APP, No privilege or table dropped, No privilege or table dropped, true"
            + " | ST_TAB, No privilege or table dropped, No privilege or table dropped, true"
            + " | ST_VIEW, TEST_DB.PS.RAWV, 1 inaccessible table(s), true", answer(SHOWN));
        assertEquals("Base table 'TEST_DB.PG.RAW' dropped, cannot read from stream 'ST_APP' in line 1 position 14.",
            answer("SELECT * FROM ps.st_app"));
        assertEquals("SQL compilation error: Change tracking is not enabled or has been missing for the time range"
            + " requested on table 'TEST_DB.PG.RAW'.", answer("SELECT * FROM ps.st_view"));
        assertEquals("Base table 'TEST_DB.PG.RAW' dropped, cannot read from stream 'ST_TAB' in line {3} position {4}.",
            answer("SELECT SYSTEM$STREAM_HAS_DATA('ps.st_tab')"));
        assertEquals("create or replace stream TEST_DB.PS.ST_APP on table \"TEST_DB.PG.RAW\" append_only = true;",
            answer("SELECT GET_DDL('STREAM', 'ps.st_app', TRUE)"));
        assertEquals("create or replace stream ST_TAB on table \"TEST_DB.PG.RAW\";",
            answer("SELECT GET_DDL('STREAM', 'ps.st_tab')"));
    }
}
