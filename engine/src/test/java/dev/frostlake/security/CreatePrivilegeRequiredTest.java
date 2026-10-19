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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a CREATE asks of where it lands. An object is created in a schema — a schema in a database — by the
 * session's primary role alone: that role, a role it inherits, PUBLIC or a database role granted to one of them
 * owns the container or holds the CREATE privilege for the object's kind on it. ACCOUNTADMIN is no exception,
 * and the refusal names the privilege and the container in full. A temporary object asks for nothing; a rename
 * creates the new name, and a replacement asks for OWNERSHIP of what it replaces first. Every statement here is
 * live-verified, run as ACCOUNTADMIN over a schema another role owns.
 */
public class CreatePrivilegeRequiredTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        // A CREATE is authorized by the primary role alone, and the refusals name only it.
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE IF NOT EXISTS crp_other");
        engine.execute("CREATE SCHEMA test_db.crp_s");
        engine.execute("CREATE TABLE test_db.crp_s.seed (a INT)");
        engine.execute("CREATE TABLE test_db.crp_s.seed2 (a INT)");
        engine.execute("CREATE VIEW test_db.crp_s.sv AS SELECT 1 AS a");
        engine.execute("CREATE SEQUENCE test_db.crp_s.sq");
        engine.execute("GRANT OWNERSHIP ON TABLE test_db.crp_s.seed2 TO ROLE crp_other");
        engine.execute("GRANT OWNERSHIP ON SCHEMA test_db.crp_s TO ROLE crp_other");
    }

    @Override
    protected void teardownTest() {
        // Everything the other role owns comes back to this session before the account is tidied.
        quietly("USE ROLE ACCOUNTADMIN");
        quietly("GRANT ROLE crp_other TO ROLE ACCOUNTADMIN");
        quietly("DROP DATABASE IF EXISTS crp_db");
        quietly("DROP DATABASE IF EXISTS crp_db2");
        quietly("DROP SCHEMA IF EXISTS test_db.crp_s");
        quietly("DROP ROLE IF EXISTS crp_other");
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
        }, sql).getMessage();
    }

    /** The refusal of a CREATE in the schema the other role owns, for the privilege it lacks. */
    private static String lacking(final String privilege) {
        return "SQL access control error:\nInsufficient privileges to operate on schema 'CRP_S'. Your primary role"
            + " ACCOUNTADMIN must have " + privilege + " granted on SCHEMA TEST_DB.CRP_S.";
    }

    @Test
    public void everyKindAsksForItsOwnPrivilegeOnTheSchema() {
        final String[][] creates = {
            {"CREATE TABLE test_db.crp_s.t1 (a INT)", "CREATE TABLE"},
            {"CREATE TABLE IF NOT EXISTS test_db.crp_s.t1 (a INT)", "CREATE TABLE"},
            {"CREATE TABLE IF NOT EXISTS test_db.crp_s.seed (a INT)", "CREATE TABLE"},
            {"CREATE OR REPLACE TABLE test_db.crp_s.t1 (a INT)", "CREATE TABLE"},
            {"CREATE OR REPLACE TABLE test_db.crp_s.seed (a INT)", "CREATE TABLE"},
            {"CREATE TRANSIENT TABLE test_db.crp_s.tr (a INT)", "CREATE TABLE"},
            {"CREATE TABLE test_db.crp_s.t2 AS SELECT 1 AS a", "CREATE TABLE"},
            {"CREATE TABLE test_db.crp_s.t3 LIKE test_db.crp_s.seed", "CREATE TABLE"},
            {"CREATE TABLE test_db.crp_s.t4 CLONE test_db.crp_s.seed", "CREATE TABLE"},
            {"CREATE TABLE crp_s.t9 (a INT)", "CREATE TABLE"},
            {"CREATE HYBRID TABLE test_db.crp_s.ht1 (a INT PRIMARY KEY)", "CREATE HYBRID TABLE"},
            {"CREATE DYNAMIC TABLE test_db.crp_s.dt1 TARGET_LAG = '1 hour' WAREHOUSE = nosuch_wh"
                + " AS SELECT a FROM test_db.crp_s.seed", "CREATE DYNAMIC TABLE"},
            {"CREATE EVENT TABLE test_db.crp_s.et1", "CREATE EVENT TABLE"},
            {"CREATE VIEW test_db.crp_s.v1 AS SELECT 1 AS a", "CREATE VIEW"},
            {"CREATE VIEW IF NOT EXISTS test_db.crp_s.sv AS SELECT 1 AS a", "CREATE VIEW"},
            // The privilege is asked before the view's query is compiled.
            {"CREATE VIEW test_db.crp_s.v1 AS SELECT nosuch FROM test_db.crp_s.seed", "CREATE VIEW"},
            {"CREATE MATERIALIZED VIEW test_db.crp_s.mv1 AS SELECT a FROM test_db.crp_s.seed",
                "CREATE MATERIALIZED VIEW"},
            {"CREATE SEQUENCE test_db.crp_s.sq1", "CREATE SEQUENCE"},
            {"CREATE SEQUENCE IF NOT EXISTS test_db.crp_s.sq", "CREATE SEQUENCE"},
            {"CREATE STAGE test_db.crp_s.st1", "CREATE STAGE"},
            {"CREATE FILE FORMAT test_db.crp_s.ff1 TYPE = CSV", "CREATE FILE FORMAT"},
            {"CREATE FUNCTION test_db.crp_s.f1() RETURNS INT AS '1'", "CREATE FUNCTION"},
            {"CREATE PROCEDURE test_db.crp_s.p1() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'",
                "CREATE PROCEDURE"},
            {"CREATE STREAM test_db.crp_s.sm1 ON TABLE test_db.crp_s.seed", "CREATE STREAM"},
            {"CREATE TASK test_db.crp_s.tk1 SCHEDULE = '60 MINUTE' AS SELECT 1", "CREATE TASK"},
            {"CREATE TAG test_db.crp_s.tg1", "CREATE TAG"},
            {"CREATE MASKING POLICY test_db.crp_s.mp1 AS (v INT) RETURNS INT -> v", "CREATE MASKING POLICY"},
            {"CREATE ROW ACCESS POLICY test_db.crp_s.rp1 AS (v INT) RETURNS BOOLEAN -> TRUE",
                "CREATE ROW ACCESS POLICY"},
            {"CREATE JOIN POLICY test_db.crp_s.jp1 AS () RETURNS JOIN_CONSTRAINT"
                + " -> JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)", "CREATE JOIN POLICY"},
            {"CREATE AGGREGATION POLICY test_db.crp_s.ap1 AS () RETURNS AGGREGATION_CONSTRAINT"
                + " -> AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)", "CREATE AGGREGATION POLICY"},
            {"CREATE PROJECTION POLICY test_db.crp_s.pj1 AS () RETURNS PROJECTION_CONSTRAINT"
                + " -> PROJECTION_CONSTRAINT(ALLOW => TRUE)", "CREATE PROJECTION POLICY"},
            {"CREATE PASSWORD POLICY test_db.crp_s.pw1 PASSWORD_MIN_LENGTH = 12", "CREATE PASSWORD POLICY"},
            {"CREATE SECRET test_db.crp_s.sc1 TYPE = GENERIC_STRING SECRET_STRING = 'x'", "CREATE SECRET"},
            {"CREATE NETWORK RULE test_db.crp_s.nr1 TYPE = IPV4 VALUE_LIST = ('1.2.3.4') MODE = INGRESS",
                "CREATE NETWORK RULE"},
        };
        for (final String[] create : creates) {
            assertEquals(lacking(create[1]), refusal(create[0]), create[0]);
        }
    }

    @Test
    public void aTemporaryObjectAsksForNothing() {
        engine.execute("CREATE TEMPORARY TABLE test_db.crp_s.tt (a INT)");
        engine.execute("CREATE TEMPORARY STAGE test_db.crp_s.tst");
        engine.execute("CREATE TEMPORARY VIEW test_db.crp_s.tv AS SELECT 1 AS a");
        engine.execute("CREATE TEMPORARY FUNCTION test_db.crp_s.tf() RETURNS INT AS '1'");
        engine.execute("CREATE TEMPORARY FILE FORMAT test_db.crp_s.tff TYPE = CSV");
        // The permanent kinds beside them are still refused.
        assertEquals(lacking("CREATE STAGE"), refusal("CREATE STAGE test_db.crp_s.st1"));
    }

    @Test
    public void aNameTakenAndATypeUnknownAreRefusedAheadOfThePrivilege() {
        assertEquals("SQL compilation error:\nObject 'TEST_DB.CRP_S.SEED' already exists.",
            refusal("CREATE TABLE test_db.crp_s.seed (a INT)"));
        assertTrue(refusal("CREATE VIEW test_db.crp_s.sv AS SELECT 1 AS a").contains("already exists"));
        assertTrue(refusal("CREATE SEQUENCE test_db.crp_s.sq").contains("already exists"));
        assertEquals("SQL compilation error:\nUnsupported data type 'NOSUCHTYPE'.",
            refusal("CREATE TABLE test_db.crp_s.t1 (a nosuchtype)"));
    }

    @Test
    public void thePrivilegeGrantedIsEnoughAndTheCreatorOwnsWhatItMade() {
        engine.execute("GRANT CREATE TABLE ON SCHEMA test_db.crp_s TO ROLE ACCOUNTADMIN");
        engine.execute("CREATE TABLE test_db.crp_s.t1 (a INT)");
        assertEquals("ACCOUNTADMIN", owner("SHOW TABLES LIKE 'T1' IN SCHEMA test_db.crp_s"));
        // One kind's privilege creates no other kind.
        assertEquals(lacking("CREATE VIEW"), refusal("CREATE VIEW test_db.crp_s.v1 AS SELECT 1 AS a"));
        engine.execute("REVOKE CREATE TABLE ON SCHEMA test_db.crp_s FROM ROLE ACCOUNTADMIN");
        assertEquals(lacking("CREATE TABLE"), refusal("CREATE TABLE test_db.crp_s.t5 (a INT)"));
        engine.execute("GRANT ALL ON SCHEMA test_db.crp_s TO ROLE ACCOUNTADMIN");
        engine.execute("CREATE VIEW test_db.crp_s.v1 AS SELECT 1 AS a");
        engine.execute("REVOKE ALL ON SCHEMA test_db.crp_s FROM ROLE ACCOUNTADMIN");
        assertEquals(lacking("CREATE VIEW"), refusal("CREATE VIEW test_db.crp_s.v2 AS SELECT 1 AS a"));
    }

    @Test
    public void aRoleThePrimaryRoleInheritsCountsAndSoDoesPublic() {
        engine.execute("GRANT ROLE crp_other TO ROLE ACCOUNTADMIN");
        engine.execute("CREATE TABLE test_db.crp_s.t6 (a INT)");
        engine.execute("REVOKE ROLE crp_other FROM ROLE ACCOUNTADMIN");
        assertEquals(lacking("CREATE TABLE"), refusal("CREATE TABLE test_db.crp_s.t7 (a INT)"));
        engine.execute("GRANT CREATE SEQUENCE ON SCHEMA test_db.crp_s TO ROLE PUBLIC");
        engine.execute("CREATE SEQUENCE test_db.crp_s.sq2");
    }

    @Test
    public void aDatabaseRoleCarriesThePrivilegeHoweverFarDown() {
        engine.execute("CREATE DATABASE ROLE test_db.crp_dr");
        engine.execute("GRANT CREATE STAGE ON SCHEMA test_db.crp_s TO DATABASE ROLE test_db.crp_dr");
        assertEquals(lacking("CREATE STAGE"), refusal("CREATE STAGE test_db.crp_s.st2"));
        engine.execute("GRANT DATABASE ROLE test_db.crp_dr TO ROLE ACCOUNTADMIN");
        engine.execute("CREATE STAGE test_db.crp_s.st2");
        engine.execute("CREATE DATABASE ROLE test_db.crp_dr2");
        engine.execute("GRANT CREATE TAG ON SCHEMA test_db.crp_s TO DATABASE ROLE test_db.crp_dr2");
        assertEquals(lacking("CREATE TAG"), refusal("CREATE TAG test_db.crp_s.tg2"));
        engine.execute("GRANT DATABASE ROLE test_db.crp_dr2 TO DATABASE ROLE test_db.crp_dr");
        engine.execute("CREATE TAG test_db.crp_s.tg2");
        engine.execute("REVOKE DATABASE ROLE test_db.crp_dr FROM ROLE ACCOUNTADMIN");
        assertEquals(lacking("CREATE STAGE"), refusal("CREATE STAGE test_db.crp_s.st3"));
    }

    @Test
    public void aSchemaAsksItsDatabase() {
        engine.execute("CREATE DATABASE crp_db");
        engine.execute("GRANT OWNERSHIP ON DATABASE crp_db TO ROLE crp_other");
        final String refused = "SQL access control error:\nInsufficient privileges to operate on database 'CRP_DB'."
            + " Your primary role ACCOUNTADMIN must have CREATE SCHEMA granted on DATABASE CRP_DB.";
        assertEquals(refused, refusal("CREATE SCHEMA crp_db.s2"));
        assertEquals(refused, refusal("CREATE SCHEMA IF NOT EXISTS crp_db.s2"));
        engine.execute("GRANT CREATE SCHEMA ON DATABASE crp_db TO ROLE ACCOUNTADMIN");
        engine.execute("CREATE SCHEMA crp_db.s2");
    }

    @Test
    public void aRenameCreatesTheNewNameWhereItLands() {
        engine.execute("CREATE TABLE test_db.test_schema.mv_t (a INT)");
        engine.execute("CREATE VIEW test_db.test_schema.mv_v AS SELECT 1 AS a");
        engine.execute("CREATE SEQUENCE test_db.test_schema.mv_q");
        assertEquals(lacking("CREATE TABLE"),
            refusal("ALTER TABLE test_db.test_schema.mv_t RENAME TO test_db.crp_s.mv_t"));
        assertEquals(lacking("CREATE VIEW"),
            refusal("ALTER VIEW test_db.test_schema.mv_v RENAME TO test_db.crp_s.mv_v"));
        assertEquals(lacking("CREATE SEQUENCE"),
            refusal("ALTER SEQUENCE test_db.test_schema.mv_q RENAME TO test_db.crp_s.mv_q"));
        // Where the session's role owns the schema a rename needs nothing more.
        engine.execute("ALTER TABLE test_db.test_schema.mv_t RENAME TO test_db.test_schema.mv_t2");
    }

    @Test
    public void aReplacementAsksForOwnershipOfWhatItReplacesFirst() {
        assertEquals("SQL access control error:\nInsufficient privileges to operate on table 'SEED2'. Your primary"
            + " role ACCOUNTADMIN must have OWNERSHIP granted on TABLE TEST_DB.CRP_S.SEED2.",
            refusal("CREATE OR REPLACE TABLE test_db.crp_s.seed2 (a INT)"));
    }

    @Test
    public void aProcedureRunningWithOwnersRightsCreatesAsItsOwner() {
        engine.execute("GRANT CREATE PROCEDURE ON SCHEMA test_db.crp_s TO ROLE ACCOUNTADMIN");
        engine.execute("GRANT USAGE ON SCHEMA test_db.crp_s TO ROLE ACCOUNTADMIN");
        engine.execute("CREATE PROCEDURE test_db.crp_s.mk() RETURNS VARCHAR LANGUAGE SQL AS"
            + " 'BEGIN CREATE TABLE test_db.crp_s.by_proc (a INT); RETURN ''made''; END'");
        engine.execute("CREATE PROCEDURE test_db.crp_s.mk_caller() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS"
            + " 'BEGIN CREATE TABLE test_db.crp_s.by_caller (a INT); RETURN ''made''; END'");
        engine.execute("CREATE PROCEDURE test_db.crp_s.mk_else() RETURNS VARCHAR LANGUAGE SQL AS"
            + " 'BEGIN CREATE TABLE test_db.test_schema.by_else (a INT); RETURN ''made''; END'");
        for (final String procedure : new String[] {"mk", "mk_caller", "mk_else"}) {
            engine.execute("GRANT OWNERSHIP ON PROCEDURE test_db.crp_s." + procedure + "() TO ROLE crp_other");
            engine.execute("GRANT USAGE ON PROCEDURE test_db.crp_s." + procedure + "() TO ROLE ACCOUNTADMIN");
        }
        engine.execute("GRANT USAGE ON DATABASE test_db TO ROLE crp_other");
        engine.execute("GRANT USAGE ON SCHEMA test_db.test_schema TO ROLE crp_other");

        // The owner holds the schema, so its procedure creates there for a caller that may not, and owns the table.
        final ResultSet made = engine.executeQuery("CALL test_db.crp_s.mk()");
        assertEquals("made", String.valueOf(made.getRows().get(0).getValue(0)));
        assertEquals("CRP_OTHER", owner("SHOW TABLES LIKE 'BY_PROC' IN SCHEMA test_db.crp_s"));
        // With the caller's rights it is the caller that lacks the privilege.
        assertTrue(refusal("CALL test_db.crp_s.mk_caller()").contains(lacking("CREATE TABLE")
            .substring("SQL access control error:\n".length())));
        // Where the owner holds nothing the refusal names it, and says whose rights are in force.
        assertTrue(refusal("CALL test_db.crp_s.mk_else()").contains("Insufficient privileges to operate on schema"
            + " 'TEST_SCHEMA'. This executable runs with owner's rights. The owner role CRP_OTHER must have CREATE"
            + " TABLE granted on SCHEMA TEST_DB.TEST_SCHEMA."));
    }

    @Test
    public void aNewDatabaseHoldsNoneOfTheRoleGrantsOfTheOneItReplaces() {
        engine.execute("CREATE DATABASE crp_db2");
        engine.execute("CREATE DATABASE ROLE crp_db2.dr9");
        engine.execute("GRANT DATABASE ROLE crp_db2.dr9 TO ROLE crp_other");
        assertTrue(holdsDatabaseRole("CRP_DB2.DR9"));
        // Gone with its database, and back with it.
        engine.execute("DROP DATABASE crp_db2");
        assertFalse(holdsDatabaseRole("CRP_DB2.DR9"));
        engine.execute("UNDROP DATABASE crp_db2");
        assertTrue(holdsDatabaseRole("CRP_DB2.DR9"));
        // A new database of the name starts with no roles, and a role made in it is granted to no one.
        engine.execute("DROP DATABASE crp_db2");
        engine.execute("CREATE DATABASE crp_db2");
        assertEquals(0, engine.executeQuery("SHOW DATABASE ROLES IN DATABASE crp_db2").getRowCount());
        engine.execute("CREATE DATABASE ROLE crp_db2.dr9");
        assertFalse(holdsDatabaseRole("CRP_DB2.DR9"));
        assertEquals(0, engine.executeQuery("SHOW GRANTS OF DATABASE ROLE crp_db2.dr9").getRowCount());
    }

    /** Whether SHOW GRANTS TO ROLE lists the database role as granted to the other role. */
    private boolean holdsDatabaseRole(final String qualifiedName) {
        final ResultSet grants = engine.executeQuery("SHOW GRANTS TO ROLE crp_other");
        for (final Row row : grants.getRows()) {
            if ("USAGE".equals(String.valueOf(row.getValue(1)))
                    && "DATABASE_ROLE".equals(String.valueOf(row.getValue(2)))
                    && qualifiedName.equals(String.valueOf(row.getValue(3)))) {
                return true;
            }
        }
        return false;
    }

    /** The owner SHOW TABLES names for the one table it lists. */
    private String owner(final String showTables) {
        final ResultSet tables = engine.executeQuery(showTables);
        assertEquals(1, tables.getRowCount());
        return String.valueOf(tables.getRows().get(0).getValue(9));
    }
}
