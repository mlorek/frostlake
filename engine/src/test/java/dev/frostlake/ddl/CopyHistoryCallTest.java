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
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INFORMATION_SCHEMA.COPY_HISTORY answers the account's 21 columns, by name or by position, and refuses a call short of
 * its table or its start time, a NULL table, a text time, a missing table and a bare name (live-verified).
 */
public class CopyHistoryCallTest extends BaseDatabaseTest {

    private static final String CALL = "SELECT * FROM TABLE(INFORMATION_SCHEMA.COPY_HISTORY(";
    private static final String RANGE = "START_TIME => DATEADD(hour, -1, CURRENT_TIMESTAMP())";

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return refused.getMessage();
    }

    @Test
    public void anEmptyHistoryHasTheAccountsColumns() {
        engine.execute("CREATE TABLE h (id INT, v VARCHAR)");
        final ResultSet history = engine.executeQuery(CALL + "TABLE_NAME => 'h', " + RANGE + "))");
        final StringBuilder names = new StringBuilder();
        for (final ResultSetColumn column : history.getColumns()) {
            names.append(names.length() > 0 ? "," : "").append(column.getName());
        }
        assertEquals("FILE_NAME,STAGE_LOCATION,LAST_LOAD_TIME,ROW_COUNT,ROW_PARSED,FILE_SIZE,FIRST_ERROR_MESSAGE,"
            + "FIRST_ERROR_LINE_NUMBER,FIRST_ERROR_CHARACTER_POS,FIRST_ERROR_COLUMN_NAME,ERROR_COUNT,ERROR_LIMIT,"
            + "STATUS,TABLE_CATALOG_NAME,TABLE_SCHEMA_NAME,TABLE_NAME,PIPE_CATALOG_NAME,PIPE_SCHEMA_NAME,PIPE_NAME,"
            + "PIPE_RECEIVED_TIME,BYTES_BILLED", names.toString());
        assertEquals(0, history.getRows().size());
        assertEquals(0,
            engine.executeQuery(CALL + "'H', DATEADD(hour, -1, CURRENT_TIMESTAMP())))").getRows().size());
        assertEquals(0, engine.executeQuery(CALL + "TABLE_NAME => 'H', " + RANGE
            + ", END_TIME => CURRENT_TIMESTAMP()))").getRows().size());
    }

    @Test
    public void aCallShortOfItsArgumentsIsRefused() {
        engine.execute("CREATE TABLE h (id INT, v VARCHAR)");
        assertEquals("SQL compilation error: error line 1 at position 20\nnot enough arguments for function"
            + " [INFORMATION_SCHEMA], expected 2, got 1", refusal(CALL + "TABLE_NAME => 'H'))"));
        assertEquals("SQL compilation error: error line 1 at position 20\nnot enough arguments for function"
            + " [INFORMATION_SCHEMA], expected 2, got 1", refusal(CALL + RANGE + "))"));
        assertEquals("SQL compilation error: error line 1 at position 20\nmissing required argument [TABLE_NAME]"
            + " for function [INFORMATION_SCHEMA]", refusal(CALL + "TABLE_NAME => NULL, " + RANGE + "))"));
        assertEquals("SQL compilation error:\ninvalid type [VARCHAR(1)] for parameter 'START_TIME'",
            refusal(CALL + "TABLE_NAME => 'H', START_TIME => 'x'))"));
    }

    @Test
    public void aMissingTableAndABareNameAreRefused() {
        assertTrue(refusal(CALL + "TABLE_NAME => 'NOPE142', " + RANGE + "))")
            .startsWith("SQL compilation error:\nTable 'NOPE142' does not exist or not authorized."));
        assertEquals("SQL compilation error:\nInvalid identifier COPY_HISTORY",
            refusal("SELECT * FROM TABLE(COPY_HISTORY(TABLE_NAME => 'H', " + RANGE + "))"));
    }
}
