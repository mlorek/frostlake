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
 * GET_DDL of an object it does not find is refused in the sentence of the kind asked for, and a handler reads
 * 2003 with SQLSTATE 02000 for it whatever the kind, a routine named by its signature 2001 (all live-verified).
 */
public class GetDdlMissingObjectTest extends MeasuredCellsTestSupport {

    @Test
    public void missingObjects() {
        engine.execute("CREATE OR REPLACE TABLE gd672t (a INT)");
        engine.execute("CREATE OR REPLACE VIEW gd672v AS SELECT 1 AS a");
        assertAnswer("REFUSED SQL compilation error: | Table 'NO_SUCH_T672' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_T672.",
            "SELECT GET_DDL('TABLE', 'no_such_t672')");
        assertAnswer("REFUSED SQL compilation error: | View 'NO_SUCH_V672' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_V672.",
            "SELECT GET_DDL('VIEW', 'no_such_v672')");
        assertAnswer("REFUSED SQL compilation error: | Sequence 'NO_SUCH_S672' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on SEQUENCE NO_SUCH_S672.",
            "SELECT GET_DDL('SEQUENCE', 'no_such_s672')");
        assertAnswer("REFUSED SQL compilation error: | Schema 'TEST_DB.NO_SUCH_SC672' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NO_SUCH_SC672.",
            "SELECT GET_DDL('SCHEMA', 'no_such_sc672')");
        assertAnswer("REFUSED SQL compilation error: | Database 'NO_SUCH_DB672' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NO_SUCH_DB672.",
            "SELECT GET_DDL('DATABASE', 'no_such_db672')");
        assertAnswer("REFUSED SQL compilation error: | File format 'NO_SUCH_FF672' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT NO_SUCH_FF672.",
            "SELECT GET_DDL('FILE_FORMAT', 'no_such_ff672')");
        assertAnswer("REFUSED SQL compilation error: | Object 'no_such_f672()' does not exist or not authorized.",
            "SELECT GET_DDL('FUNCTION', 'no_such_f672()')");
        assertAnswer("REFUSED SQL compilation error: | Object 'no_such_p672()' does not exist or not authorized.",
            "SELECT GET_DDL('PROCEDURE', 'no_such_p672()')");
        assertAnswer("REFUSED SQL compilation error: | Stream 'NO_SUCH_SM672' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on STREAM NO_SUCH_SM672.",
            "SELECT GET_DDL('STREAM', 'no_such_sm672')");
        assertAnswer("REFUSED SQL compilation error: | Task 'NO_SUCH_TK672' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TASK NO_SUCH_TK672.",
            "SELECT GET_DDL('TASK', 'no_such_tk672')");
        assertAnswer("REFUSED SQL compilation error: | Pipe 'TEST_DB.TEST_SCHEMA.NO_SUCH_PP672' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PIPE "
                + "TEST_DB.TEST_SCHEMA.NO_SUCH_PP672.",
            "SELECT GET_DDL('PIPE', 'no_such_pp672')");
        assertAnswer("REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NO_SUCH_DT672' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "TABLE TEST_DB.TEST_SCHEMA.NO_SUCH_DT672.",
            "SELECT GET_DDL('DYNAMIC_TABLE', 'no_such_dt672')");
        assertAnswer("REFUSED Unsupported feature 'EVENT_TABLE'.", "SELECT GET_DDL('EVENT_TABLE', 'no_such_et672')");
        assertAnswer("REFUSED SQL compilation error: | Iceberg table 'TEST_DB.TEST_SCHEMA.NO_SUCH_IT672' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "TABLE TEST_DB.TEST_SCHEMA.NO_SUCH_IT672.",
            "SELECT GET_DDL('ICEBERG_TABLE', 'no_such_it672')");
        assertAnswer("REFUSED SQL compilation error: | Policy 'TEST_DB.TEST_SCHEMA.NO_SUCH_PO672' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NO_SUCH_PO672.",
            "SELECT GET_DDL('POLICY', 'no_such_po672')");
        assertAnswer("REFUSED SQL compilation error: | Tag 'NO_SUCH_TG672' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TAG NO_SUCH_TG672.",
            "SELECT GET_DDL('TAG', 'no_such_tg672')");
        assertAnswer("REFUSED SQL compilation error: | Alert 'NO_SUCH_AL672' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ALERT NO_SUCH_AL672.",
            "SELECT GET_DDL('ALERT', 'no_such_al672')");
        assertAnswer("REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.NO_SUCH_ST672' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.NO_SUCH_ST672.",
            "SELECT GET_DDL('STAGE', 'no_such_st672')");
        assertAnswer("REFUSED SQL compilation error: | Warehouse 'NO_SUCH_WH672' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on WAREHOUSE NO_SUCH_WH672.",
            "SELECT GET_DDL('WAREHOUSE', 'no_such_wh672')");
        assertAnswer("REFUSED SQL compilation error: | Schema 'TEST_DB.NO_SUCH_SC672' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NO_SUCH_SC672.",
            "SELECT GET_DDL('TABLE', 'no_such_sc672.no_such_t672')");
        assertAnswer("REFUSED SQL compilation error: | Database 'NO_SUCH_DB672' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NO_SUCH_DB672.",
            "SELECT GET_DDL('TABLE', 'no_such_db672.public.no_such_t672')");
        assertAnswer("create or replace view GD672V( | 	A | ) as SELECT 1 AS a;", "SELECT GET_DDL('TABLE', 'gd672v')");
        assertAnswer("create or replace TABLE GD672T ( | 	A NUMBER(38,0) | );", "SELECT GET_DDL('VIEW', 'gd672t')");
        assertAnswer("REFUSED SQL compilation error: | Table 'NO_SUCH_T672' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_T672.",
            "SELECT GET_DDL('table', 'no_such_t672')");
        assertAnswer("REFUSED SQL compilation error: | Table 'NO_SUCH_T672' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_T672.",
            "SELECT GET_DDL('TABLE', 'no_such_t672', TRUE)");
        assertAnswer("2003 02000",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT GET_DDL('TABLE', 'no_such_t672')); EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("2003 02000",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT GET_DDL('VIEW', 'no_such_v672')); EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("2003 02000",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT GET_DDL('SEQUENCE', 'no_such_s672')); EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("2003 02000",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT GET_DDL('SCHEMA', 'no_such_sc672')); EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("2001 02000",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT GET_DDL('FUNCTION', 'no_such_f672()')); EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("2003 02000",
            "EXECUTE IMMEDIATE $$ DECLARE d VARCHAR; BEGIN d := GET_DDL('TABLE', 'no_such_t672'); RETURN d; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("REFUSED SQL compilation error: | Object 'NO_SUCH_T672' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_T672.",
            "SELECT * FROM no_such_t672");
    }
}
