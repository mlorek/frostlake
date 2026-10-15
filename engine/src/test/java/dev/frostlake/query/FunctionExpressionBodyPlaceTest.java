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

import org.junit.jupiter.api.Test;

/**
 * Where a refusal inside a SQL function's expression body is placed: in the body as written — the lines and
 * the blanks the body opens with count, and a place on the body's first line stands one further for the frame
 * the body is read in — wherever the CREATE stands: alone, or as a block's statement whose handler reads the
 * place through SQLERRM (live-verified).
 */
public class FunctionExpressionBodyPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void throughAHandlersMessage() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "904 42000 SQL compilation error: error line 1 at position 36 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE VIEW vq AS SELECT nosuch FROM t; RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "904 42000 SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE FUNCTION fq2() RETURNS INT AS 'nosuch + 1'; RETURN 'none'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "904 42000 SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE FUNCTION fq3() RETURNS INT AS $$nosuch + 1$$; RETURN 'none'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "904 42000 SQL compilation error: error line 1 at position 8 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE FUNCTION fq4() RETURNS INT AS 'SELECT nosuch FROM t'; RETURN 'none'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "904 42000 SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
            "BEGIN SELECT nosuch FROM t; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''x''.",
            "BEGIN SELECT a FROM t LIMIT 'x'; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "904 42000 SQL compilation error: error line 1 at position 37 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE TABLE tq AS SELECT nosuch FROM t; RETURN 'none'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "904 42000 SQL compilation error: error line 1 at position 21 | invalid identifier 'NOSUCH'",
            "BEGIN INSERT INTO t SELECT nosuch, 1 FROM t; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE "
                + "|| ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "904 42000 SQL compilation error: error line 1 at position 36 | invalid identifier 'NOSUCH'",
            "BEGIN LET q := 1; CREATE OR REPLACE VIEW vq AS SELECT nosuch FROM t; RETURN 'none'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
        assertAnswer(
            "904 42000 SQL compilation error: error line 1 at position 36 | invalid identifier 'NOSUCH'",
            "BEGIN\n  CREATE OR REPLACE VIEW vq AS SELECT nosuch FROM t;\nRETURN 'none'; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END");
    }

    @Test
    public void aloneAndInABlock() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq2() RETURNS INT AS 'nosuch + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq3() RETURNS INT AS $$nosuch + 1$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 1 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '\n nosuch + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 3 at position 2 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '\n\n  nosuch + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 1 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS $$\n nosuch + 1$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 7 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '\nSELECT nosuch FROM t'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 2 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS ' nosuch + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 1 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '1 +\n nosuch'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 3 at position 1 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '\n 1 +\n nosuch'");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 3 at position 4 "
                + "unexpected ')'.",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '\n\n 1 +'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 5 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '\n ''a'' + TRUE'");
        assertAnswer(
            "SQL compilation error: error line 2 at position 1 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '\n nosuch + 1'; RETURN 'none'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLERRM; END");
        assertAnswer(
            "SQL compilation error: error line 1 at position 2 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS ' nosuch + 1'; RETURN 'none'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLERRM; END");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: error line 1 at position 1 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS 'nosuch + 1'; RETURN 'none'; END");
        assertAnswer(
            "SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCH'",
            "BEGIN LET z := 1; CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS 'nosuch + 1'; RETURN 'none'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END");
        assertAnswer(
            "SQL compilation error: error line 1 at position 5 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '1 + nosuch'; RETURN 'none'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLERRM; END");
        assertAnswer(
            "SQL compilation error: error line 1 at position 5 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE FUNCTION fq5(x INT) RETURNS INT AS 'x + nosuch'; RETURN 'none'; EXCEPTION "
                + "WHEN OTHER THEN RETURN SQLERRM; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 5 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq5(x INT) RETURNS INT AS 'x + nosuch'");
        assertAnswer(
            "SQL compilation error: error line 1 at position 8 | invalid identifier 'NOSUCH'",
            "BEGIN CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS 'SELECT nosuch FROM t'; RETURN 'none'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END");
        assertAnswer(
            "SQL compilation error: error line 1 at position 5 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "BEGIN CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '''a'' + TRUE'; RETURN 'none'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLERRM; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 5 | Invalid argument types for function '+': "
                + "(VARCHAR(1), BOOLEAN)",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '''a'' + TRUE'");
    }

    @Test
    public void afterACommentAndOnLaterLines() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCH'",
            "/* c */ CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS 'nosuch + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 3 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '\t nosuch + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 4 at position 3 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '\n\n  1 +\n   nosuch'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 5 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fq5(x INT) RETURNS INT AS '\n x + nosuch'");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : SQL compilation "
                + "error: error line 1 at position 5 | invalid identifier 'NOSUCH'",
            "BEGIN\n  CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS '1 + nosuch';\nEND");
    }
}
