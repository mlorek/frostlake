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

package dev.frostlake.executor;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A missing-object refusal ends with a second sentence naming the privilege the statement's role would need to see
 * the object, and the object the way the first sentence names it. Every expectation spells the privilege and the
 * kind it is granted on outright; only the role and the account are read from the session, since they differ by
 * side.
 */
public class MissingObjectPrivilegeHintTest extends BaseDatabaseTest {

    private static final String ERROR = "SQL compilation error:\n";

    /** The account privileges any one of which lets a role describe a network rule, in the order live lists them. */
    private static final String[] NETWORK_RULE_READERS = {"APPLY AGGREGATION POLICY", "APPLY DATA MOVEMENT POLICY",
        "APPLY PRIVACY POLICY", "APPLY JOIN POLICY", "APPLY MASKING POLICY", "APPLY MULTI PARTY APPROVAL POLICY",
        "APPLY FEATURE POLICY", "APPLY PROJECTION POLICY", "APPLY ROW ACCESS POLICY",
        "APPLY STORAGE LIFECYCLE POLICY", "APPLY TOKENIZATION POLICY", "MONITOR", "RESOLVE ALL"};

    /** The message a statement fails with. */
    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    /** The first cell of a statement's first row. */
    private String scalar(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    private String role() {
        return scalar("SELECT CURRENT_ROLE()");
    }

    private String account() {
        return scalar("SELECT CURRENT_ACCOUNT()");
    }

    /** A refusal of one kind and name whose hint asks the session's primary role for {@code wanted}. */
    private String missing(final String kind, final String name, final String wanted) {
        return ERROR + kind + " '" + name + "' does not exist or not authorized. Your primary role " + role()
            + " must have " + wanted + ".";
    }

    /**
     * {@link #missing} raised inside a procedure running with owner's rights: worded for the owner, yet naming the
     * session's primary role.
     */
    private String missingAsOwner(final String kind, final String name, final String wanted) {
        return ERROR + kind + " '" + name + "' does not exist or not authorized. This executable runs with owner's"
            + " rights. The owner role " + role() + " must have " + wanted + ".";
    }

    private static String anyOn(final String securable, final String name) {
        return "at least one privilege granted on " + securable + " " + name;
    }

    private static String usageOn(final String container, final String name) {
        return "USAGE or any other privilege granted on " + container + " " + name;
    }

    @Test
    public void everyTableLikeRelationAsksForAnyPrivilegeOnATable() {
        final String[][] cells = {
            {"SELECT * FROM nosuch_t", "Object", "NOSUCH_T"},
            {"INSERT INTO nosuch_t VALUES (1)", "Table", "NOSUCH_T"},
            {"DROP TABLE nosuch_t", "Table", "TEST_DB.TEST_SCHEMA.NOSUCH_T"},
            {"DROP VIEW nosuch_v", "View", "TEST_DB.TEST_SCHEMA.NOSUCH_V"},
            {"DROP DYNAMIC TABLE nosuch_dt", "Dynamic table", "TEST_DB.TEST_SCHEMA.NOSUCH_DT"},
            {"DROP MATERIALIZED VIEW nosuch_mv", "Materialized view", "TEST_DB.TEST_SCHEMA.NOSUCH_MV"},
            {"DROP EXTERNAL TABLE nosuch_ext", "External table", "TEST_DB.TEST_SCHEMA.NOSUCH_EXT"},
            {"SHOW GRANTS ON nosuch_t", "Object", "NOSUCH_T"},
        };
        for (final String[] cell : cells) {
            assertEquals(missing(cell[1], cell[2], anyOn("TABLE", cell[2])), refusal(cell[0]), cell[0]);
        }
    }

    /** The hint names the object as the first sentence does: quoted where that is, a doubled quote kept or not. */
    @Test
    public void theHintNamesTheObjectAsTheFirstSentenceDoes() {
        final String[][] cells = {
            {"SELECT * FROM \"lower_t\"", "Object", "\"lower_t\""},
            {"SELECT * FROM \"n\"\"o\"", "Object", "\"n\"o\""},
            {"DROP TABLE \"n\"\"o\"", "Table", "TEST_DB.TEST_SCHEMA.\"n\"\"o\""},
            {"DROP VIEW \"n\"\"o\"", "View", "TEST_DB.TEST_SCHEMA.\"n\"o\""},
            {"SELECT * FROM test_schema.nosuch_t", "Object", "TEST_DB.TEST_SCHEMA.NOSUCH_T"},
        };
        for (final String[] cell : cells) {
            assertEquals(missing(cell[1], cell[2], anyOn("TABLE", cell[2])), refusal(cell[0]), cell[0]);
        }
    }

    @Test
    public void aContainerAsksForUsageOrAnyOtherPrivilege() {
        final String[][] cells = {
            {"DROP SCHEMA nosuch_s", "Schema", "TEST_DB.NOSUCH_S", "SCHEMA"},
            {"SELECT * FROM nosuch_s.t", "Schema", "TEST_DB.NOSUCH_S", "SCHEMA"},
            {"DROP DATABASE nosuch_hint_db", "Database", "NOSUCH_HINT_DB", "DATABASE"},
            {"SELECT * FROM nosuch_hint_db.public.t", "Database", "NOSUCH_HINT_DB", "DATABASE"},
            {"SHOW STREAMS IN APPLICATION nosuch_hint_app", "Application", "NOSUCH_HINT_APP", "DATABASE"},
        };
        for (final String[] cell : cells) {
            assertEquals(missing(cell[1], cell[2], usageOn(cell[3], cell[2])), refusal(cell[0]), cell[0]);
        }
    }

    @Test
    public void aUserOrANetworkRuleAsksForAnAccountPrivilege() {
        final String onAccount = " granted on ACCOUNT " + account();
        assertEquals(missing("User", "NOSUCH_HINT_USER", "USAGE" + onAccount), refusal("DROP USER nosuch_hint_user"));
        assertEquals(missing("Network rule", "TEST_DB.TEST_SCHEMA.NOSUCH_NR", "MONITOR" + onAccount),
            refusal("DROP NETWORK RULE nosuch_nr"));
        assertEquals(missing("Network rule", "TEST_DB.TEST_SCHEMA.NOSUCH_NR", "MONITOR" + onAccount),
            refusal("ALTER NETWORK RULE nosuch_nr SET COMMENT = 'x'"));
    }

    /** Describing a rule is the one statement whose hint lists several privileges, one sentence each. */
    @Test
    public void describingANetworkRuleListsEveryAccountPrivilegeThatWouldDo() {
        final String subject = " Your primary role " + role() + " must have ";
        final String onAccount = " granted on ACCOUNT " + account() + ".";
        final StringBuilder expected = new StringBuilder(ERROR
            + "Network rule 'TEST_DB.TEST_SCHEMA.NOSUCH_NR' does not exist or not authorized.");
        for (final String privilege : NETWORK_RULE_READERS) {
            expected.append(subject).append(privilege).append(onAccount);
        }
        assertEquals(expected.toString(), refusal("DESCRIBE NETWORK RULE nosuch_nr"));
    }

    /** A statement that grants on a rule, revokes on one or lists its grants asks for MONITOR, then RESOLVE ALL. */
    @Test
    public void aGrantStatementOverANetworkRuleAsksForMonitorThenResolveAll() {
        final String subject = " Your primary role " + role() + " must have ";
        final String onAccount = " granted on ACCOUNT " + account() + ".";
        final String expected = ERROR + "Network rule 'TEST_DB.TEST_SCHEMA.NOSUCH_NR' does not exist or not authorized."
            + subject + "MONITOR" + onAccount + subject + "RESOLVE ALL" + onAccount;
        assertEquals(expected, refusal("GRANT USAGE ON NETWORK RULE nosuch_nr TO ROLE PUBLIC"));
        assertEquals(expected, refusal("GRANT OWNERSHIP ON NETWORK RULE nosuch_nr TO ROLE PUBLIC"));
        assertEquals(expected, refusal("REVOKE USAGE ON NETWORK RULE nosuch_nr FROM ROLE PUBLIC"));
        assertEquals(expected, refusal("SHOW GRANTS ON NETWORK RULE test_schema.nosuch_nr"));
        final String secret = "TEST_DB.TEST_SCHEMA.NOSUCH_SC";
        assertEquals(missing("Secret", secret, anyOn("SECRET", secret)),
            refusal("GRANT USAGE ON SECRET nosuch_sc TO ROLE PUBLIC"));
    }

    @Test
    public void everyOtherKindAsksForAnyPrivilegeOnItself() {
        final String[][] cells = {
            {"DROP STAGE nosuch_st", "Stage", "TEST_DB.TEST_SCHEMA.NOSUCH_ST", "STAGE"},
            {"DROP STREAM nosuch_sm", "Stream", "TEST_DB.TEST_SCHEMA.NOSUCH_SM", "STREAM"},
            {"DROP TASK nosuch_tk", "Task", "TEST_DB.TEST_SCHEMA.NOSUCH_TK", "TASK"},
            {"DROP PIPE nosuch_pp", "Pipe", "TEST_DB.TEST_SCHEMA.NOSUCH_PP", "PIPE"},
            {"DROP SEQUENCE nosuch_sq", "Sequence", "TEST_DB.TEST_SCHEMA.NOSUCH_SQ", "SEQUENCE"},
            {"DROP TAG nosuch_tg", "Tag", "TEST_DB.TEST_SCHEMA.NOSUCH_TG", "TAG"},
            {"DROP SECRET nosuch_sc", "Secret", "TEST_DB.TEST_SCHEMA.NOSUCH_SC", "SECRET"},
            {"DROP ALERT nosuch_al", "Alert", "TEST_DB.TEST_SCHEMA.NOSUCH_AL", "ALERT"},
            {"DROP FUNCTION nosuch_f(INT)", "Function", "TEST_DB.TEST_SCHEMA.NOSUCH_F", "FUNCTION"},
            {"DROP PROCEDURE nosuch_p(INT)", "Procedure", "TEST_DB.TEST_SCHEMA.NOSUCH_P", "PROCEDURE"},
            {"DROP FILE FORMAT nosuch_ff", "File format", "TEST_DB.TEST_SCHEMA.NOSUCH_FF", "FILE FORMAT"},
            {"DROP NOTEBOOK nosuch_nb", "Notebook", "TEST_DB.TEST_SCHEMA.NOSUCH_NB", "NOTEBOOK"},
            {"DROP STREAMLIT nosuch_sl", "Streamlit", "TEST_DB.TEST_SCHEMA.NOSUCH_SL", "STREAMLIT"},
            {"DROP CORTEX SEARCH SERVICE nosuch_css", "Cortex Search Service", "TEST_DB.TEST_SCHEMA.NOSUCH_CSS",
                "CORTEX SEARCH SERVICE"},
            {"DROP CONTACT nosuch_ct", "Contact", "TEST_DB.TEST_SCHEMA.NOSUCH_CT", "CONTACT"},
            {"DROP SERVICE nosuch_svc", "Service", "TEST_DB.TEST_SCHEMA.NOSUCH_SVC", "SERVICE"},
            {"DROP MODEL nosuch_mdl", "Model", "TEST_DB.TEST_SCHEMA.NOSUCH_MDL", "MODEL"},
            {"DROP WAREHOUSE nosuch_hint_wh", "Warehouse", "NOSUCH_HINT_WH", "WAREHOUSE"},
            {"DROP ROLE nosuch_hint_role", "Role", "NOSUCH_HINT_ROLE", "ROLE"},
            {"DROP INTEGRATION nosuch_hint_int", "Integration", "NOSUCH_HINT_INT", "INTEGRATION"},
            {"DROP NETWORK POLICY nosuch_hint_np", "Network policy", "NOSUCH_HINT_NP", "NETWORK POLICY"},
            {"DROP COMPUTE POOL nosuch_hint_cp", "Compute pool", "NOSUCH_HINT_CP", "COMPUTE POOL"},
            {"GRANT MONITOR ON RESOURCE MONITOR nosuch_hint_rm TO ROLE PUBLIC", "Resource monitor", "NOSUCH_HINT_RM",
                "RESOURCE MONITOR"},
            {"DROP MANAGED ACCOUNT nosuch_hint_ma", "Managed account", "NOSUCH_HINT_MA", "MANAGED ACCOUNT"},
            {"DROP REPLICATION GROUP nosuch_hint_rg", "Replication group", "NOSUCH_HINT_RG", "REPLICATION GROUP"},
            {"DROP LISTING nosuch_hint_ls", "Data exchange listing", "NOSUCH_HINT_LS", "DATA EXCHANGE LISTING"},
            {"SHOW COMPUTE POOLS IN ACCOUNT nosuch_hint_acct", "Account", "NOSUCH_HINT_ACCT", "ACCOUNT"},
        };
        for (final String[] cell : cells) {
            assertEquals(missing(cell[1], cell[2], anyOn(cell[3], cell[2])), refusal(cell[0]), cell[0]);
        }
    }

    /** A family's members ask under the family's name: every policy but a network policy, a stage-like repository. */
    @Test
    public void someKindsAskUnderTheirFamilysName() {
        final String[][] cells = {
            {"DROP MASKING POLICY nosuch_mp", "Masking policy", "TEST_DB.TEST_SCHEMA.NOSUCH_MP", "POLICY"},
            {"DROP ROW ACCESS POLICY nosuch_rap", "Row access policy", "TEST_DB.TEST_SCHEMA.NOSUCH_RAP", "POLICY"},
            {"DROP AGGREGATION POLICY nosuch_ap", "Aggregation policy", "TEST_DB.TEST_SCHEMA.NOSUCH_AP", "POLICY"},
            {"DROP PROJECTION POLICY nosuch_pjp", "Projection policy", "TEST_DB.TEST_SCHEMA.NOSUCH_PJP", "POLICY"},
            {"DROP PASSWORD POLICY nosuch_pwp", "Password policy", "TEST_DB.TEST_SCHEMA.NOSUCH_PWP", "POLICY"},
            {"DROP JOIN POLICY nosuch_jp", "Join policy", "TEST_DB.TEST_SCHEMA.NOSUCH_JP", "POLICY"},
            {"SELECT * FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'nosuch_pol'))", "Policy",
                "TEST_DB.TEST_SCHEMA.NOSUCH_POL", "POLICY"},
            {"DROP DATABASE ROLE nosuch_dbr", "Database role", "TEST_DB.NOSUCH_DBR", "ROLE"},
            {"DROP IMAGE REPOSITORY nosuch_ir", "Image repository", "TEST_DB.TEST_SCHEMA.NOSUCH_IR", "STAGE"},
            {"DROP ARTIFACT REPOSITORY nosuch_arp", "Artifact Repository", "TEST_DB.TEST_SCHEMA.NOSUCH_ARP", "STAGE"},
            {"DROP EXTERNAL VOLUME nosuch_hint_ev", "External volume", "NOSUCH_HINT_EV", "VOLUME"},
        };
        for (final String[] cell : cells) {
            assertEquals(missing(cell[1], cell[2], anyOn(cell[3], cell[2])), refusal(cell[0]), cell[0]);
        }
    }

    /** A name that is no securable object (a star's qualifier, a column, a constraint, a stage path) has no hint. */
    @Test
    public void aNameThatIsNoSecurableObjectCarriesNoHint() {
        engine.execute("CREATE TABLE t (a INT, CONSTRAINT pk1 PRIMARY KEY (a))");
        assertEquals(ERROR + "Object 'Q' does not exist or not authorized.", refusal("SELECT q.* FROM t x"));
        assertEquals(ERROR + "Object 'FZ' does not exist or not authorized.", refusal("SELECT fz.*"));
        assertEquals(ERROR + "Object 'Q' does not exist or not authorized.", refusal("SELECT COUNT(q.*) FROM t"));
        assertEquals(ERROR + "Object 'NOSUCH_C' does not exist or not authorized.",
            refusal("ALTER TABLE t RENAME COLUMN nosuch_c TO b"));
        assertEquals(ERROR + "Object 'NOSUCH_C' does not exist or not authorized.",
            refusal("COMMENT ON COLUMN t.nosuch_c IS 'x'"));
        assertEquals(ERROR + "Object 'NOSUCH_CON' does not exist or not authorized.",
            refusal("ALTER TABLE t ALTER CONSTRAINT nosuch_con RELY"));
        assertEquals(ERROR + "Object 'A.B.C.T' does not exist or not authorized.", refusal("SELECT $1 FROM @a.b.c.%t"));
        assertEquals(ERROR + "Object '\"\".\"\".T' does not exist or not authorized.", refusal("SELECT $1 FROM @..%t"));
        assertEquals(ERROR + "Data Metric Function 'NULL_COUNT' does not exist or not authorized.",
            refusal("SELECT * FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES("
                + "METRIC_NAME => 'NULL_COUNT'))"));
        assertEquals("SQL compilation error: Object type or Class 'FILE_FORMAT' does not exist or not authorized.",
            refusal("GRANT SELECT ON FILE_FORMAT ff TO ROLE PUBLIC"));
    }

    /** Inside an owner's-rights procedure the hint is worded for the owner, whatever the kind. */
    @Test
    public void anOwnersRightsProcedureWordsTheHintForItsOwner() {
        engine.execute("""
            CREATE PROCEDURE p_own() RETURNS VARCHAR LANGUAGE SQL AS
            $$
            DECLARE
              out VARCHAR DEFAULT '';
            BEGIN
              BEGIN
                DROP TABLE nosuch_t;
              EXCEPTION
                WHEN OTHER THEN out := out || SQLERRM || '|';
              END;
              BEGIN
                DROP DATABASE nosuch_hint_db;
              EXCEPTION
                WHEN OTHER THEN out := out || SQLERRM || '|';
              END;
              BEGIN
                DROP USER nosuch_hint_user;
              EXCEPTION
                WHEN OTHER THEN out := out || SQLERRM || '|';
              END;
              RETURN out;
            END;
            $$""");
        final String table = "TEST_DB.TEST_SCHEMA.NOSUCH_T";
        assertEquals(missingAsOwner("Table", table, anyOn("TABLE", table))
            + "|" + missingAsOwner("Database", "NOSUCH_HINT_DB", usageOn("DATABASE", "NOSUCH_HINT_DB"))
            + "|" + missingAsOwner("User", "NOSUCH_HINT_USER", "USAGE granted on ACCOUNT " + account()) + "|",
            scalar("CALL p_own()"));
    }

    /**
     * The owner's wording still names the session's primary role, never the role that owns the procedure: handed
     * over to PUBLIC, the procedure's refusal names the caller's role all the same.
     */
    @Test
    public void theOwnersWordingNamesTheSessionsPrimaryRoleNotTheOwner() {
        engine.execute("""
            CREATE PROCEDURE p_handed() RETURNS VARCHAR LANGUAGE SQL AS
            $$
            BEGIN
              SELECT * FROM nosuch_hint_db.public.t;
              RETURN 'x';
            EXCEPTION
              WHEN OTHER THEN RETURN SQLERRM;
            END;
            $$""");
        engine.execute("GRANT OWNERSHIP ON PROCEDURE p_handed() TO ROLE PUBLIC");
        final ResultSet shown = engine.executeQuery("SHOW GRANTS ON PROCEDURE p_handed()");
        String owner = null;
        for (final Row row : shown.getRows()) {
            if ("OWNERSHIP".equals(String.valueOf(row.getValue(shown.getColumnIndex("privilege"))))) {
                owner = String.valueOf(row.getValue(shown.getColumnIndex("grantee_name")));
            }
        }
        assertEquals("PUBLIC", owner);
        assertNotEquals(owner, role());
        assertEquals(missingAsOwner("Database", "NOSUCH_HINT_DB", usageOn("DATABASE", "NOSUCH_HINT_DB")),
            scalar("CALL p_handed()"));
    }

    /** An uncaught refusal keeps the owner's-rights sentence inside the uncaught-exception envelope. */
    @Test
    public void anUncaughtRefusalCarriesTheOwnersSentence() {
        engine.execute("""
            CREATE PROCEDURE p_miss() RETURNS VARCHAR LANGUAGE SQL AS
            $$
            BEGIN
              SELECT * FROM nosuch_t;
              RETURN 'x';
            END;
            $$""");
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : "
            + missingAsOwner("Object", "NOSUCH_T", anyOn("TABLE", "NOSUCH_T")), refusal("CALL p_miss()"));
    }

    /** A caller's-rights procedure speaks for the caller's primary role, and a block or dynamic SQL does too. */
    @Test
    public void callersRightsABlockAndDynamicSqlSpeakForThePrimaryRole() {
        engine.execute("""
            CREATE PROCEDURE p_caller() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS
            $$
            BEGIN
              DROP TABLE nosuch_t;
              RETURN 'x';
            EXCEPTION
              WHEN OTHER THEN RETURN SQLERRM;
            END;
            $$""");
        final String name = "TEST_DB.TEST_SCHEMA.NOSUCH_T";
        final String table = missing("Table", name, anyOn("TABLE", name));
        assertEquals(table, scalar("CALL p_caller()"));
        assertEquals(table, scalar("BEGIN DROP TABLE nosuch_t; EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END"));
        assertEquals(missing("Object", "NOSUCH_T", anyOn("TABLE", "NOSUCH_T")),
            refusal("EXECUTE IMMEDIATE 'SELECT * FROM nosuch_t'"));
    }

    /**
     * Any owner's-rights procedure on the way to the refusal words it for the owner: a caller's-rights procedure
     * called from an owner's-rights one runs with that owner's rights, and an owner's-rights one called from a
     * caller's-rights one with its own. Each inner procedure logs what it caught, so the sentence is read back from a
     * table.
     */
    @Test
    public void anyOwnersRightsProcedureOnTheCallPathWordsTheHint() {
        engine.execute("CREATE TABLE caught (msg VARCHAR)");
        engine.execute("""
            CREATE PROCEDURE p_inner_caller() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS
            $$
            BEGIN
              DROP TABLE nosuch_t;
              RETURN 'x';
            EXCEPTION
              WHEN OTHER THEN
                INSERT INTO caught VALUES (:SQLERRM);
                RETURN 'caught';
            END;
            $$""");
        engine.execute("""
            CREATE PROCEDURE p_inner_owner() RETURNS VARCHAR LANGUAGE SQL AS
            $$
            BEGIN
              DROP TABLE nosuch_t;
              RETURN 'x';
            EXCEPTION
              WHEN OTHER THEN
                INSERT INTO caught VALUES (:SQLERRM);
                RETURN 'caught';
            END;
            $$""");
        engine.execute("""
            CREATE PROCEDURE p_outer_owner() RETURNS VARCHAR LANGUAGE SQL AS
            $$
            BEGIN
              CALL p_inner_caller();
              RETURN 'done';
            END;
            $$""");
        engine.execute("""
            CREATE PROCEDURE p_outer_caller() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS
            $$
            BEGIN
              CALL p_inner_owner();
              RETURN 'done';
            END;
            $$""");
        final String asOwner = missingAsOwner("Table", "TEST_DB.TEST_SCHEMA.NOSUCH_T",
            anyOn("TABLE", "TEST_DB.TEST_SCHEMA.NOSUCH_T"));
        assertEquals("done", scalar("CALL p_outer_owner()"));
        assertEquals(asOwner, scalar("SELECT msg FROM caught"));
        engine.execute("DELETE FROM caught");
        assertEquals("done", scalar("CALL p_outer_caller()"));
        assertEquals(asOwner, scalar("SELECT msg FROM caught"));
    }

    /** The session runs with no secondary roles, which is what keeps the hint to its short form. */
    @Test
    public void theSessionHasNoSecondaryRoles() {
        assertEquals("{\"roles\":\"\",\"value\":\"\"}", scalar("SELECT CURRENT_SECONDARY_ROLES()"));
    }
}
