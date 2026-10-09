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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table's stage loads into that table alone: COPY INTO another table from it is refused, whatever follows the
 * stage and however it is read, while unloading into it and querying it are not (live-verified).
 */
public class CopyOwnTableStageTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE cs1 (a VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE cs2 (a VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE \"cst\" (a VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE CST (a VARCHAR)");
        engine.execute("INSERT INTO cs1 VALUES ('x')");
        engine.execute("REMOVE @%cs1");
        engine.execute("REMOVE @%cs2");
        engine.execute("COPY INTO @%cs1/f1 FROM cs1 FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE"
            + " OVERWRITE = TRUE");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private static String anothers(final String staged, final String target) {
        return "SQL compilation error:\nAccess to the stage area of a table (" + staged + ") with the schema of another"
            + " table (" + target + ") is not allowed.";
    }

    @Test
    public void anotherTablesStageIsNoSource() {
        assertEquals(anothers("CS1", "CS2"), refusal("COPY INTO cs2 FROM @%cs1 FILE_FORMAT = (TYPE = CSV)"));
        assertEquals(anothers("CS1", "CS2"), refusal("COPY INTO cs2 FROM @%cs1/f1 FILE_FORMAT = (TYPE = CSV)"));
        assertEquals(anothers("CS1", "CS2"),
            refusal("COPY INTO cs2 FROM @%cs1 FILE_FORMAT = (TYPE = CSV) PATTERN = '.*'"));
        assertEquals(anothers("CS1", "CS2"),
            refusal("COPY INTO cs2 FROM @%cs1 FILES = ('f1') FILE_FORMAT = (TYPE = CSV)"));
        assertEquals(anothers("CS1", "CS2"),
            refusal("COPY INTO cs2 FROM (SELECT $1 FROM @%cs1) FILE_FORMAT = (TYPE = CSV)"));
        assertEquals(anothers("CS1", "CS2"),
            refusal("COPY INTO cs2 FROM @test_db.test_schema.%cs1 FILE_FORMAT = (TYPE = CSV)"));
        assertTrue(!engine.executeQuery("SELECT * FROM cs2").next(), "nothing was loaded");
    }

    @Test
    public void aTableOfAnotherCaseOrSchemaIsAnotherTable() {
        assertEquals(anothers("CST", "cst"), refusal("COPY INTO \"cst\" FROM @%CST/tf FILE_FORMAT = (TYPE = CSV)"));
        assertEquals(anothers("cst", "CST"), refusal("COPY INTO CST FROM @%\"cst\"/tf FILE_FORMAT = (TYPE = CSV)"));
        engine.execute("CREATE OR REPLACE SCHEMA st801");
        engine.execute("CREATE OR REPLACE TABLE st801.cs1 (a VARCHAR)");
        engine.execute("USE SCHEMA test_db.test_schema");
        assertEquals(anothers("CS1", "CS1"), refusal("COPY INTO st801.cs1 FROM @%cs1 FILE_FORMAT = (TYPE = CSV)"));
        assertEquals(anothers("CS1", "CS1"), refusal("COPY INTO cs1 FROM @st801.%cs1 FILE_FORMAT = (TYPE = CSV)"));
        engine.execute("DROP SCHEMA st801");
    }

    @Test
    public void aStageOfNoTableDoesNotExist() {
        final String message = refusal("COPY INTO cs2 FROM @%nosuch801 FILE_FORMAT = (TYPE = CSV)");
        assertTrue(message.startsWith("SQL compilation error:\nStage 'TEST_DB.TEST_SCHEMA.\"%NOSUCH801\"' does not exist"
            + " or not authorized."), message);
    }

    @Test
    public void theOwnStageUnloadsAndQueriesAreNotRefused() {
        final ResultSet loaded = engine.executeQuery("COPY INTO cs1 FROM @test_db.test_schema.%cs1"
            + " FILE_FORMAT = (TYPE = CSV)");
        assertTrue(loaded.next());
        assertEquals("LOADED", loaded.getValue(1));
        engine.execute("COPY INTO @%cs2/u1 FROM cs1 FILE_FORMAT = (TYPE = CSV)");
        engine.execute("COPY INTO @%cs2/u2 FROM (SELECT a FROM cs1) FILE_FORMAT = (TYPE = CSV)");
        final ResultSet counted = engine.executeQuery("SELECT COUNT(*) FROM @%cs1");
        assertTrue(counted.next());
        assertEquals(1, ((Number) counted.getValue(0)).intValue());
        // A validating COPY reads the other table's stage without the refusal.
        engine.executeQuery("COPY INTO cs2 FROM @%cs1 FILE_FORMAT = (TYPE = CSV) VALIDATION_MODE = RETURN_ERRORS");
        engine.execute("REMOVE @%cs1");
        engine.execute("REMOVE @%cs2");
    }
}
