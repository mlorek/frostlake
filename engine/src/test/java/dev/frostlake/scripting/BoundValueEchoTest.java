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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A refusal that echoes a block expression — a declared type's cast, a comparison's conversion — writes each
 * variable it reads as the conversion of its bound value's text to the variable's type, and an argument-type
 * refusal types a text variable at its value's width, a NULL at the full scripting width. Every cell is
 * live-verified.
 */
public class BoundValueEchoTest extends BaseDatabaseTest {

    private static final String UNCAUGHT = "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position ";

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private static String block(final String body) {
        return "EXECUTE IMMEDIATE $$BEGIN " + body + " END;$$";
    }

    private static String cast(final int at, final String echo, final String target, final String function) {
        return UNCAUGHT + at + " : SQL compilation error:\ninvalid type [CAST(" + echo + " AS " + target
            + ")] for parameter '" + function + "'";
    }

    private static String convert(final int at, final String echo, final String type) {
        return UNCAUGHT + at + " : SQL compilation error:\nCan not convert parameter '" + echo + "' of type [" + type
            + "] into expected type [DATE]";
    }

    private static String plus(final int at, final String type) {
        return UNCAUGHT + at + " : SQL compilation error: error line 1 at position 4\n"
            + "Invalid argument types for function '+': (" + type + ", BOOLEAN)";
    }

    @Test
    public void aVariableIntoADateIsItsBoundValuesConversion() {
        final String[][] cells = {
            {"LET v NUMBER(5,2) := 1.5; LET x DATE := v; RETURN x;", "46", "TO_NUMBER('1.50', 5, 2)"},
            {"LET v NUMBER(10,0) := 7; LET x DATE := v; RETURN x;", "45", "TO_NUMBER('7', 10, 0)"},
            {"LET v := 1.5; LET x DATE := v; RETURN x;", "34", "TO_DOUBLE('1.5')"},
            {"LET v FLOAT := 2; LET x DATE := v; RETURN x;", "38", "TO_DOUBLE('2')"},
            {"LET v := 0.1::FLOAT; LET x DATE := v; RETURN x;", "41", "TO_DOUBLE('0.1')"},
            {"LET v FLOAT := 1e20; LET x DATE := v; RETURN x;", "41", "TO_DOUBLE('1e+20')"},
            {"LET v BOOLEAN := FALSE; LET x DATE := v; RETURN x;", "44", "TO_BOOLEAN('false')"},
            {"LET v TIME := '10:00:00'; LET x DATE := v; RETURN x;", "46", "TO_TIME('10:00:00.000000000')"},
            {"LET v TIME(3) := '10:00:00.5'; LET x DATE := v; RETURN x;", "51", "TO_TIME('10:00:00.500')"},
            {"LET v := TO_BINARY('AB', 'HEX'); LET x DATE := v; RETURN x;", "53", "TO_BINARY('AB')"},
            {"LET v NUMBER := NULL; LET x DATE := v; RETURN x;", "42", "TO_NUMBER(NULL)"},
            {"LET v NUMBER(5,2) := NULL; LET x DATE := v; RETURN x;", "47", "TO_NUMBER(NULL, 5, 2)"},
            {"LET v FLOAT := NULL; LET x DATE := v; RETURN x;", "41", "TO_DOUBLE(NULL)"},
            {"LET v BOOLEAN := NULL; LET x DATE := v; RETURN x;", "43", "TO_BOOLEAN(NULL)"},
            {"LET v := -5; LET x DATE := v; RETURN x;", "33", "TO_NUMBER('-5')"},
            {"LET v := 12345678901234567890; LET x DATE := v; RETURN x;", "51", "TO_NUMBER('12345678901234567890')"},
            {"LET v NUMBER(38,2) := 5; LET x DATE := v; RETURN x;", "45", "TO_NUMBER('5.00', 38, 2)"},
            {"LET v := 5; LET w := v; LET x DATE := w; RETURN x;", "44", "TO_NUMBER('5')"},
            {"LET n := 5; LET x DATE := ABS(n); RETURN x;", "32", "ABS(TO_NUMBER('5'))"},
            {"LET n := 5; LET x DATE := (n); RETURN x;", "32", "TO_NUMBER('5')"},
            {"LET n := 5; LET x DATE := -n; RETURN x;", "32", "NEGATE(TO_NUMBER('5'))"},
            {"LET n := 5; LET x DATE := n * 2 + n; RETURN x;", "32", "((TO_NUMBER('5')) * 2) + (TO_NUMBER('5'))"},
            {"LET b := TRUE; LET x DATE := NOT b; RETURN x;", "35", "NOT(TO_BOOLEAN('true'))"},
            {"LET s := 'abc'; LET x DATE := LENGTH(s); RETURN x;", "36", "LENGTH('abc')"},
            {"LET s := 'it''s'; LET x DATE := LENGTH(s); RETURN x;", "38", "LENGTH('it''s')"},
            {"LET v VARIANT := 1; LET x DATE := TO_BOOLEAN(v); RETURN x;", "40",
                "TO_BOOLEAN(identity(PARSE_JSON('1')))"},
        };
        for (final String[] cell : cells) {
            assertEquals(cast(Integer.parseInt(cell[1]), cell[2], "DATE", "TO_DATE"), refusal(block(cell[0])), cell[0]);
        }
        assertEquals(cast(47, "TO_NUMBER('5')", "DATE", "TO_DATE"),
            refusal("EXECUTE IMMEDIATE $$DECLARE x DATE; n NUMBER DEFAULT 5; BEGIN x := n; RETURN x; END;$$"));
    }

    @Test
    public void temporalAndSemiStructuredValuesAreWrittenOutInFull() {
        final String number = "NUMBER(38,0)";
        final String[][] cells = {
            {"LET v TIMESTAMP := '2024-01-01 10:00:00'; LET x BOOLEAN := v; RETURN x;", "65",
                "TO_TIMESTAMP_NTZ('2024-01-01 10:00:00.000000000')", "BOOLEAN", "TO_BOOLEAN"},
            {"LET v TIMESTAMP_NTZ(0) := '2024-01-01 10:00:00'; LET x BOOLEAN := v; RETURN x;", "72",
                "TO_TIMESTAMP_NTZ('2024-01-01 10:00:00')", "BOOLEAN", "TO_BOOLEAN"},
            {"LET v TIMESTAMP_NTZ := '2024-01-01 10:00:00.123456789'; LET x BOOLEAN := v; RETURN x;", "79",
                "TO_TIMESTAMP_NTZ('2024-01-01 10:00:00.123456789')", "BOOLEAN", "TO_BOOLEAN"},
            {"LET v TIMESTAMP_LTZ := '2024-01-01 10:00:00'; LET x BOOLEAN := v; RETURN x;", "69",
                "TO_TIMESTAMP_LTZ('2024-01-01 10:00:00.000000000')", "BOOLEAN", "TO_BOOLEAN"},
            {"LET v TIMESTAMP_TZ := '2024-01-01 10:00:00 +02:00'; LET x BOOLEAN := v; RETURN x;", "75",
                "TO_TIMESTAMP_TZ('2024-01-01 10:00:00.000000000 +02:00')", "BOOLEAN", "TO_BOOLEAN"},
            {"LET v DATE := '2024-12-31'; LET x NUMBER := v; RETURN x;", "50", "TO_DATE('2024-12-31')", number,
                "TO_NUMBER"},
            {"LET v DATE := NULL; LET x NUMBER := v; RETURN x;", "42", "TO_DATE(NULL)", number, "TO_NUMBER"},
            {"LET v := ARRAY_CONSTRUCT(1); LET x NUMBER := v; RETURN x;", "51", "TO_ARRAY(PARSE_JSON('[\n1\n]'))",
                number, "TO_NUMBER"},
            {"LET v := ARRAY_CONSTRUCT(); LET x NUMBER := v; RETURN x;", "50", "TO_ARRAY(PARSE_JSON('[]'))", number,
                "TO_NUMBER"},
            {"LET v := OBJECT_CONSTRUCT('a', 1); LET x NUMBER := v; RETURN x;", "57",
                "TO_OBJECT(PARSE_JSON('{\n\"a\": 1\n}'))", number, "TO_NUMBER"},
            {"LET v := ARRAY_CONSTRUCT(1, ARRAY_CONSTRUCT(2), OBJECT_CONSTRUCT('k', 'v')); LET x NUMBER := v; RETURN x;",
                "99", "TO_ARRAY(PARSE_JSON('[\n1,\n[\n2\n],\n{\n\"k\": \"v\"\n}\n]'))", number, "TO_NUMBER"},
        };
        for (final String[] cell : cells) {
            assertEquals(cast(Integer.parseInt(cell[1]), cell[2], cell[3], cell[4]), refusal(block(cell[0])), cell[0]);
        }
    }

    @Test
    public void aComparisonsConversionEchoesTheBoundValue() {
        assertEquals(convert(71, "TO_NUMBER('1.50', 5, 2)", "NUMBER(5,2)"), refusal(block(
            "LET d := TO_DATE('2024-01-01'); LET v NUMBER(5,2) := 1.5; RETURN d = v;")));
        assertEquals(convert(66, "CAST('1.5' AS FLOAT)", "FLOAT"), refusal(block(
            "LET d := TO_DATE('2024-01-01'); LET v := 1.5::FLOAT; RETURN d = v;")));
        assertEquals(convert(57, "(TO_NUMBER('5')) + 1", "NUMBER(38,0)"), refusal(block(
            "LET d := TO_DATE('2024-01-01'); LET n := 5; RETURN d = n + 1;")));
        assertEquals(convert(64, "TO_NUMBER('1', 9, 0)", "NUMBER(9,0)"), refusal(block(
            "LET d := TO_DATE('2024-01-01'); FOR i IN 1 TO 1 DO RETURN d = i; END FOR;")));
    }

    @Test
    public void aProcedureParameterIsItsBoundValue() {
        engine.execute("CREATE OR REPLACE PROCEDURE bve_p(n NUMBER) RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$BEGIN LET x DATE := :n; RETURN x; END;$$");
        assertEquals(cast(20, "TO_NUMBER('5')", "DATE", "TO_DATE"), refusal("CALL bve_p(5)"));
    }

    @Test
    public void aTextVariableIsTypedAtItsValuesWidth() {
        assertEquals(plus(26, "VARCHAR(1)"), refusal(block("LET s := ''; RETURN s + TRUE;")));
        assertEquals(plus(37, "VARCHAR(1)"), refusal(block("LET s VARCHAR(5) := ''; RETURN s + TRUE;")));
        assertEquals(plus(36, "VARCHAR(134217728)"), refusal(block("LET s VARCHAR := NULL; RETURN s + TRUE;")));
        assertEquals(plus(39, "VARCHAR(134217728)"), refusal(block("LET s VARCHAR(5) := NULL; RETURN s + TRUE;")));
        assertEquals(plus(38, "VARCHAR(134217728)"), refusal(block("LET s := 'a'; s := NULL; RETURN s + TRUE;")));
        assertEquals(plus(30, "VARCHAR(4)"), refusal(block("LET s := 'żółw'; RETURN s + TRUE;")));
    }
}
