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
 * A session policy's AGENT_RESTRICTED_SESSION_SCOPE (live-verified): a predefined scope, written as a string
 * kept as written or as a word as it resolves, is set, shown by DESCRIBE and quoted by GET_DDL; a fully
 * qualified name names a scope that does not exist, anything else is no valid scope, and a list, a number and a
 * boolean are no value of it, while NULL and UNSET take it away. The policy's comment takes NULL too, and a
 * number refuses it.
 */
public class SessionPolicyAgentScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void aScopeIsOneOfThePredefinedOnes() {
        engine.execute("CREATE OR REPLACE TABLE dt1 (a INT)");
        engine.execute("CREATE OR REPLACE VIEW dv1 AS SELECT 1 AS a");
        engine.execute("CREATE OR REPLACE SESSION POLICY sp_m");
        assertCells(new String[][] {
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = 'x'",
                "REFUSED SQL compilation error: 'x' is not a valid RESTRICTED_SESSION_SCOPE value. Expected inline "
                + "YAML, a fully-qualified name (db.schema.name), or one of the predefined names: "
                + "SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = 'SNOWFLAKE$DATA_READ'",
                "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sp_m ->> SELECT \"property\", \"value\", \"default\" FROM $1 WHERE \"property\" "
                + "= 'AGENT_RESTRICTED_SESSION_SCOPE'",
                "AGENT_RESTRICTED_SESSION_SCOPE|SNOWFLAKE$DATA_READ|null"},
            {"SELECT GET_DDL('POLICY', 'sp_m')",
                "create or replace session policy SP_M SESSION_IDLE_TIMEOUT_MINS=240 "
                + "SESSION_UI_IDLE_TIMEOUT_MINS=1080 SESSION_MAX_LIFESPAN_MINS=0 SESSION_UI_MAX_LIFESPAN_MINS=0 "
                + "ALLOWED_SECONDARY_ROLES=('ALL') BLOCKED_SECONDARY_ROLES=() "
                + "AGENT_RESTRICTED_SESSION_SCOPE='SNOWFLAKE$DATA_READ' ;"},
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = 'snowflake$data_read'",
                "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sp_m ->> SELECT \"property\", \"value\", \"default\" FROM $1 WHERE \"property\" "
                + "= 'AGENT_RESTRICTED_SESSION_SCOPE'",
                "AGENT_RESTRICTED_SESSION_SCOPE|snowflake$data_read|null"},
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = 'SNOWFLAKE$DATA_READ_WITH_AI'",
                "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sp_m ->> SELECT \"property\", \"value\", \"default\" FROM $1 WHERE \"property\" "
                + "= 'AGENT_RESTRICTED_SESSION_SCOPE'",
                "AGENT_RESTRICTED_SESSION_SCOPE|SNOWFLAKE$DATA_READ_WITH_AI|null"},
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = 'SNOWFLAKE$DATA_READ_PROGRAM_USAGE'",
                "Statement executed successfully."},
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = 'test_db.test_schema.nosuch_scope'",
                "REFUSED SQL compilation error: | Restricted Session Scope 'test_db.test_schema.nosuch_scope' does "
                + "not exist or not authorized."},
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = 'nosuch_scope'",
                "REFUSED SQL compilation error: 'nosuch_scope' is not a valid RESTRICTED_SESSION_SCOPE value. "
                + "Expected inline YAML, a fully-qualified name (db.schema.name), or one of the predefined names: "
                + "SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = 'a: b'",
                "REFUSED SQL compilation error: 'a: b' is not a valid RESTRICTED_SESSION_SCOPE value. Expected "
                + "inline YAML, a fully-qualified name (db.schema.name), or one of the predefined names: "
                + "SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = ''",
                "REFUSED SQL compilation error: '' is not a valid RESTRICTED_SESSION_SCOPE value. Expected inline "
                + "YAML, a fully-qualified name (db.schema.name), or one of the predefined names: "
                + "SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = SNOWFLAKE$DATA_READ",
                "Statement executed successfully."},
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = 1",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'AGENT_RESTRICTED_SESSION_SCOPE'"},
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = NULL", "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sp_m ->> SELECT \"property\", \"value\", \"default\" FROM $1 WHERE \"property\" "
                + "= 'AGENT_RESTRICTED_SESSION_SCOPE'",
                "AGENT_RESTRICTED_SESSION_SCOPE|null|null"},
            {"ALTER SESSION POLICY sp_m SET AGENT_RESTRICTED_SESSION_SCOPE = 'SNOWFLAKE$DATA_READ'",
                "Statement executed successfully."},
            {"ALTER SESSION POLICY sp_m UNSET AGENT_RESTRICTED_SESSION_SCOPE", "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sp_m ->> SELECT \"property\", \"value\", \"default\" FROM $1 WHERE \"property\" "
                + "= 'AGENT_RESTRICTED_SESSION_SCOPE'",
                "AGENT_RESTRICTED_SESSION_SCOPE|null|null"},
            {"CREATE OR REPLACE SESSION POLICY sp_n AGENT_RESTRICTED_SESSION_SCOPE = 'SNOWFLAKE$DATA_READ_WITH_AI'",
                "Session policy 'SP_N' created successfully."},
            {"DESCRIBE SESSION POLICY sp_n ->> SELECT \"property\", \"value\", \"default\" FROM $1 WHERE \"property\" "
                + "= 'AGENT_RESTRICTED_SESSION_SCOPE'",
                "AGENT_RESTRICTED_SESSION_SCOPE|SNOWFLAKE$DATA_READ_WITH_AI|null"},
            {"SELECT GET_DDL('POLICY', 'sp_n')",
                "create or replace session policy SP_N SESSION_IDLE_TIMEOUT_MINS=240 "
                + "SESSION_UI_IDLE_TIMEOUT_MINS=1080 SESSION_MAX_LIFESPAN_MINS=0 SESSION_UI_MAX_LIFESPAN_MINS=0 "
                + "ALLOWED_SECONDARY_ROLES=('ALL') BLOCKED_SECONDARY_ROLES=() "
                + "AGENT_RESTRICTED_SESSION_SCOPE='SNOWFLAKE$DATA_READ_WITH_AI' ;"},
            {"SHOW SESSION POLICIES LIKE 'SP_N' ->> SELECT \"options\" FROM $1", ""},
            {"CREATE OR REPLACE SESSION POLICY sp_o AGENT_RESTRICTED_SESSION_SCOPE = 'x'",
                "REFUSED SQL compilation error: 'x' is not a valid RESTRICTED_SESSION_SCOPE value. Expected inline "
                + "YAML, a fully-qualified name (db.schema.name), or one of the predefined names: "
                + "SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
        });
    }

    @Test
    public void aScopeIsWrittenAsAWordOrAString() {
        engine.execute("CREATE OR REPLACE SESSION POLICY sp_r");
        engine.execute("CREATE OR REPLACE TABLE t_r (a INT)");
        assertCells(new String[][] {
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = SNOWFLAKE$DATA_READ",
                "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sp_r ->> SELECT \"value\" FROM $1 WHERE \"property\" = "
                + "'AGENT_RESTRICTED_SESSION_SCOPE'",
                "SNOWFLAKE$DATA_READ"},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = snowflake$data_read",
                "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sp_r ->> SELECT \"value\" FROM $1 WHERE \"property\" = "
                + "'AGENT_RESTRICTED_SESSION_SCOPE'",
                "SNOWFLAKE$DATA_READ"},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = \"SNOWFLAKE$DATA_READ\"",
                "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sp_r ->> SELECT \"value\" FROM $1 WHERE \"property\" = "
                + "'AGENT_RESTRICTED_SESSION_SCOPE'",
                "SNOWFLAKE$DATA_READ"},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = nosuch_word",
                "REFUSED SQL compilation error: 'NOSUCH_WORD' is not a valid RESTRICTED_SESSION_SCOPE value. "
                + "Expected inline YAML, a fully-qualified name (db.schema.name), or one of the predefined names: "
                + "SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = 'test_db.test_schema.t_r'",
                "REFUSED SQL compilation error: | Restricted Session Scope 'test_db.test_schema.t_r' does not exist "
                + "or not authorized."},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = 'test_schema.nosuch_scope'",
                "REFUSED SQL compilation error: 'test_schema.nosuch_scope' is not a valid RESTRICTED_SESSION_SCOPE "
                + "value. Expected inline YAML, a fully-qualified name (db.schema.name), or one of the predefined "
                + "names: SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = 'nosuch_db.s.x'",
                "REFUSED SQL compilation error: | Restricted Session Scope 'nosuch_db.s.x' does not exist or not "
                + "authorized."},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = 'TEST_DB.TEST_SCHEMA.NOSUCH_SCOPE'",
                "REFUSED SQL compilation error: | Restricted Session Scope 'TEST_DB.TEST_SCHEMA.NOSUCH_SCOPE' does "
                + "not exist or not authorized."},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = 'test_db.test_schema.\"x y\"'",
                "REFUSED SQL compilation error: | Restricted Session Scope 'test_db.test_schema.\"x y\"' does not "
                + "exist or not authorized."},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = ' SNOWFLAKE$DATA_READ'",
                "REFUSED SQL compilation error: ' SNOWFLAKE$DATA_READ' is not a valid RESTRICTED_SESSION_SCOPE "
                + "value. Expected inline YAML, a fully-qualified name (db.schema.name), or one of the predefined "
                + "names: SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = 'SNOWFLAKE$DATA_READ '",
                "REFUSED SQL compilation error: 'SNOWFLAKE$DATA_READ ' is not a valid RESTRICTED_SESSION_SCOPE "
                + "value. Expected inline YAML, a fully-qualified name (db.schema.name), or one of the predefined "
                + "names: SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = 'SNOWFLAKE$NOSUCH'",
                "REFUSED SQL compilation error: 'SNOWFLAKE$NOSUCH' is not a valid RESTRICTED_SESSION_SCOPE value. "
                + "Expected inline YAML, a fully-qualified name (db.schema.name), or one of the predefined names: "
                + "SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = TRUE",
                "REFUSED SQL compilation error: | invalid value [TRUE] for parameter 'AGENT_RESTRICTED_SESSION_SCOPE'"},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = 1.5",
                "REFUSED SQL compilation error: | invalid value [1.5] for parameter 'AGENT_RESTRICTED_SESSION_SCOPE'"},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = ('SNOWFLAKE$DATA_READ')",
                "REFUSED Invalid value specified for property 'AGENT_RESTRICTED_SESSION_SCOPE'"},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = 'SNOWFLAKE$DATA_READ' "
                + "SESSION_IDLE_TIMEOUT_MINS = 30",
                "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sp_r ->> SELECT \"value\" FROM $1 WHERE \"property\" = "
                + "'AGENT_RESTRICTED_SESSION_SCOPE'",
                "SNOWFLAKE$DATA_READ"},
            {"ALTER SESSION POLICY sp_r SET AGENT_RESTRICTED_SESSION_SCOPE = 'x' SESSION_IDLE_TIMEOUT_MINS = 0",
                "REFUSED SQL compilation error: | invalid value '0' for property 'SESSION_IDLE_TIMEOUT_MINS'"},
            {"ALTER SESSION POLICY sp_r SET SESSION_IDLE_TIMEOUT_MINS = 0 AGENT_RESTRICTED_SESSION_SCOPE = 'x'",
                "REFUSED SQL compilation error: | invalid value '0' for property 'SESSION_IDLE_TIMEOUT_MINS'"},
            {"SHOW SESSION POLICIES LIKE 'SP_R' ->> SELECT \"options\" FROM $1", ""},
            {"SELECT GET_DDL('POLICY', 'sp_r')",
                "create or replace session policy SP_R SESSION_IDLE_TIMEOUT_MINS=30 "
                + "SESSION_UI_IDLE_TIMEOUT_MINS=1080 SESSION_MAX_LIFESPAN_MINS=0 SESSION_UI_MAX_LIFESPAN_MINS=0 "
                + "ALLOWED_SECONDARY_ROLES=('ALL') BLOCKED_SECONDARY_ROLES=() "
                + "AGENT_RESTRICTED_SESSION_SCOPE='SNOWFLAKE$DATA_READ' ;"},
            {"ALTER SESSION POLICY sp_r UNSET AGENT_RESTRICTED_SESSION_SCOPE, SESSION_IDLE_TIMEOUT_MINS",
                "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sp_r ->> SELECT \"value\" FROM $1 WHERE \"property\" = "
                + "'AGENT_RESTRICTED_SESSION_SCOPE'",
                "null"},
            {"CREATE OR REPLACE SESSION POLICY sp_s AGENT_RESTRICTED_SESSION_SCOPE = NULL",
                "Session policy 'SP_S' created successfully."},
            {"CREATE OR REPLACE SESSION POLICY sp_s AGENT_RESTRICTED_SESSION_SCOPE = 1",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'AGENT_RESTRICTED_SESSION_SCOPE'"},
        });
    }

    @Test
    public void aQuotedWordAndNullAreValues() {
        engine.execute("CREATE OR REPLACE SESSION POLICY sr");
        assertCells(new String[][] {
            {"ALTER SESSION POLICY sr SET AGENT_RESTRICTED_SESSION_SCOPE = \"snowflake$data_read\"",
                "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sr ->> SELECT \"value\" FROM $1 WHERE \"property\" = "
                + "'AGENT_RESTRICTED_SESSION_SCOPE'",
                "snowflake$data_read"},
            {"ALTER SESSION POLICY sr SET AGENT_RESTRICTED_SESSION_SCOPE = 'Snowflake$Data_Read_With_AI'",
                "Statement executed successfully."},
            {"DESCRIBE SESSION POLICY sr ->> SELECT \"value\" FROM $1 WHERE \"property\" = "
                + "'AGENT_RESTRICTED_SESSION_SCOPE'",
                "Snowflake$Data_Read_With_AI"},
            {"SELECT GET_DDL('POLICY', 'sr')",
                "create or replace session policy SR SESSION_IDLE_TIMEOUT_MINS=240 SESSION_UI_IDLE_TIMEOUT_MINS=1080 "
                + "SESSION_MAX_LIFESPAN_MINS=0 SESSION_UI_MAX_LIFESPAN_MINS=0 ALLOWED_SECONDARY_ROLES=('ALL') "
                + "BLOCKED_SECONDARY_ROLES=() AGENT_RESTRICTED_SESSION_SCOPE='Snowflake$Data_Read_With_AI' ;"},
            {"ALTER SESSION POLICY sr SET AGENT_RESTRICTED_SESSION_SCOPE = 'it''s'",
                "REFUSED SQL compilation error: 'it's' is not a valid RESTRICTED_SESSION_SCOPE value. Expected "
                + "inline YAML, a fully-qualified name (db.schema.name), or one of the predefined names: "
                + "SNOWFLAKE$DATA_READ, SNOWFLAKE$DATA_READ_PROGRAM_USAGE, SNOWFLAKE$DATA_READ_WITH_AI."},
            {"ALTER SESSION POLICY sr SET SESSION_IDLE_TIMEOUT_MINS = NULL",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'SESSION_IDLE_TIMEOUT_MINS'"},
            {"ALTER SESSION POLICY sr SET COMMENT = NULL", "Statement executed successfully."},
            {"ALTER SESSION POLICY sr SET AGENT_RESTRICTED_SESSION_SCOPE = -1",
                "REFUSED SQL compilation error: | invalid value [-1] for parameter 'AGENT_RESTRICTED_SESSION_SCOPE'"},
        });
    }
}
