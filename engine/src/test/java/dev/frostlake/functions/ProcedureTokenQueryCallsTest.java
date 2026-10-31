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
 * A procedure called in a query by its SYSTEM$REFERENCE token, SELECT IDENTIFIER($tok)(4): a query that is the one
 * call answers as the CALL does - the value under the procedure's name, a table procedure's rows, a refusal of the
 * arguments at the call - and the call is the procedure's value anywhere else; CREATE VIEW runs no procedure, an
 * INSERT and a MERGE over a call are refused before anything runs (all live-verified). Where the account fails with a
 * defect of its own - the value's internal form read by an operator, an internal error - the engine is held to the
 * statement's ordinary answer.
 */
public class ProcedureTokenQueryCallsTest extends MeasuredCellsTestSupport {

    @Test
    public void queryCalls() {
        engine.execute("CREATE OR REPLACE TABLE marks (m INT)");
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("INSERT INTO tsr VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_mark() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN INSERT INTO "
            + "marks VALUES (1); RETURN 7; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_arg(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN "
            + "x + 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_txt() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'abc'; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_tab() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
            + "RESULTSET DEFAULT (SELECT 1 AS a UNION ALL SELECT 2); BEGIN RETURN TABLE(r); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_two(x NUMBER, y NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ "
            + "BEGIN RETURN x * 10 + y; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_fail() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 1 / 0; "
            + "END $$");
        assertAnswer("Statement executed successfully.",
            "SET atok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_arg(NUMBER)', 'SESSION', 'USAGE'))");
        assertShape("PR_ARG:NUMBER(38,0)", "SELECT IDENTIFIER($atok)(4)");
        ordinaryAnswer("6", "SELECT IDENTIFIER($atok)(4) + 1");
        assertShape("PR_ARG:NUMBER(38,0)", "SELECT IDENTIFIER($atok)(4) AS r");
        ordinaryAnswer("2 ;; 3", "SELECT IDENTIFIER($atok)(a) FROM tsr ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PR_ARG': ()",
            "SELECT IDENTIFIER($atok)()");
        assertAnswer("REFUSED Numeric value 'x' is not recognized", "SELECT IDENTIFIER($atok)('x')");
        ordinaryAnswer("5|6", "SELECT IDENTIFIER($atok)(4), IDENTIFIER($atok)(5)");
        assertAnswer("null", "SELECT IDENTIFIER($atok)(NULL)");
        ordinaryAnswer("1", "SELECT 1 WHERE IDENTIFIER($atok)(4) = 5");
        assertAnswer("5", "WITH t AS (SELECT IDENTIFIER($atok)(4) AS x) SELECT x FROM t");
        ordinaryAnswer("", "SELECT IDENTIFIER($atok)(4) FROM tsr WHERE FALSE");
        assertAnswer("Statement executed successfully.",
            "SET ptok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_mark()', 'SESSION', 'USAGE'))");
        assertAnswer("7", "SELECT IDENTIFIER($ptok)()");
        assertAnswer("1", "SELECT COUNT(*) FROM marks");
        ordinaryAnswer("7 ;; 7", "SELECT IDENTIFIER($ptok)() FROM tsr");
        assertAnswer("3", "SELECT COUNT(*) FROM marks");
        assertAnswer("REFUSED SQL compilation error: | Unsupported statement type 'LogicalInsert'.",
            "INSERT INTO marks SELECT IDENTIFIER($atok)(4)");
        assertAnswer("3", "SELECT COUNT(*) FROM marks");
        assertAnswer("Statement executed successfully.",
            "SET ttok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_txt()', 'SESSION', 'USAGE'))");
        assertShape("PR_TXT:VARCHAR(134217728)", "SELECT IDENTIFIER($ttok)()");
        assertAnswer("Statement executed successfully.",
            "SET btok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_tab()', 'SESSION', 'USAGE'))");
        assertAnswer("1 ;; 2", "SELECT IDENTIFIER($btok)()");
        answer("SELECT * FROM TABLE(IDENTIFIER($btok)())");
        answer("SELECT * FROM TABLE(IDENTIFIER($atok)(4))");
        assertAnswer("Statement executed successfully.",
            "SET wtok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_two(NUMBER, NUMBER)', 'SESSION', 'USAGE'))");
        assertAnswer("12", "SELECT IDENTIFIER($wtok)(1, 2)");
        assertAnswer("Statement executed successfully.",
            "SET ftok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_fail()', 'SESSION', 'USAGE'))");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Division by "
                + "zero",
            "SELECT IDENTIFIER($ftok)()");
        assertAnswer("Statement executed successfully.",
            "SET ctok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_arg(NUMBER)', 'CALL', 'USAGE'))");
        answer("SELECT IDENTIFIER($ctok)(4)");
        assertAnswer("View VTOK successfully created.",
            "CREATE OR REPLACE VIEW vtok AS SELECT IDENTIFIER($atok)(4) AS x");
        answer("EXPLAIN SELECT IDENTIFIER($atok)(4)");
    }

    @Test
    public void viewsWritesAndShapes() {
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
        assertAnswer("Statement executed successfully.",
            "SET atok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_arg(NUMBER)', 'SESSION', 'USAGE'))");
        assertAnswer("Statement executed successfully.",
            "SET ptok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_mark()', 'SESSION', 'USAGE'))");
        assertAnswer("Statement executed successfully.",
            "SET btok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_tab()', 'SESSION', 'USAGE'))");
        assertAnswer("Statement executed successfully.",
            "SET wtok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_two(NUMBER, NUMBER)', 'SESSION', 'USAGE'))");
        assertAnswer("View VTOK successfully created.",
            "CREATE OR REPLACE VIEW vtok AS SELECT IDENTIFIER($atok)(4) AS x");
        assertAnswer("5", "SELECT * FROM vtok");
        answer("SELECT * FROM vtok");
        assertAnswer("View VMARK successfully created.",
            "CREATE OR REPLACE VIEW vmark AS SELECT IDENTIFIER($ptok)() AS x");
        assertAnswer("0", "SELECT COUNT(*) FROM marks");
        assertAnswer("7", "SELECT * FROM vmark");
        assertAnswer("1", "SELECT COUNT(*) FROM marks");
        ordinaryAnswer("0|0", "UPDATE tsr SET a = IDENTIFIER($atok)(4) WHERE a = 9");
        ordinaryAnswer("0", "DELETE FROM tsr WHERE a = IDENTIFIER($atok)(0) AND a = 9");
        assertAnswer("REFUSED SQL compilation error: | Unsupported statement type 'Merge'.",
            "MERGE INTO tsr USING (SELECT 9 AS k) s ON tsr.a = s.k WHEN MATCHED THEN UPDATE SET a = "
                + "IDENTIFIER($atok)(4)");
        assertAnswer("Table TCTAS successfully created.",
            "CREATE OR REPLACE TABLE tctas AS SELECT IDENTIFIER($atok)(4) AS x");
        ordinaryAnswer("REFUSED SQL compilation error: | Invalid expression [PR_ARG(X)] in VALUES clause",
            "INSERT INTO marks VALUES (IDENTIFIER($atok)(4))");
        assertAnswer("1", "SELECT COUNT(*) FROM marks");
        answer("EXPLAIN SELECT IDENTIFIER($wtok)(1, 2)");
        answer("EXPLAIN SELECT IDENTIFIER($ptok)()");
        answer("EXPLAIN SELECT IDENTIFIER($atok)(4) + 1");
        answer("EXPLAIN SELECT IDENTIFIER($atok)(a) FROM tsr");
        answer("EXPLAIN SELECT IDENTIFIER($btok)()");
        answer("EXPLAIN SELECT IDENTIFIER($atok)(4) AS r");
        answer("WITH t AS (SELECT IDENTIFIER($atok)(4) AS x) SELECT * FROM t");
        assertShape("PR_ARG:NUMBER(38,0)", "SELECT * FROM (SELECT IDENTIFIER($atok)(4))");
        assertShape("PR_ARG:NUMBER(38,0)", "SELECT IDENTIFIER($atok)(4) FROM tsr");
        ordinaryAnswer("5 ;; 1", "SELECT IDENTIFIER($atok)(4) UNION ALL SELECT 1");
        assertAnswer("5", "SELECT IDENTIFIER($atok)(4) ORDER BY 1");
        assertAnswer("5", "SELECT IDENTIFIER($atok)(4) LIMIT 1");
        assertAnswer("5", "SELECT DISTINCT IDENTIFIER($atok)(4)");
        assertAnswer("5", "SELECT IDENTIFIER($atok)(4)::VARCHAR");
        ordinaryAnswer("INTEGER", "SELECT TYPEOF(IDENTIFIER($atok)(4))");
        ordinaryAnswer("REFUSED SQL compilation error: | Unknown user-defined function TEST_DB.TEST_SCHEMA.PR_TAB.",
            "SELECT IDENTIFIER($btok)() FROM tsr");
        ordinaryAnswer("REFUSED SQL compilation error: | Unknown user-defined function TEST_DB.TEST_SCHEMA.PR_TAB.",
            "SELECT * FROM (SELECT IDENTIFIER($btok)())");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for "
                + "function 'PR_ARG': ()",
            "CALL pr_arg()");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PR_TWO': (NUMBER(1,0))",
            "SELECT IDENTIFIER($wtok)(1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[PR_ARG(4, 5)] expected 1, got 2",
            "SELECT IDENTIFIER($atok)(4, 5)");
        assertAnswer("5", "SELECT IDENTIFIER($atok)(x => 4)");
        ordinaryAnswer("5|1", "SELECT IDENTIFIER($atok)(4) AS r, 1 AS s");
        ordinaryAnswer("1|5", "SELECT 1 AS s, IDENTIFIER($atok)(4)");
        assertAnswer("5", "SELECT IDENTIFIER($atok)(4) FROM tsr LIMIT 1");
        assertShape("A:NUMBER(38,0)", "SELECT IDENTIFIER($btok)()");
        assertAnswer("6", "SELECT IDENTIFIER($atok)('5')");
        assertAnswer("6", "SELECT IDENTIFIER($atok)(4.6)");
    }

    /** Refusals naming a token, whose digits are the token's own on either side. */
    @Test
    public void tokenNamedRefusals() {
        engine.execute("CREATE OR REPLACE PROCEDURE pr_arg(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS "
            + "$$ BEGIN RETURN x + 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_tab() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r "
            + "RESULTSET DEFAULT (SELECT 1 AS a UNION ALL SELECT 2); BEGIN RETURN TABLE(r); END $$");
        engine.execute("SET atok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_arg(NUMBER)', 'SESSION', 'USAGE'))");
        engine.execute("SET btok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_tab()', 'SESSION', 'USAGE'))");
        engine.execute("SET ctok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_arg(NUMBER)', 'CALL', 'USAGE'))");
        assertTrue(answer("SELECT * FROM TABLE(IDENTIFIER($btok)())")
            .startsWith("REFUSED SQL compilation error: | Unknown table function ENT_REF_PROCEDURE_"));
        assertTrue(answer("SELECT * FROM TABLE(IDENTIFIER($atok)(4))")
            .startsWith("REFUSED SQL compilation error: | Unknown table function ENT_REF_PROCEDURE_"));
        assertTrue(answer("SELECT IDENTIFIER($ctok)(4)")
            .startsWith("REFUSED SQL compilation error: | Unknown function ENT_REF_PROCEDURE_"));
    }

    /**
     * A statement the account answers with a defect of its own: both sides run it, and the embedded engine is held
     * to the statement's ordinary answer.
     */
    private void ordinaryAnswer(final String expected, final String sql) {
        final String answered = answer(sql);
        if (!isLiveSnowflake()) {
            assertEquals(expected, answered);
        }
    }
}
