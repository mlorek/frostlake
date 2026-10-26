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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A block's statements chained with the flow arrow run in turn, each reading the stages before it as $n, in a
 * loop's body too, a DDL stage handing on its status row; a $n no stage answers is refused while the block
 * compiles, one stage before the chain's first as it runs; a RESULTSET is filled from a chain's last stage
 * (all live-verified).
 */
public class BlockFlowChainStagesTest extends MeasuredCellsTestSupport {

    @Test
    public void chainedStages() {
        assertAnswer("2", "SELECT 1 AS a ->> SELECT a + 1 AS b FROM $1");
        assertAnswer("ok", "BEGIN SELECT 1 AS a ->> SELECT a + 1 AS b FROM $1; RETURN 'ok'; END;");
        assertAnswer("2",
            "DECLARE n INT; BEGIN SELECT 1 AS a ->> SELECT a + 1 AS b FROM $1; SELECT b INTO :n FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN n; END;");
        assertAnswer("20",
            "DECLARE n INT; BEGIN SELECT 1 AS a ->> SELECT a + 1 AS b FROM $1 ->> SELECT b * 10 AS c FROM $1; SELECT "
                + "c INTO :n FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN n; END;");
        assertAnswer("1",
            "DECLARE n INT; BEGIN SELECT 1 AS a ->> SELECT 5 AS b ->> SELECT a FROM $2; SELECT a INTO :n FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN n; END;");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | invalid identifier 'SQL_PIPE_1'",
            "BEGIN SELECT 1 AS a; SELECT a FROM $1; RETURN 'ok'; END;");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 54 : Statement null "
                + "not found",
            "BEGIN SELECT 1 AS a ->> SELECT a + 1 AS b FROM $1 ->> SELECT b FROM $3; RETURN 'ok'; END;");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | invalid identifier 'SQL_PIPE_4'",
            "BEGIN SELECT 1 AS a ->> SELECT a + 1 AS b FROM $1 ->> SELECT b FROM $4; RETURN 'ok'; END;");
        assertAnswer("3",
            "DECLARE n INT; BEGIN FOR i IN 1 TO 2 DO SELECT :i AS a ->> SELECT a + 1 AS b FROM $1; END FOR; SELECT b "
                + "INTO :n FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN n; END;");
        assertAnswer("ok", "BEGIN CREATE OR REPLACE TABLE ch684 (a INT) ->> SELECT * FROM $1; RETURN 'ok'; END;");
        assertAnswer("2",
            "DECLARE n INT; BEGIN INSERT INTO ch684 VALUES (1), (2) ->> SELECT * FROM $1; SELECT $1 INTO :n FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN n; END;");
        assertAnswer("2",
            "BEGIN LET r RESULTSET := (SELECT 1 AS a ->> SELECT a + 1 AS b FROM $1); RETURN TABLE(r); END;");
        assertAnswer("1",
            "BEGIN LET r RESULTSET := (SELECT 1 AS a ->> SELECT 5 AS b ->> SELECT a FROM $2); RETURN TABLE(r); END;");
        assertAnswer("2",
            "DECLARE r RESULTSET DEFAULT (SELECT 1 AS a ->> SELECT a + 1 AS b FROM $1); BEGIN RETURN TABLE(r); END;");
        assertAnswer("1 ;; 2", "SELECT * FROM ch684 ORDER BY 1");
    }

    /**
     * A chain filling a RESULTSET fails as a request's chain does, its stage placed in the chain and the stage's own
     * places counted from its first token, and the statement placed at the chain's arrow; an assignment takes a chain
     * as a declaration does (live-verified).
     */
    @Test
    public void aChainFillingAResultSetIsPlacedAtItsArrow() {
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 40 : Uncaught "
                + "exception of type 'STATEMENT_ERROR' on line 1 at position 18 : SQL compilation error: error line 1 "
                + "at position 7 | invalid identifier 'B'",
            "BEGIN LET r RESULTSET := (SELECT 1 AS a ->> SELECT b FROM $1); RETURN TABLE(r); END;");
        assertAnswer("2",
            "DECLARE r RESULTSET; BEGIN r := (SELECT 1 AS a ->> SELECT a + 1 AS b FROM $1); RETURN TABLE(r); END;");
    }
}
