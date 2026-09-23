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

package dev.frostlake.stage;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The DECLARED types of the columns a COPY or a file transfer answers with, which a client that trusts
 * them reads and a CTAS over RESULT_SCAN copies. They are not one width: the load counts are the widest
 * NUMBER, the unload summary is narrower, the per-file detail narrower still, and a GET — answered by the
 * client rather than the server — carries the driver's own DECIMAL and its own text width.
 */
public class TransferResultTypesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE STAGE sp");
        engine.execute("CREATE OR REPLACE TABLE ld (a INT, b VARCHAR)");
        engine.execute("INSERT INTO ld VALUES (1, 'x')");
    }

    /** A declared type with its parameters, which the type's own text does not print. */
    private String spell(final DataType type) {
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            return numeric.getName() + "(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        if (type instanceof StringType) {
            return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        return type.getName();
    }

    /** Each column of a result, as NAME:TYPE, joined by ", ". */
    private String shape(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (final ResultSetColumn column : rs.getColumns()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(column.getName()).append(':').append(spell(column.getDataType()));
        }
        return text.toString();
    }

    /** The unload summary's three counts are NUMBER(31,0). */
    @Test
    public void theUnloadSummaryCountsAreThirtyOneDigits() {
        engine.execute("COPY INTO @sp/summary FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV)");
        assertEquals("rows_unloaded:NUMBER(31,0), input_bytes:NUMBER(31,0), output_bytes:NUMBER(31,0)",
            shape("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
    }

    /** DETAILED_OUTPUT's per-file rows carry the 128MB name and eighteen-digit counts. */
    @Test
    public void theDetailedUnloadCarriesItsOwnWidths() {
        engine.execute(
            "COPY INTO @sp/detail FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV) DETAILED_OUTPUT = TRUE");
        assertEquals("FILE_NAME:VARCHAR(134217728), FILE_SIZE:NUMBER(18,0), ROW_COUNT:NUMBER(18,0)",
            shape("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
    }

    /** The load summary's six counts are the widest NUMBER; its texts are the ordinary VARCHAR. */
    @Test
    public void theLoadSummaryCountsAreTheWidestNumber() {
        engine.execute("COPY INTO @sp/summary FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV)");
        engine.execute("COPY INTO ld FROM @sp/summary FILE_FORMAT = (TYPE = CSV) ON_ERROR = CONTINUE");
        assertEquals("file:VARCHAR(16777216), status:VARCHAR(16777216), rows_parsed:NUMBER(38,0), "
            + "rows_loaded:NUMBER(38,0), error_limit:NUMBER(38,0), errors_seen:NUMBER(38,0), "
            + "first_error:VARCHAR(16777216), first_error_line:NUMBER(38,0), "
            + "first_error_character:NUMBER(38,0), first_error_column_name:VARCHAR(16777216)",
            shape("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
    }

    /** A GET carries the client's own widths: a ten-digit size and a 20480-wide text. */
    @Test
    public void aGetCarriesTheClientsOwnWidths() {
        engine.execute("COPY INTO @sp/summary FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV)");
        assertEquals("file:VARCHAR(20480), size:NUMBER(10,0), status:VARCHAR(20480), "
            + "encryption:VARCHAR(20480), message:VARCHAR(20480)",
            shape("GET @sp/summary 'file:///tmp/fl-get-types'"));
    }

    /** A PUT carries the same widths as a GET. */
    @Test
    public void aPutCarriesTheSameWidths() {
        assertEquals("source:VARCHAR(20480), target:VARCHAR(20480), source_size:NUMBER(10,0), "
            + "target_size:NUMBER(10,0), source_compression:VARCHAR(20480), "
            + "target_compression:VARCHAR(20480), status:VARCHAR(20480), encryption:VARCHAR(20480), "
            + "message:VARCHAR(20480)",
            shape("PUT 'file:///tmp/fl-put-probe/a.csv' @sp AUTO_COMPRESS = FALSE"));
    }
}
