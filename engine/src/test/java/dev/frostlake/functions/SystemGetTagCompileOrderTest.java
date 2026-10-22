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
 * SYSTEM$GET_TAG is judged while the statement compiles ahead of the names of its clauses and of the kind of any
 * call written with OVER, its own OVER included: its count, its constant and text arguments, its domain and its
 * tag (all live-verified).
 */
public class SystemGetTagCompileOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void windowed() {
        engine.execute("CREATE OR REPLACE TABLE t1 (c VARCHAR)");
        engine.execute("INSERT INTO t1 VALUES ('a')");
        engine.execute("CREATE OR REPLACE PROCEDURE qa(c VARCHAR) RETURNS TABLE (x VARCHAR) LANGUAGE SQL AS $$ "
            + "DECLARE r RESULTSET DEFAULT (SELECT :c AS x); BEGIN RETURN TABLE(r); END; $$");
        engine.execute("CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
        engine.execute("INSERT INTO tsrc VALUES ('x', 1)");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b INT)");
        engine.execute("INSERT INTO fz VALUES (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT SYSTEM$GET_TAG(n, 'tt', 'TABLE') OVER () FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 1: "
                + "constant arguments expected",
            "SELECT SYSTEM$GET_TAG('tt', n, 'TABLE') OVER () FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 2: "
                + "constant arguments expected",
            "SELECT SYSTEM$GET_TAG('tt', 'tsrc', n) OVER () FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Tag 'TT' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG TT.",
            "SELECT SYSTEM$GET_TAG('tt', 'tsrc', 'TABLE') OVER () FROM tsrc");
        assertAnswer("REFUSED Unknown domain: NOPE.", "SELECT SYSTEM$GET_TAG('tt', 'tsrc', 'NOPE') OVER () FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$GET_TAG(N, 'tt')], expected 3, got 2",
            "SELECT SYSTEM$GET_TAG(n, 'tt') OVER () FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT ABS(i) OVER (), SYSTEM$GET_TAG(n, 'tt', 'TABLE') FROM tsrc");
    }

    @Test
    public void beforeTheOtherClauses() {
        engine.execute("CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
        engine.execute("INSERT INTO tsrc VALUES ('x', 1)");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT nosuch, SYSTEM$GET_TAG(n, 'tt', 'TABLE') FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT SYSTEM$GET_TAG(n, 'tt', 'TABLE'), nosuch FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Tag 'TT' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG TT.",
            "SELECT ABS(i) OVER (), SYSTEM$GET_TAG('tt', 'tsrc', 'TABLE') FROM tsrc");
        assertAnswer("REFUSED Unknown domain: NOPE.",
            "SELECT ABS(i) OVER (), SYSTEM$GET_TAG('tt', 'tsrc', 'NOPE') FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 23 | not enough arguments for function "
                + "[SYSTEM$GET_TAG(N, 'tt')], expected 3, got 2",
            "SELECT ABS(i) OVER (), SYSTEM$GET_TAG(n, 'tt') FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT UPPER(1, 2), SYSTEM$GET_TAG(n, 'tt', 'TABLE') FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT SYSTEM$GET_TAG(n, 'tt', 'TABLE') FROM tsrc WHERE nosuch = 1");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT 1 FROM tsrc WHERE SYSTEM$GET_TAG(n, 'tt', 'TABLE') = 'x' QUALIFY ABS(i) OVER () = 1");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT SYSTEM$GET_TAG(n, 'tt', 'TABLE') OVER (ORDER BY nosuch) FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT SUM(i) OVER (), SYSTEM$GET_TAG(n, 'tt', 'TABLE') FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_TBL' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_TBL.",
            "SELECT ABS(i) OVER (), SYSTEM$GET_TAG(n, 'tt', 'TABLE') FROM nosuch_tbl");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT i + TRUE, SYSTEM$GET_TAG(n, 'tt', 'TABLE') FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT SYSTEM$GET_TAG(n, 'tt', 'TABLE') FROM tsrc WHERE i + TRUE = 1");
        assertAnswer("REFUSED SQL compilation error: | argument needs to be a string: 'SYSTEM$GET_TAG'",
            "SELECT ABS(i) OVER (), SYSTEM$GET_TAG('tt', 'tsrc', 'TABLE', 1) FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT LOWER(n) OVER (), SYSTEM$GET_TAG(n, 'tt', 'TABLE') OVER () FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT SYSTEM$GET_TAG(n, 'tt', 'TABLE') OVER (), SYSTEM$GET_TAG('tt', n, 'TABLE') FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT (SELECT ABS(1) OVER ()), SYSTEM$GET_TAG(n, 'tt', 'TABLE') FROM tsrc");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                + "constant arguments expected",
            "SELECT ABS(i) OVER () FROM tsrc WHERE SYSTEM$GET_TAG(n, 'tt', 'TABLE') = 'x'");
    }
}
