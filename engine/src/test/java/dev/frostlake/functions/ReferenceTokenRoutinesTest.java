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
 * A SYSTEM$REFERENCE token a CALL's arguments make with no scope written is the call's, where one a query makes
 * lasts the session; CALL IDENTIFIER(...)(...) calls the procedure a name or a token names, and a function's
 * token; a function's and a procedure's reference names an overload there, and each domain takes the privileges
 * its objects do, a warehouse's reference that is not PERSISTENT USAGE alone (all live-verified).
 */
public class ReferenceTokenRoutinesTest extends MeasuredCellsTestSupport {

    @Test
    public void callScopesAndProcedureTokens() {
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("INSERT INTO tsr VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE refs (r VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE marks (m INT)");
        engine.execute("CREATE OR REPLACE FUNCTION fr(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_id(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET n "
            + "NUMBER := (SELECT COUNT(*) FROM IDENTIFIER(:r)); RETURN n; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_keep(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
            + "INSERT INTO refs VALUES (:r); LET n NUMBER := (SELECT COUNT(*) FROM IDENTIFIER(:r)); RETURN n; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_mark() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN INSERT INTO "
            + "marks VALUES (1); RETURN 7; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_callref(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
            + "CALL IDENTIFIER(:r)(); RETURN 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_arg(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN "
            + "x + 1; END $$");
        assertAnswer("2", "CALL pr_keep(SYSTEM$REFERENCE('TABLE', 'tsr', 'PERSISTENT', 'SELECT'))");
        assertAnswer("Statement executed successfully.", "SET kept = (SELECT MAX(r) FROM refs)");
        assertAnswer("1", "DELETE FROM refs");
        assertAnswer("2", "CALL pr_keep(SYSTEM$REFERENCE('TABLE', 'tsr', 'CALL', 'SELECT'))");
        assertAnswer("Statement executed successfully.", "SET kept = (SELECT MAX(r) FROM refs)");
        assertAnswer("1", "DELETE FROM refs");
        assertAnswer("2", "CALL pr_keep(SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'SELECT'))");
        assertAnswer("Statement executed successfully.", "SET kept = (SELECT MAX(r) FROM refs)");
        assertAnswer("2", "SELECT COUNT(*) FROM IDENTIFIER($kept)");
        assertAnswer("2", "CALL pr_id($kept)");
        assertAnswer("1", "DELETE FROM refs");
        assertAnswer("2", "CALL pr_keep(SYSTEM$REFERENCE('TABLE', 'tsr'))");
        assertAnswer("Statement executed successfully.", "SET kept = (SELECT MAX(r) FROM refs)");
        assertAnswer("1", "CALL pr_callref(SYSTEM$REFERENCE('PROCEDURE', 'pr_mark()'))");
        assertAnswer("1", "SELECT COUNT(*) FROM marks");
        assertAnswer("1", "CALL pr_callref(SYSTEM$REFERENCE('PROCEDURE', 'pr_mark()', 'CALL', 'USAGE'))");
        assertAnswer("2", "SELECT COUNT(*) FROM marks");
        assertAnswer("Statement executed successfully.",
            "SET ptok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_mark()', 'SESSION', 'USAGE'))");
        assertAnswer("7", "CALL IDENTIFIER($ptok)()");
        assertAnswer("3", "SELECT COUNT(*) FROM marks");
        assertAnswer("Statement executed successfully.",
            "SET atok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_arg(NUMBER)', 'SESSION', 'USAGE'))");
        assertAnswer("5", "CALL IDENTIFIER($atok)(4)");
        assertAnswer("Statement executed successfully.",
            "SET ftok = (SELECT SYSTEM$REFERENCE('FUNCTION', 'fr(NUMBER)', 'SESSION', 'USAGE'))");
        assertAnswer("5", "SELECT IDENTIFIER($ftok)(4)");
        assertAnswer("5", "CALL IDENTIFIER($ftok)(4)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '<EOF>'.",
            "SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_mark') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Procedure 'pr_none()' does not exist or not authorized.",
            "SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_none()') IS NOT NULL");
        assertAnswer("Statement executed successfully.",
            "SET wtok = (SELECT SYSTEM$REFERENCE('WAREHOUSE', 'compute_wh', 'SESSION', 'USAGE'))");
        assertAnswer("Statement executed successfully.", "USE WAREHOUSE compute_wh");
        assertAnswer("COMPUTE_WH", "SELECT CURRENT_WAREHOUSE()");
        assertAnswer("REFUSED Privilege 'OPERATE' is not allowed on domain 'WAREHOUSE' during 'SESSION' reference "
                + "creation.",
            "SELECT SYSTEM$REFERENCE('WAREHOUSE', 'compute_wh', 'SESSION', 'OPERATE') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Warehouse 'NO_SUCH_WH' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on WAREHOUSE NO_SUCH_WH.",
            "SELECT SYSTEM$REFERENCE('WAREHOUSE', 'no_such_wh', 'SESSION', 'USAGE') IS NOT NULL");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('WAREHOUSE', 'compute_wh', 'CALL', 'USAGE') IS NOT NULL");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('WAREHOUSE', 'compute_wh') IS NOT NULL");
    }

    @Test
    public void callIdentifierAndPrivileges() {
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("INSERT INTO tsr VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE marks (m INT)");
        engine.execute("CREATE OR REPLACE FUNCTION fr(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_id(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET n "
            + "NUMBER := (SELECT COUNT(*) FROM IDENTIFIER(:r)); RETURN n; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_mark() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN INSERT INTO "
            + "marks VALUES (1); RETURN 7; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_arg(x NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN "
            + "x + 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_callarg(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE "
            + "n NUMBER; BEGIN CALL IDENTIFIER(:r)(4) INTO :n; RETURN n; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr_sel(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET n "
            + "NUMBER := (SELECT IDENTIFIER(:r)(4)); RETURN n; END $$");
        assertAnswer("7", "CALL IDENTIFIER('pr_mark')()");
        assertAnswer("5", "CALL IDENTIFIER('pr_arg')(4)");
        assertAnswer("REFUSED SQL compilation error: | Unknown function FR.", "CALL IDENTIFIER('fr')(4)");
        assertAnswer("REFUSED SQL compilation error: | Unknown function PR_ARG.", "SELECT IDENTIFIER('pr_arg')(4)");
        assertAnswer("5", "SELECT IDENTIFIER('fr')(4)");
        assertAnswer("REFUSED SQL compilation error: | Unknown function NO_SUCH_PR.",
            "CALL IDENTIFIER('no_such_pr')()");
        assertAnswer("Statement executed successfully.", "SET pname = 'pr_arg'");
        assertAnswer("5", "CALL IDENTIFIER($pname)(4)");
        assertAnswer("5", "CALL pr_callarg(SYSTEM$REFERENCE('PROCEDURE', 'pr_arg(NUMBER)'))");
        assertAnswer("5", "CALL pr_callarg(SYSTEM$REFERENCE('FUNCTION', 'fr(NUMBER)'))");
        assertAnswer("5", "CALL pr_callarg('pr_arg')");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL "
                + "compilation error: | Unknown function PR_ARG.",
            "CALL pr_sel('pr_arg')");
        assertAnswer("Statement executed successfully.",
            "SET ttok = (SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'SELECT'))");
        assertAnswer("Statement executed successfully.",
            "SET ptok = (SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_mark()', 'SESSION', 'USAGE'))");
        assertAnswer("Statement executed successfully.", "SET otok = (SELECT SYSTEM$REFERENCE('TABLE', 'tsr'))");
        assertAnswer("2", "SELECT COUNT(*) FROM IDENTIFIER($otok)");
        assertAnswer("Statement executed successfully.", "SET ntok = (SELECT SYSTEM$REFERENCE('TABLE', 'tsr', NULL))");
        assertAnswer("2", "SELECT COUNT(*) FROM IDENTIFIER($ntok)");
        assertAnswer("2", "CALL pr_id(SYSTEM$REFERENCE('TABLE', 'tsr', NULL))");
        assertAnswer("REFUSED SQL compilation error: | Procedure 'pr_mark(NUMBER)' does not exist or not authorized.",
            "SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_mark(NUMBER)') IS NOT NULL");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('PROCEDURE', 'PR_MARK()') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Procedure 'test_db.public.pr_none()' does not exist or not "
                + "authorized.",
            "SELECT SYSTEM$REFERENCE('PROCEDURE', 'test_db.public.pr_none()') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Procedure 'fr(NUMBER)' does not exist or not authorized.",
            "SELECT SYSTEM$REFERENCE('PROCEDURE', 'fr(NUMBER)') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Function 'no_such_fn(NUMBER)' does not exist or not authorized.",
            "SELECT SYSTEM$REFERENCE('FUNCTION', 'no_such_fn(NUMBER)') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Function 'fr(VARCHAR)' does not exist or not authorized.",
            "SELECT SYSTEM$REFERENCE('FUNCTION', 'fr(VARCHAR)') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Function 'pr_arg(NUMBER)' does not exist or not authorized.",
            "SELECT SYSTEM$REFERENCE('FUNCTION', 'pr_arg(NUMBER)') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Invalid object type 'PROCEDURE' for privilege 'SELECT'.",
            "SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_mark()', 'SESSION', 'SELECT') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Invalid object type 'PROCEDURE' for privilege 'OPERATE'.",
            "SELECT SYSTEM$REFERENCE('PROCEDURE', 'pr_mark()', 'SESSION', 'OPERATE') IS NOT NULL");
        assertAnswer("REFUSED Privilege 'MONITOR' is not allowed on domain 'WAREHOUSE' during 'SESSION' reference "
                + "creation.",
            "SELECT SYSTEM$REFERENCE('WAREHOUSE', 'compute_wh', 'SESSION', 'MONITOR') IS NOT NULL");
        assertAnswer("REFUSED Privilege 'MODIFY' is not allowed on domain 'WAREHOUSE' during 'SESSION' reference "
                + "creation.",
            "SELECT SYSTEM$REFERENCE('WAREHOUSE', 'compute_wh', 'SESSION', 'MODIFY') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Invalid object type 'WAREHOUSE' for privilege 'SELECT'.",
            "SELECT SYSTEM$REFERENCE('WAREHOUSE', 'compute_wh', 'SESSION', 'SELECT') IS NOT NULL");
        assertAnswer("REFUSED Privilege 'OPERATE' is not allowed on domain 'WAREHOUSE' during 'SESSION' reference "
                + "creation.",
            "SELECT SYSTEM$REFERENCE('WAREHOUSE', 'compute_wh', 'SESSION', 'USAGE', 'OPERATE') IS NOT NULL");
        assertAnswer("REFUSED Privilege 'OPERATE' is not allowed on domain 'WAREHOUSE' during 'CALL' reference "
                + "creation.",
            "SELECT SYSTEM$REFERENCE('WAREHOUSE', 'compute_wh', 'CALL', 'OPERATE') IS NOT NULL");
        assertAnswer("true", "SELECT SYSTEM$REFERENCE('WAREHOUSE', 'compute_wh', 'PERSISTENT', 'OPERATE') IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Invalid object type 'FUNCTION' for privilege 'OPERATE'.",
            "SELECT SYSTEM$REFERENCE('FUNCTION', 'fr(NUMBER)', 'SESSION', 'OPERATE') IS NOT NULL");
        assertAnswer("REFUSED Object type TABLE does not match the specified type VIEW for reference creation",
            "SELECT SYSTEM$REFERENCE('VIEW', 'tsr') IS NOT NULL");
    }
}
