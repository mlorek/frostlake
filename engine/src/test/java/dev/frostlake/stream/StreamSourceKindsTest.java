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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The objects a stream can track beyond a table and a view: a stage's directory table, whose changes arrive
 * with ALTER STAGE … REFRESH; a dynamic table, whose changes arrive with its refreshes; an event table, which
 * is a table to a stream; and an external table, which this engine keeps none of, so only its refusals apply.
 * A stream also takes tags as it is created, written before COPY GRANTS.
 */
public class StreamSourceKindsTest extends BaseDatabaseTest {

    private static final String PREFIX = "TEST_DB.TEST_SCHEMA.";

    private String refusalOf(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return error.getMessage();
    }

    private String streamCell(final String stream, final String column) {
        final ResultSet streams = engine.executeQuery("SHOW STREAMS LIKE '" + stream + "'");
        return cell(streams, soleRowWhere(streams, "name", stream), column);
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
    public void aStageStreamRecordsWhatEachDirectoryRefreshChanged() {
        engine.execute("CREATE STAGE st DIRECTORY = (ENABLE = TRUE)");
        engine.execute("CREATE STREAM ss ON STAGE st COMMENT = 'files'");
        assertEquals("Stage", streamCell("SS", "source_type"));
        assertEquals(PREFIX + "ST", streamCell("SS", "table_name"));
        assertEquals("DEFAULT", streamCell("SS", "mode"));
        stageLocalFile("st", "f1.csv", "a,1\n");
        assertEquals("0", rows("SELECT COUNT(*) FROM ss"), "a file arrives in the stream with the refresh");
        engine.execute("ALTER STAGE st REFRESH");
        assertEquals("f1.csv,INSERT,false,", rows("SELECT relative_path, METADATA$ACTION, METADATA$ISUPDATE, "
            + "METADATA$ROW_ID FROM ss"));
        engine.execute("CREATE TABLE fsink (p VARCHAR)");
        engine.execute("INSERT INTO fsink SELECT relative_path FROM ss");
        assertEquals("0", rows("SELECT COUNT(*) FROM ss"));
        stageLocalFile("st", "f2.csv", "c,3\n");
        engine.execute("REMOVE @st/f1.csv");
        engine.execute("ALTER STAGE st REFRESH");
        assertEquals("f1.csv,DELETE,false|f2.csv,INSERT,false", rows("SELECT relative_path, METADATA$ACTION, "
            + "METADATA$ISUPDATE FROM ss ORDER BY relative_path"));
    }

    @Test
    public void aStageStreamNeedsADirectoryTableAndTakesNoOptions() {
        engine.execute("CREATE STAGE st DIRECTORY = (ENABLE = TRUE)");
        engine.execute("CREATE STAGE st_nodir");
        assertEquals("DIRECTORY not enabled for the stage ST_NODIR", refusalOf("CREATE STREAM s ON STAGE st_nodir"));
        assertEquals("DIRECTORY not enabled for the stage ST_NODIR", refusalOf("ALTER STAGE st_nodir REFRESH"));
        assertEquals("Streams on directories cannot have APPEND_ONLY set to true.",
            refusalOf("CREATE STREAM s ON STAGE st APPEND_ONLY = TRUE"));
        assertEquals("Streams on directories cannot have SHOW_INITIAL_ROWS set to true.",
            refusalOf("CREATE STREAM s ON STAGE st SHOW_INITIAL_ROWS = TRUE"));
        engine.execute("CREATE STREAM ss ON STAGE st");
        assertEquals("SQL compilation error: Time travel not supported for stream creation on stages.",
            refusalOf("CREATE STREAM s ON STAGE st AT (STREAM => 'ss')"));
        assertTrue(refusalOf("CREATE STREAM s ON STAGE no_such_stage").startsWith("SQL compilation error:\nStage '"
            + PREFIX + "NO_SUCH_STAGE' does not exist or not authorized."));
        engine.execute("ALTER STAGE st REFRESH SUBPATH = 'a/'");
    }

    @Test
    public void aDynamicTableStreamRecordsWhatEachRefreshChanged() {
        engine.execute("CREATE TABLE t (id INT, v VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'a')");
        engine.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = DOWNSTREAM WAREHOUSE = COMPUTE_WH "
            + "INITIALIZE = ON_SCHEDULE AS SELECT id, v FROM t");
        engine.execute("CREATE STREAM sd ON DYNAMIC TABLE dt");
        assertEquals("Dynamic Table", streamCell("SD", "source_type"));
        assertEquals(PREFIX + "DT", streamCell("SD", "table_name"));
        assertEquals("Dynamic Table '" + PREFIX + "DT' is not initialized. Please run a manual refresh or wait for a "
            + "scheduled refresh before querying.", refusalOf("SELECT * FROM sd"));
        engine.execute("ALTER DYNAMIC TABLE dt REFRESH");
        assertEquals("1,a,INSERT,false", rows("SELECT id, v, METADATA$ACTION, METADATA$ISUPDATE FROM sd ORDER BY id"));
        engine.execute("UPDATE t SET v = 'z' WHERE id = 1");
        engine.execute("INSERT INTO t VALUES (3, 'd')");
        engine.execute("ALTER DYNAMIC TABLE dt REFRESH");
        assertEquals("1,z,INSERT,false|3,d,INSERT,false",
            rows("SELECT id, v, METADATA$ACTION, METADATA$ISUPDATE FROM sd ORDER BY id, METADATA$ACTION"));
        engine.execute("CREATE STREAM sd2 ON DYNAMIC TABLE dt SHOW_INITIAL_ROWS = TRUE");
        assertEquals("1,z,INSERT|3,d,INSERT", rows("SELECT id, v, METADATA$ACTION FROM sd2 ORDER BY id"));
    }

    @Test
    public void aDynamicTableIsNamedAsOneAndTakesNoAppendOnlyStream() {
        engine.execute("CREATE TABLE t (id INT, v VARCHAR)");
        engine.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = DOWNSTREAM WAREHOUSE = COMPUTE_WH "
            + "INITIALIZE = ON_SCHEDULE AS SELECT id, v FROM t");
        assertEquals("SQL compilation error: Object found is of type 'DYNAMIC_TABLE', not specified type 'TABLE'.",
            refusalOf("CREATE STREAM s ON TABLE dt"));
        assertEquals("SQL compilation error: Object found is of type 'TABLE', not specified type 'DYNAMIC_TABLE'.",
            refusalOf("CREATE STREAM s ON DYNAMIC TABLE t"));
        assertEquals("Change tracking of type APPEND_ONLY is not supported on dynamic tables.",
            refusalOf("CREATE STREAM s ON DYNAMIC TABLE dt APPEND_ONLY = TRUE"));
        assertTrue(refusalOf("CREATE STREAM s ON DYNAMIC TABLE no_such_dt").startsWith("SQL compilation error:\n"
            + "Dynamic table '" + PREFIX + "NO_SUCH_DT' does not exist or not authorized."));
    }

    @Test
    public void anEventTableIsATableToAStream() {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE EVENT TABLE et");
        engine.execute("CREATE STREAM se ON EVENT TABLE et");
        engine.execute("CREATE STREAM se2 ON TABLE et");
        engine.execute("CREATE STREAM se4 ON EVENT TABLE et APPEND_ONLY = TRUE");
        assertEquals("Table", streamCell("SE", "source_type"));
        assertEquals(PREFIX + "ET", streamCell("SE", "table_name"));
        assertEquals("APPEND_ONLY", streamCell("SE4", "mode"));
        assertEquals("SQL compilation error: Object found is of type 'TABLE', not specified type 'EVENT_TABLE'.",
            refusalOf("CREATE STREAM s ON EVENT TABLE t"));
        assertTrue(refusalOf("CREATE STREAM s ON EVENT TABLE no_such").startsWith("SQL compilation error:\n"
            + "Event table '" + PREFIX + "NO_SUCH' does not exist or not authorized."));
    }

    @Test
    public void anExternalTableStreamIsRefusedAsTheStatementNamesIt() {
        engine.execute("CREATE TABLE t (id INT)");
        assertTrue(refusalOf("CREATE STREAM s ON EXTERNAL TABLE no_such_ext").startsWith("SQL compilation error:\n"
            + "External table '" + PREFIX + "NO_SUCH_EXT' does not exist or not authorized."));
        assertEquals("SQL compilation error: Object found is of type 'TABLE', not specified type 'EXTERNAL_TABLE'.",
            refusalOf("CREATE STREAM s ON EXTERNAL TABLE t INSERT_ONLY = TRUE"));
        assertEquals("Streams of type INSERT_ONLY can only be created on external tables or Iceberg tables with an "
            + "external catalog integration.", refusalOf("CREATE STREAM s ON TABLE t INSERT_ONLY = TRUE"));
    }

    @Test
    public void aStreamTakesTagsBeforeCopyGrants() {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE TAG tg");
        engine.execute("CREATE STREAM s0 WITH TAG (tg = 'v0') ON TABLE t");
        engine.execute("CREATE STREAM s0b TAG (tg = 'v0b') ON TABLE t");
        engine.execute("CREATE OR REPLACE STREAM s0e WITH TAG (tg = 'v0e') COPY GRANTS ON TABLE t");
        assertEquals("v0|v0b|v0e", rows("SELECT SYSTEM$GET_TAG('tg', 's0', 'STREAM') || '|' || "
            + "SYSTEM$GET_TAG('tg', 's0b', 'STREAM') || '|' || SYSTEM$GET_TAG('tg', 's0e', 'STREAM')"));
        assertTrue(refusalOf("CREATE STREAM s0f WITH TAG (no_such_tag = 'x') ON TABLE t").startsWith(
            "SQL compilation error:\nTag 'NO_SUCH_TAG' does not exist or not authorized."));
        assertTrue(refusalOf("CREATE STREAM s0c ON TABLE t WITH TAG (tg = 'late')").contains("syntax error"));
    }
}
