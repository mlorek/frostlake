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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A session policy is a schema object: CREATE answers {@code Session policy 'SP1' created successfully.}, its name is
 * shared with the schema's other policies and not with a table, its four timeouts are whole numbers within their
 * ranges and its two role lists name roles or ALL alone, DESCRIBE lists every property with its default, SHOW
 * SESSION POLICIES reads LIKE, a scope, STARTS WITH and LIMIT, GET_DDL prints the policy whole, a tag is set on it
 * and read back, and UNDROP restores none (live-verified).
 */
public class SessionPolicyTest extends MeasuredCellsTestSupport {

    @Test
    public void aPolicyIsCreatedAlteredAndDropped() {
        assertCells(new String[][] {
            {"CREATE SESSION POLICY sp1 SESSION_IDLE_TIMEOUT_MINS = 30", "Session policy 'SP1' created successfully."},
            {"CREATE SESSION POLICY sp1 SESSION_IDLE_TIMEOUT_MINS = 30",
                "REFUSED SQL compilation error: | Object 'SP1' already exists."},
            {"CREATE SESSION POLICY IF NOT EXISTS sp1 SESSION_IDLE_TIMEOUT_MINS = 30",
                "SP1 already exists, statement succeeded."},
            {"CREATE OR REPLACE SESSION POLICY sp1 SESSION_IDLE_TIMEOUT_MINS = 20 SESSION_UI_IDLE_TIMEOUT_MINS "
                + "= 10 COMMENT = 'c'",
                "Session policy 'SP1' created successfully."},
        });
        assertShape("created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), options:VARCHAR(16777216)",
            "SHOW SESSION POLICIES LIKE 'SP1'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", \"comment\", \"options\" FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "SP1|TEST_DB|TEST_SCHEMA|SESSION_POLICY|c|"},
        });
        assertShape("property:VARCHAR(16777216), value:VARCHAR(16777216), default:VARCHAR(16777216), "
                + "description:VARCHAR(16777216)",
            "DESCRIBE SESSION POLICY sp1");
        assertCells(new String[][] {
            {"SELECT \"name\", \"session_idle_timeout_mins\", \"session_ui_idle_timeout_mins\", \"comment\" "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"name\"'"},
            {"ALTER SESSION POLICY sp1 SET SESSION_IDLE_TIMEOUT_MINS = 10", "Statement executed successfully."},
            {"ALTER SESSION POLICY sp1 SET COMMENT = 'x'", "Statement executed successfully."},
            {"ALTER SESSION POLICY sp1 UNSET COMMENT", "Statement executed successfully."},
            {"ALTER SESSION POLICY sp1 UNSET SESSION_IDLE_TIMEOUT_MINS", "Statement executed successfully."},
            {"ALTER SESSION POLICY sp1 SET FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'SESSION_POLICY'"},
        });
        assertShape("property:VARCHAR(16777216), value:VARCHAR(16777216), default:VARCHAR(16777216), "
                + "description:VARCHAR(16777216)",
            "DESCRIBE SESSION POLICY sp1");
        assertCells(new String[][] {
            {"SELECT \"name\", \"session_idle_timeout_mins\", \"session_ui_idle_timeout_mins\", \"comment\" "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"name\"'"},
            {"ALTER SESSION POLICY sp1 RENAME TO sp2", "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sp1",
                "REFUSED SQL compilation error: | Session policy 'TEST_DB.TEST_SCHEMA.SP1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.SP1."},
            {"ALTER SESSION POLICY IF EXISTS nosuch SET COMMENT = 'x'", "Statement executed successfully."},
            {"ALTER SESSION POLICY nosuch SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Session policy 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH."},
            {"CREATE SESSION POLICY sp3 FOO = 1",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'SESSION_POLICY'"},
            {"CREATE SESSION POLICY sp3 SESSION_IDLE_TIMEOUT_MINS = 0",
                "REFUSED SQL compilation error: | invalid value '0' for property 'SESSION_IDLE_TIMEOUT_MINS'"},
            {"CREATE SESSION POLICY sp3 SESSION_IDLE_TIMEOUT_MINS = 240", "Session policy 'SP3' created successfully."},
            {"CREATE SESSION POLICY sp5 SESSION_IDLE_TIMEOUT_MINS = 241", "Session policy 'SP5' created successfully."},
            {"CREATE SESSION POLICY sp5 SESSION_IDLE_TIMEOUT_MINS = 'x'",
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'SESSION_IDLE_TIMEOUT_MINS'"},
            {"CREATE SESSION POLICY sp4 ALLOWED_SECONDARY_ROLES = ('ALL')",
                "Session policy 'SP4' created successfully."},
            {"CREATE SESSION POLICY sp6 BLOCKED_SECONDARY_ROLES = ('ALL')",
                "Session policy 'SP6' created successfully."},
            {"CREATE SESSION POLICY sp7", "Session policy 'SP7' created successfully."},
            {"CREATE PASSWORD POLICY pw1", "Password policy 'PW1' created successfully"},
            {"CREATE SESSION POLICY pw1", "REFUSED SQL compilation error: | Object 'PW1' already exists."},
            {"SELECT GET_DDL('POLICY', 'sp4')",
                "create or replace session policy SP4 SESSION_IDLE_TIMEOUT_MINS=240 "
                + "SESSION_UI_IDLE_TIMEOUT_MINS=1080 SESSION_MAX_LIFESPAN_MINS=0 SESSION_UI_MAX_LIFESPAN_MINS=0 "
                + "ALLOWED_SECONDARY_ROLES=('ALL') BLOCKED_SECONDARY_ROLES=() AGENT_RESTRICTED_SESSION_SCOPE=NULL ;"},
            {"SELECT GET_DDL('POLICY', 'sp7')",
                "create or replace session policy SP7 SESSION_IDLE_TIMEOUT_MINS=240 "
                + "SESSION_UI_IDLE_TIMEOUT_MINS=1080 SESSION_MAX_LIFESPAN_MINS=0 SESSION_UI_MAX_LIFESPAN_MINS=0 "
                + "ALLOWED_SECONDARY_ROLES=('ALL') BLOCKED_SECONDARY_ROLES=() AGENT_RESTRICTED_SESSION_SCOPE=NULL ;"},
        });
        engine.execute("SHOW SESSION POLICIES IN SCHEMA test_db.test_schema");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "6"},
            {"DROP SESSION POLICY sp2", "SP2 successfully dropped."},
            {"DROP SESSION POLICY sp2",
                "REFUSED SQL compilation error: | Session policy 'TEST_DB.TEST_SCHEMA.SP2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.SP2."},
            {"DROP SESSION POLICY IF EXISTS sp2", "Drop statement executed successfully (SP2 already dropped)."},
            {"SHOW SESSION POLICIES IN TABLE x",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type SESSION "
                + "POLICY in TABLE'."},
            {"DESCRIBE SESSION POLICY sp7 x y z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                + "error line 1 at position 33 unexpected '<EOF>'."},
        });
    }

    @Test
    public void itsPropertiesAreCheckedAndListed() {
        engine.execute("CREATE OR REPLACE SESSION POLICY sq1 SESSION_IDLE_TIMEOUT_MINS = 30 COMMENT = 'c'");
        engine.execute("CREATE OR REPLACE SESSION POLICY sq2 SESSION_UI_IDLE_TIMEOUT_MINS = 7 "
            + "SESSION_MAX_LIFESPAN_MINS = 60 SESSION_UI_MAX_LIFESPAN_MINS = 90 BLOCKED_SECONDARY_ROLES = "
            + "('ALL') ALLOWED_SECONDARY_ROLES = ()");
        engine.execute("DESCRIBE SESSION POLICY sq1");
        engine.execute("DESCRIBE SESSION POLICY sq2");
        assertCells(new String[][] {
            {"SELECT GET_DDL('POLICY', 'sq1')",
                "create or replace session policy SQ1 SESSION_IDLE_TIMEOUT_MINS=30 "
                + "SESSION_UI_IDLE_TIMEOUT_MINS=1080 SESSION_MAX_LIFESPAN_MINS=0 SESSION_UI_MAX_LIFESPAN_MINS=0 "
                + "ALLOWED_SECONDARY_ROLES=('ALL') BLOCKED_SECONDARY_ROLES=() AGENT_RESTRICTED_SESSION_SCOPE=NULL ;"},
            {"SELECT GET_DDL('POLICY', 'sq2')",
                "create or replace session policy SQ2 SESSION_IDLE_TIMEOUT_MINS=240 SESSION_UI_IDLE_TIMEOUT_MINS=7 "
                + "SESSION_MAX_LIFESPAN_MINS=60 SESSION_UI_MAX_LIFESPAN_MINS=90 ALLOWED_SECONDARY_ROLES=() "
                + "BLOCKED_SECONDARY_ROLES=('ALL') AGENT_RESTRICTED_SESSION_SCOPE=NULL ;"},
        });
        engine.execute("SHOW SESSION POLICIES LIKE 'SQ%'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", \"comment\", \"owner_role_type\", "
                + "\"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY \"name\"",
                "SQ1|TEST_DB|TEST_SCHEMA|SESSION_POLICY|c|ROLE| ;; SQ2|TEST_DB|TEST_SCHEMA|SESSION_POLICY||ROLE|"},
            {"SHOW POLICIES LIKE 'SQ%'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'POLICIES'."},
        });
        assertShapedAnswer("REFUSED Query <QID> has no result because it failed",
            "SELECT \"name\", \"kind\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY \"name\"");
        assertCells(new String[][] {
            {"CREATE SESSION POLICY sq3 SESSION_IDLE_TIMEOUT_MINS = -1",
                "REFUSED SQL compilation error: | invalid value '-1' for property 'SESSION_IDLE_TIMEOUT_MINS'"},
            {"CREATE SESSION POLICY sq3 SESSION_IDLE_TIMEOUT_MINS = 1.5",
                "REFUSED SQL compilation error: | invalid value [1.5] for parameter 'SESSION_IDLE_TIMEOUT_MINS'"},
            {"CREATE SESSION POLICY sq3 SESSION_IDLE_TIMEOUT_MINS = 99999999",
                "REFUSED SQL compilation error: | invalid value '99999999' for property 'SESSION_IDLE_TIMEOUT_MINS'"},
            {"CREATE SESSION POLICY sq3 SESSION_UI_IDLE_TIMEOUT_MINS = 0",
                "REFUSED SQL compilation error: | invalid value '0' for property 'SESSION_UI_IDLE_TIMEOUT_MINS'"},
            {"CREATE SESSION POLICY sq3 SESSION_MAX_LIFESPAN_MINS = -1",
                "REFUSED SQL compilation error: | invalid value '-1' for property 'SESSION_MAX_LIFESPAN_MINS'"},
            {"CREATE SESSION POLICY sq3 ALLOWED_SECONDARY_ROLES = ('x')",
                "REFUSED SQL compilation error: | Role '[X]' does not exist or not authorized."},
            {"CREATE SESSION POLICY sq3 ALLOWED_SECONDARY_ROLES = 'ALL'",
                "REFUSED SQL compilation error: | invalid type of property ''ALL'' for 'ALLOWED_SECONDARY_ROLES'"},
            {"CREATE SESSION POLICY sq3 COMMENT = 1",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'COMMENT'"},
            {"CREATE SESSION POLICY sq3 SESSION_IDLE_TIMEOUT_MINS = 30 SESSION_IDLE_TIMEOUT_MINS = 40",
                "REFUSED SQL compilation error: | duplicate property 'SESSION_IDLE_TIMEOUT_MINS';"},
            {"ALTER SESSION POLICY sq1 SET SESSION_IDLE_TIMEOUT_MINS = 0",
                "REFUSED SQL compilation error: | invalid value '0' for property 'SESSION_IDLE_TIMEOUT_MINS'"},
            {"ALTER SESSION POLICY sq1 UNSET FOO",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'SESSION_POLICY'"},
            {"ALTER SESSION POLICY sq1 SET TAG nosuch_tag = 'x'",
                "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG."},
            {"ALTER SESSION POLICY sq1 RENAME TO sq2", "REFUSED SQL compilation error: | Object 'SQ2' already exists."},
            {"DROP SESSION POLICY sq1, sq2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','."},
            {"DESCRIBE SESSION POLICY nosuch",
                "REFUSED SQL compilation error: | Session policy 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH."},
            {"SELECT GET_DDL('POLICY', 'nosuch_sp')",
                "REFUSED SQL compilation error: | Policy 'TEST_DB.TEST_SCHEMA.NOSUCH_SP' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_SP."},
        });
        engine.execute("SHOW SESSION POLICIES IN DATABASE test_db");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "2"},
            {"UNDROP SESSION POLICY sq1", "REFUSED SQL compilation error: | Object 'SQ1' already exists."},
            {"CREATE SESSION POLICY t SESSION_IDLE_TIMEOUT_MINS = 30", "Session policy 'T' created successfully."},
            {"SELECT sq1", "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'SQ1'"},
        });
    }

    @Test
    public void itsDescriptionAndDdlFollowItsChanges() {
        engine.execute("CREATE OR REPLACE SESSION POLICY sq1 SESSION_IDLE_TIMEOUT_MINS = 30 COMMENT = 'c'");
        engine.execute("CREATE OR REPLACE SESSION POLICY sq2 SESSION_UI_IDLE_TIMEOUT_MINS = 7 "
            + "BLOCKED_SECONDARY_ROLES = ('ALL')");
        engine.execute("CREATE OR REPLACE PASSWORD POLICY pw1");
        engine.execute("CREATE OR REPLACE TAG tg1");
        engine.execute("DESCRIBE SESSION POLICY sq1");
        assertCells(new String[][] {
            {"SELECT \"property\", \"value\", \"default\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"property\" NOT IN ('CREATED_ON', 'OWNER')",
                "NAME|SQ1|null ;; OWNER_ROLE_TYPE|ROLE|null ;; COMMENT|c|null ;; SESSION_IDLE_TIMEOUT_MINS|30|240 "
                + ";; SESSION_UI_IDLE_TIMEOUT_MINS|1080|1080 ;; SESSION_MAX_LIFESPAN_MINS|0|0 ;; "
                + "SESSION_UI_MAX_LIFESPAN_MINS|0|0 ;; ALLOWED_SECONDARY_ROLES|[ALL]|null ;; "
                + "BLOCKED_SECONDARY_ROLES|[]|null ;; AGENT_RESTRICTED_SESSION_SCOPE|null|null"},
        });
        engine.execute("DESCRIBE SESSION POLICY sq2");
        assertCells(new String[][] {
            {"SELECT \"property\", \"value\", \"default\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"property\" NOT IN ('CREATED_ON', 'OWNER')",
                "NAME|SQ2|null ;; OWNER_ROLE_TYPE|ROLE|null ;; COMMENT|null|null ;; "
                + "SESSION_IDLE_TIMEOUT_MINS|240|240 ;; SESSION_UI_IDLE_TIMEOUT_MINS|7|1080 ;; "
                + "SESSION_MAX_LIFESPAN_MINS|0|0 ;; SESSION_UI_MAX_LIFESPAN_MINS|0|0 ;; "
                + "ALLOWED_SECONDARY_ROLES|[ALL]|null ;; BLOCKED_SECONDARY_ROLES|[ALL]|null ;; "
                + "AGENT_RESTRICTED_SESSION_SCOPE|null|null"},
            {"ALTER SESSION POLICY sq1 SET SESSION_IDLE_TIMEOUT_MINS = 45, ALLOWED_SECONDARY_ROLES = (), "
                + "BLOCKED_SECONDARY_ROLES = ('ALL')",
                "Statement executed successfully."},
        });
        engine.execute("DESCRIBE SESSION POLICY sq1");
        assertCells(new String[][] {
            {"SELECT \"property\", \"value\", \"default\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"property\" NOT IN ('CREATED_ON', 'OWNER')",
                "NAME|SQ1|null ;; OWNER_ROLE_TYPE|ROLE|null ;; COMMENT|c|null ;; SESSION_IDLE_TIMEOUT_MINS|45|240 "
                + ";; SESSION_UI_IDLE_TIMEOUT_MINS|1080|1080 ;; SESSION_MAX_LIFESPAN_MINS|0|0 ;; "
                + "SESSION_UI_MAX_LIFESPAN_MINS|0|0 ;; ALLOWED_SECONDARY_ROLES|[]|null ;; "
                + "BLOCKED_SECONDARY_ROLES|[ALL]|null ;; AGENT_RESTRICTED_SESSION_SCOPE|null|null"},
            {"SELECT GET_DDL('POLICY', 'sq1')",
                "create or replace session policy SQ1 SESSION_IDLE_TIMEOUT_MINS=45 "
                + "SESSION_UI_IDLE_TIMEOUT_MINS=1080 SESSION_MAX_LIFESPAN_MINS=0 SESSION_UI_MAX_LIFESPAN_MINS=0 "
                + "ALLOWED_SECONDARY_ROLES=() BLOCKED_SECONDARY_ROLES=('ALL') AGENT_RESTRICTED_SESSION_SCOPE=NULL ;"},
            {"ALTER SESSION POLICY sq1 UNSET ALLOWED_SECONDARY_ROLES, BLOCKED_SECONDARY_ROLES, "
                + "SESSION_IDLE_TIMEOUT_MINS, COMMENT",
                "Statement executed successfully."},
        });
        engine.execute("DESCRIBE SESSION POLICY sq1");
        assertCells(new String[][] {
            {"SELECT \"property\", \"value\", \"default\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"property\" NOT IN ('CREATED_ON', 'OWNER')",
                "NAME|SQ1|null ;; OWNER_ROLE_TYPE|ROLE|null ;; COMMENT|null|null ;; "
                + "SESSION_IDLE_TIMEOUT_MINS|240|240 ;; SESSION_UI_IDLE_TIMEOUT_MINS|1080|1080 ;; "
                + "SESSION_MAX_LIFESPAN_MINS|0|0 ;; SESSION_UI_MAX_LIFESPAN_MINS|0|0 ;; "
                + "ALLOWED_SECONDARY_ROLES|[ALL]|null ;; BLOCKED_SECONDARY_ROLES|[]|null ;; "
                + "AGENT_RESTRICTED_SESSION_SCOPE|null|null"},
            {"SELECT GET_DDL('POLICY', 'sq1')",
                "create or replace session policy SQ1 SESSION_IDLE_TIMEOUT_MINS=240 "
                + "SESSION_UI_IDLE_TIMEOUT_MINS=1080 SESSION_MAX_LIFESPAN_MINS=0 SESSION_UI_MAX_LIFESPAN_MINS=0 "
                + "ALLOWED_SECONDARY_ROLES=('ALL') BLOCKED_SECONDARY_ROLES=() AGENT_RESTRICTED_SESSION_SCOPE=NULL ;"},
            {"SELECT GET_DDL('POLICY', 'test_db.test_schema.sq2')",
                "create or replace session policy SQ2 SESSION_IDLE_TIMEOUT_MINS=240 SESSION_UI_IDLE_TIMEOUT_MINS=7 "
                + "SESSION_MAX_LIFESPAN_MINS=0 SESSION_UI_MAX_LIFESPAN_MINS=0 ALLOWED_SECONDARY_ROLES=('ALL') "
                + "BLOCKED_SECONDARY_ROLES=('ALL') AGENT_RESTRICTED_SESSION_SCOPE=NULL ;"},
            {"SELECT GET_DDL('POLICY', 'sq2', TRUE)",
                "create or replace session policy TEST_DB.TEST_SCHEMA.SQ2 SESSION_IDLE_TIMEOUT_MINS=240 "
                + "SESSION_UI_IDLE_TIMEOUT_MINS=7 SESSION_MAX_LIFESPAN_MINS=0 SESSION_UI_MAX_LIFESPAN_MINS=0 "
                + "ALLOWED_SECONDARY_ROLES=('ALL') BLOCKED_SECONDARY_ROLES=('ALL') "
                + "AGENT_RESTRICTED_SESSION_SCOPE=NULL ;"},
        });
        engine.execute("SHOW SESSION POLICIES");
        assertCells(new String[][] {
            {"SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", \"comment\", \"owner_role_type\", "
                + "\"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "SQ1|TEST_DB|TEST_SCHEMA|SESSION_POLICY||ROLE| ;; SQ2|TEST_DB|TEST_SCHEMA|SESSION_POLICY||ROLE|"},
        });
        engine.execute("SHOW TERSE SESSION POLICIES LIKE 'SQ%'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", \"comment\", \"owner_role_type\", "
                + "\"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "SQ1|TEST_DB|TEST_SCHEMA|SESSION_POLICY||ROLE| ;; SQ2|TEST_DB|TEST_SCHEMA|SESSION_POLICY||ROLE|"},
        });
        engine.execute("SHOW SESSION POLICIES LIMIT 1");
        assertCells(new String[][] {
            {"SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", \"comment\", \"owner_role_type\", "
                + "\"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "SQ1|TEST_DB|TEST_SCHEMA|SESSION_POLICY||ROLE|"},
        });
        engine.execute("SHOW SESSION POLICIES LIMIT 1 FROM 'SQ1'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", \"comment\", \"owner_role_type\", "
                + "\"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "SQ1|TEST_DB|TEST_SCHEMA|SESSION_POLICY||ROLE|"},
        });
        engine.execute("SHOW SESSION POLICIES IN DATABASE test_db");
        assertCells(new String[][] {
            {"SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", \"comment\", \"owner_role_type\", "
                + "\"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "SQ1|TEST_DB|TEST_SCHEMA|SESSION_POLICY||ROLE| ;; SQ2|TEST_DB|TEST_SCHEMA|SESSION_POLICY||ROLE|"},
        });
        engine.execute("SHOW SESSION POLICIES IN SCHEMA test_schema");
        assertCells(new String[][] {
            {"SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", \"comment\", \"owner_role_type\", "
                + "\"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "SQ1|TEST_DB|TEST_SCHEMA|SESSION_POLICY||ROLE| ;; SQ2|TEST_DB|TEST_SCHEMA|SESSION_POLICY||ROLE|"},
        });
        engine.execute("DESCRIBE SESSION POLICY test_db.test_schema.sq2");
        assertCells(new String[][] {
            {"SELECT \"property\", \"value\", \"default\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "
                + "\"property\" NOT IN ('CREATED_ON', 'OWNER')",
                "NAME|SQ2|null ;; OWNER_ROLE_TYPE|ROLE|null ;; COMMENT|null|null ;; "
                + "SESSION_IDLE_TIMEOUT_MINS|240|240 ;; SESSION_UI_IDLE_TIMEOUT_MINS|7|1080 ;; "
                + "SESSION_MAX_LIFESPAN_MINS|0|0 ;; SESSION_UI_MAX_LIFESPAN_MINS|0|0 ;; "
                + "ALLOWED_SECONDARY_ROLES|[ALL]|null ;; BLOCKED_SECONDARY_ROLES|[ALL]|null ;; "
                + "AGENT_RESTRICTED_SESSION_SCOPE|null|null"},
            {"CREATE PASSWORD POLICY pw2 PASSWORD_MIN_LENGTH = 1.5",
                "REFUSED SQL compilation error: | invalid value [1.5] for parameter 'PASSWORD_MIN_LENGTH'"},
            {"CREATE SESSION POLICY sq3 ALLOWED_SECONDARY_ROLES = ('x', 'y')",
                "REFUSED SQL compilation error: | Role '[X, Y]' does not exist or not authorized."},
            {"CREATE SESSION POLICY sq3 ALLOWED_SECONDARY_ROLES = ('ALL', 'x')",
                "REFUSED SQL compilation error: | invalid value [[ALL, x]] for parameter 'ALLOWED_SECONDARY_ROLES'"},
            {"CREATE SESSION POLICY sq3 BLOCKED_SECONDARY_ROLES = ('nosuch_r')",
                "REFUSED SQL compilation error: | Role '[NOSUCH_R]' does not exist or not authorized."},
            {"CREATE MASKING POLICY sq1 AS (v INT) RETURNS INT -> v",
                "REFUSED SQL compilation error: | Object 'SQ1' already exists."},
            {"CREATE OR REPLACE SESSION POLICY pw1",
                "REFUSED SQL compilation error: Object found is of type 'PASSWORD_POLICY', not specified type "
                + "'SESSION_POLICY'."},
            {"CREATE OR REPLACE PASSWORD POLICY sq1",
                "REFUSED SQL compilation error: Object found is of type 'SESSION_POLICY', not specified type "
                + "'PASSWORD_POLICY'."},
            {"ALTER SESSION POLICY sq2 RENAME TO pw1", "REFUSED SQL compilation error: | Object 'PW1' already exists."},
            {"CREATE PASSWORD POLICY sq2", "REFUSED SQL compilation error: | Object 'SQ2' already exists."},
            {"ALTER SESSION POLICY sq1 SET SESSION_UI_IDLE_TIMEOUT_MINS = 1441",
                "REFUSED SQL compilation error: | invalid value '1441' for property 'SESSION_UI_IDLE_TIMEOUT_MINS'"},
            {"ALTER SESSION POLICY sq1 SET SESSION_MAX_LIFESPAN_MINS = 43200", "Statement executed successfully."},
            {"ALTER SESSION POLICY sq1 SET SESSION_UI_MAX_LIFESPAN_MINS = 43201",
                "REFUSED SQL compilation error: | invalid value '43201' for property 'SESSION_UI_MAX_LIFESPAN_MINS'"},
            {"ALTER SESSION POLICY sq1 SET TAG tg1 = 'v'", "Statement executed successfully."},
            {"SELECT SYSTEM$GET_TAG('tg1', 'sq1', 'SESSION POLICY')", "v"},
            {"ALTER SESSION POLICY sq1 UNSET TAG tg1", "Statement executed successfully."},
            {"CREATE SESSION POLICY \"Sq5\"", "Session policy 'Sq5' created successfully."},
        });
        engine.execute("SHOW SESSION POLICIES LIKE 'Sq5'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", \"comment\", \"owner_role_type\", "
                + "\"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "Sq5|TEST_DB|TEST_SCHEMA|SESSION_POLICY||ROLE|"},
            {"DROP SESSION POLICY \"Sq5\"", "Sq5 successfully dropped."},
            {"DROP SESSION POLICY sq2", "SQ2 successfully dropped."},
            {"UNDROP SESSION POLICY sq2",
                "REFUSED Unsupported feature 'UNDROP not supported for objects of type SESSION_POLICY'."},
            {"UNDROP SESSION POLICY nosuch_sp",
                "REFUSED Unsupported feature 'UNDROP not supported for objects of type SESSION_POLICY'."},
            {"DROP SESSION POLICY sq1 CASCADE", "SQ1 successfully dropped."},
            {"DROP SESSION POLICY IF EXISTS sq1 RESTRICT",
                "Drop statement executed successfully (SQ1 already dropped)."},
        });
    }
}
