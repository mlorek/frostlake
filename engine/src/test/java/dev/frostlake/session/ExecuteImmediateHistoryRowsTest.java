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

package dev.frostlake.session;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * What EXECUTE IMMEDIATE leaves in QUERY_HISTORY (live-verified): a text of one statement runs as the EXECUTE
 * IMMEDIATE itself, with one row and one query ID - the request's own EXECUTE IMMEDIATE listed under its own text and
 * the statement's kind, one a script runs under the text it runs, a block's text as a CALL whose statements have rows
 * of their own; a text refused for its statement count is UNKNOWN. A script's statements are listed without the
 * semicolon that ends them, or the space and comments before it.
 */
public class ExecuteImmediateHistoryRowsTest extends MeasuredCellsTestSupport {

    @Test
    public void oneStatementRunsAsTheExecuteImmediate() {
        engine.execute("CREATE OR REPLACE TABLE eh946 (a INT)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE 'SELECT 946001'", "946001"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "946001"},
            {"SELECT 946002", "946002"},
            {"EXECUTE IMMEDIATE 'SELECT 946003'", "946003"},
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))", "946002"},
            {"BEGIN SELECT 946004; EXECUTE IMMEDIATE 'SELECT 946005'; LET r RESULTSET := (SELECT * FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))); RETURN TABLE(r); END;",
                "946004"},
            {"BEGIN SELECT 946006 /* c */ ; INSERT INTO eh946 VALUES (946007) ; IF (1 = 1) THEN SELECT 946008; "
                + "END IF; RETURN 'x'; END;",
                "x"},
            {"BEGIN EXECUTE IMMEDIATE $$BEGIN SELECT 946009; RETURN 'n'; END;$$; RETURN 'y'; END;", "y"},
            {"BEGIN LET s VARCHAR := 'INSERT INTO eh946 VALUES (946010)'; EXECUTE IMMEDIATE :s; RETURN 'c'; END;", "c"},
            {"BEGIN EXECUTE IMMEDIATE 'SELECT 946011; SELECT 946012'; EXCEPTION WHEN OTHER THEN RETURN 'd'; END;", "d"},
            {"EXECUTE IMMEDIATE 'SELECT 946013; SELECT 946014'",
                "REFUSED Actual statement count 2 did not match the desired statement count 1."},
            {"EXECUTE IMMEDIATE 'CREATE OR REPLACE TABLE eh9460 (a INT)'", "Table EH9460 successfully created."},
            {"BEGIN EXECUTE IMMEDIATE 'SELECT 946015/0'; EXCEPTION WHEN OTHER THEN RETURN 'f'; END;", "f"},
            {"SELECT QUERY_TYPE, EXECUTION_STATUS, ROWS_INSERTED, REPLACE(REPLACE(QUERY_TEXT, CHR(10), ' '), "
                + "CHR(13), ' ') FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION(RESULT_LIMIT => 1000)) WHERE "
                + "CONTAINS(QUERY_TEXT, '946' || '0') ORDER BY 4, 1, 2",
                "CALL|SUCCESS|0|BEGIN EXECUTE IMMEDIATE $$BEGIN SELECT 946009; RETURN 'n'; END;$$; RETURN 'y'; "
                + "END; ;; CALL|SUCCESS|0|BEGIN EXECUTE IMMEDIATE 'SELECT 946011; SELECT 946012'; EXCEPTION WHEN "
                + "OTHER THEN RETURN 'd'; END; ;; CALL|SUCCESS|0|BEGIN EXECUTE IMMEDIATE 'SELECT 946015/0'; "
                + "EXCEPTION WHEN OTHER THEN RETURN 'f'; END; ;; CALL|SUCCESS|0|BEGIN LET s VARCHAR := 'INSERT INTO "
                + "eh946 VALUES (946010)'; EXECUTE IMMEDIATE :s; RETURN 'c'; END; ;; CALL|SUCCESS|0|BEGIN SELECT "
                + "946004; EXECUTE IMMEDIATE 'SELECT 946005'; LET r RESULTSET := (SELECT * FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))); RETURN TABLE(r); END; ;; CALL|SUCCESS|0|BEGIN SELECT "
                + "946006 /* c */ ; INSERT INTO eh946 VALUES (946007) ; IF (1 = 1) THEN SELECT 946008; END IF; "
                + "RETURN 'x'; END; ;; CALL|SUCCESS|0|BEGIN SELECT 946009; RETURN 'n'; END; ;; "
                + "CREATE_TABLE|SUCCESS|0|EXECUTE IMMEDIATE 'CREATE OR REPLACE TABLE eh9460 (a INT)' ;; "
                + "SELECT|SUCCESS|0|EXECUTE IMMEDIATE 'SELECT 946001' ;; SELECT|SUCCESS|0|EXECUTE IMMEDIATE 'SELECT "
                + "946003' ;; UNKNOWN|FAILED_WITH_ERROR|0|EXECUTE IMMEDIATE 'SELECT 946013; SELECT 946014' ;; "
                + "INSERT|SUCCESS|1|INSERT INTO eh946 VALUES (946007) ;; INSERT|SUCCESS|1|INSERT INTO eh946 VALUES "
                + "(946010) ;; SELECT|SUCCESS|0|SELECT 946002 ;; SELECT|SUCCESS|0|SELECT 946004 ;; "
                + "SELECT|SUCCESS|0|SELECT 946005 ;; SELECT|SUCCESS|0|SELECT 946006 ;; SELECT|SUCCESS|0|SELECT "
                + "946008 ;; SELECT|SUCCESS|0|SELECT 946009 ;; UNKNOWN|FAILED_WITH_ERROR|0|SELECT 946011; SELECT "
                + "946012 ;; SELECT|FAILED_WITH_ERROR|0|SELECT 946015/0"},
        });
    }
}
