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
 * A block's own expression is judged with the names an exception handler supplies typed as the account binds them
 * (live-verified): SQLCODE a NUMBER(9,0), SQLSTATE and SQLERRM texts at their values' widths, so {@code 'x' ||
 * STARTSWITH(SQLERRM, 'x')} is refused for its BOOLEAN operand as {@code 'x' || STARTSWITH('a', 'b')} is, at the last
 * {@code ||} of its chain and with each name written out as a bind, {@code (:SQLCODE)::NUMBER(9,0)}; a {@code ||} or
 * a CONCAT with a BOOLEAN operand is refused in a query alike.
 */
public class HandlerErrorVariableTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void aHandlersNamesAreTypedAsTheAccountBindsThem() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN 'x' || STARTSWITH(SQLERRM, 'x'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 4 | Invalid argument types for function '||': (VARCHAR(1), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abcdef'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN 'x' || STARTSWITH(SQLERRM, 'x'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 89 : SQL compilation "
                + "error: error line 1 at position 4 | Invalid argument types for function '||': (VARCHAR(1), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || STARTSWITH('a', 'b'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 24 | Invalid argument types for function '||': (NUMBER(9,0), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLSTATE || STARTSWITH('a', 'b'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 12 | Invalid argument types for function '||': (VARCHAR(5), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN 'x' || STARTSWITH(SQLSTATE, 'x'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 4 | Invalid argument types for function '||': (VARCHAR(1), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN 'x' || STARTSWITH('a', 'b'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 4 | Invalid argument types for function '||': (VARCHAR(1), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLERRM || STARTSWITH('a', 'b'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 11 | Invalid argument types for function '||': (VARCHAR(3), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abcdef'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLERRM || STARTSWITH('a', 'b'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 89 : SQL compilation "
                + "error: error line 1 at position 11 | Invalid argument types for function '||': (VARCHAR(6), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || 'x'; END; $$",
                "-20001x"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || '|' || SQLSTATE || '|' || SQLERRM; END; $$",
                "-20001|P0001|abc"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || '|' || STARTSWITH(SQLERRM, 'x'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 31 | Invalid argument types for function '||': (NUMBER(9,0), "
                + "VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN 'x' || '|' || STARTSWITH(SQLERRM, 'x') || '|' || CONTAINS(SQLERRM, 'y'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 49 | Invalid argument types for function '||': (VARCHAR(1), "
                + "VARCHAR(1), BOOLEAN, VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abcdef'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN 'x' || '|' || STARTSWITH(SQLERRM, 'x') || '|' || CONTAINS(SQLERRM, 'y'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 89 : SQL compilation "
                + "error: error line 1 at position 49 | Invalid argument types for function '||': (VARCHAR(1), "
                + "VARCHAR(1), BOOLEAN, VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN CONCAT('x', STARTSWITH(SQLERRM, 'x')); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 0 | Invalid argument types for function 'CONCAT': (VARCHAR(1), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN RETURN UPPER(SQLERRM) || STARTSWITH('a', 'b'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 86 : SQL compilation "
                + "error: error line 1 at position 18 | Invalid argument types for function '||': (VARCHAR(9), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE m VARCHAR DEFAULT 'abc'; BEGIN RETURN 'x' || STARTSWITH(m, 'x'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL compilation "
                + "error: error line 1 at position 4 | Invalid argument types for function '||': (VARCHAR(1), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE m VARCHAR DEFAULT 'abcdef'; BEGIN RETURN 'x' || STARTSWITH(m, 'x'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL compilation "
                + "error: error line 1 at position 4 | Invalid argument types for function '||': (VARCHAR(1), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE m VARCHAR DEFAULT 'abc'; BEGIN RETURN m || STARTSWITH('a', 'b'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL compilation "
                + "error: error line 1 at position 4 | Invalid argument types for function '||': (VARCHAR(3), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE n NUMBER DEFAULT 5; BEGIN RETURN n || STARTSWITH('a', 'b'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: error line 1 at position 18 | Invalid argument types for function '||': (NUMBER(38,0), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'abc'); BEGIN RAISE e; EXCEPTION WHEN OTHER "
                + "THEN LET r VARCHAR := 'x' || STARTSWITH(SQLERRM, 'x'); RETURN r; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 96 : SQL compilation "
                + "error: error line 1 at position 12 | Invalid argument types for function '||': (VARCHAR(1), "
                + "BOOLEAN)"},
        });
    }

    @Test
    public void aConcatenationWithABooleanOperandIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE tb900 (b BOOLEAN, s VARCHAR(5), n NUMBER(9,0))");
        engine.execute("INSERT INTO tb900 VALUES (TRUE, 'a', 1)");
        assertCells(new String[][] {
            {"SELECT TRUE || 'x'", "truex"},
            {"SELECT 'x' || FALSE", "xfalse"},
            {"SELECT CONCAT('x', TRUE)", "xtrue"},
            {"SELECT 'a' || TRUE || 'b'", "atrueb"},
            {"SELECT 1 || TRUE", "1true"},
            {"SELECT TRUE || TRUE", "truetrue"},
            {"SELECT NULL::BOOLEAN || 'x'", "null"},
            {"SELECT CONCAT(TRUE, FALSE)", "truefalse"},
            {"SELECT 'x' || (1 = 1)",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'||': (VARCHAR(1), BOOLEAN)"},
            {"SELECT 'x' || STARTSWITH('ab', 'a')",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'||': (VARCHAR(1), BOOLEAN)"},
            {"SELECT 'x' || TO_BOOLEAN('true')", "xtrue"},
            {"SELECT b || s FROM tb900", "truea"},
            {"SELECT s || b FROM tb900", "atrue"},
            {"SELECT CONCAT(s, b) FROM tb900", "atrue"},
            {"SELECT b::VARCHAR || s FROM tb900", "truea"},
            {"SELECT n || s || b FROM tb900", "1atrue"},
            {"SELECT CONCAT(n, s, b) FROM tb900", "1atrue"},
            {"SELECT 'x' || PARSE_JSON('true')", "xtrue"},
            {"SELECT 'x' || TRUE::VARIANT", "xtrue"},
            {"SELECT 'x' || IFF(TRUE, TRUE, FALSE)", "xtrue"},
            {"SELECT 'a' || 1 || TRUE", "a1true"},
            {"SELECT CONCAT_WS(',', 'a', TRUE)", "a,true"},
            {"SELECT ARRAY_TO_STRING([TRUE], ',')", "true"},
            {"SELECT 'x' || NULL", "null"},
            {"SELECT TRUE || NULL", "null"},
            {"SELECT s FROM tb900 WHERE s || b = 'atrue'", "a"},
            {"SELECT TRUE || 'x' FROM tb900 WHERE FALSE", ""},
            {"SELECT TYPEOF(TRUE || 'x')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TYPEOF': (VARCHAR(134217728))"},
            {"CREATE OR REPLACE VIEW vb900 AS SELECT TRUE || 'x' AS c", "View VB900 successfully created."},
            {"SELECT COUNT(*) FROM tb900 GROUP BY b || s", "1"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN TRUE || 'x'; END; $$", "truex"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN 'x' || STARTSWITH('ab', 'a'); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL compilation "
                + "error: error line 1 at position 4 | Invalid argument types for function '||': (VARCHAR(1), "
                + "BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ DECLARE b BOOLEAN DEFAULT TRUE; BEGIN RETURN 'x' || b; END; $$", "xtrue"},
            {"EXECUTE IMMEDIATE $$ DECLARE b BOOLEAN DEFAULT TRUE; r VARCHAR; BEGIN r := 'x' || b; RETURN r; END; $$",
                "xtrue"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL nosuch_p2342(1); RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN "
                + "SQLCODE || '|' || SQLSTATE || '|' || STARTSWITH(SQLERRM, 'x') || '|' || CONTAINS(SQLERRM, 'y'); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 77 : SQL compilation "
                + "error: error line 1 at position 91 | Invalid argument types for function '||': (NUMBER(9,0), "
                + "VARCHAR(1), VARCHAR(5), VARCHAR(1), BOOLEAN, VARCHAR(1), BOOLEAN)"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN CONCAT('x', TRUE); END; $$", "xtrue"},
            {"EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN SELECT TRUE || 'x' INTO :r; RETURN r; END; $$", "truex"},
            {"EXECUTE IMMEDIATE $$ BEGIN\n  RETURN 'a' ||\n    TRUE;\nEND; $$", "atrue"},
            {"CREATE OR REPLACE FUNCTION fb900() RETURNS VARCHAR AS 'TRUE || ''x'''",
                "Function FB900 successfully created."},
            {"CREATE OR REPLACE FUNCTION fb901(b BOOLEAN) RETURNS VARCHAR AS 'b || ''x'''",
                "Function FB901 successfully created."},
        });
    }
}
