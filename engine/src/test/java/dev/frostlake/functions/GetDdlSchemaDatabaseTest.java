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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GET_DDL for a SCHEMA and a DATABASE — the container's own statement, then every object it holds, each in the
 * text GET_DDL gives it alone — and the per-object texts the recursive forms are built from.
 */
public class GetDdlSchemaDatabaseTest extends BaseDatabaseTest {

    /** A comment holding every character a DDL literal escapes, as SQL writes it: {@code b\\s "q" t<TAB>n<LF>e's}. */
    private static final String ESCAPED_SQL = "'b\\\\s \"q\" t\\tn\\ne''s'";

    /** The same comment as GET_DDL prints it. */
    private static final String ESCAPED_DDL = "'b\\\\s \\\"q\\\" t\\tn\\ne''s'";

    private String ddl(final String type, final String name) {
        final ResultSet rs = engine.executeQuery("SELECT GET_DDL('" + type + "', '" + name + "')");
        return (String) rs.getRows().get(0).getValue(0);
    }

    private String ddl(final String type, final String name, final String qualifiedSql) {
        final ResultSet rs = engine.executeQuery("SELECT GET_DDL('" + type + "', '" + name + "', " + qualifiedSql + ")");
        return (String) rs.getRows().get(0).getValue(0);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private void run(final String... statements) {
        for (final String statement : statements) {
            engine.execute(statement);
        }
    }

    /** The lines of a DDL text that open a routine's statement, in order. */
    private static List<String> routineHeads(final String text) {
        final List<String> heads = new ArrayList<String>();
        for (final String line : text.split("\n")) {
            if (line.startsWith("CREATE OR REPLACE FUNCTION") || line.startsWith("CREATE OR REPLACE PROCEDURE")) {
                heads.add(line);
            }
        }
        return heads;
    }

    // ─────────────────────────── schema headers ───────────────────────────

    @Test
    public void anEmptySchemaIsItsOwnStatementAndALineBreak() {
        run("CREATE SCHEMA s_empty");
        assertEquals("create or replace schema S_EMPTY;\n", ddl("SCHEMA", "s_empty"));
        assertEquals("create or replace schema S_EMPTY;\n", ddl("SCHEMA", "\"S_EMPTY\""));
        assertEquals("create or replace schema S_EMPTY;\n", ddl("SCHEMA", "TEST_DB.S_EMPTY"));
        assertEquals("create or replace schema S_EMPTY;\n", ddl("schema", "S_EMPTY"));
    }

    @Test
    public void theThirdArgumentQualifiesTheRecreatedNamesAsABoolean() {
        run("CREATE SCHEMA s_empty");
        assertEquals("create or replace schema TEST_DB.S_EMPTY;\n", ddl("SCHEMA", "S_EMPTY", "TRUE"));
        assertEquals("create or replace schema TEST_DB.S_EMPTY;\n", ddl("SCHEMA", "S_EMPTY", "'yes'"));
        assertEquals("create or replace schema TEST_DB.S_EMPTY;\n", ddl("SCHEMA", "S_EMPTY", "1"));
        assertEquals("create or replace schema TEST_DB.S_EMPTY;\n", ddl("SCHEMA", "S_EMPTY", "2.5"));
        assertEquals("create or replace schema S_EMPTY;\n", ddl("SCHEMA", "S_EMPTY", "FALSE"));
        assertEquals("create or replace schema S_EMPTY;\n", ddl("SCHEMA", "S_EMPTY", "'no'"));
        assertEquals("create or replace schema S_EMPTY;\n", ddl("SCHEMA", "S_EMPTY", "0"));
        assertEquals("SQL compilation error:\nInvalid value [CAST('abc' AS BOOLEAN)] for function '2', parameter "
            + "IMPORT_DDL: constant arguments expected", refusal("SELECT GET_DDL('SCHEMA', 'S_EMPTY', 'abc')"));
    }

    @Test
    public void aNullArgumentAnswersNull() {
        run("CREATE SCHEMA s_empty");
        assertNull(engine.executeQuery("SELECT GET_DDL('SCHEMA', NULL)").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT GET_DDL(NULL, 'S_EMPTY')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT GET_DDL('SCHEMA', 'S_EMPTY', NULL)").getRows().get(0).getValue(0));
    }

    @Test
    public void theSchemaStatementSpellsTransienceManagedAccessAndComment() {
        run("CREATE TRANSIENT SCHEMA s_tr",
            "CREATE SCHEMA s_ma WITH MANAGED ACCESS",
            "CREATE SCHEMA s_cm COMMENT = 'it''s here'",
            "CREATE TRANSIENT SCHEMA s_all WITH MANAGED ACCESS COMMENT = 'both'",
            "CREATE SCHEMA s_rt DATA_RETENTION_TIME_IN_DAYS = 0",
            "CREATE SCHEMA \"my Schema\"");
        assertEquals("create or replace transient schema S_TR;\n", ddl("SCHEMA", "S_TR"));
        assertEquals("create or replace schema S_MA with managed access;\n", ddl("SCHEMA", "S_MA"));
        assertEquals("create or replace schema S_CM COMMENT='it''s here';\n", ddl("SCHEMA", "S_CM"));
        assertEquals("create or replace transient schema S_ALL with managed access COMMENT='both';\n",
            ddl("SCHEMA", "S_ALL"));
        assertEquals("create or replace schema S_RT;\n", ddl("SCHEMA", "S_RT"));
        assertEquals("create or replace schema \"my Schema\";\n", ddl("SCHEMA", "\"my Schema\""));
        assertEquals("create or replace schema TEST_DB.\"my Schema\";\n", ddl("SCHEMA", "\"my Schema\"", "TRUE"));
    }

    @Test
    public void informationSchemaIsItsStatementThenAnEmptyEntryPerView() {
        final String text = ddl("SCHEMA", "INFORMATION_SCHEMA");
        final String head = "create or replace schema INFORMATION_SCHEMA "
            + "COMMENT='Views describing the contents of schemas in this database';\n";
        assertTrue(text.startsWith(head), text);
        final String rest = text.substring(head.length());
        assertTrue(!rest.isEmpty() && rest.replace("\n", "").isEmpty(), rest);
    }

    // ─────────────────────────── schema members ───────────────────────────

    @Test
    public void everyKindFollowsInTheAccountsOrder() {
        run("CREATE SCHEMA s",
            "USE SCHEMA s",
            "CREATE TABLE z_tab (id NUMBER(38,0), name VARCHAR(10))",
            "CREATE TABLE a_tab (x INT)",
            "CREATE TRANSIENT TABLE m_trans (x INT)",
            "CREATE TEMPORARY TABLE k_temp (x INT)",
            "CREATE VIEW y_view AS SELECT ID FROM Z_TAB",
            "CREATE SECURE VIEW b_sview AS SELECT X FROM A_TAB",
            "CREATE SEQUENCE x_seq",
            "CREATE SEQUENCE c_seq START = 3 INCREMENT = 2",
            "CREATE FILE FORMAT w_ff TYPE = CSV",
            "CREATE FILE FORMAT d_ff TYPE = JSON",
            "CREATE STAGE v_stg",
            "CREATE STREAM u_str ON TABLE z_tab",
            "CREATE STREAM e_str ON TABLE a_tab",
            "CREATE TASK t_task SCHEDULE = '60 MINUTE' AS SELECT 1",
            "CREATE TASK f_task SCHEDULE = '60 MINUTE' AS SELECT 2",
            "CREATE FUNCTION s_fn() RETURNS NUMBER AS '1'",
            "CREATE PROCEDURE g_proc() RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN 1; END'",
            "CREATE MASKING POLICY r_mp AS (v VARCHAR) RETURNS VARCHAR -> V",
            "CREATE ROW ACCESS POLICY h_rap AS (v VARCHAR) RETURNS BOOLEAN -> TRUE",
            "CREATE TAG q_tag",
            "CREATE PIPE p_pipe AS COPY INTO A_TAB FROM @V_STG",
            "CREATE ALERT o_alert SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1",
            "CREATE EVENT TABLE n_evt",
            "CREATE AGGREGATION POLICY l_agg AS () RETURNS AGGREGATION_CONSTRAINT -> NO_AGGREGATION_CONSTRAINT()",
            "CREATE PROJECTION POLICY j_proj AS () RETURNS PROJECTION_CONSTRAINT "
                + "-> PROJECTION_CONSTRAINT(ALLOW => TRUE)",
            "CREATE JOIN POLICY i_join AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        final String expected = """
            create or replace schema S;

            create or replace tag Q_TAG ;
            create or replace sequence C_SEQ start with 3 increment by 2 noorder;
            create or replace sequence X_SEQ start with 1 increment by 1 noorder;
            create or replace TEMPORARY TABLE K_TEMP (
            \tX NUMBER(38,0)
            );
            create or replace TABLE A_TAB (
            \tX NUMBER(38,0)
            );
            create or replace TRANSIENT TABLE M_TRANS (
            \tX NUMBER(38,0)
            );
            create or replace event table N_EVT;
            create or replace TABLE Z_TAB (
            \tID NUMBER(38,0),
            \tNAME VARCHAR(10)
            );
            create or replace secure view B_SVIEW(
            \tX
            ) as SELECT X FROM A_TAB;
            create or replace view Y_VIEW(
            \tID
            ) as SELECT ID FROM Z_TAB;
            CREATE OR REPLACE FILE FORMAT D_FF
            \tTYPE = JSON
            \tNULL_IF = ()
            ;
            CREATE OR REPLACE FILE FORMAT W_FF
            ;
            CREATE OR REPLACE PROCEDURE "G_PROC"()
            RETURNS NUMBER(38,0)
            LANGUAGE SQL
            EXECUTE AS OWNER
            AS 'BEGIN RETURN 1; END';
            CREATE OR REPLACE FUNCTION "S_FN"()
            RETURNS NUMBER(38,0)
            LANGUAGE SQL
            AS '1';
            create or replace stream E_STR on table A_TAB;
            create or replace stream U_STR on table Z_TAB;
            create or replace pipe P_PIPE auto_ingest=false as COPY INTO A_TAB FROM @V_STG;
            create or replace task F_TASK
            \tschedule='60 MINUTE'
            \tas SELECT 2;
            create or replace task T_TASK
            \tschedule='60 MINUTE'
            \tas SELECT 1;
            create or replace row access policy H_RAP as (V VARCHAR)\s
            returns BOOLEAN ->
            TRUE
            ;
            create or replace join policy I_JOIN as ()\s
            returns JOIN_CONSTRAINT ->
            JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)
            ;
            create or replace projection policy J_PROJ as ()\s
            returns PROJECTION_CONSTRAINT ->
            PROJECTION_CONSTRAINT(ALLOW => TRUE)
            ;
            create or replace aggregation policy L_AGG as ()\s
            returns AGGREGATION_CONSTRAINT ->
            NO_AGGREGATION_CONSTRAINT()
            ;
            create or replace masking policy R_MP as (V VARCHAR)\s
            returns VARCHAR ->
            V
            ;
            create or replace alert O_ALERT
            \tschedule='60 MINUTE'
            \tif (exists(
            \t\tSELECT 1
            \t))
            \tthen
            \tSELECT 1;""";
        assertEquals(expected, ddl("SCHEMA", "S"));
        // The text does not depend on the session's current schema.
        run("USE SCHEMA test_schema");
        assertEquals(expected, ddl("SCHEMA", "S"));
    }

    @Test
    public void tableLikeRelationsListTemporaryTablesFirstThenTheRestByName() {
        run("CREATE SCHEMA m",
            "USE SCHEMA m",
            "CREATE TABLE y_perm (x INT)",
            "CREATE TABLE \"a_lower\" (x INT)",
            "CREATE TABLE b_perm (x INT)",
            "CREATE TABLE \"_UND\" (x INT)",
            "CREATE TEMPORARY TABLE x_temp (x INT)",
            "CREATE TEMPORARY TABLE c_temp (x INT)",
            "CREATE TRANSIENT TABLE d_trans (x INT)",
            "CREATE EVENT TABLE a_evt",
            "CREATE MATERIALIZED VIEW n_mv AS SELECT X FROM B_PERM",
            "CREATE VIEW \"m view\" AS SELECT 1 AS ONE",
            "CREATE VIEW a_view AS SELECT 2 AS TWO",
            "CREATE DYNAMIC TABLE k_dt TARGET_LAG = DOWNSTREAM WAREHOUSE = COMPUTE_WH INITIALIZE = ON_SCHEDULE "
                + "AS SELECT X FROM B_PERM",
            "CREATE SEQUENCE \"s q\"",
            "CREATE TAG \"t g\"",
            "CREATE TAG a_tag");
        assertEquals("""
            create or replace schema M;

            create or replace tag A_TAG ;
            create or replace tag "t g" ;
            create or replace sequence "s q" start with 1 increment by 1 noorder;
            create or replace TEMPORARY TABLE C_TEMP (
            \tX NUMBER(38,0)
            );
            create or replace TEMPORARY TABLE X_TEMP (
            \tX NUMBER(38,0)
            );
            create or replace event table A_EVT;
            create or replace TABLE B_PERM (
            \tX NUMBER(38,0)
            );
            create or replace TRANSIENT TABLE D_TRANS (
            \tX NUMBER(38,0)
            );
            create or replace dynamic table K_DT(
            \tX
            ) target_lag = 'DOWNSTREAM' refresh_mode = AUTO initialize = ON_SCHEDULE warehouse = COMPUTE_WH
             as SELECT X FROM B_PERM;
            create or replace materialized view N_MV(
            \tX
            ) as SELECT X FROM B_PERM;
            create or replace TABLE Y_PERM (
            \tX NUMBER(38,0)
            );
            create or replace TABLE _UND (
            \tX NUMBER(38,0)
            );
            create or replace TABLE "a_lower" (
            \tX NUMBER(38,0)
            );
            create or replace view A_VIEW(
            \tTWO
            ) as SELECT 2 AS TWO;
            create or replace view "m view"(
            \tONE
            ) as SELECT 1 AS ONE;""", ddl("SCHEMA", "M"));
    }

    @Test
    public void routinesFollowByNameThenByTheirParameterList() {
        run("CREATE SCHEMA r",
            "USE SCHEMA r",
            "CREATE FUNCTION f() RETURNS NUMBER AS '3'",
            "CREATE FUNCTION f(x BOOLEAN) RETURNS NUMBER AS '4'",
            "CREATE FUNCTION f(x DATE) RETURNS NUMBER AS '5'",
            "CREATE FUNCTION f(x NUMBER, y VARCHAR) RETURNS NUMBER AS '6'",
            "CREATE FUNCTION \"f\"() RETURNS NUMBER AS '7'",
            "CREATE FUNCTION \"my fn\"() RETURNS NUMBER AS '8'",
            "CREATE PROCEDURE \"B\"() RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN 4; END'",
            "CREATE PROCEDURE \"_z\"() RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN 5; END'",
            "CREATE PROCEDURE f(a VARCHAR) RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN 6; END'",
            "CREATE FUNCTION g(z NUMBER) RETURNS NUMBER AS '1'",
            "CREATE FUNCTION g(a NUMBER, b NUMBER) RETURNS NUMBER AS '2'",
            "CREATE FUNCTION g(x_1 ARRAY) RETURNS NUMBER AS '3'",
            "CREATE FUNCTION g(x DATE) RETURNS NUMBER AS '4'",
            "CREATE PROCEDURE g(b VARCHAR) RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        final List<String> expected = new ArrayList<String>();
        expected.add("CREATE OR REPLACE PROCEDURE \"B\"()");
        expected.add("CREATE OR REPLACE FUNCTION \"F\"()");
        expected.add("CREATE OR REPLACE PROCEDURE \"F\"(\"A\" VARCHAR)");
        expected.add("CREATE OR REPLACE FUNCTION \"F\"(\"X\" BOOLEAN)");
        expected.add("CREATE OR REPLACE FUNCTION \"F\"(\"X\" DATE)");
        expected.add("CREATE OR REPLACE FUNCTION \"F\"(\"X\" NUMBER(38,0), \"Y\" VARCHAR)");
        expected.add("CREATE OR REPLACE FUNCTION \"G\"(\"A\" NUMBER(38,0), \"B\" NUMBER(38,0))");
        expected.add("CREATE OR REPLACE PROCEDURE \"G\"(\"B\" VARCHAR)");
        expected.add("CREATE OR REPLACE FUNCTION \"G\"(\"X\" DATE)");
        expected.add("CREATE OR REPLACE FUNCTION \"G\"(\"X_1\" ARRAY)");
        expected.add("CREATE OR REPLACE FUNCTION \"G\"(\"Z\" NUMBER(38,0))");
        expected.add("CREATE OR REPLACE PROCEDURE \"_z\"()");
        expected.add("CREATE OR REPLACE FUNCTION \"f\"()");
        expected.add("CREATE OR REPLACE FUNCTION \"my fn\"()");
        assertEquals(expected, routineHeads(ddl("SCHEMA", "R")));
        assertTrue(ddl("SCHEMA", "R").startsWith("""
            create or replace schema R;

            CREATE OR REPLACE PROCEDURE "B"()
            RETURNS NUMBER(38,0)
            LANGUAGE SQL
            EXECUTE AS OWNER
            AS 'BEGIN RETURN 4; END';
            CREATE OR REPLACE FUNCTION "F"()
            RETURNS NUMBER(38,0)
            LANGUAGE SQL
            AS '3';
            """), ddl("SCHEMA", "R"));
    }

    @Test
    public void qualifiedNamesSpellTheDatabaseAndSchemaOfEveryRecreatedObject() {
        run("CREATE SCHEMA q",
            "USE SCHEMA q",
            "CREATE TABLE t (x INT)",
            "CREATE VIEW \"m view\" AS SELECT X FROM T",
            "CREATE SEQUENCE \"s q\"",
            "CREATE STREAM st ON TABLE t",
            "CREATE FUNCTION \"_z\"() RETURNS NUMBER AS '1'",
            "CREATE PROCEDURE b() RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN 4; END'",
            "CREATE FUNCTION f(x BOOLEAN) RETURNS NUMBER AS '4'",
            "CREATE TASK tk1 SCHEDULE = '60 MINUTE' AS SELECT 1",
            "CREATE TASK tk2 AFTER tk1 AS SELECT 2");
        assertEquals("""
            create or replace schema TEST_DB.Q;

            create or replace sequence TEST_DB.Q."s q" start with 1 increment by 1 noorder;
            create or replace TABLE TEST_DB.Q.T (
            \tX NUMBER(38,0)
            );
            create or replace view TEST_DB.Q."m view"(
            \tX
            ) as SELECT X FROM T;
            CREATE OR REPLACE PROCEDURE TEST_DB.Q.B()
            RETURNS NUMBER(38,0)
            LANGUAGE SQL
            EXECUTE AS OWNER
            AS 'BEGIN RETURN 4; END';
            CREATE OR REPLACE FUNCTION TEST_DB.Q.F("X" BOOLEAN)
            RETURNS NUMBER(38,0)
            LANGUAGE SQL
            AS '4';
            CREATE OR REPLACE FUNCTION TEST_DB.Q."_z"()
            RETURNS NUMBER(38,0)
            LANGUAGE SQL
            AS '1';
            create or replace stream TEST_DB.Q.ST on table T;
            create or replace task TEST_DB.Q.TK1
            \tschedule='60 MINUTE'
            \tas SELECT 1;
            create or replace task TEST_DB.Q.TK2
            \tafter TEST_DB.Q.TK1
            \tas SELECT 2;""", ddl("SCHEMA", "Q", "TRUE"));
        // A single object is qualified the same way.
        assertEquals("create or replace TABLE TEST_DB.Q.T (\n\tX NUMBER(38,0)\n);", ddl("TABLE", "T", "TRUE"));
        assertEquals("CREATE OR REPLACE FUNCTION TEST_DB.Q.\"_z\"()\nRETURNS NUMBER(38,0)\nLANGUAGE SQL\nAS '1';",
            ddl("FUNCTION", "\"_z\"()", "TRUE"));
    }

    // ─────────────────────────── database ───────────────────────────

    @Test
    public void aDatabaseListsEverySchemaByNameButInformationSchema() {
        run("CREATE DATABASE ddl_listing_db",
            "CREATE SCHEMA ddl_listing_db.n2",
            "CREATE TABLE ddl_listing_db.n2.t (x INT)",
            "CREATE SCHEMA ddl_listing_db.\"a2\"",
            "CREATE SCHEMA ddl_listing_db.\"_b\"",
            "CREATE SCHEMA ddl_listing_db.z9 COMMENT = 'zz'",
            "ALTER DATABASE ddl_listing_db SET COMMENT = 'd''b'");
        assertEquals("""
            create or replace database DDL_LISTING_DB COMMENT='d''b';

            create or replace schema N2;

            create or replace TABLE T (
            \tX NUMBER(38,0)
            );
            create or replace schema PUBLIC;

            create or replace schema Z9 COMMENT='zz';

            create or replace schema "_b";

            create or replace schema "a2";
            """, ddl("DATABASE", "ddl_listing_db"));
        assertEquals("""
            create or replace database DDL_LISTING_DB COMMENT='d''b';

            create or replace schema DDL_LISTING_DB.N2;

            create or replace TABLE DDL_LISTING_DB.N2.T (
            \tX NUMBER(38,0)
            );
            create or replace schema DDL_LISTING_DB.PUBLIC;

            create or replace schema DDL_LISTING_DB.Z9 COMMENT='zz';

            create or replace schema DDL_LISTING_DB."_b";

            create or replace schema DDL_LISTING_DB."a2";
            """, ddl("DATABASE", "DDL_LISTING_DB", "TRUE"));
    }

    @Test
    public void aTransientDatabaseAndItsSchemasSayTheyAreTransient() {
        run("CREATE TRANSIENT DATABASE ddl_transient_db COMMENT = 'tdb'");
        assertEquals("""
            create or replace transient database DDL_TRANSIENT_DB COMMENT='tdb';

            create or replace transient schema PUBLIC;
            """, ddl("DATABASE", "DDL_TRANSIENT_DB"));
    }

    // ─────────────────────────── refusals ───────────────────────────

    @Test
    public void aMissingContainerIsRefusedAsMissing() {
        assertEquals(hinted("SQL compilation error:\nSchema 'TEST_DB.NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('SCHEMA', 'NOPE')"));
        assertEquals(hinted("SQL compilation error:\nSchema 'TEST_DB.\"s_empty\"' does not exist or not authorized."),
            refusal("SELECT GET_DDL('SCHEMA', '\"s_empty\"')"));
        assertEquals(hinted("SQL compilation error:\nDatabase 'NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('SCHEMA', 'NOPE.S_EMPTY')"));
        assertEquals(hinted("SQL compilation error:\nDatabase 'NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('DATABASE', 'NOPE')"));
        assertEquals(hinted("SQL compilation error:\nDatabase '\"nope\"' does not exist or not authorized."),
            refusal("SELECT GET_DDL('DATABASE', '\"nope\"')"));
        assertEquals("SQL compilation error:\nObject does not exist, or operation cannot be performed.",
            refusal("SELECT GET_DDL('SCHEMA', 'A.B.C')"));
        assertEquals("SQL compilation error:\nObject does not exist, or operation cannot be performed.",
            refusal("SELECT GET_DDL('DATABASE', 'A.B')"));
    }

    @Test
    public void anObjectTypeIsReadUpperCasedWithASpaceAsAnUnderscore() {
        run("CREATE FILE FORMAT ff TYPE = JSON");
        assertEquals("CREATE OR REPLACE FILE FORMAT FF\n\tTYPE = JSON\n\tNULL_IF = ()\n;", ddl("file format", "FF"));
        assertEquals("SQL compilation error:\nInvalid object type: ' SCHEMA '",
            refusal("SELECT GET_DDL(' Schema ', 'TEST_SCHEMA')"));
        assertEquals("SQL compilation error:\nInvalid object type: 'MASKING_POLICY'",
            refusal("SELECT GET_DDL('MASKING_POLICY', 'X')"));
        assertEquals("SQL compilation error:\nInvalid object type: 'PIPEX'", refusal("SELECT GET_DDL('PIPEX', 'X')"));
        assertEquals("Unsupported feature 'EVENT_TABLE'.", refusal("SELECT GET_DDL('EVENT_TABLE', 'X')"));
        assertEquals("Unsupported feature 'EVENT_TABLE'.", refusal("SELECT GET_DDL('Event Table', 'X')"));
    }

    @Test
    public void aMissingObjectIsEchoedBareOrInFullByItsKind() {
        run("CREATE TABLE t (x INT)");
        assertEquals(hinted("SQL compilation error:\nTable 'NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('TABLE', 'NOPE')"));
        assertEquals(hinted("SQL compilation error:\nTable 'TEST_DB.TEST_SCHEMA.NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('TABLE', 'TEST_SCHEMA.NOPE')"));
        assertEquals(hinted("SQL compilation error:\nTable '\"n\"\"o\"' does not exist or not authorized."),
            refusal("SELECT GET_DDL('TABLE', '\"n\"\"o\"')"));
        assertEquals(hinted("SQL compilation error:\nView 'NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('VIEW', 'NOPE')"));
        assertEquals(hinted("SQL compilation error:\nSequence 'NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('SEQUENCE', 'NOPE')"));
        assertEquals(hinted("SQL compilation error:\nStream 'NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('STREAM', 'NOPE')"));
        assertEquals(hinted("SQL compilation error:\nTask 'NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('TASK', 'NOPE')"));
        assertEquals(hinted("SQL compilation error:\nTag 'NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('TAG', 'nope')"));
        assertEquals(hinted("SQL compilation error:\nFile format 'NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('FILE_FORMAT', 'NOPE')"));
        assertEquals(hinted("SQL compilation error:\nAlert 'NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('ALERT', 'NOPE')"));
        assertEquals(hinted("SQL compilation error:\nPipe 'TEST_DB.TEST_SCHEMA.T' does not exist or not authorized."),
            refusal("SELECT GET_DDL('PIPE', 'T')"));
        assertEquals(hinted("SQL compilation error:\nPolicy 'TEST_DB.TEST_SCHEMA.NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('POLICY', 'NOPE')"));
        assertEquals(hinted("SQL compilation error:\n"
            + "Dynamic table 'TEST_DB.TEST_SCHEMA.NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('DYNAMIC_TABLE', 'NOPE')"));
        assertEquals(hinted("SQL compilation error:\nContact 'TEST_DB.TEST_SCHEMA.NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('CONTACT', 'NOPE')"));
        assertEquals(hinted("SQL compilation error:\n"
            + "Streamlit 'TEST_DB.TEST_SCHEMA.NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('STREAMLIT', 'NOPE')"));
    }

    // ─────────────────────────── single objects ───────────────────────────

    @Test
    public void tableViewAndDynamicTableNameWhicheverRelationHoldsTheName() {
        run("CREATE TABLE b_perm (x INT)",
            "CREATE SECURE VIEW b_sview COMMENT = 'v c' AS SELECT X FROM B_PERM",
            "CREATE MATERIALIZED VIEW n_mv AS SELECT X FROM B_PERM",
            "CREATE SECURE MATERIALIZED VIEW n_smv COMMENT = 'mv c' AS SELECT X FROM B_PERM");
        final String view = "create or replace secure view B_SVIEW(\n\tX\n) COMMENT='v c'\n as SELECT X FROM B_PERM;";
        assertEquals(view, ddl("VIEW", "B_SVIEW"));
        assertEquals(view, ddl("TABLE", "B_SVIEW"));
        final String materialized = "create or replace materialized view N_MV(\n\tX\n) as SELECT X FROM B_PERM;";
        assertEquals(materialized, ddl("TABLE", "N_MV"));
        assertEquals(materialized, ddl("VIEW", "N_MV"));
        assertEquals(materialized, ddl("MATERIALIZED_VIEW", "N_MV"));
        assertEquals(materialized, ddl("MATERIALIZED VIEW", "N_MV"));
        assertEquals("create or replace secure materialized view N_SMV(\n\tX\n) COMMENT='mv c'\n as SELECT X FROM B_PERM;",
            ddl("VIEW", "N_SMV"));
        assertEquals("create or replace TABLE B_PERM (\n\tX NUMBER(38,0)\n);", ddl("DYNAMIC_TABLE", "B_PERM"));
    }

    @Test
    public void aDynamicTableSpellsItsSettingsCommentAndClusteringKey() {
        run("CREATE SCHEMA other",
            "CREATE TABLE other.src (id INT)",
            "USE SCHEMA test_schema",
            "CREATE DYNAMIC TABLE dtc TARGET_LAG = '5 minutes' WAREHOUSE = COMPUTE_WH REFRESH_MODE = FULL "
                + "INITIALIZE = ON_SCHEDULE COMMENT = 'dy''n' AS SELECT ID FROM OTHER.SRC",
            "CREATE TRANSIENT DYNAMIC TABLE dtt TARGET_LAG = '1 hour' WAREHOUSE = COMPUTE_WH "
                + "INITIALIZE = ON_SCHEDULE CLUSTER BY (id) AS SELECT ID FROM OTHER.SRC");
        assertEquals("""
            create or replace dynamic table DTC(
            \tID
            ) target_lag = '5 minutes' refresh_mode = FULL initialize = ON_SCHEDULE warehouse = COMPUTE_WH
             COMMENT='dy''n'
             as SELECT ID FROM OTHER.SRC;""", ddl("DYNAMIC_TABLE", "DTC"));
        assertEquals("""
            create or replace transient dynamic table DTT(
            \tID
            ) target_lag = '1 hour' refresh_mode = AUTO initialize = ON_SCHEDULE warehouse = COMPUTE_WH
             cluster by (id) as SELECT ID FROM OTHER.SRC;""", ddl("TABLE", "DTT"));
    }

    @Test
    public void aTableSpellsItsKindClusteringKeyCommentAndTags() {
        run("CREATE TEMPORARY TABLE c_temp (x INT)",
            "CREATE TRANSIENT TABLE d_trans (x INT)",
            "CREATE TABLE tc (a INT COMMENT 'col c', b VARCHAR(5) DEFAULT 'x') COMMENT = 'tab''le' CLUSTER BY (a) "
                + "DATA_RETENTION_TIME_IN_DAYS = 3 CHANGE_TRACKING = TRUE",
            "CREATE TABLE tcom (a INT) COMMENT = 'only c'",
            "CREATE TABLE tcl (a INT, b INT) CLUSTER BY (a, b)",
            "CREATE TRANSIENT TABLE ttc (a INT) CLUSTER BY (a) COMMENT = 'tt'",
            "CREATE TABLE tq (a INT COMMENT 'it''s')",
            "CREATE EVENT TABLE evc COMMENT = 'ev''t' CHANGE_TRACKING = TRUE",
            "CREATE EVENT TABLE evt",
            "CREATE TAG tg",
            "CREATE TABLE tct (a INT PRIMARY KEY) COMMENT = 'ct' WITH TAG (tg = 'x')",
            "CREATE TABLE tt (a INT) WITH TAG (tg = 'x')",
            "CREATE SCHEMA s2",
            "CREATE TAG s2.tc",
            "CREATE TABLE test_schema.ty (a INT) WITH TAG (s2.tc = 'z')",
            "USE SCHEMA test_schema",
            "CREATE TRANSIENT SCHEMA ts",
            "CREATE TABLE ts.t (x INT)",
            "USE SCHEMA test_schema");
        assertEquals("create or replace TEMPORARY TABLE C_TEMP (\n\tX NUMBER(38,0)\n);", ddl("TABLE", "C_TEMP"));
        assertEquals("create or replace TRANSIENT TABLE D_TRANS (\n\tX NUMBER(38,0)\n);", ddl("TABLE", "D_TRANS"));
        assertEquals("""
            create or replace TABLE TC cluster by (a)(
            \tA NUMBER(38,0) COMMENT 'col c',
            \tB VARCHAR(5) DEFAULT 'x'
            )COMMENT='tab''le'
            ;""", ddl("TABLE", "TC"));
        assertEquals("create or replace TABLE TCOM (\n\tA NUMBER(38,0)\n)COMMENT='only c'\n;", ddl("TABLE", "TCOM"));
        // A clustering key is spelled as written.
        assertEquals("create or replace TABLE TCL cluster by (a, b)(\n\tA NUMBER(38,0),\n\tB NUMBER(38,0)\n);",
            ddl("TABLE", "TCL"));
        assertEquals("create or replace TRANSIENT TABLE TTC cluster by (a)(\n\tA NUMBER(38,0)\n)COMMENT='tt'\n;",
            ddl("TABLE", "TTC"));
        assertEquals("create or replace TABLE TQ (\n\tA NUMBER(38,0) COMMENT 'it''s'\n);", ddl("TABLE", "TQ"));
        assertEquals("create or replace event table EVC COMMENT='ev''t'\n;", ddl("TABLE", "EVC"));
        assertEquals("create or replace event table EVT;", ddl("TABLE", "EVT"));
        assertEquals("""
            create or replace TABLE TCT (
            \tA NUMBER(38,0) NOT NULL,
            \tprimary key (A)
            ) WITH TAG (TEST_DB.TEST_SCHEMA.TG='x')
            COMMENT='ct'
            ;""", ddl("TABLE", "TCT"));
        assertEquals("create or replace TABLE TT (\n\tA NUMBER(38,0)\n) WITH TAG (TEST_DB.TEST_SCHEMA.TG='x')\n;",
            ddl("TABLE", "TT"));
        assertEquals("create or replace TABLE TY (\n\tA NUMBER(38,0)\n) WITH TAG (TEST_DB.S2.TC='z')\n;",
            ddl("TABLE", "TY"));
        assertEquals("create or replace TRANSIENT TABLE T (\n\tX NUMBER(38,0)\n);", ddl("TABLE", "TS.T"));
    }

    @Test
    public void aViewSpellsItsCommentsKindAndTags() {
        run("CREATE VIEW vc (x COMMENT 'vx', y) COMMENT = 'vi''ew' AS SELECT 1, 2",
            "CREATE VIEW vcol (x COMMENT 'it''s') AS SELECT 1",
            "CREATE RECURSIVE VIEW vr (n) AS SELECT 1 UNION ALL SELECT N + 1 FROM VR WHERE N < 3",
            "CREATE SECURE RECURSIVE VIEW srv (n) COMMENT = 'sr' AS SELECT 1",
            "CREATE TAG ta",
            "CREATE VIEW vx COMMENT = 'vx' WITH TAG (ta = 'v') AS SELECT 1 AS ONE");
        assertEquals("create or replace view VC(\n\tX COMMENT 'vx',\n\tY\n) COMMENT='vi''ew'\n as SELECT 1, 2;",
            ddl("VIEW", "VC"));
        assertEquals("create or replace view VCOL(\n\tX COMMENT 'it''s'\n) as SELECT 1;", ddl("VIEW", "VCOL"));
        assertEquals("create or replace recursive view VR(\n\tN\n) as SELECT 1 UNION ALL SELECT N + 1 FROM VR WHERE N < 3;",
            ddl("VIEW", "VR"));
        assertEquals("create or replace secure recursive view SRV(\n\tN\n) COMMENT='sr'\n as SELECT 1;",
            ddl("VIEW", "SRV"));
        assertEquals("""
            create or replace view VX(
            \tONE
            ) WITH TAG (TEST_DB.TEST_SCHEMA.TA='v')
             COMMENT='vx'
             as SELECT 1 AS ONE;""", ddl("VIEW", "VX"));
    }

    @Test
    public void aSequenceLeavesItsCommentOut() {
        run("CREATE SEQUENCE sqc START = 10 INCREMENT = -5 ORDER COMMENT = 'se''q'");
        assertEquals("create or replace sequence SQC start with 10 increment by -5 order;", ddl("SEQUENCE", "SQC"));
    }

    @Test
    public void aTagSpellsItsAllowedValuesAndComment() {
        run("CREATE TAG tg1 ALLOWED_VALUES 'a', 'b''c' COMMENT = 'ta''g'",
            "CREATE TAG tg2 COMMENT = 'only'",
            "CREATE TAG tg_a ALLOWED_VALUES 'x', 'y'",
            "CREATE TAG q_tag");
        assertEquals("create or replace tag TG1  allowed_values  'a' , 'b''c' COMMENT='ta''g'\n;", ddl("TAG", "TG1"));
        assertEquals("create or replace tag TG2 COMMENT='only'\n;", ddl("TAG", "TG2"));
        assertEquals("create or replace tag TG_A  allowed_values  'x' , 'y' ;", ddl("TAG", "TG_A"));
        assertEquals("create or replace tag Q_TAG ;", ddl("TAG", "Q_TAG"));
    }

    @Test
    public void aFileFormatSpellsTheOptionsThatDifferFromTheDefaults() {
        run("CREATE FILE FORMAT ff_csv1 TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 1 NULL_IF = ('NULL', '') "
                + "COMPRESSION = GZIP FIELD_OPTIONALLY_ENCLOSED_BY = '\"' TRIM_SPACE = TRUE COMMENT = 'c''sv'",
            "CREATE FILE FORMAT ff_csv2 RECORD_DELIMITER = ';' DATE_FORMAT = 'YYYY-MM-DD' ESCAPE = '\\\\' "
                + "ESCAPE_UNENCLOSED_FIELD = NONE ERROR_ON_COLUMN_COUNT_MISMATCH = FALSE EMPTY_FIELD_AS_NULL = FALSE "
                + "ENCODING = 'UTF-16' PARSE_HEADER = TRUE BINARY_FORMAT = BASE64 SKIP_BLANK_LINES = TRUE "
                + "FILE_EXTENSION = '.txt' NULL_IF = ()",
            "CREATE FILE FORMAT ff_csv3 FIELD_DELIMITER = ',' SKIP_HEADER = 0 COMPRESSION = AUTO NULL_IF = ('\\\\N') "
                + "TRIM_SPACE = FALSE",
            "CREATE FILE FORMAT ffx FIELD_DELIMITER = '\\t' RECORD_DELIMITER = '\\n' FIELD_OPTIONALLY_ENCLOSED_BY = '\\'' "
                + "NULL_IF = ('it''s', 'a\\\\b') COMMENT = 'a\\\\b''c'",
            "CREATE FILE FORMAT ff_json1 TYPE = JSON STRIP_OUTER_ARRAY = TRUE COMPRESSION = NONE NULL_IF = ('x') "
                + "ALLOW_DUPLICATE = TRUE",
            "CREATE FILE FORMAT ff_json2 TYPE = JSON NULL_IF = ('\\\\N')",
            "CREATE FILE FORMAT ff_pq TYPE = PARQUET",
            "CREATE FILE FORMAT ff_xml TYPE = XML STRIP_OUTER_ELEMENT = TRUE",
            "CREATE FILE FORMAT ff_avro TYPE = AVRO",
            "CREATE FILE FORMAT ff_orc TYPE = ORC TRIM_SPACE = TRUE");
        assertEquals("""
            CREATE OR REPLACE FILE FORMAT FF_CSV1
            \tFIELD_DELIMITER = '|'
            \tSKIP_HEADER = 1
            \tTRIM_SPACE = TRUE
            \tFIELD_OPTIONALLY_ENCLOSED_BY = '\\"'
            \tNULL_IF = ('NULL', '')
            \tCOMPRESSION = GZIP
            COMMENT='c''sv'
            ;""", ddl("FILE_FORMAT", "FF_CSV1"));
        assertEquals("""
            CREATE OR REPLACE FILE FORMAT FF_CSV2
            \tRECORD_DELIMITER = ';'
            \tFILE_EXTENSION = '.txt'
            \tPARSE_HEADER = TRUE
            \tDATE_FORMAT = 'YYYY-MM-DD'
            \tBINARY_FORMAT = 'BASE64'
            \tESCAPE = '\\\\'
            \tESCAPE_UNENCLOSED_FIELD = 'NONE'
            \tNULL_IF = ()
            \tERROR_ON_COLUMN_COUNT_MISMATCH = FALSE
            \tSKIP_BLANK_LINES = TRUE
            \tEMPTY_FIELD_AS_NULL = FALSE
            \tENCODING = 'UTF-16'
            ;""", ddl("FILE_FORMAT", "FF_CSV2"));
        assertEquals("CREATE OR REPLACE FILE FORMAT FF_CSV3\n;", ddl("FILE_FORMAT", "FF_CSV3"));
        assertEquals("""
            CREATE OR REPLACE FILE FORMAT FFX
            \tFIELD_DELIMITER = '\\t'
            \tFIELD_OPTIONALLY_ENCLOSED_BY = ''''
            \tNULL_IF = ('it''s', 'a\\\\b')
            COMMENT='a\\\\b''c'
            ;""", ddl("FILE_FORMAT", "FFX"));
        assertEquals("""
            CREATE OR REPLACE FILE FORMAT FF_JSON1
            \tTYPE = JSON
            \tNULL_IF = ('x')
            \tCOMPRESSION = NONE
            \tALLOW_DUPLICATE = TRUE
            \tSTRIP_OUTER_ARRAY = TRUE
            ;""", ddl("FILE_FORMAT", "FF_JSON1"));
        assertEquals("CREATE OR REPLACE FILE FORMAT FF_JSON2\n\tTYPE = JSON\n;", ddl("FILE_FORMAT", "FF_JSON2"));
        assertEquals("CREATE OR REPLACE FILE FORMAT FF_PQ\n\tTYPE = PARQUET\n\tNULL_IF = ()\n;", ddl("FILE_FORMAT", "FF_PQ"));
        assertEquals("CREATE OR REPLACE FILE FORMAT FF_XML\n\tTYPE = XML\n\tSTRIP_OUTER_ELEMENT = TRUE\n;",
            ddl("FILE_FORMAT", "FF_XML"));
        assertEquals("CREATE OR REPLACE FILE FORMAT FF_AVRO\n\tTYPE = AVRO\n\tNULL_IF = ()\n;", ddl("FILE_FORMAT", "FF_AVRO"));
        assertEquals("CREATE OR REPLACE FILE FORMAT FF_ORC\n\tTYPE = ORC\n\tTRIM_SPACE = TRUE\n\tNULL_IF = ()\n;",
            ddl("FILE_FORMAT", "FF_ORC"));
    }

    @Test
    public void streamsAndPipesSpellTheirSourceAndOptions() {
        run("CREATE TABLE t1 (id INT, v VARCHAR)",
            "CREATE VIEW v1 AS SELECT ID FROM T1",
            "CREATE SCHEMA other",
            "CREATE TABLE other.src (id INT)",
            "CREATE TABLE test_schema.\"Mixed Src\" (id INT)",
            "USE SCHEMA test_schema",
            "CREATE STREAM s_app ON TABLE t1 APPEND_ONLY = TRUE SHOW_INITIAL_ROWS = TRUE COMMENT = 'str''m'",
            "CREATE STREAM s_view ON VIEW v1",
            "CREATE STREAM s_false ON TABLE t1 APPEND_ONLY = FALSE SHOW_INITIAL_ROWS = FALSE",
            "CREATE STREAM s_other ON TABLE other.src",
            "CREATE STREAM s_mixed ON TABLE \"Mixed Src\"",
            "CREATE STAGE stg1",
            "CREATE PIPE p1 COMMENT = 'pi''pe' AS COPY INTO T1 FROM @STG1 FILE_FORMAT = (TYPE = CSV)",
            "CREATE PIPE p2 AUTO_INGEST = FALSE AS COPY INTO T1 (ID, V) FROM (SELECT $1, $2 FROM @STG1)");
        assertEquals("create or replace stream S_APP on table T1 append_only = true;", ddl("STREAM", "S_APP"));
        assertEquals("create or replace stream S_VIEW on view V1;", ddl("STREAM", "S_VIEW"));
        assertEquals("create or replace stream S_FALSE on table T1;", ddl("STREAM", "S_FALSE"));
        assertEquals("create or replace stream S_OTHER on table SRC;", ddl("STREAM", "S_OTHER"));
        assertEquals("create or replace stream S_MIXED on table \"Mixed Src\";", ddl("STREAM", "S_MIXED"));
        assertEquals("create or replace pipe P1 auto_ingest=false comment='pi\\'pe' as COPY INTO T1 FROM @STG1 "
            + "FILE_FORMAT = (TYPE = CSV);", ddl("PIPE", "P1"));
        assertEquals("create or replace pipe P2 auto_ingest=false as COPY INTO T1 (ID, V) FROM (SELECT $1, $2 FROM @STG1);",
            ddl("PIPE", "P2"));
    }

    @Test
    public void aTaskSpellsItsWarehouseScheduleParametersCommentAndPredecessors() {
        run("CREATE TABLE t1 (id INT, v VARCHAR)",
            "CREATE STREAM s_app ON TABLE t1 APPEND_ONLY = TRUE",
            "CREATE TASK tk1 WAREHOUSE = COMPUTE_WH SCHEDULE = 'USING CRON 0 9 * * * UTC' COMMENT = 'ta''sk' "
                + "AS INSERT INTO T1 VALUES (1, 'a')",
            "CREATE TASK tk2 WAREHOUSE = COMPUTE_WH AFTER tk1 WHEN SYSTEM$STREAM_HAS_DATA('S_APP') AS SELECT 2",
            "CREATE TASK tk3 SCHEDULE = '5 MINUTE' USER_TASK_TIMEOUT_MS = 1000 SUSPEND_TASK_AFTER_NUM_FAILURES = 3 "
                + "ALLOW_OVERLAPPING_EXECUTION = TRUE USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL' "
                + "TASK_AUTO_RETRY_ATTEMPTS = 2 AS SELECT 3",
            "CREATE TASK tk5 AFTER tk1, tk2 AS SELECT 5",
            "CREATE TASK child_t WAREHOUSE = COMPUTE_WH COMMENT = 'kid' USER_TASK_TIMEOUT_MS = 7000 AFTER tk3 "
                + "WHEN 1 = 1 AS SELECT 2",
            "CREATE TASK t3 SCHEDULE = '15 MINUTE' SUSPEND_TASK_AFTER_NUM_FAILURES = 10 USER_TASK_TIMEOUT_MS = 3600000 "
                + "AS SELECT 3");
        assertEquals("""
            create or replace task TK1
            \twarehouse=COMPUTE_WH
            \tschedule='USING CRON 0 9 * * * UTC'
            \tCOMMENT='ta''sk'
            \tas INSERT INTO T1 VALUES (1, 'a');""", ddl("TASK", "TK1"));
        assertEquals("""
            create or replace task TK2
            \twarehouse=COMPUTE_WH
            \tafter TEST_DB.TEST_SCHEMA.TK1
            \twhen SYSTEM$STREAM_HAS_DATA('S_APP')
            \tas SELECT 2;""", ddl("TASK", "TK2"));
        assertEquals("""
            create or replace task TK3
            \tschedule='5 MINUTE'
            \tUSER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE='XSMALL'
            \tSUSPEND_TASK_AFTER_NUM_FAILURES=3
            \tUSER_TASK_TIMEOUT_MS=1000
            \tTASK_AUTO_RETRY_ATTEMPTS=2
            \tallow_overlapping_execution=true
            \tas SELECT 3;""", ddl("TASK", "TK3"));
        assertEquals("""
            create or replace task TK5
            \tafter TEST_DB.TEST_SCHEMA.TK1, TEST_DB.TEST_SCHEMA.TK2
            \tas SELECT 5;""", ddl("TASK", "TK5"));
        assertEquals("""
            create or replace task CHILD_T
            \twarehouse=COMPUTE_WH
            \tUSER_TASK_TIMEOUT_MS=7000
            \tCOMMENT='kid'
            \tafter TEST_DB.TEST_SCHEMA.TK3
            \twhen 1 = 1
            \tas SELECT 2;""", ddl("TASK", "CHILD_T"));
        // A parameter set to its default value is still spelled.
        assertEquals("""
            create or replace task T3
            \tschedule='15 MINUTE'
            \tSUSPEND_TASK_AFTER_NUM_FAILURES=10
            \tUSER_TASK_TIMEOUT_MS=3600000
            \tas SELECT 3;""", ddl("TASK", "T3"));
    }

    @Test
    public void aTaskSpellsItsConfigAfterTheSchedule() {
        run("CREATE TASK root_t WAREHOUSE = COMPUTE_WH SCHEDULE = '15 MINUTE' COMMENT = 'root' CONFIG = $${\"k\": \"v\"}$$ "
                + "ALLOW_OVERLAPPING_EXECUTION = TRUE USER_TASK_TIMEOUT_MS = 5000 SUSPEND_TASK_AFTER_NUM_FAILURES = 2 "
                + "AS SELECT 1",
            "CREATE TASK tq1 SCHEDULE = '60 MINUTE' CONFIG = $${\"a\": \"it's\", \"b\": \"c\\\\d\"}$$ AS SELECT 1",
            "CREATE TASK tq2 SCHEDULE = '60 MINUTE' CONFIG = '{\"x\": [1, 2]}' COMMENT = 'c' AS SELECT 1",
            "CREATE TASK tq3 SCHEDULE = '60 MINUTE' OVERLAP_POLICY = ALLOW_ALL_OVERLAP AS SELECT 1");
        assertEquals("""
            create or replace task ROOT_T
            \twarehouse=COMPUTE_WH
            \tschedule='15 MINUTE'
            \tconfig='{"k": "v"}'
            \tSUSPEND_TASK_AFTER_NUM_FAILURES=2
            \tUSER_TASK_TIMEOUT_MS=5000
            \tCOMMENT='root'
            \tallow_overlapping_execution=true
            \tas SELECT 1;""", ddl("TASK", "ROOT_T"));
        // The text as written, a single quote and a backslash each doubled.
        assertEquals("""
            create or replace task TQ1
            \tschedule='60 MINUTE'
            \tconfig='{"a": "it''s", "b": "c\\\\\\\\d"}'
            \tas SELECT 1;""", ddl("TASK", "TQ1"));
        assertEquals("""
            create or replace task TQ2
            \tschedule='60 MINUTE'
            \tconfig='{"x": [1, 2]}'
            \tCOMMENT='c'
            \tas SELECT 1;""", ddl("TASK", "TQ2"));
        run("ALTER TASK tq2 UNSET CONFIG", "ALTER TASK tq3 SET CONFIG = '{\"n\": null}'");
        assertEquals("""
            create or replace task TQ2
            \tschedule='60 MINUTE'
            \tCOMMENT='c'
            \tas SELECT 1;""", ddl("TASK", "TQ2"));
        assertEquals("""
            create or replace task TQ3
            \tschedule='60 MINUTE'
            \tconfig='{"n": null}'
            \tas SELECT 1;""", ddl("TASK", "TQ3"));
    }

    @Test
    public void anAlertSpellsItsConditionAndActionButNoComment() {
        run("CREATE TABLE t1 (id INT, v VARCHAR)",
            "CREATE ALERT a1 WAREHOUSE = COMPUTE_WH SCHEDULE = '1 MINUTE' COMMENT = 'al''ert' "
                + "IF (EXISTS (SELECT ID FROM T1 WHERE ID > 5)) THEN INSERT INTO T1 VALUES (9, 'x')",
            "CREATE ALERT a2 SCHEDULE = 'USING CRON 0 * * * * UTC' IF (EXISTS (SELECT 1)) "
                + "THEN BEGIN SELECT 1; SELECT 2; END");
        assertEquals("""
            create or replace alert A1
            \twarehouse=COMPUTE_WH
            \tschedule='1 MINUTE'
            \tif (exists(
            \t\tSELECT ID FROM T1 WHERE ID > 5
            \t))
            \tthen
            \tINSERT INTO T1 VALUES (9, 'x');""", ddl("ALERT", "A1"));
        assertEquals("""
            create or replace alert A2
            \tschedule='USING CRON 0 * * * * UTC'
            \tif (exists(
            \t\tSELECT 1
            \t))
            \tthen
            \tBEGIN SELECT 1; SELECT 2; END;""", ddl("ALERT", "A2"));
    }

    @Test
    public void everyPolicyKindIsOnePolicyNamespace() {
        run("CREATE MASKING POLICY mp1 AS (v VARCHAR, w NUMBER) RETURNS VARCHAR ->\n"
                + "  CASE WHEN W > 0 THEN V\n       ELSE '***' END\n  COMMENT = 'ma''sk'",
            "CREATE ROW ACCESS POLICY rap1 AS (a VARCHAR, b NUMBER) RETURNS BOOLEAN -> A = 'x' AND B > 1 "
                + "COMMENT = 'ro''w'",
            "CREATE AGGREGATION POLICY agg1 AS () RETURNS AGGREGATION_CONSTRAINT "
                + "-> AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5) COMMENT = 'ag''g'",
            "CREATE PROJECTION POLICY prj1 AS () RETURNS PROJECTION_CONSTRAINT "
                + "-> PROJECTION_CONSTRAINT(ALLOW => FALSE) COMMENT = 'pr'",
            "CREATE JOIN POLICY jn1 AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE) "
                + "COMMENT = 'jn'",
            "CREATE PASSWORD POLICY pwc PASSWORD_MIN_LENGTH = 20 COMMENT = 'pw''d'");
        assertEquals("""
            create or replace masking policy MP1 as (V VARCHAR, W NUMBER(38,0))\s
            returns VARCHAR ->
            CASE WHEN W > 0 THEN V
                   ELSE '***' END
            COMMENT='ma''sk'
            ;""", ddl("POLICY", "MP1"));
        assertEquals("""
            create or replace row access policy RAP1 as (A VARCHAR, B NUMBER(38,0))\s
            returns BOOLEAN ->
            A = 'x' AND B > 1
            COMMENT='ro''w'
            ;""", ddl("POLICY", "RAP1"));
        assertEquals("""
            create or replace aggregation policy AGG1 as ()\s
            returns AGGREGATION_CONSTRAINT ->
            AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)
            COMMENT='ag''g'
            ;""", ddl("POLICY", "AGG1"));
        assertEquals("""
            create or replace projection policy PRJ1 as ()\s
            returns PROJECTION_CONSTRAINT ->
            PROJECTION_CONSTRAINT(ALLOW => FALSE)
            COMMENT='pr'
            ;""", ddl("POLICY", "PRJ1"));
        assertEquals("""
            create or replace join policy JN1 as ()\s
            returns JOIN_CONSTRAINT ->
            JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)
            COMMENT='jn'
            ;""", ddl("POLICY", "JN1"));
        assertEquals("create or replace password policy PWC PASSWORD_MIN_LENGTH=20 PASSWORD_MAX_LENGTH=256 "
            + "PASSWORD_MIN_UPPER_CASE_CHARS=1 PASSWORD_MIN_LOWER_CASE_CHARS=1 PASSWORD_MIN_NUMERIC_CHARS=1 "
            + "PASSWORD_MIN_SPECIAL_CHARS=0 PASSWORD_MIN_AGE_DAYS=0 PASSWORD_MAX_AGE_DAYS=90 PASSWORD_MAX_RETRIES=5 "
            + "PASSWORD_LOCKOUT_TIME_MINS=15 PASSWORD_HISTORY=5 ;", ddl("POLICY", "PWC"));
    }

    @Test
    public void contactsAndStreamlitAppsFollowThePoliciesAndPrecedeTheAlerts() {
        run("CREATE SCHEMA y",
            "USE SCHEMA y",
            "CREATE STAGE stg",
            "CREATE TABLE a_t (x INT)",
            "CREATE STREAMLIT a_stl ROOT_LOCATION = '@Y.STG/app' MAIN_FILE = 'a.py'",
            "CREATE CONTACT a_ct EMAIL_DISTRIBUTION_LIST = 'x@example.com'",
            "CREATE ALERT a_al SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1",
            "CREATE MASKING POLICY b_mp AS (v VARCHAR) RETURNS VARCHAR -> V",
            "CREATE PASSWORD POLICY c_pwd",
            "CREATE ROW ACCESS POLICY d_rap AS (v VARCHAR) RETURNS BOOLEAN -> TRUE",
            "CREATE CONTACT z_ct URL = 'https://example.com' COMMENT = 'c''t'",
            "CREATE STREAMLIT z_stl ROOT_LOCATION = '@Y.STG/app' MAIN_FILE = 'a.py' QUERY_WAREHOUSE = COMPUTE_WH "
                + "TITLE = 'T''t' COMMENT = 'st'");
        assertEquals("""
            create or replace schema Y;

            create or replace TABLE A_T (
            \tX NUMBER(38,0)
            );
            create or replace masking policy B_MP as (V VARCHAR)\s
            returns VARCHAR ->
            V
            ;
            create or replace password policy C_PWD PASSWORD_MIN_LENGTH=14 PASSWORD_MAX_LENGTH=256 \
            PASSWORD_MIN_UPPER_CASE_CHARS=1 PASSWORD_MIN_LOWER_CASE_CHARS=1 PASSWORD_MIN_NUMERIC_CHARS=1 \
            PASSWORD_MIN_SPECIAL_CHARS=0 PASSWORD_MIN_AGE_DAYS=0 PASSWORD_MAX_AGE_DAYS=90 PASSWORD_MAX_RETRIES=5 \
            PASSWORD_LOCKOUT_TIME_MINS=15 PASSWORD_HISTORY=5 ;
            create or replace row access policy D_RAP as (V VARCHAR)\s
            returns BOOLEAN ->
            TRUE
            ;
            create or replace contact A_CT EMAIL_DISTRIBUTION_LIST='x@example.com'
            ;
            create or replace contact Z_CT URL='https://example.com'
            COMMENT='c''t'
            ;
            create or replace streamlit A_STL
            \tfrom '@Y.STG/app'
            \tmain_file='a.py';
            create or replace streamlit Z_STL
            \tfrom '@Y.STG/app'
            \tmain_file='a.py'
            \tquery_warehouse='COMPUTE_WH'
            \tcomment='st'
            \ttitle='T't';
            create or replace alert A_AL
            \tschedule='60 MINUTE'
            \tif (exists(
            \t\tSELECT 1
            \t))
            \tthen
            \tSELECT 1;""", ddl("SCHEMA", "Y"));
    }

    // ─────────────────────────── quoted texts ───────────────────────────

    @Test
    public void everyCommentAndAllowedValueIsBackslashEscaped() {
        run("CREATE SCHEMA es COMMENT = " + ESCAPED_SQL,
            "USE SCHEMA test_schema",
            "CREATE TABLE te (a INT COMMENT " + ESCAPED_SQL + ") COMMENT = " + ESCAPED_SQL,
            "CREATE VIEW ve (x COMMENT " + ESCAPED_SQL + ") COMMENT = " + ESCAPED_SQL + " AS SELECT 1",
            "CREATE EVENT TABLE eve COMMENT = " + ESCAPED_SQL,
            "CREATE TAG tge ALLOWED_VALUES " + ESCAPED_SQL + ", 'z' COMMENT = " + ESCAPED_SQL,
            "CREATE MASKING POLICY mpe AS (v VARCHAR) RETURNS VARCHAR -> V COMMENT = " + ESCAPED_SQL,
            "CREATE TASK tke SCHEDULE = '60 MINUTE' COMMENT = " + ESCAPED_SQL + " AS SELECT 1",
            "CREATE CONTACT cte EMAIL_DISTRIBUTION_LIST = 'x@example.com' COMMENT = " + ESCAPED_SQL,
            "CREATE FUNCTION fne() RETURNS NUMBER COMMENT = " + ESCAPED_SQL + " AS '1'",
            "CREATE PROCEDURE pre() RETURNS NUMBER LANGUAGE SQL COMMENT = " + ESCAPED_SQL
                + " AS 'BEGIN RETURN 1; END'",
            "CREATE FILE FORMAT ffe COMMENT = " + ESCAPED_SQL + " TYPE = JSON",
            "CREATE STAGE ste",
            "CREATE PIPE ppe COMMENT = " + ESCAPED_SQL + " AS COPY INTO TE (A) FROM @STE",
            "CREATE SCHEMA es_cr COMMENT = 'a\\rb'",
            "CREATE SCHEMA es_bf COMMENT = 'a\\bb\\fc'",
            "CREATE SCHEMA es_x COMMENT = 'a\\x01b'",
            "USE SCHEMA test_schema");
        assertEquals("create or replace schema ES COMMENT=" + ESCAPED_DDL + ";\n", ddl("SCHEMA", "ES"));
        assertEquals("create or replace TABLE TE (\n\tA NUMBER(38,0) COMMENT " + ESCAPED_DDL + "\n)COMMENT="
            + ESCAPED_DDL + "\n;", ddl("TABLE", "TE"));
        assertEquals("create or replace view VE(\n\tX COMMENT " + ESCAPED_DDL + "\n) COMMENT=" + ESCAPED_DDL
            + "\n as SELECT 1;", ddl("VIEW", "VE"));
        assertEquals("create or replace event table EVE COMMENT=" + ESCAPED_DDL + "\n;", ddl("TABLE", "EVE"));
        assertEquals("create or replace tag TGE  allowed_values  " + ESCAPED_DDL + " , 'z' COMMENT=" + ESCAPED_DDL
            + "\n;", ddl("TAG", "TGE"));
        assertEquals("create or replace masking policy MPE as (V VARCHAR) \nreturns VARCHAR ->\nV\nCOMMENT="
            + ESCAPED_DDL + "\n;", ddl("POLICY", "MPE"));
        assertEquals("create or replace task TKE\n\tschedule='60 MINUTE'\n\tCOMMENT=" + ESCAPED_DDL
            + "\n\tas SELECT 1;", ddl("TASK", "TKE"));
        assertEquals("create or replace contact CTE EMAIL_DISTRIBUTION_LIST='x@example.com'\nCOMMENT=" + ESCAPED_DDL
            + "\n;", ddl("CONTACT", "CTE"));
        assertEquals("CREATE OR REPLACE FUNCTION \"FNE\"()\nRETURNS NUMBER(38,0)\nLANGUAGE SQL\nCOMMENT=" + ESCAPED_DDL
            + "\nAS '1';", ddl("FUNCTION", "FNE()"));
        assertEquals("CREATE OR REPLACE PROCEDURE \"PRE\"()\nRETURNS NUMBER(38,0)\nLANGUAGE SQL\nCOMMENT="
            + ESCAPED_DDL + "\nEXECUTE AS OWNER\nAS 'BEGIN RETURN 1; END';", ddl("PROCEDURE", "PRE()"));
        assertEquals("CREATE OR REPLACE FILE FORMAT FFE\n\tTYPE = JSON\n\tNULL_IF = ()\nCOMMENT=" + ESCAPED_DDL + "\n;",
            ddl("FILE_FORMAT", "FFE"));
        // A pipe's comment escapes only its single quotes, with a backslash.
        assertEquals("create or replace pipe PPE auto_ingest=false comment='b\\s \"q\" t\tn\ne\\'s' "
            + "as COPY INTO TE (A) FROM @STE;", ddl("PIPE", "PPE"));
        assertEquals("create or replace schema ES_CR COMMENT='a\\rb';\n", ddl("SCHEMA", "ES_CR"));
        assertEquals("create or replace schema ES_BF COMMENT='a\\bb\\fc';\n", ddl("SCHEMA", "ES_BF"));
        assertEquals("create or replace schema ES_X COMMENT='a\u0001b';\n", ddl("SCHEMA", "ES_X"));
    }

    @Test
    public void aDatabaseCommentIsEscapedToo() {
        run("CREATE DATABASE ddl_comment_db COMMENT = " + ESCAPED_SQL);
        assertEquals("create or replace database DDL_COMMENT_DB COMMENT=" + ESCAPED_DDL
            + ";\n\ncreate or replace schema PUBLIC;\n", ddl("DATABASE", "DDL_COMMENT_DB"));
    }

    @Test
    public void aCommentClearedWithUnsetStaysCleared() {
        run("CREATE TABLE t1 (a INT)",
            "COMMENT ON TABLE t1 IS 'c1'",
            "ALTER TABLE t1 UNSET COMMENT",
            "CREATE TABLE t2 (a INT) COMMENT = 'c2'",
            "ALTER TABLE t2 SET COMMENT = ''",
            "CREATE TABLE t3 (a INT) COMMENT = 'c3'",
            "ALTER TABLE t3 UNSET COMMENT, DATA_RETENTION_TIME_IN_DAYS",
            "CREATE EVENT TABLE ev1 COMMENT = 'ec'",
            "ALTER TABLE ev1 UNSET COMMENT",
            "CREATE VIEW v1 COMMENT = 'vc' AS SELECT 1 AS ONE",
            "ALTER VIEW v1 UNSET COMMENT",
            "CREATE VIEW v2 AS SELECT 2 AS TWO",
            "COMMENT ON VIEW v2 IS 'on'",
            "ALTER VIEW IF EXISTS v2 UNSET COMMENT",
            "ALTER VIEW IF EXISTS nope_v UNSET COMMENT",
            "CREATE MATERIALIZED VIEW mv1 COMMENT = 'mc' AS SELECT A FROM T1",
            "ALTER MATERIALIZED VIEW mv1 UNSET COMMENT",
            "CREATE CONTACT ct1 EMAIL_DISTRIBUTION_LIST = 'x@example.com' COMMENT = 'cc'",
            "ALTER CONTACT ct1 UNSET COMMENT");
        assertEquals("create or replace TABLE T1 (\n\tA NUMBER(38,0)\n);", ddl("TABLE", "T1"));
        assertNull(engine.executeQuery("SELECT COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'T1'")
            .getRows().get(0).getValue(0));
        assertEquals("create or replace TABLE T2 (\n\tA NUMBER(38,0)\n);", ddl("TABLE", "T2"));
        assertEquals("create or replace TABLE T3 (\n\tA NUMBER(38,0)\n);", ddl("TABLE", "T3"));
        assertEquals("create or replace event table EV1;", ddl("TABLE", "EV1"));
        assertEquals("create or replace view V1(\n\tONE\n) as SELECT 1 AS ONE;", ddl("VIEW", "V1"));
        assertEquals("create or replace view V2(\n\tTWO\n) as SELECT 2 AS TWO;", ddl("VIEW", "V2"));
        assertEquals("create or replace materialized view MV1(\n\tA\n) as SELECT A FROM T1;", ddl("TABLE", "MV1"));
        assertEquals("create or replace contact CT1 EMAIL_DISTRIBUTION_LIST='x@example.com'\n;", ddl("CONTACT", "CT1"));
        assertEquals(hinted("SQL compilation error:\nView 'TEST_DB.TEST_SCHEMA.NOPE_V' does not exist or not authorized."),
            refusal("ALTER VIEW nope_v UNSET COMMENT"));
    }

    // ─────────────────────────── attachments and tags ───────────────────────────

    @Test
    public void aTablesAttachmentsFollowItsColumnsOnePerLine() {
        run("CREATE TAG ta",
            "CREATE TAG tb",
            "CREATE SCHEMA s2",
            "CREATE ROW ACCESS POLICY s2.rap9 AS (v INT) RETURNS BOOLEAN -> TRUE",
            "CREATE SCHEMA z2",
            "CREATE TAG z2.aa",
            "USE SCHEMA test_schema",
            "CREATE ROW ACCESS POLICY rap AS (v INT) RETURNS BOOLEAN -> TRUE",
            "CREATE ROW ACCESS POLICY rap2 AS (v INT, w INT) RETURNS BOOLEAN -> TRUE",
            "CREATE AGGREGATION POLICY ap AS () RETURNS AGGREGATION_CONSTRAINT -> NO_AGGREGATION_CONSTRAINT()",
            "CREATE JOIN POLICY jp AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)",
            "CREATE TABLE tr1 (a INT) WITH ROW ACCESS POLICY rap ON (a)",
            "CREATE TABLE tr2 (a INT, b INT) COMMENT = 'c2' WITH ROW ACCESS POLICY rap2 ON (b, a) "
                + "WITH TAG (tb = 'b', ta = 't')",
            "CREATE TABLE tr3 (a INT) WITH ROW ACCESS POLICY rap ON (a) WITH AGGREGATION POLICY ap "
                + "WITH TAG (ta = 't') COMMENT = 'c3'",
            "CREATE TABLE tj1 (a INT) WITH JOIN POLICY jp WITH TAG (ta = 'j')",
            "CREATE TABLE tja (a INT) WITH AGGREGATION POLICY ap WITH JOIN POLICY jp COMMENT = 'ja'",
            "CREATE TABLE tjr (a INT) WITH TAG (ta = 'x') WITH JOIN POLICY jp WITH ROW ACCESS POLICY rap ON (a) "
                + "WITH AGGREGATION POLICY ap",
            "CREATE TABLE tr9 (a INT) WITH ROW ACCESS POLICY s2.rap9 ON (a)",
            "CREATE TABLE \"tq\" (\"a\" INT) WITH ROW ACCESS POLICY rap ON (\"a\")",
            "CREATE TABLE tr5 (a INT, b INT)",
            "ALTER TABLE tr5 ADD ROW ACCESS POLICY rap ON (b)",
            "ALTER TABLE tr5 SET COMMENT = 'c5'",
            "CREATE TABLE tsh (a INT)",
            "ALTER TABLE tsh SET AGGREGATION POLICY ap ENTITY KEY (a)",
            "ALTER TABLE tsh SET TAG tb = 'x'",
            "CREATE TABLE tm2 (a INT) WITH TAG (z2.aa = 'x', tb = 'b')",
            "CREATE VIEW vrp COMMENT = 'vr' AS SELECT A FROM TR5",
            "ALTER VIEW vrp ADD ROW ACCESS POLICY rap ON (a)",
            "ALTER VIEW vrp SET TAG ta = 'v'");
        final String at = "TEST_DB.TEST_SCHEMA.";
        assertEquals("create or replace TABLE TR1 (\n\tA NUMBER(38,0)\n) WITH ROW ACCESS POLICY " + at + "RAP ON (A)\n;",
            ddl("TABLE", "TR1"));
        assertEquals("create or replace TABLE TR2 (\n\tA NUMBER(38,0),\n\tB NUMBER(38,0)\n) WITH ROW ACCESS POLICY " + at
            + "RAP2 ON (B, A)\n WITH TAG (" + at + "TA='t', " + at + "TB='b')\nCOMMENT='c2'\n;", ddl("TABLE", "TR2"));
        assertEquals("create or replace TABLE TR3 (\n\tA NUMBER(38,0)\n) WITH AGGREGATION POLICY " + at + "AP\n"
            + " WITH ROW ACCESS POLICY " + at + "RAP ON (A)\n WITH TAG (" + at + "TA='t')\nCOMMENT='c3'\n;",
            ddl("TABLE", "TR3"));
        assertEquals("create or replace TABLE TJ1 (\n\tA NUMBER(38,0)\n) WITH JOIN POLICY " + at + "JP\n WITH TAG (" + at
            + "TA='j')\n;", ddl("TABLE", "TJ1"));
        assertEquals("create or replace TABLE TJA (\n\tA NUMBER(38,0)\n) WITH AGGREGATION POLICY " + at + "AP\n"
            + " WITH JOIN POLICY " + at + "JP\nCOMMENT='ja'\n;", ddl("TABLE", "TJA"));
        assertEquals("create or replace TABLE TJR (\n\tA NUMBER(38,0)\n) WITH AGGREGATION POLICY " + at + "AP\n"
            + " WITH ROW ACCESS POLICY " + at + "RAP ON (A)\n WITH JOIN POLICY " + at + "JP\n WITH TAG (" + at
            + "TA='x')\n;", ddl("TABLE", "TJR"));
        assertEquals("create or replace TABLE TR9 (\n\tA NUMBER(38,0)\n) WITH ROW ACCESS POLICY TEST_DB.S2.RAP9 ON (A)\n;",
            ddl("TABLE", "TR9"));
        assertEquals("create or replace TABLE \"tq\" (\n\t\"a\" NUMBER(38,0)\n) WITH ROW ACCESS POLICY " + at
            + "RAP ON (\"a\")\n;", ddl("TABLE", "\"tq\""));
        assertEquals("create or replace TABLE TR5 (\n\tA NUMBER(38,0),\n\tB NUMBER(38,0)\n) WITH ROW ACCESS POLICY " + at
            + "RAP ON (B)\nCOMMENT='c5'\n;", ddl("TABLE", "TR5"));
        assertEquals("create or replace TABLE TSH (\n\tA NUMBER(38,0)\n) WITH AGGREGATION POLICY " + at
            + "AP ENTITY KEY (A)\n WITH TAG (" + at + "TB='x')\n;", ddl("TABLE", "TSH"));
        // Tags follow one another by their full names, not by their own.
        assertEquals("create or replace TABLE TM2 (\n\tA NUMBER(38,0)\n) WITH TAG (" + at + "TB='b', TEST_DB.Z2.AA='x')\n;",
            ddl("TABLE", "TM2"));
        assertEquals("create or replace view VRP(\n\tA\n) WITH ROW ACCESS POLICY " + at + "RAP ON (A)\n WITH TAG (" + at
            + "TA='v')\n COMMENT='vr'\n as SELECT A FROM TR5;", ddl("VIEW", "VRP"));
    }

    @Test
    public void aTagIsNamedAsDefinedAndItsValuePrintedAsStored() {
        run("CREATE TAG ta",
            "CREATE TAG tb",
            "CREATE TAG \"tl\"",
            "CREATE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> V",
            "CREATE TABLE tx2 (a INT WITH TAG (ta = 'a'), b INT)",
            "CREATE TABLE tcm (a VARCHAR WITH MASKING POLICY mp WITH TAG (ta = 'a') COMMENT 'c', "
                + "b INT WITH TAG (tb = 'b''q'))",
            "CREATE TABLE tlow (a INT) WITH TAG (\"tl\" = 'v')",
            "CREATE TAG \"Tc\"",
            "CREATE TABLE tord (a INT) WITH TAG (ta = '3', \"Tc\" = '2')",
            "CREATE TABLE tv (a INT) WITH TAG (ta = 'a\\\\b')");
        final String at = "TEST_DB.TEST_SCHEMA.";
        assertEquals("create or replace TABLE TX2 (\n\tA NUMBER(38,0) WITH TAG (" + at + "TA='a'),\n\tB NUMBER(38,0)\n);",
            ddl("TABLE", "TX2"));
        run("ALTER TABLE tx2 MODIFY COLUMN b SET TAG tb = 'b''c'",
            "ALTER TABLE tx2 SET TAG ta = 'q''z'");
        assertEquals("create or replace TABLE TX2 (\n\tA NUMBER(38,0) WITH TAG (" + at + "TA='a'),\n\tB NUMBER(38,0) "
            + "WITH TAG (" + at + "TB='b'c')\n) WITH TAG (" + at + "TA='q'z')\n;", ddl("TABLE", "TX2"));
        assertEquals("create or replace TABLE TCM (\n\tA VARCHAR(16777216) WITH MASKING POLICY " + at + "MP WITH TAG ("
            + at + "TA='a') COMMENT 'c',\n\tB NUMBER(38,0) WITH TAG (" + at + "TB='b'q')\n);", ddl("TABLE", "TCM"));
        assertEquals("create or replace TABLE TLOW (\n\tA NUMBER(38,0)\n) WITH TAG (" + at + "\"tl\"='v')\n;",
            ddl("TABLE", "TLOW"));
        // By full name as printed: a quoted name's quote sorts before a letter.
        assertEquals("create or replace TABLE TORD (\n\tA NUMBER(38,0)\n) WITH TAG (" + at + "\"Tc\"='2', " + at
            + "TA='3')\n;", ddl("TABLE", "TORD"));
        assertEquals("create or replace TABLE TV (\n\tA NUMBER(38,0)\n) WITH TAG (" + at + "TA='a\\b')\n;",
            ddl("TABLE", "TV"));
    }

    // ─────────────────────────── routine order ───────────────────────────

    @Test
    public void routinesSortByTheirNameAndParameterListTogether() {
        run("CREATE SCHEMA r2",
            "USE SCHEMA r2",
            "CREATE FUNCTION f() RETURNS NUMBER AS '1'",
            "CREATE FUNCTION f$x() RETURNS NUMBER AS '2'",
            "CREATE FUNCTION \"my\"() RETURNS NUMBER AS '3'",
            "CREATE FUNCTION \"my fn\"() RETURNS NUMBER AS '4'",
            "CREATE FUNCTION h() RETURNS NUMBER AS '5'",
            "CREATE FUNCTION h_() RETURNS NUMBER AS '6'",
            "CREATE FUNCTION \"H!\"() RETURNS NUMBER AS '7'",
            "CREATE PROCEDURE \"H \"() RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN 8; END'",
            "CREATE FUNCTION k(x NUMBER) RETURNS NUMBER AS '9'",
            "CREATE FUNCTION k$(x NUMBER) RETURNS NUMBER AS '10'",
            "USE SCHEMA test_schema");
        final List<String> expected = new ArrayList<String>();
        expected.add("CREATE OR REPLACE FUNCTION \"F$X\"()");
        expected.add("CREATE OR REPLACE FUNCTION \"F\"()");
        expected.add("CREATE OR REPLACE PROCEDURE \"H \"()");
        expected.add("CREATE OR REPLACE FUNCTION \"H!\"()");
        expected.add("CREATE OR REPLACE FUNCTION \"H\"()");
        expected.add("CREATE OR REPLACE FUNCTION \"H_\"()");
        expected.add("CREATE OR REPLACE FUNCTION \"K$\"(\"X\" NUMBER(38,0))");
        expected.add("CREATE OR REPLACE FUNCTION \"K\"(\"X\" NUMBER(38,0))");
        expected.add("CREATE OR REPLACE FUNCTION \"my fn\"()");
        expected.add("CREATE OR REPLACE FUNCTION \"my\"()");
        assertEquals(expected, routineHeads(ddl("SCHEMA", "R2")));
    }

    // ─────────────────────────── arguments ───────────────────────────

    @Test
    public void theThirdArgumentIsReadOnceTheObjectIsFound() {
        final String notConstant = "] for function '2', parameter IMPORT_DDL: constant arguments expected";
        assertEquals("SQL compilation error:\nInvalid value [CAST(' yes ' AS BOOLEAN)" + notConstant,
            refusal("SELECT GET_DDL('SCHEMA', 'TEST_SCHEMA', ' yes ')"));
        assertEquals("SQL compilation error:\nInvalid value [CAST('TRUE ' AS BOOLEAN)" + notConstant,
            refusal("SELECT GET_DDL('SCHEMA', 'TEST_SCHEMA', 'TRUE ')"));
        assertEquals("SQL compilation error:\nInvalid value [CAST('' AS BOOLEAN)" + notConstant,
            refusal("SELECT GET_DDL('SCHEMA', 'TEST_SCHEMA', '')"));
        assertEquals("SQL compilation error:\nInvalid value [CAST(CAST(TRUE AS VARIANT) AS BOOLEAN)" + notConstant,
            refusal("SELECT GET_DDL('SCHEMA', 'TEST_SCHEMA', TO_VARIANT(TRUE))"));
        assertEquals("SQL compilation error:\nInvalid value [CAST(PARSE_JSON('true') AS BOOLEAN)" + notConstant,
            refusal("SELECT GET_DDL('SCHEMA', 'TEST_SCHEMA', PARSE_JSON('true'))"));
        assertEquals("SQL compilation error:\nInvalid value [CAST(1 = 1 AS BOOLEAN)" + notConstant,
            refusal("SELECT GET_DDL('SCHEMA', 'TEST_SCHEMA', 1 = 1)"));
        final String header = "create or replace schema TEST_DB.TEST_SCHEMA;\n";
        assertEquals(header, ddl("SCHEMA", "TEST_SCHEMA", "'tRuE'"));
        assertEquals(header, ddl("SCHEMA", "TEST_SCHEMA", "'1'"));
        assertEquals(header, ddl("SCHEMA", "TEST_SCHEMA", "1.5::FLOAT"));
        assertEquals(header, ddl("SCHEMA", "TEST_SCHEMA", "-1"));
        assertEquals("create or replace schema TEST_SCHEMA;\n", ddl("SCHEMA", "TEST_SCHEMA", "'off'"));
        // A type no cast to BOOLEAN takes is refused before anything is looked up, at the call.
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'IMPORT_DDL': (VARCHAR(134217728), DATE)",
            refusal("SELECT GET_DDL('SCHEMA', 'NOPE', CURRENT_DATE())"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'IMPORT_DDL': (VARCHAR(134217728), ARRAY)",
            refusal("SELECT GET_DDL('SCHEMA', 'TEST_SCHEMA', ARRAY_CONSTRUCT(1))"));
        // Otherwise the object comes first: a missing one, or a bad type, is refused ahead of the third argument.
        assertEquals(hinted("SQL compilation error:\nSchema 'TEST_DB.NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('SCHEMA', 'NOPE', 'abc')"));
        assertEquals(hinted("SQL compilation error:\nSchema 'TEST_DB.NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('SCHEMA', 'NOPE', TO_VARIANT(TRUE))"));
        assertEquals("SQL compilation error:\nInvalid object type: 'BOGUS'", refusal("SELECT GET_DDL('BOGUS', 'X', 'abc')"));
        assertEquals("SQL compilation error:\nObject does not exist, or operation cannot be performed.",
            refusal("SELECT GET_DDL('SCHEMA', 'A.B.C', 'abc')"));
        assertNull(engine.executeQuery("SELECT GET_DDL('SCHEMA', NULL, TO_VARIANT(TRUE))").getRows().get(0).getValue(0));
    }

    @Test
    public void theObjectTypeAndNameMustBeConstants() {
        run("CREATE TABLE t0 (n VARCHAR, b BOOLEAN, ty VARCHAR)",
            "INSERT INTO t0 VALUES ('TEST_SCHEMA', TRUE, 'SCHEMA')",
            "SET sv = 'TEST_SCHEMA'");
        final String export = ", parameter EXPORT_DDL: constant arguments expected";
        assertEquals("SQL compilation error:\nInvalid value [T0.N] for function '2'" + export,
            refusal("SELECT GET_DDL('SCHEMA', n) FROM t0"));
        assertEquals("SQL compilation error:\nInvalid value [T0.N] for function '2'" + export,
            refusal("SELECT GET_DDL('SCHEMA', n) FROM t0 WHERE 1 = 0"));
        assertEquals("SQL compilation error:\nInvalid value [T0.TY] for function '1'" + export,
            refusal("SELECT GET_DDL(ty, 'TEST_SCHEMA') FROM t0"));
        assertEquals("SQL compilation error:\nInvalid value [UPPER(T0.N)] for function '2'" + export,
            refusal("SELECT GET_DDL('SCHEMA', UPPER(n)) FROM t0"));
        assertEquals("SQL compilation error:\nInvalid value [T0.N] for function '2'" + export,
            refusal("SELECT GET_DDL('BOGUS', n) FROM t0"));
        assertEquals("SQL compilation error:\nInvalid value [TRIM(' TEST_SCHEMA ')] for function '2'" + export,
            refusal("SELECT GET_DDL('SCHEMA', TRIM(' TEST_SCHEMA '))"));
        assertEquals("SQL compilation error:\nInvalid value [CAST(1 AS VARCHAR(134217728))] for function '1'" + export,
            refusal("SELECT GET_DDL(1, 'TEST_SCHEMA')"));
        assertEquals("SQL compilation error:\nInvalid value [T0.B] for function '2', parameter IMPORT_DDL: "
            + "constant arguments expected", refusal("SELECT GET_DDL('SCHEMA', 'TEST_SCHEMA', b) FROM t0"));
        assertEquals(hinted("SQL compilation error:\nSchema 'TEST_DB.NOPE' does not exist or not authorized."),
            refusal("SELECT GET_DDL('SCHEMA', 'NOPE', b) FROM t0"));
        // Session values and joins of constant text fold.
        final String text = ddl("SCHEMA", "TEST_SCHEMA");
        for (final String name : new String[] {"CURRENT_SCHEMA()", "(SELECT 'TEST_SCHEMA')", "$sv", "GETVARIABLE('SV')",
                "'TEST_' || 'SCHEMA'", "LOWER(CURRENT_SCHEMA())"}) {
            assertEquals(text, engine.executeQuery("SELECT GET_DDL('SCHEMA', " + name + ")").getRows().get(0).getValue(0),
                name);
        }
    }

    @Test
    public void aNameIsReadWholeSpacesIncluded() {
        run("CREATE TABLE t0 (a INT)",
            "CREATE SEQUENCE sq1",
            "CREATE FUNCTION f1(x NUMBER) RETURNS NUMBER AS 'X'");
        assertEquals(hinted("SQL compilation error:\nTable '\" T0 \"' does not exist or not authorized."),
            refusal("SELECT GET_DDL('TABLE', ' T0 ')"));
        assertEquals(hinted("SQL compilation error:\nView '\" T0 \"' does not exist or not authorized."),
            refusal("SELECT GET_DDL('VIEW', ' T0 ')"));
        assertEquals(hinted("SQL compilation error:\nSequence '\" SQ1 \"' does not exist or not authorized."),
            refusal("SELECT GET_DDL('SEQUENCE', ' SQ1 ')"));
        // A routine's argument is read as a reference, around which spaces do not count.
        assertEquals("CREATE OR REPLACE FUNCTION \"F1\"(\"X\" NUMBER(38,0))\nRETURNS NUMBER(38,0)\nLANGUAGE SQL\nAS 'X';",
            ddl("FUNCTION", " F1(NUMBER) "));
    }

    // ─────────────────────────── file formats ───────────────────────────

    @Test
    public void nullIfValuesKeepTheirCommasAndAnEmptyString() {
        run("CREATE FILE FORMAT ff_comma NULL_IF = ('a,b', 'c')",
            "CREATE FILE FORMAT ff_e1 NULL_IF = ('')",
            "CREATE FILE FORMAT ff_e2 NULL_IF = ('', 'x,y', ',')",
            "CREATE FILE FORMAT ff_e3 TYPE = JSON NULL_IF = ('a,b')");
        assertEquals("CREATE OR REPLACE FILE FORMAT FF_COMMA\n\tNULL_IF = ('a,b', 'c')\n;", ddl("FILE_FORMAT", "FF_COMMA"));
        assertEquals("CREATE OR REPLACE FILE FORMAT FF_E1\n\tNULL_IF = ('')\n;", ddl("FILE_FORMAT", "FF_E1"));
        assertEquals("CREATE OR REPLACE FILE FORMAT FF_E2\n\tNULL_IF = ('', 'x,y', ',')\n;", ddl("FILE_FORMAT", "FF_E2"));
        assertEquals("CREATE OR REPLACE FILE FORMAT FF_E3\n\tTYPE = JSON\n\tNULL_IF = ('a,b')\n;", ddl("FILE_FORMAT", "FF_E3"));
        assertEquals("[a,b, c]", describedNullIf("FF_COMMA"));
        assertEquals("[]", describedNullIf("FF_E1"));
        assertEquals("[, x,y, ,]", describedNullIf("FF_E2"));
    }

    /** DESC FILE FORMAT's NULL_IF value. */
    private String describedNullIf(final String format) {
        for (final Row row : engine.executeQuery("DESC FILE FORMAT " + format).getRows()) {
            if ("NULL_IF".equals(row.getValue(0))) {
                return (String) row.getValue(2);
            }
        }
        return null;
    }

    // ─────────────────────────── engine safety ───────────────────────────

    @Test
    public void aRelationsColumnListIsPlannedNeverRun() {
        Assumptions.assumeFalse(isLiveSnowflake(), "how the engine derives a column list is its own concern");
        run("CREATE TABLE src (id INT)",
            "INSERT INTO src VALUES (1), (2)",
            "CREATE SEQUENCE sq",
            "CREATE DYNAMIC TABLE dt1 TARGET_LAG = '1 hour' WAREHOUSE = COMPUTE_WH INITIALIZE = ON_SCHEDULE "
                + "AS SELECT SQ.NEXTVAL AS N FROM SRC",
            "CREATE DYNAMIC TABLE dt2 TARGET_LAG = '1 hour' WAREHOUSE = COMPUTE_WH INITIALIZE = ON_SCHEDULE "
                + "AS SELECT GET_DDL('SCHEMA', 'TEST_SCHEMA') AS D");
        final Object before = engine.executeQuery("SELECT SQ.NEXTVAL").getRows().get(0).getValue(0);
        ddl("TABLE", "DT1");
        ddl("TABLE", "DT1");
        final Object after = engine.executeQuery("SELECT SQ.NEXTVAL").getRows().get(0).getValue(0);
        assertEquals(((Number) before).longValue() + 1, ((Number) after).longValue());
        // A dynamic table whose query asks for its own schema's DDL is rendered, not recursed into.
        assertTrue(ddl("SCHEMA", "TEST_SCHEMA").contains("create or replace dynamic table DT2"));
    }
}
