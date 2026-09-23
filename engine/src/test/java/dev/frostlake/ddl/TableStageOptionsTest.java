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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * A table keeps the {@code STAGE_FILE_FORMAT} and {@code STAGE_COPY_OPTIONS} written at CREATE TABLE, and
 * {@code DESCRIBE TABLE … TYPE = STAGE} reports them: the format's TYPE decides which tree is answered,
 * each written value stands in the value column, and the default column keeps the format's own default.
 * Frostlake accepted the options and dropped them, so every table reported the CSV defaults
 * (live-verified).
 */
public class TableStageOptionsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t4 (b VARCHAR) STAGE_FILE_FORMAT = (TYPE = JSON)");
        engine.execute("CREATE TABLE t5 (b VARCHAR) STAGE_FILE_FORMAT = "
            + "(TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 2)");
        engine.execute("CREATE TABLE t6 (b VARCHAR) STAGE_COPY_OPTIONS = (ON_ERROR = CONTINUE PURGE = TRUE)");
        engine.execute("CREATE TABLE t7 (b VARCHAR)");
    }

    /** The number of property rows a stage description answers. */
    private int rowCount(final String sql) {
        return engine.executeQuery(sql).getRowCount();
    }

    /** One property's row as {@code parent, name, type, value, default}, or "" when it is not there. */
    private String property(final String sql, final String name) {
        final ResultSet result = engine.executeQuery(sql);
        for (int r = 0; r < result.getRowCount(); r++) {
            if (name.equals(String.valueOf(result.getRows().get(r).getValue(1)))) {
                final StringBuilder text = new StringBuilder();
                for (int c = 0; c < result.getColumnCount(); c++) {
                    text.append(c > 0 ? ", " : "").append(result.getRows().get(r).getValue(c));
                }
                return text.toString();
            }
        }
        return "";
    }

    @Test
    public void theFormatsOwnTreeIsAnswered() {
        assertEquals(25, rowCount("DESCRIBE TABLE t4 TYPE = STAGE"));
        assertEquals("STAGE_FILE_FORMAT, TYPE, String, JSON, CSV", property("DESCRIBE TABLE t4 TYPE = STAGE", "TYPE"));
        assertEquals("STAGE_FILE_FORMAT, MULTI_LINE, Boolean, true, true",
            property("DESCRIBE TABLE t4 TYPE = STAGE", "MULTI_LINE"));
        assertEquals("STAGE_FILE_FORMAT, NULL_IF, List, [], [\\\\N]",
            property("DESCRIBE TABLE t4 TYPE = STAGE", "NULL_IF"));
        // A JSON format has no field delimiter of its own.
        assertEquals("", property("DESCRIBE TABLE t4 TYPE = STAGE", "FIELD_DELIMITER"));
        // A table with no options of its own is the CSV tree, its values the defaults.
        assertEquals(32, rowCount("DESCRIBE TABLE t7 TYPE = STAGE"));
        assertEquals("STAGE_FILE_FORMAT, FIELD_DELIMITER, String, ,, ,",
            property("DESCRIBE TABLE t7 TYPE = STAGE", "FIELD_DELIMITER"));
    }

    @Test
    public void aWrittenValueStandsAgainstTheFormatsDefault() {
        assertEquals(32, rowCount("DESCRIBE TABLE t5 TYPE = STAGE"));
        assertEquals("STAGE_FILE_FORMAT, FIELD_DELIMITER, String, |, ,",
            property("DESCRIBE TABLE t5 TYPE = STAGE", "FIELD_DELIMITER"));
        assertEquals("STAGE_FILE_FORMAT, SKIP_HEADER, Integer, 2, 0",
            property("DESCRIBE TABLE t5 TYPE = STAGE", "SKIP_HEADER"));
        assertEquals("STAGE_FILE_FORMAT, RECORD_DELIMITER, String, \\n, \\n",
            property("DESCRIBE TABLE t5 TYPE = STAGE", "RECORD_DELIMITER"));
    }

    @Test
    public void theCopyOptionsAreKeptTheSameWay() {
        assertEquals(32, rowCount("DESCRIBE TABLE t6 TYPE = STAGE"));
        assertEquals("STAGE_COPY_OPTIONS, ON_ERROR, String, CONTINUE, ABORT_STATEMENT",
            property("DESCRIBE TABLE t6 TYPE = STAGE", "ON_ERROR"));
        assertEquals("STAGE_COPY_OPTIONS, PURGE, Boolean, true, false",
            property("DESCRIBE TABLE t6 TYPE = STAGE", "PURGE"));
        assertEquals("STAGE_COPY_OPTIONS, FORCE, Boolean, false, false",
            property("DESCRIBE TABLE t6 TYPE = STAGE", "FORCE"));
        assertEquals("STAGE_LOCATION, URL, String, , ", property("DESCRIBE TABLE t6 TYPE = STAGE", "URL"));
    }
}
