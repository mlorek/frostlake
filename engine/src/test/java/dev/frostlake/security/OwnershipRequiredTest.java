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

package dev.frostlake.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Replacing, dropping, renaming or swapping an object needs OWNERSHIP of it: the object's owner must be the primary
 * role, a secondary role, or a role either inherits, however many levels down. ACCOUNTADMIN is no exception, IF
 * EXISTS does not forgive the refusal, and CREATE … IF NOT EXISTS over the object succeeds without touching it.
 */
public class OwnershipRequiredTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        // Privileges come from the primary role alone, and the refusals name only it.
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE IF NOT EXISTS own_other");
        engine.execute("CREATE ROLE IF NOT EXISTS own_mid");
    }

    @Override
    protected void teardownTest() {
        // Everything the other role owns comes back to this session before the account is tidied.
        quietly("USE ROLE ACCOUNTADMIN");
        quietly("GRANT ROLE own_other TO ROLE ACCOUNTADMIN");
        quietly("DROP DATABASE IF EXISTS own_db");
        quietly("DROP ROLE IF EXISTS own_mid");
        quietly("DROP ROLE IF EXISTS own_other");
    }

    private void quietly(final String sql) {
        try {
            engine.execute(sql);
        } catch (final RuntimeException ignored) {
            // cleanup only
        }
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private static String ownership(final String kind, final String name, final String grantKind, final String full) {
        return "SQL access control error:\nInsufficient privileges to operate on " + kind + " '" + name
            + "'. Your primary role ACCOUNTADMIN must have OWNERSHIP granted on "
            + grantKind + " " + full + ".";
    }

    private void giveAway(final String kindAndName) {
        engine.execute("GRANT OWNERSHIP ON " + kindAndName + " TO ROLE own_other COPY CURRENT GRANTS");
    }

    @Test
    public void aTableAnotherRoleOwnsIsNeitherReplacedDroppedRenamedNorSwapped() {
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("CREATE TABLE mine (a INT)");
        giveAway("TABLE t1");
        final String refused = ownership("table", "T1", "TABLE", "TEST_DB.TEST_SCHEMA.T1");
        assertEquals(refused, refusal("CREATE OR REPLACE TABLE t1 (a INT, b INT)"));
        assertEquals(refused, refusal("CREATE OR REPLACE TABLE t1 AS SELECT 1 AS a"));
        assertEquals(refused, refusal("DROP TABLE t1"));
        assertEquals(refused, refusal("DROP TABLE IF EXISTS t1"));
        assertEquals(refused, refusal("ALTER TABLE t1 RENAME TO t9"));
        assertEquals(refused, refusal("ALTER TABLE mine SWAP WITH t1"));
        // IF NOT EXISTS answers that the table is there, and leaves it alone.
        engine.execute("CREATE TABLE IF NOT EXISTS t1 (a INT)");
        assertEquals(refused, refusal("DROP TABLE t1"));
    }

    @Test
    public void aRoleTheSessionInheritsCountsHoweverFarDown() {
        engine.execute("CREATE TABLE t1 (a INT)");
        engine.execute("CREATE TABLE t2 (a INT)");
        engine.execute("CREATE TABLE t3 (a INT)");
        giveAway("TABLE t1");
        giveAway("TABLE t2");
        giveAway("TABLE t3");
        engine.execute("GRANT ROLE own_other TO ROLE ACCOUNTADMIN");
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("REVOKE ROLE own_other FROM ROLE ACCOUNTADMIN");
        engine.execute("GRANT ROLE own_other TO ROLE own_mid");
        engine.execute("GRANT ROLE own_mid TO ROLE ACCOUNTADMIN");
        engine.execute("DROP TABLE t2");
        engine.execute("REVOKE ROLE own_mid FROM ROLE ACCOUNTADMIN");
        // The replacement belongs to the role that made it; the untouched table is still the other role's.
        engine.execute("DROP TABLE t1");
        assertEquals(ownership("table", "T3", "TABLE", "TEST_DB.TEST_SCHEMA.T3"), refusal("DROP TABLE t3"));
    }

    @Test
    public void aViewIsNamedAsAViewAndGrantedOnAsATable() {
        engine.execute("CREATE VIEW v1 AS SELECT 1 AS a");
        giveAway("VIEW v1");
        final String refused = ownership("view", "V1", "TABLE", "TEST_DB.TEST_SCHEMA.V1");
        assertEquals(refused, refusal("CREATE OR REPLACE VIEW v1 AS SELECT 2 AS a"));
        assertEquals(refused, refusal("ALTER VIEW v1 RENAME TO v9"));
        assertEquals(refused, refusal("DROP VIEW v1"));
    }

    @Test
    public void aSchemaAndADatabaseAnotherRoleOwns() {
        engine.execute("CREATE SCHEMA own_s");
        engine.execute("USE SCHEMA test_db.test_schema");
        giveAway("SCHEMA own_s");
        final String schema = ownership("schema", "OWN_S", "SCHEMA", "TEST_DB.OWN_S");
        assertEquals(schema, refusal("CREATE OR REPLACE SCHEMA own_s"));
        assertEquals(schema, refusal("ALTER SCHEMA own_s RENAME TO own_s9"));
        assertEquals(schema, refusal("DROP SCHEMA own_s"));
        assertEquals(schema, refusal("DROP SCHEMA IF EXISTS own_s"));
        engine.execute("CREATE DATABASE own_db");
        engine.execute("USE SCHEMA test_db.test_schema");
        giveAway("DATABASE own_db");
        final String database = ownership("database", "OWN_DB", "DATABASE", "OWN_DB");
        assertEquals(database, refusal("DROP DATABASE own_db"));
        assertEquals(database, refusal("CREATE OR REPLACE DATABASE own_db"));
        assertEquals(database, refusal("ALTER DATABASE own_db RENAME TO own_db9"));
    }

    @Test
    public void everySchemaObjectKindNamesItselfInTheRefusal() {
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE TASK tk SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE SEQUENCE sq");
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV");
        engine.execute("CREATE FUNCTION fn(x INT) RETURNS INT AS 'x + 1'");
        engine.execute("CREATE TAG tg");
        engine.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> v");
        engine.execute("CREATE ROW ACCESS POLICY rap AS (v INT) RETURNS BOOLEAN -> TRUE");
        for (final String kind : new String[] {"STAGE st", "TASK tk", "SEQUENCE sq", "FILE FORMAT ff",
            "FUNCTION fn(INT)", "TAG tg", "MASKING POLICY mp", "ROW ACCESS POLICY rap"}) {
            giveAway(kind);
        }
        final String stage = ownership("stage", "ST", "STAGE", "TEST_DB.TEST_SCHEMA.ST");
        assertEquals(stage, refusal("DROP STAGE st"));
        assertEquals(stage, refusal("CREATE OR REPLACE STAGE st"));
        final String task = ownership("task", "TK", "TASK", "TEST_DB.TEST_SCHEMA.TK");
        assertEquals(task, refusal("DROP TASK tk"));
        assertEquals(task, refusal("CREATE OR REPLACE TASK tk SCHEDULE = '60 MINUTE' AS SELECT 2"));
        final String sequence = ownership("sequence", "SQ", "SEQUENCE", "TEST_DB.TEST_SCHEMA.SQ");
        assertEquals(sequence, refusal("DROP SEQUENCE sq"));
        assertEquals(sequence, refusal("CREATE OR REPLACE SEQUENCE sq"));
        final String format = ownership("file_format", "FF", "FILE FORMAT", "TEST_DB.TEST_SCHEMA.FF");
        assertEquals(format, refusal("DROP FILE FORMAT ff"));
        assertEquals(format, refusal("CREATE OR REPLACE FILE FORMAT ff TYPE = JSON"));
        final String function = ownership("function", "FN", "FUNCTION", "TEST_DB.TEST_SCHEMA.FN(NUMBER)");
        assertEquals(function, refusal("DROP FUNCTION fn(INT)"));
        assertEquals(function, refusal("CREATE OR REPLACE FUNCTION fn(x INT) RETURNS INT AS 'x + 2'"));
        final String tag = ownership("tag", "TG", "TAG", "TEST_DB.TEST_SCHEMA.TG");
        assertEquals(tag, refusal("DROP TAG tg"));
        assertEquals(tag, refusal("CREATE OR REPLACE TAG tg"));
        final String masking = ownership("masking_policy", "MP", "POLICY", "TEST_DB.TEST_SCHEMA.MP");
        assertEquals(masking, refusal("DROP MASKING POLICY mp"));
        assertEquals(masking, refusal("CREATE OR REPLACE MASKING POLICY mp AS (v STRING) RETURNS STRING -> v"));
        assertEquals(ownership("row_access_policy", "RAP", "POLICY", "TEST_DB.TEST_SCHEMA.RAP"),
            refusal("DROP ROW ACCESS POLICY rap"));
    }
}
