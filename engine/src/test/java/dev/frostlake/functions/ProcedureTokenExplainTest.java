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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXPLAIN of a query calling a procedure by its SYSTEM$REFERENCE token: the call is planned as the procedure over its
 * parameters' names, a table procedure's too, and a query of no FROM holding one reads an empty ValuesClause (all
 * live-verified). The account reads the call's value in arithmetic as a text, {@code (TO_NUMBER(PR_ARG(X), 18, 5)) +
 * 1}, the defect the query itself fails on; the engine is held to the plan of the value it computes.
 */
public class ProcedureTokenExplainTest extends MeasuredCellsTestSupport {

    private static final String STATS = "null|null|null|GlobalStats|null|null|null|0|0|0 ;; ";
    private static final String VALUES = " ;; 1|1|[0]|ValuesClause|null|null|()|null|null|null";

    @Test
    public void theCallIsTheProcedureOverItsParameters() {
        engine.execute("CREATE OR REPLACE TABLE marks (m INT)");
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("INSERT INTO tsr VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_mark() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN INSERT INTO "
            + "marks VALUES (1); RETURN 7; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_arg(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN "
            + "x + 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_tab() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
            + "RESULTSET DEFAULT (SELECT 1 AS a UNION ALL SELECT 2); BEGIN RETURN TABLE(r); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_two(x NUMBER, y NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ "
            + "BEGIN RETURN x * 10 + y; END $$");
        engine.execute("SET atok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_arg(NUMBER)', 'SESSION', 'USAGE'))");
        engine.execute("SET ptok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_mark()', 'SESSION', 'USAGE'))");
        engine.execute("SET btok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_tab()', 'SESSION', 'USAGE'))");
        engine.execute("SET wtok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_two(NUMBER, NUMBER)', 'SESSION', "
            + "'USAGE'))");
        assertAnswer(STATS + "1|0|null|Result|null|null|PR_TWO(X,Y)|null|null|null" + VALUES,
            "EXPLAIN SELECT IDENTIFIER($wtok)(1, 2)");
        assertAnswer(STATS + "1|0|null|Result|null|null|PR_MARK()|null|null|null" + VALUES,
            "EXPLAIN SELECT IDENTIFIER($ptok)()");
        assertAnswer(STATS + "1|0|null|Result|null|null|PR_TAB()|null|null|null" + VALUES,
            "EXPLAIN SELECT IDENTIFIER($btok)()");
        assertAnswer(STATS + "1|0|null|Result|null|null|PR_ARG(X)|null|null|null" + VALUES,
            "EXPLAIN SELECT IDENTIFIER($atok)(4) AS r");
        // The bytes a scan assigns are a matter of their own: both sides plan the call over the scan of its column.
        final String scanned = answer("EXPLAIN SELECT IDENTIFIER($atok)(a) FROM tsr");
        assertTrue(scanned.contains(" ;; 1|0|null|Result|null|null|PR_ARG(X)|null|null|null ;; "), scanned);
        assertTrue(scanned.contains(" ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.TSR|null|A|1|1|"), scanned);
        final String sum = answer("EXPLAIN SELECT IDENTIFIER($atok)(4) + 1");
        if (!isLiveSnowflake()) {
            assertEquals(STATS + "1|0|null|Result|null|null|(PR_ARG(X)) + 1|null|null|null" + VALUES, sum);
            // Nothing runs while the plan is compiled.
            assertEquals("0", answer("SELECT COUNT(*) FROM marks"));
        }
    }
}
