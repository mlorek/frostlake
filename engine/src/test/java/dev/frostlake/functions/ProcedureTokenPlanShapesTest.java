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
 * EXPLAIN of a call of a procedure's SYSTEM$REFERENCE token in other shapes (live-verified): a call with more arguments
 * than the procedure has parameters, or fewer, is refused while the plan is written as the query itself refuses it,
 * placed at the call; named arguments are planned as the procedure over its parameters; a FROM-less scalar subquery
 * of a call is written as the call, over an empty ValuesClause; and a SQL function's call, by name or by its token,
 * and a FROM-less scalar subquery of a constant are folded to their values.
 */
public class ProcedureTokenPlanShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void aTokenCallIsPlannedAndRefusedAsTheQueryIs() {
        engine.execute("CREATE OR REPLACE PROCEDURE pr981(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
            + "RETURN x + 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr981b(x NUMBER, y NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ "
            + "BEGIN RETURN x + y; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fn981(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        engine.execute("SET atok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr981(NUMBER)', 'SESSION', 'USAGE'))");
        engine.execute("SET btok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr981b(NUMBER, NUMBER)', 'SESSION', "
            + "'USAGE'))");
        engine.execute("SET ftok = (SELECT SYSTEM$REFERENCE('FUNCTION', 'fn981(NUMBER)', 'SESSION', 'USAGE'))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
            + "function 'PR981': ()",
            "SELECT IDENTIFIER($atok)()");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for "
            + "function [PR981(1, 2)] expected 1, got 2",
            "SELECT IDENTIFIER($atok)(1, 2)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
            + "function 'PR981B': (NUMBER(1,0))",
            "SELECT IDENTIFIER($btok)(1)");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL "
            + "compilation error: error line 1 at position 15 | Invalid argument types for function 'PR981': ()",
            "EXPLAIN SELECT IDENTIFIER($atok)() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL "
            + "compilation error: error line 1 at position 15 | too many arguments for function [PR981(1, 2)] "
            + "expected 1, got 2",
            "EXPLAIN SELECT IDENTIFIER($atok)(1, 2) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL "
            + "compilation error: error line 1 at position 15 | Invalid argument types for function 'PR981B': "
            + "(NUMBER(1,0))",
            "EXPLAIN SELECT IDENTIFIER($btok)(1) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL "
            + "compilation error: error line 1 at position 15 | Invalid argument types for function 'PR981B': ()",
            "EXPLAIN SELECT IDENTIFIER($btok)() ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("5",
            "SELECT IDENTIFIER($atok)(x => 4)");
        assertAnswer("GlobalStats|null ;; Result|PR981(X) ;; ValuesClause|()",
            "EXPLAIN SELECT IDENTIFIER($atok)(x => 4) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|PR981B(X,Y) ;; ValuesClause|()",
            "EXPLAIN SELECT IDENTIFIER($btok)(y => 1, x => 2) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("5",
            "SELECT IDENTIFIER($ftok)(4)");
        assertAnswer("GlobalStats|null ;; Result|5 ;; Generator|1",
            "EXPLAIN SELECT IDENTIFIER($ftok)(4) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|5 ;; Generator|1",
            "EXPLAIN SELECT fn981(4) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|6 ;; Generator|1",
            "EXPLAIN SELECT fn981(4) + 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|6 ;; Generator|1",
            "EXPLAIN SELECT IDENTIFIER($ftok)(4) + 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|PR981(X) ;; ValuesClause|()",
            "EXPLAIN SELECT (SELECT IDENTIFIER($atok)(4)) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|PR981(X), 1 ;; ValuesClause|()",
            "EXPLAIN SELECT (SELECT IDENTIFIER($atok)(4)) AS r, 1 ->> SELECT \"operation\", \"expressions\" FROM "
                + "$1");
        assertAnswer("GlobalStats|null ;; Result|5 ;; Generator|1",
            "EXPLAIN SELECT (SELECT 5) ->> SELECT \"operation\", \"expressions\" FROM $1");
        assertAnswer("GlobalStats|null ;; Result|1 ;; Generator|1",
            "EXPLAIN SELECT 1 WHERE 1 = 1 ->> SELECT \"operation\", \"expressions\" FROM $1");
    }
}
