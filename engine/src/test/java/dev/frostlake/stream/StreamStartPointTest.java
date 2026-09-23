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
 * Where a stream starts. {@code AT | BEFORE (STREAM => '<name>')} starts it at another stream's offset, with that
 * stream's pending changes; a TIMESTAMP, an OFFSET or a STATEMENT starts it at that version of its table, with
 * the changes made since pending. A SHOW_INITIAL_ROWS stream first reports the rows its table held at the start —
 * and nothing else until it is consumed, after which the changes since the start follow.
 */
public class StreamStartPointTest extends BaseDatabaseTest {

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
    public void aStreamStartsAtAnotherStreamsOffset() {
        engine.execute("CREATE TABLE t (id INT, v VARCHAR)");
        engine.execute("CREATE STREAM s1 ON TABLE t");
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE STREAM s2 ON TABLE t AT (STREAM => 's1')");
        assertEquals("1,a,INSERT,false|2,b,INSERT,false",
            rows("SELECT id, v, METADATA$ACTION, METADATA$ISUPDATE FROM s2 ORDER BY id"));
        assertEquals("Stream 'no_such_stream' not found.",
            refusalOf("CREATE STREAM s2b ON TABLE t AT (STREAM => 'no_such_stream')"));
        engine.execute("CREATE STREAM s2c ON TABLE t BEFORE (STREAM => 's1')");
        assertEquals("SQL compilation error: error line 1 at position 43\ninvalid identifier 'S1'",
            refusalOf("CREATE STREAM s2d ON TABLE t AT (STREAM => s1)"));
        engine.execute("CREATE OR REPLACE STREAM s1 ON TABLE t AT (STREAM => 's1')");
        assertEquals("1,INSERT|2,INSERT", rows("SELECT id, METADATA$ACTION FROM s1 ORDER BY id"),
            "a stream recreated at its own offset keeps its pending changes");
        engine.execute("CREATE STREAM s2f ON TABLE t AT (STREAM => 's1') APPEND_ONLY = TRUE COMMENT = 'y'");
        assertEquals("1,INSERT|2,INSERT", rows("SELECT id, METADATA$ACTION FROM s2f ORDER BY id"));
        engine.execute("UPDATE t SET v = 'z' WHERE id = 1");
        engine.execute("CREATE TABLE sink (id INT)");
        engine.execute("INSERT INTO sink SELECT id FROM s1");
        engine.execute("CREATE STREAM s3 ON TABLE t AT (STREAM => 's1')");
        assertEquals("0", rows("SELECT COUNT(*) FROM s3"), "a consumed offset has nothing pending");
        assertEquals("1,z,INSERT|2,b,INSERT", rows("SELECT id, v, METADATA$ACTION FROM s2 ORDER BY id"),
            "each stream keeps its own offset");
    }

    @Test
    public void anotherTablesStreamOffsetIsAPointInTime() {
        engine.execute("CREATE TABLE t_old (id INT)");
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s1 ON TABLE t");
        engine.execute("INSERT INTO t_old VALUES (7)");
        assertEquals("SQL compilation error: Change tracking is not enabled or has been missing for the time range "
            + "requested on table 'T_OLD'.", refusalOf("CREATE STREAM s_old ON TABLE t_old AT (STREAM => 's1')"));
        engine.execute("CREATE TABLE t2 (id INT)");
        assertEquals(String.format(TIME_TRAVEL_REFUSAL, "T2"),
            refusalOf("CREATE STREAM s2e ON TABLE t2 AT (STREAM => 's1')"));
    }

    @Test
    public void aStatementATimestampOrAnOffsetStartsAStreamAtThatVersion() {
        engine.execute("CREATE TABLE ct (id INT) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO ct VALUES (10)");
        engine.execute("SET qid = LAST_QUERY_ID()");
        engine.execute("INSERT INTO ct VALUES (20)");
        engine.execute("CREATE STREAM s3 ON TABLE ct BEFORE (STATEMENT => $qid)");
        assertEquals("10,INSERT|20,INSERT", rows("SELECT id, METADATA$ACTION FROM s3 ORDER BY id"));
        engine.execute("CREATE STREAM s3b ON TABLE ct AT (STATEMENT => $qid)");
        assertEquals("20,INSERT", rows("SELECT id, METADATA$ACTION FROM s3b ORDER BY id"));
        assertEquals("Statement not-a-query-id not found",
            refusalOf("CREATE STREAM s3c ON TABLE ct AT (STATEMENT => 'not-a-query-id')"));
        assertEquals("SQL compilation error:\nInvalid data type [60] in AT(OFFSET => 60)",
            refusalOf("CREATE STREAM s_j ON TABLE ct AT (OFFSET => 60)"));
        assertEquals(String.format(TIME_TRAVEL_REFUSAL, "CT"),
            refusalOf("CREATE STREAM s2i ON TABLE ct BEFORE (TIMESTAMP => '2020-01-01'::TIMESTAMP_LTZ)"));
        assertEquals("SQL compilation error: line 1 at position 47:\nQuery contains time travel to a version "
            + "containing function 'CURRENT_TIMESTAMP', but change tracking is not supported on queries with "
            + "non-deterministic functions in versions.",
            refusalOf("CREATE STREAM s2g ON TABLE ct AT (TIMESTAMP => CURRENT_TIMESTAMP())"));
    }

    @Test
    public void initialRowsComeFirstAndTheChangesSinceTheStartAfterThem() {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s ON TABLE t APPEND_ONLY = TRUE SHOW_INITIAL_ROWS = TRUE");
        engine.execute("INSERT INTO t VALUES (1)");
        assertEquals("0", rows("SELECT COUNT(*) FROM s"), "the table's rows at the start: none");
        engine.execute("CREATE OR REPLACE TEMP TABLE reset AS SELECT * FROM s WHERE 1 = 0");
        assertEquals("1", rows("SELECT id FROM s ORDER BY id"), "consumed, the stream reports changes since its start");
        engine.execute("INSERT INTO t VALUES (2), (3)");
        assertEquals("1|2|3", rows("SELECT id FROM s ORDER BY id"));

        engine.execute("CREATE TABLE t2 (id INT)");
        engine.execute("INSERT INTO t2 VALUES (10)");
        engine.execute("CREATE STREAM s2 ON TABLE t2 SHOW_INITIAL_ROWS = TRUE");
        engine.execute("DELETE FROM t2 WHERE id = 10");
        engine.execute("INSERT INTO t2 VALUES (20)");
        assertEquals("10,INSERT", rows("SELECT id, METADATA$ACTION FROM s2 ORDER BY id"));
        engine.execute("CREATE TABLE sink (id INT)");
        engine.execute("INSERT INTO sink SELECT id FROM s2");
        assertEquals("10,DELETE|20,INSERT", rows("SELECT id, METADATA$ACTION FROM s2 ORDER BY id"));
    }

    @Test
    public void initialRowsAtAnotherStreamsOffsetAreTheTableAtThatOffset() {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s1 ON TABLE t");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE STREAM s2 ON TABLE t AT (STREAM => 's1') SHOW_INITIAL_ROWS = TRUE");
        assertEquals("0", rows("SELECT COUNT(*) FROM s2"));
        engine.execute("CREATE TABLE sink (id INT)");
        engine.execute("INSERT INTO sink SELECT id FROM s2");
        assertEquals("1,INSERT", rows("SELECT id, METADATA$ACTION FROM s2"));
        engine.execute("INSERT INTO t VALUES (2)");
        assertEquals("1,INSERT|2,INSERT", rows("SELECT id, METADATA$ACTION FROM s2 ORDER BY id"));
    }
}
