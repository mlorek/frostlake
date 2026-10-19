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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A COPY load splits a CSV file as a staged-file query splits it — one splitter for both — so a record the query
 * refuses is rejected by the load at the same place: a character after a closing enclosure, an enclosure still open
 * at the end of the file. The load reports the fault through its result (or fails the statement under the aborting
 * default), against the table's column of the field it was found in. Engine only: the live account allows no COPY
 * INTO.
 */
public class CopyCsvSplittingTest extends BaseDatabaseTest {

    private static final String NO_COPY = "the live account allows no COPY INTO";

    private static final String ENCLOSED = "FILE_FORMAT = (TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = '\"')";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_COPY);
        engine.execute("CREATE TABLE t (c1 VARCHAR, c2 VARCHAR)");
        engine.execute("CREATE STAGE st");
    }

    /** The load's one result row: status, rows parsed and loaded, errors seen, and the first error's four cells. */
    private List<String> loaded(final String copy) {
        final ResultSet rs = engine.executeQuery(copy);
        final Row row = rs.getRows().get(0);
        final List<String> out = new ArrayList<>();
        for (final String column : new String[] {"status", "rows_parsed", "rows_loaded", "errors_seen",
            "first_error", "first_error_line", "first_error_character", "first_error_column_name"}) {
            out.add(String.valueOf(row.getValue(rs.getColumnIndex(column))));
        }
        return out;
    }

    private List<String> rows() {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery("SELECT c1 || '|' || c2 FROM t ORDER BY 1").getRows()) {
            out.add(String.valueOf(row.getValue(0)));
        }
        return out;
    }

    @Test
    public void aCharacterAfterAClosingEnclosureFailsTheLoadAsTheQueryRefusesIt() {
        stageLocalFile("st", "encl.csv", "\"a\"x,b\nc,d\n");
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COPY INTO t FROM @st " + ENCLOSED);
            }
        });
        assertTrue(refused.getMessage().contains("""
            Found character 'x' instead of field delimiter ','
              File 'encl.csv', line 1, character 4
              Row 1, column "T"["C1":1]"""), refused.getMessage());
        assertEquals(List.of(), rows());
    }

    @Test
    public void underContinueTheFaultIsTheFilesFirstErrorAndTheNextRecordLoads() {
        stageLocalFile("st", "encl.csv", "\"a\"x,b\nc,d\n");
        assertEquals(List.of("PARTIALLY_LOADED", "2", "1", "1", "Found character 'x' instead of field delimiter ','",
            "1", "4", "\"T\"[\"C1\":1]"), loaded("COPY INTO t FROM @st " + ENCLOSED + " ON_ERROR = CONTINUE"));
        assertEquals(List.of("c|d"), rows());
    }

    @Test
    public void aCharacterAfterTheLastFieldsEnclosureStandsInForTheRecordDelimiter() {
        stageLocalFile("st", "last.csv", "a,\"b\"x\n");
        assertEquals(List.of("LOAD_FAILED", "1", "0", "1", "Found character 'x' instead of record delimiter '\\n'",
            "1", "6", "\"T\"[\"C2\":2]"), loaded("COPY INTO t FROM @st " + ENCLOSED + " ON_ERROR = CONTINUE"));
    }

    @Test
    public void anEnclosureStillOpenAtTheEndOfTheFileIsRejectedAtItsOpening() {
        stageLocalFile("st", "open.csv", "x,y\na,\"b\n");
        assertEquals(List.of("PARTIALLY_LOADED", "2", "1", "1",
            "matching enclosing character '\"' not found before end of file", "2", "3", "\"T\"[\"C2\":2]"),
            loaded("COPY INTO t FROM @st " + ENCLOSED + " ON_ERROR = CONTINUE"));
        assertEquals(List.of("x|y"), rows());
    }

    @Test
    public void aValidationReportsTheFaultWithItsRecord() {
        stageLocalFile("st", "encl.csv", "c,d\n\"a\"x,b\n");
        final ResultSet rs = engine.executeQuery("COPY INTO t FROM @st " + ENCLOSED
            + " VALIDATION_MODE = RETURN_ERRORS");
        assertEquals(1, rs.getRowCount());
        final Row row = rs.getRows().get(0);
        assertEquals("Found character 'x' instead of field delimiter ','", row.getValue(rs.getColumnIndex("ERROR")));
        assertEquals(2, row.getValue(rs.getColumnIndex("LINE")));
        assertEquals(4, row.getValue(rs.getColumnIndex("CHARACTER")));
        assertEquals(7L, row.getValue(rs.getColumnIndex("BYTE_OFFSET")));
        assertEquals("\"T\"[\"C1\":1]", row.getValue(rs.getColumnIndex("COLUMN_NAME")));
        assertEquals(2, row.getValue(rs.getColumnIndex("ROW_NUMBER")));
        assertEquals(2, row.getValue(rs.getColumnIndex("ROW_START_LINE")));
        assertEquals("\"a\"x,b", row.getValue(rs.getColumnIndex("REJECTED_RECORD")));
        assertEquals(List.of(), rows());
    }

    @Test
    public void aDelimiterOfSeveralCharactersIsQuotedAsWritten() {
        stageLocalFile("st", "pipes.csv", "1||a\n2||b||c\n");
        assertEquals(List.of("PARTIALLY_LOADED", "2", "1", "1",
            "Field delimiter '||' found while expecting record delimiter '\\n'", "2", "5", "\"T\"[\"C2\":2]"),
            loaded("COPY INTO t FROM @st FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '||') ON_ERROR = CONTINUE"));
        assertEquals(List.of("1|a"), rows());
    }
}
