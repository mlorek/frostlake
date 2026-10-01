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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The source a session variable's SET accepts. A source that fails as it folds to a constant, and a value the
 * variable cannot hold (a source typed VARIANT, OBJECT or ARRAY whose value is not NULL, a JSON null included),
 * are refused as a non-constant source; a source that is one scalar subquery runs as a query and fails with the
 * query's own error; a compilation error stays itself, placed in the statement's own text, as a CALL argument's
 * is; several variables are all computed before any is set. Every cell is live-verified.
 */
public class SessionVariableSourceTest extends BaseDatabaseTest {

    private static final String NON_CONSTANT = "Unsupported feature 'assignment from non-constant source expression'.";

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private String answer(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    @Test
    public void aSourceThatFailsToFoldIsNotConstant() {
        final String[] sources = {
            "1 / 0", "'x'::NUMBER", "'abc'::BINARY", "'abc'::BINARY(10)", "'a'::VARCHAR(1)::BINARY",
            "TO_NUMBER('x')", "CAST('x' AS NUMBER)", "'abc'::DATE", "TO_DATE('abc')", "10::NUMBER(1,0)",
            "UPPER(1/0)", "IFF(FALSE, 1, 1/0)", "1/0 = 1", "NOT (1/0 = 1)", "'a' || 1/0", "(SELECT 1/0) + 1",
            "(SELECT 1) + 1/0", "1/0 + (SELECT 1)", "(SELECT 1 UNION ALL SELECT 2) + 1",
        };
        for (final String source : sources) {
            assertEquals(NON_CONSTANT, refusal("SET x = " + source), source);
        }
    }

    @Test
    public void aBranchTheFoldDoesNotTakeNeverFailsIt() {
        engine.execute("SET x = IFF(TRUE, 1, 1/0)");
        assertEquals("true", answer("SELECT $x = 1").toLowerCase());
        engine.execute("SET x = CASE WHEN TRUE THEN 2 ELSE 1/0 END");
        assertEquals("true", answer("SELECT $x = 2").toLowerCase());
        engine.execute("SET x = COALESCE(3, 1/0)");
        assertEquals("true", answer("SELECT $x = 3").toLowerCase());
        engine.execute("SET x = TRY_TO_NUMBER('x') + 1");
        assertEquals("null", answer("SELECT TO_VARCHAR($x)").toLowerCase());
        engine.execute("SET x = TRY_CAST('abc' AS BINARY)");
        assertEquals("null", String.valueOf(engine.executeQuery("SELECT $x").getRows().get(0).getValue(0)));
    }

    @Test
    public void aWholeSubqueryFailsWithTheQuerysOwnError() {
        assertEquals("Division by zero", refusal("SET x = (SELECT 1/0)"));
        assertEquals("Division by zero", refusal("SET x = ((SELECT 1/0))"));
        assertEquals("Single-row subquery returns more than one row.", refusal("SET x = (SELECT 1 UNION ALL SELECT 2)"));
        assertEquals("Division by zero", refusal("SET (a, b) = ((SELECT 1/0), 1)"));
        assertEquals("Division by zero", refusal("SET (a, b) = (1, (SELECT 1/0))"));
    }

    @Test
    public void aSemiStructuredValueIsNotConstant() {
        final String[] sources = {
            "TO_VARIANT(1)", "'1'::VARIANT", "OBJECT_CONSTRUCT()", "ARRAY_CONSTRUCT()", "OBJECT_CONSTRUCT('a', 1)",
            "PARSE_JSON('{\"a\":1}')", "ARRAY_CONSTRUCT(1, 2)", "PARSE_JSON('null')", "TO_ARRAY(1)",
            "TO_OBJECT(PARSE_JSON('{}'))", "(SELECT OBJECT_CONSTRUCT('a', 1))", "(SELECT PARSE_JSON('1'))",
            "(SELECT TO_VARIANT(1))", "(SELECT ARRAY_CONSTRUCT())", "(SELECT PARSE_JSON('null'))",
        };
        for (final String source : sources) {
            assertEquals(NON_CONSTANT, refusal("SET x = " + source), source);
        }
        assertEquals(NON_CONSTANT, refusal("SET (a, b) = (1, TO_VARIANT(1))"));
        engine.execute("CREATE OR REPLACE FUNCTION svs_variant() RETURNS VARIANT AS 'TO_VARIANT(1)'");
        assertEquals(NON_CONSTANT, refusal("SET x = svs_variant()"));
    }

    @Test
    public void aSemiStructuredSourceIsNotConstantWhateverItsValueReadsAs() {
        final String[] sources = {
            "GET(PARSE_JSON('{\"a\":1}'), 'a')", "PARSE_JSON('{\"a\":1}'):a", "OBJECT_CONSTRUCT('a', 1):a",
            "OBJECT_CONSTRUCT('a', 1)['a']", "ARRAY_CONSTRUCT(1)[0]", "PARSE_JSON('[1]')[0]",
            "ARRAY_SLICE(ARRAY_CONSTRUCT(1, 2), 0, 1)", "(SELECT PARSE_JSON('[1]'))[0]",
        };
        for (final String source : sources) {
            assertEquals(NON_CONSTANT, refusal("SET x = " + source), source);
        }
    }

    @Test
    public void aSqlNullOfASemiStructuredTypeIsTaken() {
        final String[] sources = {
            "((SELECT NULL::VARIANT))", "(SELECT NULL::OBJECT)", "(SELECT TO_ARRAY(NULL))", "NULL::ARRAY",
            "CAST(NULL AS OBJECT)", "TO_OBJECT(NULL)", "(NULL::VARIANT)", "NULL::ARRAY::ARRAY",
            "IFF(TRUE, NULL::VARIANT, NULL::VARIANT)", "IFF(FALSE, TO_VARIANT(1), NULL::VARIANT)",
            "COALESCE(NULL::VARIANT, NULL::VARIANT)", "NVL(NULL::VARIANT, NULL::VARIANT)",
            "CASE WHEN TRUE THEN NULL::VARIANT END", "CASE WHEN FALSE THEN TO_VARIANT(1) END",
            "(SELECT NULL::VARIANT FROM (SELECT 1))", "(SELECT NULL::VARIANT WHERE TRUE)",
            "(SELECT (SELECT NULL::VARIANT))", "(SELECT IFF(TRUE, NULL::VARIANT, NULL::VARIANT))",
        };
        for (final String source : sources) {
            engine.execute("SET x = " + source);
            assertEquals("null", String.valueOf(engine.executeQuery("SELECT $x").getRows().get(0).getValue(0)), source);
        }
        engine.execute("SET (x, y) = ((SELECT NULL::VARIANT), 1)");
        assertEquals("1", answer("EXECUTE IMMEDIATE $$BEGIN SET x = (SELECT NULL::VARIANT); RETURN 1; END;$$"));
        engine.execute("SET x = NULL::VARIANT");
        engine.execute("SET x = (SELECT NULL::VARIANT)");
        engine.execute("SET x = TO_VARIANT(NULL)");
        engine.execute("SET x = 'x' || TO_VARIANT(1)");
        assertEquals("x1", answer("SELECT $x"));
        engine.execute("SET x = TO_VARIANT(1)::NUMBER");
        assertEquals("1", answer("SELECT $x"));
        engine.execute("SET x = (SELECT TO_VARIANT(1)::NUMBER)");
        assertEquals("1", answer("SELECT $x"));
    }

    @Test
    public void foldableSourcesAreTaken() {
        final String[][] cells = {
            {"'a' || 'b'", "ab"}, {"UPPER('a')", "A"}, {"CONCAT('a', 'b')", "ab"}, {"TO_VARCHAR(5)", "5"},
            {"1 + 1", "2"}, {"(SELECT 1)", "1"}, {"TO_CHAR(5)", "5"}, {"SUBSTR('abc', 1, 2)", "ab"},
            {"ABS(-1)", "1"}, {"MOD(5, 2)", "1"}, {"IFF(TRUE, 1, 2)", "1"}, {"COALESCE(NULL, 1)", "1"},
            {"TO_BINARY('AB', 'HEX')", "AB"}, {"'41'::BINARY", "41"}, {"INITCAP('ab')", "Ab"},
            {"TRIM(' a ')", "a"}, {"SPLIT_PART('a,b', ',', 1)", "a"}, {"(SELECT CHR(65))", "A"},
            {"(SELECT 1) + 1", "2"}, {"LEFT('abc', 1)", "a"},
        };
        for (final String[] cell : cells) {
            engine.execute("SET x = " + cell[0]);
            assertEquals(cell[1], answer("SELECT TO_VARCHAR($x)"), cell[0]);
        }
        engine.execute("CREATE OR REPLACE SEQUENCE svs_seq");
        engine.execute("SET x = svs_seq.NEXTVAL");
        assertEquals("1", answer("SELECT TO_VARCHAR($x)"));
        engine.execute("CREATE OR REPLACE FUNCTION svs_one() RETURNS NUMBER AS '1'");
        engine.execute("SET x = svs_one()");
        assertEquals("1", answer("SELECT TO_VARCHAR($x)"));
    }

    @Test
    public void compilationErrorsStayThemselves() {
        assertEquals("SQL compilation error:\nUnknown function NOSUCH_FN.", refusal("SET x = nosuch_fn()"));
        assertEquals("SQL compilation error:\ninvalid type [CAST('{\"a\":1}' AS OBJECT)] for parameter 'TO_OBJECT'",
            refusal("SET x = '{\"a\":1}'::OBJECT"));
    }

    @Test
    public void aCompilationErrorIsPlacedInTheStatementsOwnText() {
        final String[][] cells = {
            {"SET x = rv_a.rv_b", "1", "8", "invalid identifier 'RV_A.RV_B'"},
            {"SET x = nosuchcol", "1", "8", "invalid identifier 'NOSUCHCOL'"},
            {"SET x = (SELECT nosuchcol)", "1", "16", "invalid identifier 'NOSUCHCOL'"},
            {"SET x = (SELECT nosuchcol) + 1", "1", "16", "invalid identifier 'NOSUCHCOL'"},
            {"SET x = UPPER(1, 2)", "1", "8", "too many arguments for function [UPPER(1, 2)] expected 1, got 2"},
            {"SET longername = UPPER(1, 2)", "1", "17", "too many arguments for function [UPPER(1, 2)] expected 1, got 2"},
            {"SET x =\n  rv_a.rv_b", "2", "2", "invalid identifier 'RV_A.RV_B'"},
            {"SET x = 1 + rv_a.rv_b", "1", "12", "invalid identifier 'RV_A.RV_B'"},
            {"SET (x, y) = (1, rv_a.rv_b)", "1", "17", "invalid identifier 'RV_A.RV_B'"},
        };
        for (final String[] cell : cells) {
            assertEquals("SQL compilation error: error line " + cell[1] + " at position " + cell[2] + "\n" + cell[3],
                refusal(cell[0]), cell[0]);
        }
        engine.execute("CREATE OR REPLACE PROCEDURE sv_pa(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN RETURN n; END;$$");
        final String[][] calls = {
            {"CALL sv_pa(nosuch)", "11", "invalid identifier 'NOSUCH'"},
            {"CALL sv_pa(rv_a.rv_b)", "11", "invalid identifier 'RV_A.RV_B'"},
            {"CALL sv_pa((SELECT nosuchcol))", "19", "invalid identifier 'NOSUCHCOL'"},
            {"CALL sv_pa(n => rv_a.rv_b)", "16", "invalid identifier 'RV_A.RV_B'"},
        };
        for (final String[] call : calls) {
            assertEquals("SQL compilation error: error line 1 at position " + call[1] + "\n" + call[2], refusal(call[0]),
                call[0]);
        }
    }

    @Test
    public void aStatementOfABlockIsPlacedInItsOwnText() {
        final String arity = "too many arguments for function [UPPER(1, 2)] expected 1, got 2";
        engine.execute("CREATE OR REPLACE PROCEDURE sv_v(n VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$BEGIN RETURN n; END;$$");
        final String[][] cells = {
            {"BEGIN CALL sv_v(UPPER(1, 2)); RETURN 1; END;", "1", "6", "1", "10", arity},
            {"BEGIN\n  CALL sv_v(UPPER(1, 2));\n  RETURN 1;\nEND;", "2", "2", "1", "10", arity},
            {"BEGIN\n  CALL sv_v(\n    UPPER(1, 2));\n  RETURN 1;\nEND;", "2", "2", "2", "4", arity},
            {"BEGIN CALL sv_v('a' || UPPER(1, 2)); RETURN 1; END;", "1", "6", "1", "17", arity},
            {"BEGIN CALL sv_v((SELECT nosuchcol)); RETURN 1; END;", "1", "6", "1", "18", "invalid identifier 'NOSUCHCOL'"},
            {"BEGIN CALL sv_v(n => UPPER(1, 2)); RETURN 1; END;", "1", "6", "1", "15", arity},
            {"DECLARE v VARCHAR DEFAULT 'x'; BEGIN CALL sv_v(UPPER(1, 2)); RETURN 1; END;", "1", "37", "1", "10", arity},
            {"BEGIN SET x = (SELECT nosuchcol); RETURN 1; END;", "1", "6", "1", "16", "invalid identifier 'NOSUCHCOL'"},
            {"BEGIN SET x = 1 + UPPER(1, 2); RETURN 1; END;", "1", "6", "1", "12", arity},
            {"BEGIN\n  SET x = 1 + UPPER(1, 2);\n  RETURN 1;\nEND;", "2", "2", "1", "12", arity},
        };
        for (final String[] cell : cells) {
            assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line " + cell[1] + " at position " + cell[2]
                    + " : SQL compilation error: error line " + cell[3] + " at position " + cell[4] + "\n" + cell[5],
                refusal("EXECUTE IMMEDIATE $$" + cell[0] + "$$"), cell[0]);
        }
        engine.execute("CREATE OR REPLACE PROCEDURE sv_b() RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$BEGIN CALL sv_v(UPPER(1, 2)); RETURN 1; END;$$");
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation error: error"
            + " line 1 at position 10\n" + arity, refusal("CALL sv_b()"));
    }

    @Test
    public void aSystemFunctionFailsInItsOwnWords() {
        for (final String source : new String[] {"SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('NO_SUCH_TASK')",
            "UPPER(SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('NO_SUCH_TASK'))"}) {
            final String refused = refusal("SET o = " + source);
            assertEquals(true, refused.startsWith("Task NO_SUCH_TASK not found or not authorized."), refused);
        }
        engine.execute("SET t = SYSTEM$TYPEOF(123)");
        assertEquals(true, answer("SELECT $t").startsWith("NUMBER(3,0)"));
    }

    @Test
    public void everyValueIsComputedBeforeAnyVariableIsSet() {
        engine.execute("SET (mn, mx) = (40, 70)");
        engine.execute("SET (mn, mx) = (50, 2 * $mn)");
        assertEquals("50,80", answer("SELECT $mn || ',' || $mx"));
        engine.execute("SET a = 0");
        assertEquals(NON_CONSTANT, refusal("SET (a, b) = (1, TO_VARIANT(1))"));
        assertEquals("0", answer("SELECT TO_VARCHAR($a)"));
        assertEquals(NON_CONSTANT, refusal("SET (a, b) = (2, 1/0)"));
        assertEquals("0", answer("SELECT TO_VARCHAR($a)"));
        engine.execute("SET x = 1");
        assertEquals("Division by zero", refusal("SET x = (SELECT 1/0)"));
        assertEquals("1", answer("SELECT TO_VARCHAR($x)"));
    }
}
