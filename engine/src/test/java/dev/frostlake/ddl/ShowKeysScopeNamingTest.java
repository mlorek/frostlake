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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * The schema or table a SHOW PRIMARY, UNIQUE or IMPORTED KEYS scope names: a missing table is named as written when
 * its name has one part and in full when it is a path; a one-part schema scope asks for the full search path, even
 * for an existing schema; a view lists nothing, and a stream is no domain the listing supports. Every cell is
 * live-verified.
 */
public class ShowKeysScopeNamingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE tpk (id INT PRIMARY KEY, u INT UNIQUE)");
        engine.execute("CREATE VIEW v AS SELECT 1 AS x");
        engine.execute("CREATE SEQUENCE sq");
        engine.execute("CREATE STREAM s ON TABLE tpk");
    }

    /** Every row's first cell, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String missingTable(final String name) {
        return hinted("SQL compilation error:|Table '" + name + "' does not exist or not authorized.");
    }

    /** The number of rows a SHOW answers. */
    private int rowsOf(final String sql) {
        return engine.executeQuery(sql).getRows().size();
    }

    @Test
    public void aMissingOnePartTableIsNamedAsWritten() {
        assertEquals(missingTable("NOSUCH"), answer("SHOW PRIMARY KEYS IN nosuch"));
        assertEquals(missingTable("NOSUCH"), answer("SHOW PRIMARY KEYS IN TABLE nosuch"));
        assertEquals(missingTable("NOSUCH"), answer("SHOW PRIMARY KEYS IN NoSuch"));
        assertEquals(missingTable("\"nosuch\""), answer("SHOW PRIMARY KEYS IN \"nosuch\""));
        assertEquals(missingTable("NOSUCH"), answer("SHOW PRIMARY KEYS IN IDENTIFIER('nosuch')"));
        assertEquals(missingTable("NOSUCH"), answer("SHOW PRIMARY KEYS IN TABLE IDENTIFIER('nosuch')"));
        assertEquals(missingTable("USER"), answer("SHOW UNIQUE KEYS IN USER"));
        assertEquals(missingTable("NOSUCH"), answer("SHOW IMPORTED KEYS IN nosuch"));
        assertEquals(missingTable("NOSUCH"), answer("SHOW IMPORTED KEYS IN TABLE nosuch"));
        assertEquals(missingTable("SQ"), answer("SHOW PRIMARY KEYS IN sq"));
    }

    @Test
    public void aMissingTableNamedByAPathIsNamedInFull() {
        assertEquals(missingTable("TEST_DB.TEST_SCHEMA.NOSUCH"), answer("SHOW PRIMARY KEYS IN test_schema.nosuch"));
        assertEquals(missingTable("TEST_DB.TEST_SCHEMA.NOSUCH"),
            answer("SHOW PRIMARY KEYS IN TABLE test_schema.nosuch"));
        assertEquals(missingTable("TEST_DB.TEST_SCHEMA.NOSUCH"),
            answer("SHOW IMPORTED KEYS IN test_db.test_schema.nosuch"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.NOSCH' does not exist or not authorized."),
            answer("SHOW PRIMARY KEYS IN nosch.x"));
    }

    @Test
    public void aOnePartSchemaScopeAsksForTheFullSearchPath() {
        final String fullPath = "SQL compilation error:|Must specify the full search path starting from database for "
            + "TEST_SCHEMA";
        assertEquals(fullPath, answer("SHOW PRIMARY KEYS IN SCHEMA test_schema"));
        assertEquals(fullPath, answer("SHOW PRIMARY KEYS IN SCHEMA \"TEST_SCHEMA\""));
        assertEquals(fullPath, answer("SHOW UNIQUE KEYS IN SCHEMA test_schema"));
        assertEquals(fullPath, answer("SHOW IMPORTED KEYS IN SCHEMA IDENTIFIER('test_schema')"));
        assertEquals("SQL compilation error:|Must specify the full search path starting from database for NOSCH",
            answer("SHOW PRIMARY KEYS IN SCHEMA nosch"));
        assertEquals(1, rowsOf("SHOW PRIMARY KEYS IN SCHEMA test_db.test_schema"));
    }

    @Test
    public void aViewListsNothingAndAStreamIsNoDomain() {
        assertEquals(0, rowsOf("SHOW PRIMARY KEYS IN v"));
        assertEquals(0, rowsOf("SHOW PRIMARY KEYS IN TABLE v"));
        assertEquals(0, rowsOf("SHOW UNIQUE KEYS IN v"));
        assertEquals(0, rowsOf("SHOW IMPORTED KEYS IN v"));
        assertEquals(0, rowsOf("SHOW PRIMARY KEYS IN test_db.test_schema.v"));
        assertEquals("SQL compilation error: show [constraint] command doesn't support this domain.",
            answer("SHOW PRIMARY KEYS IN s"));
        assertEquals(1, rowsOf("SHOW PRIMARY KEYS IN tpk"));
        assertEquals(1, rowsOf("SHOW UNIQUE KEYS IN tpk"));
    }

    @Test
    public void aKeyRowIsCreatedAtATimestamp() {
        engine.executeQuery("SHOW PRIMARY KEYS IN tpk");
        assertEquals("true",
            answer("SELECT \"created_on\" <= CURRENT_TIMESTAMP() FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))")
                .toLowerCase());
    }
}
