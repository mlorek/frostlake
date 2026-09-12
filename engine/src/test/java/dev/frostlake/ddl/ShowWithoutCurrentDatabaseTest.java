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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An UNSCOPED SHOW in a session with no current database lists the whole account, as its IN ACCOUNT
 * form does, ordered database, schema, name, each row naming its own database; a SHOW scoped to a
 * qualified schema or relation names that one's database, not the session's. Every cell is
 * live-verified; LIKE keeps each listing to this test's own objects.
 */
public class ShowWithoutCurrentDatabaseTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    /** Tables, views, columns, schemas, objects, sequences, stages, functions, streams, tasks and pipes; SHOW COLUMNS reads a fully qualified relation, misses a bare one and refuses a schema-qualified one. */
    @Test
    public void anUnscopedShowListsTheWholeAccount() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P437_A");
            engine.execute("CREATE OR REPLACE DATABASE P437_B");
            engine.execute("CREATE OR REPLACE TABLE P437_A.PUBLIC.P437_T1 (p437col INT)");
            engine.execute("CREATE OR REPLACE TABLE P437_B.PUBLIC.P437_T2 (p437col INT)");
            engine.execute("CREATE OR REPLACE VIEW P437_A.PUBLIC.P437_V1 AS SELECT 1 AS x");
            engine.execute("CREATE OR REPLACE SCHEMA P437_A.P437_S1");
            engine.execute("CREATE OR REPLACE SEQUENCE P437_A.PUBLIC.P437_SEQ");
            engine.execute("CREATE OR REPLACE STAGE P437_A.PUBLIC.P437_STG");
            engine.execute("CREATE OR REPLACE FUNCTION P437_A.PUBLIC.P437_F() RETURNS INT AS '1'");
            engine.execute("CREATE OR REPLACE STREAM P437_A.PUBLIC.P437_STR ON TABLE P437_A.PUBLIC.P437_T1");
            engine.execute("CREATE OR REPLACE TASK P437_A.PUBLIC.P437_TSK SCHEDULE = '60 MINUTE' AS SELECT 1");
            engine.execute("CREATE OR REPLACE DATABASE P437_IDLE");
            engine.execute("DROP DATABASE P437_IDLE");
            assertEquals("null, null",
                rows("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()"));
            engine.execute("SHOW TABLES LIKE 'P437%'");
            assertEquals("P437_A, PUBLIC, P437_T1 | P437_B, PUBLIC, P437_T2",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW VIEWS LIKE 'P437%'");
            assertEquals("P437_A, PUBLIC, P437_V1",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW COLUMNS LIKE 'P437COL'");
            assertEquals("P437_A, PUBLIC, P437_T1, P437COL | P437_B, PUBLIC, P437_T2, P437COL",
                rows("SELECT \"database_name\", \"schema_name\", \"table_name\", \"column_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW SCHEMAS LIKE 'P437%'");
            assertEquals("P437_A, P437_S1",
                rows("SELECT \"database_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW OBJECTS LIKE 'P437%'");
            assertEquals("P437_A, PUBLIC, P437_T1, TABLE | P437_A, PUBLIC, P437_V1, VIEW | P437_B, PUBLIC, P437_T2, TABLE",
                rows("SELECT \"database_name\", \"schema_name\", \"name\", \"kind\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW SEQUENCES LIKE 'P437%'");
            assertEquals("P437_A, PUBLIC, P437_SEQ",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW STAGES LIKE 'P437%'");
            assertEquals("P437_A, PUBLIC, P437_STG",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW USER FUNCTIONS LIKE 'P437%'");
            assertEquals("P437_A, PUBLIC, P437_F",
                rows("SELECT \"catalog_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW STREAMS LIKE 'P437%'");
            assertEquals("P437_A, PUBLIC, P437_STR",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW TASKS LIKE 'P437%'");
            assertEquals("P437_A, PUBLIC, P437_TSK",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            assertEquals("",
                rows("SHOW PIPES LIKE 'P437%'"));
            engine.execute("SHOW TABLES IN SCHEMA P437_A.PUBLIC");
            assertEquals("P437_A, PUBLIC, P437_T1",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW COLUMNS IN TABLE P437_A.PUBLIC.P437_T1");
            assertEquals("P437_A, PUBLIC, P437_T1, P437COL",
                rows("SELECT \"database_name\", \"schema_name\", \"table_name\", \"column_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            assertRefused("SHOW COLUMNS IN TABLE P437_T1",
                "Table 'P437_T1' does not exist or not authorized.");
            assertRefused("SHOW COLUMNS IN TABLE PUBLIC.P437_T1",
                "Cannot perform SHOW COLUMNS. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("SHOW COLUMNS IN VIEW PUBLIC.P437_V1",
                "Must specify the full search path starting from database for P437_V1");
            engine.execute("SHOW TABLES LIKE 'P437%' IN DATABASE P437_B");
            assertEquals("P437_B, PUBLIC, P437_T2",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW TERSE TABLES LIKE 'P437%'");
            assertEquals("P437_A, PUBLIC, P437_T1 | P437_B, PUBLIC, P437_T2",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P437_A");
            engine.execute("DROP DATABASE IF EXISTS P437_B");
        }
    }

    /** File formats, tags, the policy kinds, procedures, functions, materialized views, keys and cortex search services too; a kind with nothing to list answers no rows rather than refusing. */
    @Test
    public void everyListingKindReachesTheAccount() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P437B_A");
            engine.execute("CREATE OR REPLACE TABLE P437B_A.PUBLIC.P437B_T1 (x INT PRIMARY KEY, y INT UNIQUE)");
            engine.execute("CREATE OR REPLACE FILE FORMAT P437B_A.PUBLIC.P437B_FF TYPE = CSV");
            engine.execute("CREATE OR REPLACE TAG P437B_A.PUBLIC.P437B_TAG");
            engine.execute("CREATE OR REPLACE MASKING POLICY P437B_A.PUBLIC.P437B_MP AS (v STRING) RETURNS STRING -> v");
            engine.execute("CREATE OR REPLACE ROW ACCESS POLICY P437B_A.PUBLIC.P437B_RAP AS (v INT) RETURNS BOOLEAN -> TRUE");
            engine.execute("CREATE OR REPLACE PROCEDURE P437B_A.PUBLIC.P437B_P() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            engine.execute("CREATE OR REPLACE FUNCTION P437B_A.PUBLIC.P437B_F() RETURNS INT AS '1'");
            engine.execute("CREATE OR REPLACE STREAM P437B_A.PUBLIC.P437B_STR ON TABLE P437B_A.PUBLIC.P437B_T1");
            engine.execute("CREATE OR REPLACE MATERIALIZED VIEW P437B_A.PUBLIC.P437B_MV AS SELECT x FROM P437B_A.PUBLIC.P437B_T1");
            engine.execute("CREATE OR REPLACE DATABASE P437B_IDLE");
            engine.execute("DROP DATABASE P437B_IDLE");
            engine.execute("SHOW FILE FORMATS LIKE 'P437B%'");
            assertEquals("P437B_A, PUBLIC, P437B_FF",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW TAGS LIKE 'P437B%'");
            assertEquals("P437B_A, PUBLIC, P437B_TAG",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW MASKING POLICIES LIKE 'P437B%'");
            assertEquals("P437B_A, PUBLIC, P437B_MP",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW ROW ACCESS POLICIES LIKE 'P437B%'");
            assertEquals("P437B_A, PUBLIC, P437B_RAP",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW PROCEDURES LIKE 'P437B%'");
            assertEquals("P437B_A, PUBLIC, P437B_P",
                rows("SELECT \"catalog_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW USER PROCEDURES LIKE 'P437B%'");
            assertEquals("P437B_A, PUBLIC, P437B_P",
                rows("SELECT \"catalog_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW FUNCTIONS LIKE 'P437B%'");
            assertEquals("P437B_A, PUBLIC, P437B_F",
                rows("SELECT \"catalog_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW MATERIALIZED VIEWS LIKE 'P437B%'");
            assertEquals("P437B_A, PUBLIC, P437B_MV",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            assertEquals("",
                rows("SHOW DYNAMIC TABLES LIKE 'P437B%'"));
            engine.execute("SHOW PRIMARY KEYS");
            assertEquals("P437B_A, PUBLIC, P437B_T1, X",
                rows("SELECT \"database_name\", \"schema_name\", \"table_name\", \"column_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"database_name\" = 'P437B_A'"));
            engine.execute("SHOW UNIQUE KEYS");
            assertEquals("P437B_A, PUBLIC, P437B_T1, Y",
                rows("SELECT \"database_name\", \"schema_name\", \"table_name\", \"column_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"database_name\" = 'P437B_A'"));
            assertEquals("",
                rows("SHOW JOIN POLICIES LIKE 'P437B%'"));
            assertEquals("",
                rows("SHOW AGGREGATION POLICIES LIKE 'P437B%'"));
            assertEquals("",
                rows("SHOW PROJECTION POLICIES LIKE 'P437B%'"));
            assertEquals("",
                rows("SHOW CONTACTS LIKE 'P437B%'"));
            assertEquals("",
                rows("SHOW CORTEX SEARCH SERVICES LIKE 'P437B%'"));
            engine.execute("SHOW STREAMS LIKE 'P437B%'");
            assertEquals("P437B_A, PUBLIC, P437B_STR",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW TABLES IN SCHEMA P437B_A.PUBLIC");
            assertEquals("P437B_A, PUBLIC, P437B_T1",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW TABLES IN DATABASE P437B_A");
            assertEquals("P437B_A, PUBLIC, P437B_T1",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW COLUMNS IN VIEW P437B_A.PUBLIC.P437B_MV");
            assertEquals("P437B_A, PUBLIC, P437B_MV, X",
                rows("SELECT \"database_name\", \"schema_name\", \"table_name\", \"column_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW COLUMNS IN P437B_A.PUBLIC.P437B_T1");
            assertEquals("P437B_A, PUBLIC, P437B_T1, X | P437B_A, PUBLIC, P437B_T1, Y",
                rows("SELECT \"database_name\", \"schema_name\", \"table_name\", \"column_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            assertRefused("SHOW COLUMNS IN P437B_T1",
                "Table 'P437B_T1' does not exist or not authorized.");
            assertRefused("SHOW COLUMNS IN PUBLIC.P437B_T1",
                "Cannot perform SHOW COLUMNS. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("SHOW COLUMNS IN TABLE P437B_A.PUBLIC.NOSUCH",
                "Table 'P437B_A.PUBLIC.NOSUCH' does not exist or not authorized.");
            assertRefused("SHOW COLUMNS IN VIEW P437B_MV",
                "Must specify the full search path starting from database for P437B_MV");
            assertRefused("SHOW COLUMNS IN TABLE PUBLIC.NOSUCH",
                "Cannot perform SHOW COLUMNS. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("SHOW COLUMNS IN VIEW PUBLIC.NOSUCH",
                "Must specify the full search path starting from database for NOSUCH");
            assertEquals("",
                rows("SHOW SEQUENCES IN SCHEMA P437B_A.PUBLIC"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P437B_A");
        }
    }

    /** With a current database elsewhere, IN SCHEMA listings and SHOW COLUMNS over a qualified table name the schema's own database; a stream over a fully qualified table is created. */
    @Test
    public void aScopedListingNamesItsOwnDatabase() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P437C_A");
            engine.execute("CREATE OR REPLACE TABLE P437C_A.PUBLIC.T1 (x INT)");
            engine.execute("CREATE OR REPLACE MATERIALIZED VIEW P437C_A.PUBLIC.P437C_MV AS SELECT x FROM P437C_A.PUBLIC.T1");
            engine.execute("CREATE OR REPLACE FUNCTION P437C_A.PUBLIC.P437C_F() RETURNS INT AS '1'");
            engine.execute("CREATE OR REPLACE PROCEDURE P437C_A.PUBLIC.P437C_P() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            engine.execute("CREATE OR REPLACE STREAM P437C_A.PUBLIC.P437C_STR ON TABLE P437C_A.PUBLIC.T1");
            engine.execute("CREATE OR REPLACE DATABASE P437C_B");
            assertEquals("",
                rows("SHOW MATERIALIZED VIEWS LIKE 'P437C%'"));
            assertEquals("",
                rows("SHOW USER FUNCTIONS LIKE 'P437C%'"));
            engine.execute("SHOW STREAMS IN SCHEMA P437C_A.PUBLIC");
            assertEquals("P437C_A, PUBLIC, P437C_STR, P437C_A.PUBLIC.T1",
                rows("SELECT \"database_name\", \"schema_name\", \"name\", \"table_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW TABLES IN SCHEMA P437C_A.PUBLIC");
            assertEquals("P437C_A, PUBLIC, T1",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW MATERIALIZED VIEWS IN SCHEMA P437C_A.PUBLIC");
            assertEquals("P437C_A, PUBLIC, P437C_MV",
                rows("SELECT \"database_name\", \"schema_name\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("SHOW COLUMNS IN TABLE P437C_A.PUBLIC.T1");
            assertEquals("P437C_A, PUBLIC, T1, X",
                rows("SELECT \"database_name\", \"schema_name\", \"table_name\", \"column_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P437C_A");
            engine.execute("DROP DATABASE IF EXISTS P437C_B");
        }
    }
}
