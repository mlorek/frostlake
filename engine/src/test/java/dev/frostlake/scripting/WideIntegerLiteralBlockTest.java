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

/**
 * An integer literal past 38 significant digits anywhere in a block refuses the whole block while it compiles:
 * the refusal comes bare, placed in the block's own text, before any statement runs, reachable or not, an
 * exception handler and an embedded statement included, ahead of an unknown name earlier in the block; a
 * procedure holding one is refused at CREATE. Every cell is live-verified.
 */
public class WideIntegerLiteralBlockTest extends BaseDatabaseTest {

    private static final String WIDE = "123456789012345678901234567890123456789";

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

    private static String outOfRange(final int line, final int position) {
        return "SQL compilation error: Error line " + line + " at position " + position
            + "\nInteger literal is out of representable range: " + WIDE;
    }

    private static String block(final String body) {
        return "EXECUTE IMMEDIATE $$ " + body.replace("{W}", WIDE) + " $$";
    }

    @Test
    public void theBlockIsRefusedBeforeAnythingRuns() {
        final String[][] cells = {
            {"BEGIN RETURN {W}; END;", "14"},
            {"BEGIN IF (FALSE) THEN RETURN {W}; END IF; RETURN 1; END;", "30"},
            {"BEGIN SELECT {W}; RETURN 1; END;", "14"},
            {"DECLARE x NUMBER DEFAULT {W}; BEGIN RETURN 1; END;", "26"},
            {"BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN RETURN {W}; END;", "50"},
            {"BEGIN RETURN -{W}; END;", "15"},
            {"BEGIN RETURN missing; RETURN {W}; END;", "30"},
            {"BEGIN RETURN {W}; RETURN 9{W}; END;", "14"},
            {"BEGIN LET v VARCHAR := {W}; RETURN v; END;", "24"},
            {"BEGIN SELECT 1 WHERE 1 = {W}; RETURN 1; END;", "26"},
            {"BEGIN BEGIN RETURN {W}; END; END;", "20"},
            {"BEGIN RETURN {W} + missing_fn(); END;", "14"},
            {"BEGIN LET x := 1; x := {W}; RETURN x; END;", "24"},
            {"BEGIN FOR i IN 1 TO {W} DO RETURN 1; END FOR; RETURN 2; END;", "21"},
            {"BEGIN RETURN 'a' || {W}; END;", "21"},
        };
        for (final String[] cell : cells) {
            assertEquals(outOfRange(1, Integer.parseInt(cell[1])), refusal(block(cell[0])), cell[0]);
        }
        assertEquals(outOfRange(3, 9), refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  RETURN " + WIDE + ";\nEND; $$"));
        assertEquals(outOfRange(1, 13), refusal("BEGIN RETURN " + WIDE + "; END;"));
    }

    @Test
    public void anEarlierStatementDoesNotRun() {
        assertEquals(outOfRange(1, 58), refusal(block("BEGIN CREATE OR REPLACE TABLE wilb_one (a INT); LET x := {W}; RETURN 1; END;")));
        assertEquals(outOfRange(1, 56), refusal(block("BEGIN CREATE OR REPLACE TABLE wilb_two (a INT); SELECT {W}; RETURN 1; END;")));
        assertEquals(0, engine.executeQuery("SHOW TABLES LIKE 'WILB_%'").getRows().size());
    }

    @Test
    public void aProcedureIsRefusedAtCreate() {
        assertEquals(outOfRange(1, 14), refusal("CREATE OR REPLACE PROCEDURE wilb_p() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN"
            + " RETURN " + WIDE + "; END; $$"));
        assertEquals(outOfRange(1, 30), refusal("CREATE OR REPLACE PROCEDURE wilb_q() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN"
            + " IF (FALSE) THEN RETURN " + WIDE + "; END IF; RETURN 1; END; $$"));
        engine.execute("CREATE OR REPLACE PROCEDURE wilb_r() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN LET s := '" + WIDE
            + "'; RETURN 1; END; $$");
        assertEquals("1", answer("CALL wilb_r()"));
    }

    /** A block run through EXECUTE IMMEDIATE with no space after its opening quote. */
    private static String unspaced(final String body) {
        return "EXECUTE IMMEDIATE $$" + body.replace("{W}", WIDE) + "$$";
    }

    private static String typeFault(final int position, final String sentence) {
        return "SQL compilation error: error line 1 at position " + position + "\n" + sentence;
    }

    private static final String WIDE_GROUPED = "123,456,789,012,345,678,901,234,567,890,123,456,789";

    private static final String PRECISION = "Invalid number precision: " + WIDE_GROUPED + ". Must be between 0 and 38.";

    @Test
    public void aTypeParameterIsJudgedByItsType() {
        final String[][] cells = {
            {"BEGIN LET x NUMBER({W}, 0) := 1; RETURN x; END;", "19", PRECISION},
            {"BEGIN LET x VARCHAR({W}) := 'a'; RETURN x; END;", "20",
                "Invalid character length: " + WIDE_GROUPED + ". Must be between 1 and 134,217,728."},
            {"BEGIN LET x NUMBER(38, {W}) := 1; RETURN x; END;", "23",
                "Invalid number scale: " + WIDE_GROUPED + ". Must be between 0 and 37."},
            {"BEGIN LET x TIMESTAMP({W}) := CURRENT_TIMESTAMP(); RETURN 1; END;", "22",
                "Invalid timestamp scale: " + WIDE_GROUPED + ". Must be between 0 and 9."},
            {"BEGIN RETURN 1::NUMBER({W}, 0); END;", "23", PRECISION},
            {"BEGIN CREATE OR REPLACE TABLE wilb_w (a NUMBER({W}, 0)); RETURN 1; END;", "47", PRECISION},
            {"BEGIN LET x NUMBER({W}, 0) := 1; RETURN {W}; END;", "19", PRECISION},
            {"DECLARE x NUMBER({W}, 0) DEFAULT 1; BEGIN RETURN x; END;", "17", PRECISION},
            {"BEGIN LET x NUMBER := (SELECT 1::NUMBER({W}, 0)); RETURN x; END;", "40", PRECISION},
            {"BEGIN LET x NUMBER({W}, 0) := 1; DECLARE e EXCEPTION (-{W}, 'm'); BEGIN RETURN 1; END; END;", "19",
                PRECISION},
        };
        for (final String[] cell : cells) {
            assertEquals(typeFault(Integer.parseInt(cell[1]), cell[2]), refusal(unspaced(cell[0])), cell[0]);
        }
        assertEquals(outOfRange(1, 15), refusal(unspaced(
            "BEGIN LET y := {W}; LET x NUMBER({W}, 0) := 1; RETURN 1; END;")));
        assertEquals("SQL compilation error: error line 0 at position 0\nInvalid number precision: -" + WIDE_GROUPED
            + ". Must be between 0 and 38.", refusal(unspaced("BEGIN LET x NUMBER(-{W}, 0) := 1; RETURN x; END;")));
        assertEquals(typeFault(19, PRECISION), refusal("CREATE OR REPLACE PROCEDURE wilb_t() RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN LET x NUMBER(" + WIDE + ", 0) := 1; RETURN x; END;$$"));
        assertEquals("1.50", answer(unspaced(
            "BEGIN LET x NUMBER(0000000000000000000000000000000000000000010, 2) := 1.5; RETURN x; END;")));
    }

    @Test
    public void anExceptionCodeIsReadWithItsSign() {
        final String signed = "SQL compilation error: Error line 0 at position 0\n"
            + "Integer literal is out of representable range: -" + WIDE;
        assertEquals(outOfRange(1, 21), refusal(unspaced(
            "DECLARE e EXCEPTION ({W}, 'm'); BEGIN RETURN 1; END;")));
        final String[] cells = {
            "DECLARE e EXCEPTION (-{W}, 'm'); BEGIN RETURN 1; END;",
            "DECLARE\n  x NUMBER DEFAULT 1;\n  e EXCEPTION (-{W}, 'm');\nBEGIN RETURN 1; END;",
            "BEGIN LET y := 1; DECLARE e EXCEPTION (-{W}, 'm'); BEGIN RETURN 1; END; END;",
            "DECLARE e EXCEPTION (-{W}, 'm'); BEGIN RETURN {W}; END;",
            "DECLARE e EXCEPTION (-{W}, 'm'); BEGIN LET y NUMBER({W}, 0) := 1; RETURN 1; END;",
            "BEGIN LET q := missing; DECLARE e EXCEPTION (-{W}, 'm'); BEGIN RETURN 1; END; END;",
            "DECLARE e EXCEPTION (-20001, 'm'); f EXCEPTION (-{W}, 'n'); BEGIN RETURN 1; END;",
        };
        for (final String cell : cells) {
            assertEquals(signed, refusal(unspaced(cell)), cell);
        }
        assertEquals(outOfRange(1, 15), refusal(unspaced(
            "BEGIN LET y := {W}; DECLARE e EXCEPTION (-{W}, 'm'); BEGIN RETURN 1; END; END;")));
        assertEquals(signed, refusal("CREATE OR REPLACE PROCEDURE wilb_e() RETURNS NUMBER LANGUAGE SQL AS"
            + " $$DECLARE e EXCEPTION (-" + WIDE + ", 'm'); BEGIN RETURN 1; END;$$"));
        assertEquals("1", answer(unspaced(
            "DECLARE e EXCEPTION (-00000000000000000000000000000000000000000020001, 'm'); BEGIN RETURN 1; END;")));
    }

    @Test
    public void everyLiteralReaderRefusesIt() {
        final String[][] cells = {
            {"BEGIN SELECT TOP {W} 1; RETURN 1; END;", "17"},
            {"BEGIN SELECT * FROM (SELECT 1 AS a) SAMPLE ({W} ROWS); RETURN 1; END;", "44"},
            {"BEGIN SELECT * FROM (SELECT 1 AS a) SAMPLE (10 ROWS) SEED ({W}); RETURN 1; END;", "59"},
            {"BEGIN CREATE OR REPLACE TABLE wilb_v (a VECTOR(INT, {W})); RETURN 1; END;", "52"},
            {"BEGIN SELECT 1 FETCH FIRST {W} ROWS ONLY; RETURN 1; END;", "27"},
            {"BEGIN SELECT 1 LIMIT 1 OFFSET {W}; RETURN 1; END;", "30"},
        };
        for (final String[] cell : cells) {
            assertEquals(outOfRange(1, Integer.parseInt(cell[1])), refusal(unspaced(cell[0])), cell[0]);
        }
    }

    @Test
    public void typesAndLiteralsAreJudgedInTheOrderWrittenAheadOfNames() {
        final String length = "Invalid character length: 0. Must be between 1 and 134,217,728.";
        final String[][] cells = {
            {"BEGIN LET v VARCHAR(0) := 'x'; RETURN {W}; END;", "20"},
            {"BEGIN RETURN missing; LET v VARCHAR(0) := 'x'; END;", "36"},
            {"BEGIN LET v VARCHAR(0) := missing; RETURN 1; END;", "20"},
            {"BEGIN RETURN :nosuch; LET v VARCHAR(0) := 'x'; END;", "36"},
            {"BEGIN LET x := 1; LET x := 2; LET v VARCHAR(0) := 'a'; RETURN 1; END;", "44"},
            {"BEGIN LET v VARCHAR(0) := 'a'; LET x := 1; LET x := 2; RETURN 1; END;", "20"},
            {"BEGIN RETURN ?; LET v VARCHAR(0) := 'a'; END;", "30"},
            {"BEGIN LET v VARCHAR(0) := 'a'; RETURN ?; END;", "20"},
        };
        for (final String[] cell : cells) {
            assertEquals(typeFault(Integer.parseInt(cell[1]), length), refusal(unspaced(cell[0])), cell[0]);
        }
        assertEquals(outOfRange(1, 13), refusal(unspaced("BEGIN RETURN {W}; LET v VARCHAR(0) := 'x'; END;")));
        assertEquals(outOfRange(1, 29), refusal(unspaced("BEGIN RETURN missing; RETURN {W}; END;")));
        assertEquals(outOfRange(1, 37), refusal(unspaced("BEGIN LET x := 1; LET x := 2; RETURN {W}; END;")));
        assertEquals(outOfRange(1, 25), refusal(unspaced("BEGIN LET y := ?; RETURN {W}; END;")));
        assertEquals(typeFault(44, length), refusal("CREATE OR REPLACE PROCEDURE wilb_p8() RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN LET x := 1; LET x := 2; LET v VARCHAR(0) := 'a'; RETURN 1; END;$$"));
        assertEquals(outOfRange(1, 13), refusal("CREATE OR REPLACE PROCEDURE wilb_p10() RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN RETURN " + WIDE + "; LET v VARCHAR(0) := 'a'; END;$$"));
        assertEquals(typeFault(20, length), refusal("CREATE OR REPLACE PROCEDURE wilb_p11() RETURNS NUMBER LANGUAGE SQL AS"
            + " $$BEGIN LET v VARCHAR(0) := 'a'; RETURN " + WIDE + "; END;$$"));
        assertEquals("SQL compilation error:\nInvalid vector dimension '0'.", refusal(unspaced(
            "BEGIN CREATE OR REPLACE TABLE wilb_v7 (a VECTOR(INT, 0)); RETURN {W}; END;")));
    }

    /**
     * A routine statement reads its literals with its declared types, in the order written: a wide DEFAULT or VECTOR
     * dimension is refused ahead of the schema, the language and the invocation type, whether the statement runs on
     * its own or inside a block, and a zero width written first is refused first. A name a DECLARE section repeats
     * is refused where the pass reaches it, ahead of a wide literal written after it and of a LET that repeated a
     * name before it.
     */
    @Test
    public void aRoutineStatementReadsItsLiteralsWithItsTypesInTheOrderWritten() {
        final String[][] cells = {
            {"CREATE FUNCTION wrf1(a NUMBER DEFAULT {W}) RETURNS INT AS '1'", "38"},
            {"CREATE FUNCTION nosuch_schema.wrf2(a NUMBER DEFAULT {W}) RETURNS INT AS '1'", "52"},
            {"CREATE FUNCTION wrf3(a NUMBER DEFAULT {W}) RETURNS INT LANGUAGE COBOL AS 'x'", "38"},
            {"CREATE FUNCTION wrf4(a NUMBER DEFAULT -{W}) RETURNS INT AS '1'", "39"},
            {"CREATE FUNCTION wrf5(a NUMBER DEFAULT {W}, b VARCHAR(0)) RETURNS INT AS '1'", "38"},
            {"CREATE FUNCTION wrf8(a VECTOR(INT, {W})) RETURNS INT AS '1'", "35"},
            {"CREATE PROCEDURE wrp1(a NUMBER DEFAULT {W}) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$", "39"},
            {"CREATE FUNCTION wrf9(a NUMBER DEFAULT {W}) RETURNS INT EXECUTE AS CALLER AS '1'", "38"},
        };
        for (final String[] cell : cells) {
            final String sql = cell[0].replace("{W}", WIDE);
            assertEquals(outOfRange(1, Integer.parseInt(cell[1])), refusal(sql), sql);
        }
        assertEquals(typeFault(31, "Invalid character length: 0. Must be between 1 and 134,217,728."),
            refusal("CREATE FUNCTION wrf6(b VARCHAR(0), a NUMBER DEFAULT " + WIDE + ") RETURNS INT AS '1'"));
        assertEquals(outOfRange(3, 41), refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  CREATE FUNCTION wrf12(a NUMBER DEFAULT "
            + WIDE + ") RETURNS INT LANGUAGE COBOL AS 'x';\n  RETURN 1;\nEND;\n$$"));
        assertEquals(outOfRange(3, 9), refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  RETURN " + WIDE
            + ";\n  CREATE FUNCTION wrf16() RETURNS INT EXECUTE AS CALLER AS '1';\nEND;\n$$"));
        assertEquals("SQL compilation error: error line 4 at position 2\n Variable with name 'X' declared twice.",
            refusal("EXECUTE IMMEDIATE $$\nDECLARE\n  x INT;\n  x INT;\nBEGIN\n  RETURN " + WIDE + ";\nEND;\n$$"));
        assertEquals("SQL compilation error: error line 4 at position 2\n Variable with name 'Y' declared twice.",
            refusal("CREATE PROCEDURE wrp3() RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n  y INT;\n  y INT;\nBEGIN\n"
                + "  RETURN " + WIDE + ";\nEND\n$$"));
        assertEquals("SQL compilation error: error line 7 at position 4\n Variable with name 'Y' declared twice.",
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  LET x := 1;\n  LET x := 2;\n  DECLARE\n    y INT;\n    y INT;\n"
                + "  BEGIN\n    RETURN 1;\n  END;\nEND;\n$$"));
    }

    @Test
    public void whatIsNoWideIntegerLiteralRuns() {
        assertEquals("1", answer(block("BEGIN LET s := 'SELECT {W}'; RETURN 1; END;")));
        assertEquals("1", answer(block("BEGIN RETURN 00000000000000000000000000000000000000001; END;")));
        assertEquals(1e39, Double.parseDouble(answer(block("BEGIN RETURN 1e39; END;"))));
    }
}
