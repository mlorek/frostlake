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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block's own expression is judged for argument types as SQL judges them. A RETURN's value, a condition and a
 * typed or assigned value are judged when reached and refused as their EXPRESSION_ERROR, placed in the expression
 * with each variable written out as its type; an untyped declaration's initialiser and a simple CASE's operand
 * are judged while the block compiles and refused bare, placed in the block. Every cell is live-verified.
 */
public class BlockExpressionArgumentTypeTest extends BaseDatabaseTest {

    private static final String PLUS_DATE_BOOLEAN = "Invalid argument types for function '+': (DATE, BOOLEAN)";

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

    private static String block(final String body) {
        return "EXECUTE IMMEDIATE $$ " + body + " $$";
    }

    private static String uncaught(final int at, final int inner, final String sentence) {
        return "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position " + at
            + " : SQL compilation error: error line 1 at position " + inner + "\n" + sentence;
    }

    private static String compiled(final int at, final String sentence) {
        return "SQL compilation error: error line 1 at position " + at + "\n" + sentence;
    }

    @Test
    public void aReachedExpressionIsRefusedAsItsExpressionError() {
        final String[][] cells = {
            {"BEGIN RETURN TO_DATE('2024-01-01') + TRUE; END;", "14", "22", PLUS_DATE_BOOLEAN},
            {"BEGIN RETURN TO_DATE('2024-01-01') - TRUE; END;", "14", "22",
                "Invalid argument types for function '-': (DATE, BOOLEAN)"},
            {"BEGIN LET x := 1; RETURN x + TRUE; END;", "26", "18",
                "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"},
            {"BEGIN LET x := 1; RETURN TRUE + x; END;", "26", "5",
                "Invalid argument types for function '+': (BOOLEAN, NUMBER(38,0))"},
            {"BEGIN RETURN TO_DATE('2024-01-01') AND TRUE; END;", "14", "22",
                "Invalid argument types for function 'AND': (DATE, BOOLEAN)"},
            {"BEGIN LET x := TO_DATE('2024-01-01'); RETURN x AND TRUE; END;", "46", "10",
                "Invalid argument types for function 'AND': (DATE, BOOLEAN)"},
            {"BEGIN RETURN NOT TO_DATE('2024-01-01'); END;", "14", "0", "Invalid argument types for function 'NOT': (DATE)"},
            {"BEGIN LET d := TO_DATE('2024-01-01'); RETURN NOT d; END;", "46", "0",
                "Invalid argument types for function 'NOT': (DATE)"},
            {"BEGIN RETURN ABS(TRUE); END;", "14", "0", "Invalid argument types for function 'ABS': (BOOLEAN)"},
            {"BEGIN LET x VARCHAR := 'a'; RETURN x + TRUE; END;", "36", "4",
                "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"},
            {"BEGIN LET x DATE := '2024-01-01'; RETURN x + TRUE; END;", "42", "10", PLUS_DATE_BOOLEAN},
            {"BEGIN LET x NUMBER(5,2) := 1; RETURN x + TRUE; END;", "38", "17",
                "Invalid argument types for function '+': (NUMBER(5,2), BOOLEAN)"},
            {"BEGIN LET x FLOAT := 1; RETURN x + TRUE; END;", "32", "11",
                "Invalid argument types for function '+': (FLOAT, BOOLEAN)"},
            {"BEGIN LET x TIMESTAMP := '2024-01-01'; RETURN x + TRUE; END;", "47", "22",
                "Invalid argument types for function '+': (TIMESTAMP_NTZ(9), BOOLEAN)"},
            {"BEGIN LET x := 1; RETURN 1 + x + TRUE; END;", "26", "22",
                "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"},
            {"BEGIN LET x := 1; LET z := 2; RETURN x + z + TRUE; END;", "38", "38",
                "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"},
            {"BEGIN LET x := 1; RETURN :x + TRUE; END;", "26", "16",
                "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"},
            {"BEGIN LET x := 1; RETURN :x + :x + TRUE; END;", "26", "34",
                "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"},
            {"BEGIN LET d := TO_DATE('2024-01-01'); RETURN :d + TRUE; END;", "46", "8", PLUS_DATE_BOOLEAN},
            {"BEGIN LET s := 'q'; RETURN :s + TRUE; END;", "28", "2",
                "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"},
            {"BEGIN LET abcdef := 1; RETURN abcdef + TRUE; END;", "31", "23",
                "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"},
            {"BEGIN RETURN (SELECT 1) + TRUE; END;", "14", "18",
                "Invalid argument types for function '+': (NUMBER(1,0), BOOLEAN)"},
            {"BEGIN LET x := 1; RETURN (SELECT 1) + x + TRUE; END;", "26", "39",
                "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN i + TRUE; END FOR; RETURN 1; END;", "33", "18",
                "Invalid argument types for function '+': (NUMBER(9,0), BOOLEAN)"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN 1 + i + TRUE; END FOR; RETURN 1; END;", "33", "22",
                "Invalid argument types for function '+': (NUMBER(10,0), BOOLEAN)"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN :i + TRUE; END FOR; RETURN 1; END;", "33", "15",
                "Invalid argument types for function '+': (NUMBER(9,0), BOOLEAN)"},
            {"BEGIN LET t := CURRENT_TIMESTAMP(); RETURN t + TRUE; END;", "44", "22",
                "Invalid argument types for function '+': (TIMESTAMP_LTZ(9), BOOLEAN)"},
            {"BEGIN LET b BINARY := TO_BINARY('AB'); RETURN b + TRUE; END;", "47", "22",
                "Invalid argument types for function '+': (BINARY(67108864), BOOLEAN)"},
        };
        for (final String[] cell : cells) {
            assertEquals(uncaught(Integer.parseInt(cell[1]), Integer.parseInt(cell[2]), cell[3]),
                refusal(block(cell[0])), cell[0]);
        }
    }

    @Test
    public void conditionsAndAssignedValuesArePlacedAsTheAccountPlacesThem() {
        final String[][] cells = {
            {"BEGIN IF (TO_DATE('2024-01-01') + TRUE = 1) THEN RETURN 1; END IF; RETURN 2; END;", "11", "22"},
            {"BEGIN WHILE (TO_DATE('2024-01-01') + TRUE = 1) DO RETURN 1; END WHILE; RETURN 2; END;", "14", "22"},
            {"BEGIN IF (FALSE) THEN RETURN 1; ELSEIF (TO_DATE('2024-01-01') + TRUE = 1) THEN RETURN 2; END IF; RETURN 3;"
                + " END;", "41", "22"},
            {"BEGIN LET y NUMBER := TO_DATE('2024-01-01') + TRUE; RETURN 1; END;", "23", "30"},
            {"BEGIN LET y NUMBER := 1 + (TO_DATE('2024-01-01') + TRUE); RETURN 1; END;", "23", "35"},
            {"DECLARE d DATE DEFAULT TO_DATE('2024-01-01') + TRUE; BEGIN RETURN 1; END;", "24", "30"},
            {"BEGIN LET d := TO_DATE('2024-01-01'); d := d + TRUE; RETURN 1; END;", "44", "18"},
        };
        for (final String[] cell : cells) {
            assertEquals(uncaught(Integer.parseInt(cell[1]), Integer.parseInt(cell[2]), PLUS_DATE_BOOLEAN),
                refusal(block(cell[0])), cell[0]);
        }
        assertEquals(uncaught(23, 18, "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"),
            refusal(block("BEGIN LET x := 1; IF (x + TRUE = 1) THEN RETURN 1; END IF; RETURN 2; END;")));
        assertEquals(uncaught(35, 26, "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"),
            refusal(block("BEGIN LET x := 1; LET y NUMBER := x + TRUE; RETURN 1; END;")));
        assertEquals(uncaught(35, 47, "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"),
            refusal(block("BEGIN LET x := 1; LET y NUMBER := (SELECT 1) + x + TRUE; RETURN 1; END;")));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL compilation error: error"
                + " line 2 at position 25\n" + PLUS_DATE_BOOLEAN,
            refusal("EXECUTE IMMEDIATE $$ BEGIN RETURN 1 +\n  (TO_DATE('2024-01-01') + TRUE); END; $$"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 17 : SQL compilation error: error"
                + " line 1 at position 2\nInvalid argument types for function '+': (NUMBER(1,0), BOOLEAN)",
            refusal(block("BEGIN CASE WHEN 6 + TRUE = 1 THEN RETURN 1; END CASE; RETURN 2; END;")));
    }

    @Test
    public void aComparisonThatCannotConvertIsRefusedUnplaced() {
        final String sentence = "SQL compilation error:\nCan not convert parameter 'TRUE' of type [BOOLEAN] into expected type [DATE]";
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : " + sentence,
            refusal(block("BEGIN RETURN TO_DATE('2024-01-01') = TRUE; END;")));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 11 : " + sentence,
            refusal(block("BEGIN IF (TO_DATE('2024-01-01') = TRUE) THEN RETURN 1; END IF; RETURN 2; END;")));
        assertEquals(sentence, refusal(block("BEGIN LET y := TO_DATE('2024-01-01') = TRUE; RETURN 1; END;")));
    }

    @Test
    public void anExpressionErrorHandlerCatchesIt() {
        assertEquals("caught 1044 42P13", answer(block("BEGIN RETURN TO_DATE('2024-01-01') + TRUE; EXCEPTION WHEN"
            + " EXPRESSION_ERROR THEN RETURN 'caught ' || SQLCODE || ' ' || SQLSTATE; END;")));
        assertEquals("1038 22023", answer(block("BEGIN RETURN TO_DATE('2024-01-01') = TRUE; EXCEPTION WHEN"
            + " EXPRESSION_ERROR THEN RETURN SQLCODE || ' ' || SQLSTATE; END;")));
        assertEquals("other", answer(block("BEGIN RETURN TO_DATE('2024-01-01') + TRUE; EXCEPTION WHEN STATEMENT_ERROR"
            + " THEN RETURN 'stmt'; WHEN OTHER THEN RETURN 'other'; END;")));
        assertEquals("expr", answer(block("BEGIN LET x DATE := TO_DATE('2024-01-01') + TRUE; RETURN 1; EXCEPTION WHEN"
            + " STATEMENT_ERROR THEN RETURN 'stmt'; WHEN EXPRESSION_ERROR THEN RETURN 'expr'; END;")));
    }

    @Test
    public void anUntypedInitialiserIsRefusedWhileTheBlockCompiles() {
        final String[][] cells = {
            {"BEGIN LET d := TO_DATE('2024-01-01'); LET y := d + TRUE; RETURN 1; END;", "48", PLUS_DATE_BOOLEAN},
            {"BEGIN LET d := TO_DATE('2024-01-01'); LET y := 1 + (d + TRUE); RETURN 1; END;", "55", PLUS_DATE_BOOLEAN},
            {"DECLARE d DATE DEFAULT '2024-01-01'; y DEFAULT d + TRUE; BEGIN RETURN 1; END;", "48", PLUS_DATE_BOOLEAN},
            {"BEGIN LET d := TO_DATE('2024-01-01'); IF (FALSE) THEN LET y := d + TRUE; END IF; RETURN 1; END;", "64",
                PLUS_DATE_BOOLEAN},
            {"BEGIN LET y := TO_DATE('2024-01-01') + TRUE; RETURN 1; END;", "16", PLUS_DATE_BOOLEAN},
            {"BEGIN LET d DATE := '2024-01-01'; LET y := d + TRUE; RETURN 1; END;", "44", PLUS_DATE_BOOLEAN},
            {"BEGIN LET d := TO_DATE('2024-01-01'); LET y := TRUE + d; RETURN 1; END;", "48",
                "Invalid argument types for function '+': (BOOLEAN, DATE)"},
            {"BEGIN LET d := TO_DATE('2024-01-01'); LET y := 1 + (TRUE + d); RETURN 1; END;", "58",
                "Invalid argument types for function '+': (BOOLEAN, DATE)"},
            {"BEGIN LET d := TO_DATE('2024-01-01'); LET y := (d + TRUE); RETURN 1; END;", "48", PLUS_DATE_BOOLEAN},
            {"BEGIN LET x := 1; LET y := 5 + (x + TRUE); RETURN 1; END;", "35",
                "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"},
            {"BEGIN LET y := 5 + (6 + TRUE); RETURN 1; END;", "23",
                "Invalid argument types for function '+': (NUMBER(1,0), BOOLEAN)"},
            {"BEGIN LET y := ABS(TRUE); RETURN 1; END;", "16", "Invalid argument types for function 'ABS': (BOOLEAN)"},
            {"BEGIN LET y := 5 + ABS(TRUE); RETURN 1; END;", "20", "Invalid argument types for function 'ABS': (BOOLEAN)"},
            {"BEGIN LET y := NOT TO_DATE('2024-01-01'); RETURN 1; END;", "16",
                "Invalid argument types for function 'NOT': (DATE)"},
            {"BEGIN LET s := 'abc'; LET y := s + TRUE; RETURN 1; END;", "32",
                "Invalid argument types for function '+': (VARCHAR(134217728), BOOLEAN)"},
            {"DECLARE s VARCHAR DEFAULT 'abc'; BEGIN LET y := s + TRUE; RETURN 1; END;", "49",
                "Invalid argument types for function '+': (VARCHAR(134217728), BOOLEAN)"},
            {"BEGIN FOR i IN 1 TO 2 DO LET y := i + TRUE; END FOR; RETURN 1; END;", "35",
                "Invalid argument types for function '+': (NUMBER(9,0), BOOLEAN)"},
            {"BEGIN RETURN 1; LET y := TRUE + 1; END;", "26", "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))"},
            {"BEGIN LET d := TO_DATE('2024-01-01'); BEGIN LET y := d + TRUE; END; RETURN 1; END;", "54", PLUS_DATE_BOOLEAN},
            {"BEGIN LET y := 6 + TRUE; RETURN 1; EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;", "16",
                "Invalid argument types for function '+': (NUMBER(1,0), BOOLEAN)"},
            {"BEGIN LET y := 6 + TRUE; LET z NUMBER := :nosuch; RETURN 1; END;", "16",
                "Invalid argument types for function '+': (NUMBER(1,0), BOOLEAN)"},
            {"BEGIN LET y := missing + 1; LET z := 6 + TRUE; RETURN 1; END;", "38",
                "Invalid argument types for function '+': (NUMBER(1,0), BOOLEAN)"},
            {"BEGIN RETURN missing; LET y := 5 + (6 + TRUE); END;", "39",
                "Invalid argument types for function '+': (NUMBER(1,0), BOOLEAN)"},
        };
        for (final String[] cell : cells) {
            assertEquals(compiled(Integer.parseInt(cell[1]), cell[2]), refusal(block(cell[0])), cell[0]);
        }
        assertEquals(compiled(91, PLUS_DATE_BOOLEAN), refusal(block("BEGIN CREATE OR REPLACE TABLE beat_mark (a INT);"
            + " LET d := TO_DATE('2024-01-01'); LET y := d + TRUE; RETURN 1; END;")));
        assertEquals(0, engine.executeQuery("SHOW TABLES LIKE 'BEAT_MARK'").getRows().size());
    }

    @Test
    public void aSimpleCaseOperandIsRefusedWhileTheBlockCompiles() {
        final String sentence = "Invalid argument types for function '+': (NUMBER(1,0), BOOLEAN)";
        assertEquals(compiled(12, sentence), refusal(block("BEGIN CASE (6 + TRUE) WHEN 1 THEN RETURN 1; END CASE; RETURN 2; END;")));
        assertEquals(compiled(28, sentence), refusal(block(
            "BEGIN IF (FALSE) THEN CASE (6 + TRUE) WHEN 1 THEN RETURN 1; END CASE; END IF; RETURN 2; END;")));
        assertEquals(compiled(20, sentence), refusal(block(
            "BEGIN CASE (5 + (6 + TRUE)) WHEN 1 THEN RETURN 1; END CASE; RETURN 2; END;")));
        assertEquals(compiled(24, "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)"), refusal(block(
            "BEGIN LET x := 1; CASE (x + TRUE) WHEN 1 THEN RETURN 1; END CASE; RETURN 2; END;")));
    }

    @Test
    public void anInitialiserOfTypeVariantIsNotInferred() {
        final String inferred = "SQL compilation error: error line 1 at position %d\n"
            + " variable '%s' cannot have its type inferred from initializer";
        final String[][] cells = {
            {"BEGIN LET v := PARSE_JSON('1'); RETURN 1; END;", "7", "V"},
            {"BEGIN LET v := TO_VARIANT(NULL); RETURN 1; END;", "7", "V"},
            {"BEGIN LET v := 1::VARIANT; RETURN 1; END;", "7", "V"},
            {"BEGIN LET v := TO_VARIANT(1); RETURN v AND TRUE; END;", "7", "V"},
            {"BEGIN LET v VARIANT := 1; LET w := v; RETURN 1; END;", "27", "W"},
            {"BEGIN LET v VARIANT := 1; LET w := v:a; RETURN 1; END;", "27", "W"},
            {"DECLARE v DEFAULT TO_VARIANT(1); BEGIN RETURN 1; END;", "9", "V"},
        };
        for (final String[] cell : cells) {
            assertEquals(String.format(inferred, Integer.parseInt(cell[1]), cell[2]), refusal(block(cell[0])), cell[0]);
        }
        assertEquals("1", answer(block("BEGIN LET v := OBJECT_CONSTRUCT('a', 1); RETURN 1; END;")));
        assertEquals("1", answer(block("BEGIN LET v := ARRAY_CONSTRUCT(1); RETURN 1; END;")));
        assertEquals("1", answer(block("BEGIN LET v := TO_VARIANT(1)::NUMBER; RETURN v; END;")));
    }

    @Test
    public void whatTheArgumentsTakeRuns() {
        assertEquals("1", answer(block("BEGIN IF (FALSE) THEN RETURN TO_DATE('2024-01-01') + TRUE; END IF; RETURN 1; END;")));
        assertEquals("FALSE", answer(block("BEGIN LET b := TRUE; RETURN NOT b; END;")).toUpperCase());
        assertEquals("2", answer(block("BEGIN LET x := 1; RETURN x + 1; END;")));
        assertEquals("abc1", answer(block("BEGIN LET s := 'abc'; RETURN s || 1; END;")));
        assertEquals("2024-01-02", answer(block("BEGIN LET d := TO_DATE('2024-01-01'); RETURN TO_VARCHAR(d + 1); END;")));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Numeric value '1true' is"
            + " not recognized", refusal(block("BEGIN LET x := 1; RETURN x || TRUE + 1; END;")));
    }

    @Test
    public void aCallRefusedForItsShapeOutranksTheArgumentTypesAroundIt() {
        final String all = "SQL compilation error:\ninvalid use of 'all' for function 'ABS(ALL 1)'";
        final String named = "\nfunction ABS does not support named arguments";
        // Reached: the call's own sentence, as the expression's EXPRESSION_ERROR.
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : " + all,
            refusal("EXECUTE IMMEDIATE $$BEGIN RETURN ABS(ALL 1) + TRUE; END;$$"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : " + all,
            refusal("EXECUTE IMMEDIATE $$BEGIN LET x NUMBER := ABS(ALL 1) + TRUE; RETURN x; END;$$"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 10 : " + all,
            refusal("EXECUTE IMMEDIATE $$BEGIN IF (ABS(ALL 1) + TRUE > 0) THEN RETURN 1; END IF; RETURN 2; END;$$"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 35 : SQL compilation error:\n"
                + "invalid use of 'distinct' for function 'UPPER(DISTINCT 'a')'",
            refusal("EXECUTE IMMEDIATE $$BEGIN LET s VARCHAR := 'a'; RETURN UPPER(DISTINCT s) || TO_DATE('2020-01-01')"
                + " + TRUE; END;$$"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL compilation error: error"
                + " line 1 at position 0" + named,
            refusal("EXECUTE IMMEDIATE $$BEGIN LET n NUMBER := 1; RETURN ABS(x => n) + TRUE; END;$$"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL compilation error: error"
                + " line 1 at position 0" + named,
            refusal("EXECUTE IMMEDIATE $$BEGIN FOR i IN 1 TO 1 DO RETURN ABS(x => i) + TRUE; END FOR; RETURN 0; END;$$"));
        // Judged while the block compiles: the call's own sentence, bare, on a branch that never runs too.
        assertEquals(all, refusal("EXECUTE IMMEDIATE $$BEGIN LET a := ABS(ALL 1) + TRUE; RETURN 1; END;$$"));
        assertEquals(all, refusal("EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET a := ABS(ALL 1) + TRUE; END IF;"
            + " RETURN 1; END;$$"));
        assertEquals(all, refusal("EXECUTE IMMEDIATE $$BEGIN CASE ABS(ALL 1) + TRUE WHEN 1 THEN RETURN 1; END CASE;"
            + " RETURN 2; END;$$"));
        assertEquals(all, refusal("EXECUTE IMMEDIATE $$DECLARE a DEFAULT ABS(ALL 1); BEGIN RETURN 1; END;$$"));
        assertEquals("SQL compilation error: error line 1 at position 19" + named,
            refusal("EXECUTE IMMEDIATE $$BEGIN LET a := 1 + ABS(x => 1); RETURN 1; END;$$"));
        assertEquals("SQL compilation error: error line 1 at position 31" + named,
            refusal("EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET a := ABS(x => 1) + TRUE; END IF; RETURN 1; END;$$"));
        // A RETURN that never runs is not compiled, and a quantifier the aggregate takes leaves the '+' to refuse.
        assertEquals("1", answer("EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN RETURN ABS(ALL 1) + TRUE; END IF;"
            + " RETURN 1; END;$$"));
        final String counted = refusal("EXECUTE IMMEDIATE $$BEGIN RETURN COUNT(DISTINCT 1) + TRUE; END;$$");
        assertTrue(counted.endsWith("\nInvalid argument types for function '+': (NUMBER(18,0), BOOLEAN)"), counted);
    }
}
