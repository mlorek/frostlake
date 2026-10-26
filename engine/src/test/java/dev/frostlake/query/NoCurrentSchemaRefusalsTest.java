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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A session whose current schema is gone, with no PUBLIC to fall back to, refuses a statement that makes,
 * drops or changes an object, or lists or removes a stage's files, naming the operation - STAGE LS, STAGE RM,
 * CREATE TABLE, DROP - and a handler reads code 90106 and state 22000 for every such refusal (all
 * live-verified).
 */
public class NoCurrentSchemaRefusalsTest extends MeasuredCellsTestSupport {

    @Test
    public void withoutAPublicSchema() {
        engine.execute("CREATE OR REPLACE STAGE qt_st");
        engine.execute("CREATE OR REPLACE FUNCTION qt_f() RETURNS INT AS '1'");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("DROP SCHEMA PUBLIC");
        assertAnswer("TEST_DB|null", "SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()");
        assertAnswer("REFUSED Cannot perform STAGE LS. This session does not have a current schema. Call 'USE SCHEMA',"
                + " or use a qualified name.",
            "LIST @qt_st");
        assertAnswer("REFUSED Cannot perform STAGE RM. This session does not have a current schema. Call 'USE SCHEMA',"
                + " or use a qualified name.",
            "REMOVE @qt_st/x");
        assertAnswer("REFUSED Cannot perform DESCRIBE. This session does not have a current schema. Call 'USE SCHEMA',"
                + " or use a qualified name.",
            "DESC FUNCTION qt_f()");
        assertAnswer("REFUSED Cannot perform DESCRIBE. This session does not have a current schema. Call 'USE SCHEMA',"
                + " or use a qualified name.",
            "DESCRIBE FUNCTION qt_f()");
        assertAnswer("REFUSED Cannot perform CREATE TABLE. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name.",
            "CREATE TABLE ncs_t (a INT)");
        assertAnswer("REFUSED Cannot perform CREATE TEMPTABLE. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name.",
            "CREATE TEMPORARY TABLE ncs_t (a INT)");
        assertAnswer("REFUSED Cannot perform CREATE VIEW. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name.",
            "CREATE VIEW ncs_v AS SELECT 1 AS a");
        assertAnswer("REFUSED Cannot perform CREATE STAGE. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name.",
            "CREATE STAGE ncs_st");
        assertAnswer("REFUSED Cannot perform CREATE SEQUENCE. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name.",
            "CREATE SEQUENCE ncs_q");
        assertAnswer("REFUSED Cannot perform CREATE FUNCTION. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name.",
            "CREATE FUNCTION ncs_f() RETURNS INT AS '1'");
        assertAnswer("REFUSED Cannot perform DROP. This session does not have a current schema. Call 'USE SCHEMA', or "
                + "use a qualified name.",
            "DROP TABLE ncs_t");
        assertAnswer("REFUSED Cannot perform CREATE TABLE. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name.",
            "UNDROP TABLE ncs_t");
        assertAnswer("REFUSED Cannot perform ALTER. This session does not have a current schema. Call 'USE SCHEMA', "
                + "or use a qualified name.",
            "ALTER TABLE ncs_t ADD COLUMN b INT");
        assertAnswer("REFUSED SQL compilation error: | Object 'NCS_T' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NCS_T.",
            "SELECT * FROM ncs_t");
        assertAnswer("REFUSED Cannot perform ALTER. This session does not have a current schema. Call 'USE SCHEMA', "
                + "or use a qualified name.",
            "COMMENT ON COLUMN ncs_t.a IS 'x'");
        assertAnswer("", "SHOW TABLES");
        assertAnswer("REFUSED SQL compilation error: | Unknown function QT_F.", "SELECT qt_f()");
        assertAnswer("", "LIST @test_schema.qt_st");
        assertAnswer("signature|() ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESC FUNCTION test_schema.qt_f()");
        assertAnswer("90106|22000|Cannot perform STAGE LS. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name.",
            "EXECUTE IMMEDIATE $$ BEGIN LIST @qt_st; RETURN 'ran'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '|' ||"
                + " SQLSTATE || '|' || SQLERRM; END; $$");
        assertAnswer("90106|22000|Cannot perform STAGE RM. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name.",
            "EXECUTE IMMEDIATE $$ BEGIN REMOVE @qt_st/x; RETURN 'ran'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '|"
                + "' || SQLSTATE || '|' || SQLERRM; END; $$");
        assertAnswer("90106|22000|Cannot perform DESCRIBE. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name.",
            "EXECUTE IMMEDIATE $$ BEGIN DESC FUNCTION qt_f(); RETURN 'ran'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE "
                + "|| '|' || SQLSTATE || '|' || SQLERRM; END; $$");
        assertAnswer("90106|22000|Cannot perform CREATE TABLE. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE ncs_t (a INT); RETURN 'ran'; EXCEPTION WHEN OTHER THEN RETURN "
                + "SQLCODE || '|' || SQLSTATE || '|' || SQLERRM; END; $$");
        assertAnswer("90106|22000|Cannot perform DROP. This session does not have a current schema. Call 'USE SCHEMA',"
                + " or use a qualified name.",
            "EXECUTE IMMEDIATE $$ BEGIN DROP TABLE ncs_t; RETURN 'ran'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || "
                + "'|' || SQLSTATE || '|' || SQLERRM; END; $$");
        assertAnswer("2003|42S02|SQL compilation error: | Object 'NCS_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NCS_T.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT * FROM ncs_t; RETURN 'ran'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE |"
                + "| '|' || SQLSTATE || '|' || SQLERRM; END; $$");
        assertAnswer("90106|22000|Cannot perform CREATE VIEW. This session does not have a current schema. Call 'USE "
                + "SCHEMA', or use a qualified name.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE VIEW ncs_v AS SELECT 1 AS a; RETURN 'ran'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || '|' || SQLSTATE || '|' || SQLERRM; END; $$");
    }
}
