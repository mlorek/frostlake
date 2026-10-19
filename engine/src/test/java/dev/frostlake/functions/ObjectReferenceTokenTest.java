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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SYSTEM$REFERENCE answers a token for a table, a view, a stream or a warehouse - ENT_REF_ and the domain, sixteen
 * decimal digits and forty hexadecimal ones, new for every call - after judging its scope, its domain, its
 * privileges and the object in that order, each refusal in the account's words; SYSTEM$STREAM_BACKLOG is no scalar
 * function (all live-verified).
 */
public class ObjectReferenceTokenTest extends MeasuredCellsTestSupport {

    @Test
    public void aReferenceIsATokenForAnObjectThatIsThere() {
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("INSERT INTO tsr VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM ssr ON TABLE tsr");
        assertAnswer("true", "SELECT LENGTH(SYSTEM$REFERENCE('TABLE', 'tsr')) > 0");
        assertAnswer("VARCHAR(1)[LOB]", "SELECT SYSTEM$TYPEOF(SYSTEM$REFERENCE('TABLE', 'tsr'))");
        assertAnswer("false", "SELECT SYSTEM$REFERENCE('TABLE', 'tsr') = SYSTEM$REFERENCE('TABLE', 'tsr')");
        assertAnswer("ENT_", "SELECT LEFT(SYSTEM$REFERENCE('TABLE', 'tsr'), 4)");
        assertAnswer("REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "SELECT SYSTEM$REFERENCE('TABLE', 'nosuch_t')");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'SELECT') IS NOT NULL");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'SELECT') IS NOT NULL");
        assertAnswer("REFUSED Object type TABLE does not match the specified type VIEW for reference creation",
            "SELECT SYSTEM$REFERENCE('VIEW', 'tsr') IS NOT NULL");
        assertAnswer("true", "BEGIN RETURN LENGTH(SYSTEM$REFERENCE('TABLE', 'tsr')) > 0; END;");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$STREAM_BACKLOG.",
            "SELECT SYSTEM$STREAM_BACKLOG('ssr')");
    }

    @Test
    public void aReferencesArgumentsAreJudgedInOrder() {
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("INSERT INTO tsr VALUES (1)");
        engine.execute("CREATE OR REPLACE VIEW vsr AS SELECT a FROM tsr");
        engine.execute("CREATE OR REPLACE STREAM ssr ON TABLE tsr");
        assertAnswer("xNT_Rxx_TxxLx_xxxxxxxxxxxxxxxx_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "SELECT REGEXP_REPLACE(SYSTEM$REFERENCE('TABLE', 'tsr'), '[0-9A-Fa-f]', 'x')");
        assertAnswer("71", "SELECT LENGTH(SYSTEM$REFERENCE('TABLE', 'tsr'))");
        assertAnswer("xNT_Rxx_TxxLx_xxxxxxxxxxxxxxxx_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "SELECT REGEXP_REPLACE(SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'SELECT'), '[0-9A-Fa-f]', 'x')");
        assertAnswer("xNT_Rxx_VIxW_xxxxxxxxxxxxxxxx_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "SELECT REGEXP_REPLACE(SYSTEM$REFERENCE('VIEW', 'vsr'), '[0-9A-Fa-f]', 'x')");
        assertAnswer("xNT_Rxx_TxxLx_xxxxxxxxxxxxxxxx_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "SELECT REGEXP_REPLACE(SYSTEM$REFERENCE('table', 'test_db.test_schema.tsr'), '[0-9A-Fa-f]', 'x')");
        assertAnswer("REFUSED Scope NOSUCH not valid. Scope must be SESSION, CALL or PERSISTENT.",
            "SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'NOSUCH')");
        assertAnswer("REFUSED Domain NOSUCH not valid for reference creation with scope SESSION",
            "SELECT SYSTEM$REFERENCE('NOSUCH', 'tsr')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$REFERENCE('TABLE')], expected 2, got 1",
            "SELECT SYSTEM$REFERENCE('TABLE')");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | invalid number of arguments for "
                + "[1], expected 2, got {4}",
            "SELECT SYSTEM$REFERENCE(NULL, 'tsr')");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | invalid number of arguments for "
                + "[2], expected 2, got {4}",
            "SELECT SYSTEM$REFERENCE('TABLE', NULL)");
        assertAnswer("REFUSED Object type VIEW does not match the specified type TABLE for reference creation",
            "SELECT SYSTEM$REFERENCE('TABLE', 'vsr')");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('STREAM', 'ssr') IS NOT NULL");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'INSERT', 'SELECT') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 4 | Invalid privilege NOSUCH.",
            "SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'NOSUCH')");
    }

    @Test
    public void aReferencesRefusalsNameWhatIsWrong() {
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("CREATE OR REPLACE VIEW vsr AS SELECT a FROM tsr");
        engine.execute("CREATE OR REPLACE STREAM ssr ON TABLE tsr");
        answer("SELECT REGEXP_REPLACE(SYSTEM$REFERENCE('TABLE', 'tsr'), '[0-9a-f]', 'x')");
        assertAnswer("xNT_Rxx_TxxLx_xxxxxxxxxxxxxxxx_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "SELECT REGEXP_REPLACE(SYSTEM$REFERENCE('TABLE', 'tsr'), '[0-9A-F]', 'x')");
        answer("SELECT REGEXP_REPLACE(SYSTEM$REFERENCE('STREAM', 'ssr'), '[0-9a-f]', 'x')");
        answer("SELECT REGEXP_REPLACE(SYSTEM$REFERENCE('VIEW', 'vsr'), '[0-9a-f]', 'x')");
        assertAnswer("REFUSED Scope NOSUCH not valid. Scope must be SESSION, CALL or PERSISTENT.",
            "SELECT SYSTEM$REFERENCE('NOSUCH', 'tsr', 'NOSUCH')");
        assertAnswer("REFUSED Scope NOSUCH not valid. Scope must be SESSION, CALL or PERSISTENT.",
            "SELECT SYSTEM$REFERENCE('TABLE', 'nosuch_t', 'NOSUCH')");
        assertAnswer("REFUSED Domain NOSUCH not valid for reference creation with scope SESSION",
            "SELECT SYSTEM$REFERENCE('NOSUCH', 'nosuch_t')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 4 | Invalid privilege NOSUCH.",
            "SELECT SYSTEM$REFERENCE('TABLE', 'nosuch_t', 'SESSION', 'NOSUCH')");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('table', 'tsr', 'session', 'select') IS NOT NULL");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'PERSISTENT') IS NOT NULL");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL') IS NOT NULL");
        answer("SELECT SYSTEM$REFERENCE('TABLE', 'tsr', NULL)");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | invalid number of arguments for "
                + "[4], expected 4, got {4}",
            "SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', NULL)");
        assertAnswer("true",
            "SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'SELECT', 'INSERT', 'UPDATE') IS NOT NULL");
        assertAnswer("REFUSED No default privileges defined for reference domain MATERIALIZED_VIEW. Please provide "
                + "allowed privileges during reference creation.",
            "SELECT SYSTEM$REFERENCE('MATERIALIZED VIEW', 'tsr')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 3 unexpected '<EOF>'.",
            "SELECT SYSTEM$REFERENCE('FUNCTION', 'tsr')");
        assertAnswer("REFUSED Domain EXTERNAL TABLE not valid for reference creation with scope SESSION",
            "SELECT SYSTEM$REFERENCE('EXTERNAL TABLE', 'tsr')");
        assertAnswer("REFUSED SQL compilation error: | Warehouse 'NOSUCH_WH' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on WAREHOUSE NOSUCH_WH.",
            "SELECT SYSTEM$REFERENCE('WAREHOUSE', 'nosuch_wh')");
        assertAnswer("REFUSED A reference cannot be created with OWNERSHIP privilege.",
            "SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'OWNERSHIP') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Invalid object type 'TABLE' for privilege 'USAGE'.",
            "SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'USAGE')");
        assertAnswer("REFUSED Privilege 'INSERT' is not allowed on domain 'VIEW' during 'SESSION' reference creation.",
            "SELECT SYSTEM$REFERENCE('VIEW', 'vsr', 'SESSION', 'INSERT')");
        assertAnswer("REFUSED SQL compilation error: | Table '\"tsr\"' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE \"tsr\".",
            "SELECT SYSTEM$REFERENCE('TABLE', '\"tsr\"')");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('TABLE', 'TEST_SCHEMA.TSR') IS NOT NULL");
        answer("SELECT SYSTEM$REFERENCE(1, 'tsr')");
        assertAnswer("", "SELECT SYSTEM$REFERENCE('TABLE', 'tsr') FROM tsr");
        assertAnswer("VARCHAR(1)[LOB]", "SELECT SYSTEM$TYPEOF(SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL'))");
    }
}
