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

import dev.frostlake.TaskRunWait;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A table function's columns have the types the function declares before any row is read — SYSTEM$TYPEOF over
 * QUERY_HISTORY_BY_SESSION's answers VARCHAR, TIMESTAMP_LTZ(3) and NUMBER(38,0) — and a query that reads no row
 * of its FROM, by a constant WHERE that is FALSE or NULL, a LIMIT 0 or an inner join on such a constant, never
 * calls its table functions: a call whose arguments would be refused, or whose input could not be read, answers
 * no row (all live-verified).
 */
public class TableFunctionReadsTest extends MeasuredCellsTestSupport {

    @Test
    public void aTableFunctionIsTypedAndCalledOnlyWhenRead() {
        engine.execute("CREATE OR REPLACE TABLE gt (a INT)");
        engine.execute("INSERT INTO gt VALUES (1)");
        assertAnswer("VARIANT[LOB]|NUMBER(38,0)[SB16]|NUMBER(38,0)[SB16]|VARCHAR[LOB]|VARCHAR[LOB]|VARIANT[LOB]",
            "SELECT SYSTEM$TYPEOF(value), SYSTEM$TYPEOF(index), SYSTEM$TYPEOF(seq), SYSTEM$TYPEOF(key), "
                + "SYSTEM$TYPEOF(path), SYSTEM$TYPEOF(this) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1,2]'))) LIMIT 1");
        assertAnswer("VARIANT[LOB]|NUMBER(38,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(f.value), SYSTEM$TYPEOF(f.index) FROM TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1, "
                + "2))) f LIMIT 1");
        assertAnswer("NUMBER(38,0)[SB16]|NUMBER(38,0)[SB16]|VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(seq), SYSTEM$TYPEOF(index), SYSTEM$TYPEOF(value) FROM TABLE(SPLIT_TO_TABLE('a,b', "
                + "',')) LIMIT 1");
        assertAnswer("NUMBER(10,0)[SB4]", "SELECT SYSTEM$TYPEOF(seq4()) FROM TABLE(GENERATOR(ROWCOUNT => 1))");
        assertAnswer("VARCHAR[LOB]|VARCHAR[LOB]|TIMESTAMP_LTZ(3)[SB8]|NUMBER(38,0)[SB16]|VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(query_id), SYSTEM$TYPEOF(query_text), SYSTEM$TYPEOF(start_time), "
                + "SYSTEM$TYPEOF(rows_produced), SYSTEM$TYPEOF(execution_status) FROM "
                + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) LIMIT 1");
        assertAnswer("",
            "SELECT SYSTEM$TYPEOF(ANY_VALUE(attempt_number)), SYSTEM$TYPEOF(ANY_VALUE(state)), "
                + "SYSTEM$TYPEOF(ANY_VALUE(scheduled_time)), SYSTEM$TYPEOF(ANY_VALUE(query_id)) FROM "
                + "TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => 'nosuch_tk'))");
        assertAnswer("0", "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => 'nosuch_tk'))");
        assertAnswer("",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk')) WHERE 1 = 0");
        assertAnswer("",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk')) WHERE FALSE");
        assertAnswer("",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk')) WHERE NULL");
        assertAnswer("",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk')) WHERE 2 < 1");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [nosuch_tk] for function 'TASK_DEPENDENTS_SCAN', "
                + "parameter 1: must be a valid task name",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk')) WHERE 1 = 1");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [nosuch_tk] for function 'TASK_DEPENDENTS_SCAN', "
                + "parameter 1: must be a valid task name",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk'))");
        assertAnswer("0",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk')) WHERE 1 = 0");
        assertAnswer("", "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk')) LIMIT 0");
        assertAnswer("",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk')) WHERE 1 = 0 AND name "
                + "= 'x'");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [nosuch_tk] for function 'TASK_DEPENDENTS_SCAN', "
                + "parameter 1: must be a valid task name",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk')) WHERE 1 = 0 OR name = "
                + "'x'");
        assertAnswer("",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk')) t JOIN gt ON 1 = 0");
        assertAnswer("",
            "SELECT * FROM gt WHERE EXISTS (SELECT 1 FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => "
                + "'nosuch_tk')) WHERE 1 = 0)");
        assertAnswer("", "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('['))) WHERE 1 = 0");
        assertAnswer("REFUSED Error parsing JSON: incomplete array value, pos 2",
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[')))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns",
            "SELECT * FROM TABLE(GENERATOR(ROWCOUNT => -1)) WHERE 1 = 0");
        assertAnswer("",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk')) WHERE 1 = 0 ORDER BY 1");
        assertAnswer("",
            "SELECT name FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk')) WHERE 1 = 0");
        assertAnswer("",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'nosuch_tk', RECURSIVE => FALSE)) "
                + "WHERE 1 = 0");
    }

    /** A task's run history is typed as the account types it (live-verified); the run is made on the engine alone. */
    @Test
    public void aTaskRunsHistoryIsTypedAsTheAccountTypesIt() {
        Assumptions.assumeFalse(isLiveSnowflake(), "a task run is made on the embedded engine alone");
        engine.execute("CREATE OR REPLACE TASK tk AS SELECT 1");
        engine.execute("EXECUTE TASK tk");
        TaskRunWait.untilRows(engine,
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS(ROOT_TASK_NAME => 'tk'))", 1);
        assertAnswer("NUMBER(38,0)[SB16]|VARCHAR[LOB]|OBJECT[LOB]|TIMESTAMP_LTZ(3)[SB8]|VARCHAR[LOB]", """
            SELECT SYSTEM$TYPEOF(attempt_number), SYSTEM$TYPEOF(config), SYSTEM$TYPEOF(backfill_info),
                   SYSTEM$TYPEOF(scheduled_time), SYSTEM$TYPEOF(state)
            FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => 'tk')) WHERE state <> 'SCHEDULED'""");
        assertAnswer("VARCHAR[LOB]|NUMBER(38,0)[SB16]", """
            SELECT SYSTEM$TYPEOF(first_error_code), SYSTEM$TYPEOF(run_id)
            FROM TABLE(INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS(ROOT_TASK_NAME => 'tk'))""");
    }
}
