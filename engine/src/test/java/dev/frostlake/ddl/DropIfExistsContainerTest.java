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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DROP … IF EXISTS and TRUNCATE … IF EXISTS forgive only the object's own absence: a missing database is
 * refused as a missing database and a missing schema as a missing schema, for every schema-scoped kind,
 * and with no current database a name the session cannot place is refused naming the statement. Every
 * cell is live-verified.
 */
public class DropIfExistsContainerTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    /** A missing database or schema is refused for every schema-scoped DROP kind, TRUNCATE and ALTER. */
    @Test
    public void ifExistsForgivesOnlyTheMissingObject() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P442_DB");
            engine.execute("CREATE OR REPLACE TABLE P442_DB.PUBLIC.T (x INT)");
            assertRefused("DROP TABLE IF EXISTS no_such_db.PUBLIC.t",
                "Database 'NO_SUCH_DB' does not exist or not authorized.");
            assertRefused("DROP TABLE IF EXISTS nosuch_schema.t",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            engine.execute("DROP TABLE IF EXISTS PUBLIC.nosuch");
            engine.execute("DROP TABLE IF EXISTS nosuch");
            assertRefused("DROP VIEW IF EXISTS no_such_db.PUBLIC.v",
                "Database 'NO_SUCH_DB' does not exist or not authorized.");
            assertRefused("DROP VIEW IF EXISTS nosuch_schema.v",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP SCHEMA IF EXISTS no_such_db.s",
                "Database 'NO_SUCH_DB' does not exist or not authorized.");
            assertRefused("DROP SEQUENCE IF EXISTS no_such_db.PUBLIC.sq",
                "Database 'NO_SUCH_DB' does not exist or not authorized.");
            assertRefused("DROP STAGE IF EXISTS nosuch_schema.st",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP FILE FORMAT IF EXISTS nosuch_schema.ff",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP FUNCTION IF EXISTS nosuch_schema.f()",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP PROCEDURE IF EXISTS nosuch_schema.p()",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP STREAM IF EXISTS nosuch_schema.s",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP TASK IF EXISTS nosuch_schema.tk",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP MATERIALIZED VIEW IF EXISTS nosuch_schema.mv",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP DYNAMIC TABLE IF EXISTS nosuch_schema.dt",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("TRUNCATE TABLE IF EXISTS nosuch_schema.t",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("TRUNCATE TABLE IF EXISTS no_such_db.PUBLIC.t",
                "Database 'NO_SUCH_DB' does not exist or not authorized.");
            engine.execute("TRUNCATE TABLE IF EXISTS nosuch");
            engine.execute("DROP DATABASE IF EXISTS no_such_db");
            assertRefused("DROP TAG IF EXISTS nosuch_schema.tg",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP MASKING POLICY IF EXISTS nosuch_schema.mp",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP TABLE no_such_db.PUBLIC.t",
                "Database 'NO_SUCH_DB' does not exist or not authorized.");
            assertRefused("DROP TABLE nosuch_schema.t",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP PIPE IF EXISTS nosuch_schema.pp",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP ROW ACCESS POLICY IF EXISTS nosuch_schema.rap",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("ALTER TABLE IF EXISTS nosuch_schema.t ADD COLUMN b INT",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            engine.execute("DROP VIEW IF EXISTS PUBLIC.nosuch");
            assertRefused("DROP FUNCTION IF EXISTS no_such_db.PUBLIC.f(INT)",
                "Database 'NO_SUCH_DB' does not exist or not authorized.");
            assertRefused("DROP SEQUENCE IF EXISTS nosuch_schema.sq",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP STAGE IF EXISTS no_such_db.PUBLIC.st",
                "Database 'NO_SUCH_DB' does not exist or not authorized.");
            engine.execute("DROP SCHEMA IF EXISTS nosuch_schema");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P442_DB");
        }
    }

    /** With none, IF EXISTS still refuses a name the session cannot place, naming DROP or TRUNCATE. */
    @Test
    public void withNoCurrentDatabaseANameThatCannotBePlacedIsRefused() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P442_DB");
            engine.execute("CREATE OR REPLACE TABLE P442_DB.PUBLIC.T (x INT)");
            engine.execute("CREATE OR REPLACE DATABASE P442_IDLE");
            engine.execute("DROP DATABASE P442_IDLE");
            assertRefused("DROP TABLE IF EXISTS no_such_db.PUBLIC.t",
                "Database 'NO_SUCH_DB' does not exist or not authorized.");
            assertRefused("DROP TABLE IF EXISTS PUBLIC.t",
                "Cannot perform DROP. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("DROP TABLE IF EXISTS t",
                "Cannot perform DROP. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("TRUNCATE TABLE IF EXISTS PUBLIC.t",
                "Cannot perform TRUNCATE. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("DROP VIEW IF EXISTS PUBLIC.v",
                "Cannot perform DROP. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("DROP SCHEMA IF EXISTS s",
                "Cannot perform DROP. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("DROP SEQUENCE IF EXISTS PUBLIC.sq",
                "Cannot perform DROP. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("TRUNCATE TABLE IF EXISTS t",
                "Cannot perform TRUNCATE. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("DROP TABLE IF EXISTS P442_DB.nosuch_schema.t",
                "Schema 'P442_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            engine.execute("DROP TABLE IF EXISTS P442_DB.PUBLIC.nosuch");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P442_DB");
        }
    }

    /** Cortex search services, contacts, the three policy kinds and DROP TABLE IDENTIFIER(...) too. */
    @Test
    public void everyOtherKindAndIdentifierFollowTheSameRule() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P442B_DB");
            engine.execute("CREATE OR REPLACE TABLE P442B_DB.PUBLIC.T (x INT)");
            assertRefused("DROP CORTEX SEARCH SERVICE IF EXISTS nosuch_schema.css",
                "Schema 'P442B_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP CONTACT IF EXISTS nosuch_schema.ct",
                "Schema 'P442B_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP PROJECTION POLICY IF EXISTS nosuch_schema.pp",
                "Schema 'P442B_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP AGGREGATION POLICY IF EXISTS nosuch_schema.ap",
                "Schema 'P442B_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP JOIN POLICY IF EXISTS nosuch_schema.jp",
                "Schema 'P442B_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP CORTEX SEARCH SERVICE IF EXISTS no_such_db.PUBLIC.css",
                "Database 'NO_SUCH_DB' does not exist or not authorized.");
            assertRefused("DROP TABLE IF EXISTS IDENTIFIER('nosuch_schema.t')",
                "Schema 'P442B_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
            assertRefused("DROP TABLE IF EXISTS IDENTIFIER('no_such_db.PUBLIC.t')",
                "Database 'NO_SUCH_DB' does not exist or not authorized.");
            engine.execute("DROP CONTACT IF EXISTS PUBLIC.nosuch");
            engine.execute("DROP JOIN POLICY IF EXISTS PUBLIC.nosuch");
            engine.execute("DROP SCHEMA IF EXISTS P442B_DB.nosuch");
            engine.execute("DROP FUNCTION IF EXISTS PUBLIC.nosuch(INT)");
            engine.execute("TRUNCATE TABLE IF EXISTS P442B_DB.PUBLIC.nosuch");
            assertRefused("TRUNCATE IF EXISTS nosuch_schema.t",
                "Schema 'P442B_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P442B_DB");
        }
    }
}
