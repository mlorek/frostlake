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
 * GRANT, REVOKE and SHOW GRANTS find the object they name before anything else: a missing table, view,
 * schema, database, function, sequence or stage is refused in its own words, a missing container as
 * such, a bulk grant's scope likewise, and all of it ahead of a missing grantee. With no current
 * database a name the session cannot place is refused naming GRANT or SHOW GRANTS. Every cell is
 * live-verified.
 */
public class GrantObjectExistsTest extends BaseDatabaseTest {

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

    /** With a current database: every kind refused in its own words, ahead of a missing grantee. */
    @Test
    public void aGrantFindsItsObjectFirst() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P444_DB");
            engine.execute("CREATE OR REPLACE TABLE P444_DB.PUBLIC.T (x INT)");
            engine.execute("CREATE OR REPLACE VIEW P444_DB.PUBLIC.V AS SELECT 1 AS x");
            engine.execute("CREATE OR REPLACE SCHEMA P444_DB.S1");
            engine.execute("USE SCHEMA P444_DB.PUBLIC");
            engine.execute("CREATE OR REPLACE FUNCTION P444_DB.PUBLIC.F() RETURNS INT AS '1'");
            engine.execute("CREATE OR REPLACE SEQUENCE P444_DB.PUBLIC.SQ");
            engine.execute("CREATE OR REPLACE STAGE P444_DB.PUBLIC.ST");
            assertRefused("GRANT SELECT ON TABLE nosuch TO ROLE PUBLIC",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("REVOKE SELECT ON TABLE nosuch FROM ROLE PUBLIC",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("SHOW GRANTS ON TABLE nosuch",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("GRANT SELECT ON VIEW nosuchv TO ROLE PUBLIC",
                "View 'NOSUCHV' does not exist or not authorized.");
            assertRefused("GRANT USAGE ON SCHEMA nosuchschema TO ROLE PUBLIC",
                "Schema 'P444_DB.NOSUCHSCHEMA' does not exist or not authorized.");
            assertRefused("GRANT SELECT ON ALL TABLES IN SCHEMA nosuchschema TO ROLE PUBLIC",
                "Schema 'P444_DB.NOSUCHSCHEMA' does not exist or not authorized.");
            assertRefused("GRANT SELECT ON FUTURE TABLES IN SCHEMA nosuchschema TO ROLE PUBLIC",
                "Schema 'P444_DB.NOSUCHSCHEMA' does not exist or not authorized.");
            assertRefused("GRANT USAGE ON DATABASE nosuchdb TO ROLE PUBLIC",
                "Database 'NOSUCHDB' does not exist or not authorized.");
            assertRefused("GRANT USAGE ON FUNCTION nosuchf() TO ROLE PUBLIC",
                "Function 'P444_DB.PUBLIC.NOSUCHF' does not exist or not authorized.");
            assertRefused("GRANT USAGE ON SEQUENCE nosuchsq TO ROLE PUBLIC",
                "Sequence 'P444_DB.PUBLIC.NOSUCHSQ' does not exist or not authorized.");
            assertRefused("GRANT USAGE ON STAGE nosuchst TO ROLE PUBLIC",
                "Stage 'P444_DB.PUBLIC.NOSUCHST' does not exist or not authorized.");
            assertRefused("GRANT SELECT ON TABLE nosuch_schema.t TO ROLE PUBLIC",
                "Schema 'P444_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("GRANT SELECT ON TABLE no_such_db.PUBLIC.t TO ROLE PUBLIC",
                "Database 'NO_SUCH_DB' does not exist or not authorized.");
            assertRefused("SHOW GRANTS ON VIEW nosuchv",
                "View 'NOSUCHV' does not exist or not authorized.");
            assertRefused("SHOW GRANTS ON SCHEMA nosuchschema",
                "Schema 'P444_DB.NOSUCHSCHEMA' does not exist or not authorized.");
            assertRefused("SHOW GRANTS ON DATABASE nosuchdb",
                "Database 'NOSUCHDB' does not exist or not authorized.");
            engine.execute("GRANT SELECT ON TABLE T TO ROLE PUBLIC");
            engine.execute("SHOW GRANTS ON TABLE T");
            assertEquals("SELECT, TABLE, P444_DB.PUBLIC.T, PUBLIC",
                rows("SELECT \"privilege\", \"granted_on\", \"name\", \"grantee_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"grantee_name\" = 'PUBLIC'"));
            engine.execute("REVOKE SELECT ON TABLE T FROM ROLE PUBLIC");
            engine.execute("GRANT SELECT ON VIEW T TO ROLE PUBLIC");
            engine.execute("GRANT SELECT ON TABLE V TO ROLE PUBLIC");
            assertRefused("GRANT USAGE ON FUNCTION F(INT) TO ROLE PUBLIC",
                "Function 'P444_DB.PUBLIC.F' does not exist or not authorized.");
            assertRefused("GRANT SELECT ON ALL TABLES IN DATABASE nosuchdb TO ROLE PUBLIC",
                "Database 'NOSUCHDB' does not exist or not authorized.");
            assertRefused("GRANT OWNERSHIP ON TABLE nosuch TO ROLE PUBLIC",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("REVOKE USAGE ON SCHEMA nosuchschema FROM ROLE PUBLIC",
                "Schema 'P444_DB.NOSUCHSCHEMA' does not exist or not authorized.");
            assertRefused("GRANT SELECT ON TABLE nosuch TO ROLE nosuchrole",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("GRANT SELECT ON TABLE T TO ROLE nosuchrole",
                "Role 'NOSUCHROLE' does not exist or not authorized.");
            assertRefused("REVOKE SELECT ON VIEW nosuchv FROM ROLE PUBLIC",
                "View 'NOSUCHV' does not exist or not authorized.");
            assertRefused("SHOW GRANTS ON FUNCTION nosuchf()",
                "Function 'P444_DB.PUBLIC.NOSUCHF' does not exist or not authorized.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P444_DB");
        }
    }

    /** With none: a bare name misses, and a schema-qualified one is refused naming GRANT or SHOW GRANTS. */
    @Test
    public void withNoCurrentDatabaseAGrantNamesItsStatement() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P444_DB");
            engine.execute("CREATE OR REPLACE TABLE P444_DB.PUBLIC.T (x INT)");
            engine.execute("CREATE OR REPLACE VIEW P444_DB.PUBLIC.V AS SELECT 1 AS x");
            engine.execute("CREATE OR REPLACE SCHEMA P444_DB.S1");
            engine.execute("USE SCHEMA P444_DB.PUBLIC");
            engine.execute("CREATE OR REPLACE FUNCTION P444_DB.PUBLIC.F() RETURNS INT AS '1'");
            engine.execute("CREATE OR REPLACE SEQUENCE P444_DB.PUBLIC.SQ");
            engine.execute("CREATE OR REPLACE STAGE P444_DB.PUBLIC.ST");
            engine.execute("CREATE OR REPLACE DATABASE P444_IDLE");
            engine.execute("DROP DATABASE P444_IDLE");
            assertRefused("GRANT SELECT ON TABLE t TO ROLE PUBLIC",
                "Table 'T' does not exist or not authorized.");
            assertRefused("GRANT SELECT ON TABLE PUBLIC.t TO ROLE PUBLIC",
                "Cannot perform GRANT. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("REVOKE SELECT ON TABLE t FROM ROLE PUBLIC",
                "Table 'T' does not exist or not authorized.");
            assertRefused("SHOW GRANTS ON TABLE t",
                "Table 'T' does not exist or not authorized.");
            assertRefused("SHOW GRANTS ON TABLE PUBLIC.t",
                "Cannot perform SHOW GRANTS. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("GRANT USAGE ON SCHEMA s1 TO ROLE PUBLIC",
                "Cannot perform GRANT. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            engine.execute("GRANT SELECT ON TABLE P444_DB.PUBLIC.T TO ROLE PUBLIC");
            engine.execute("REVOKE SELECT ON TABLE P444_DB.PUBLIC.T FROM ROLE PUBLIC");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P444_DB");
        }
    }
}
