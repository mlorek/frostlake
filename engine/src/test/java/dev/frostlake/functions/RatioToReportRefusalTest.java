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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RATIO_TO_REPORT is refused in SUM's vocabulary, because the account rewrites it into a division by
 * SUM and the rewrite leaks into every sentence: a DATE argument is "Invalid argument types for
 * function 'SUM': (DATE)", a second argument is "too many arguments for function [SUM(1, 2)] expected
 * 1, got 2", and both are anchored at the CALL — its own position, not the argument's. Frostlake
 * used to answer all of them.
 *
 * <p>★ THE FAMILY IS SUM'S, MEASURED MEMBER BY MEMBER: every temporal, a BOOLEAN, a BINARY, an OBJECT
 * and an ARRAY are refused at compile time, while a VARCHAR, a VARIANT, a string literal and a NULL are
 * taken — the text and the variant divide as doubles. SUM itself refuses the same members in the
 * same words, plain and windowed, so those cells are pinned here too.
 *
 * <p>The echo of an arity refusal is the call re-printed canonically under SUM's name — a column
 * qualified and upper-cased, a nested aggregate kept — and the zero-argument shape uses the
 * "not enough arguments" sentence with its own comma.
 */
public class RatioToReportRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE re (a NUMBER(10,2), d DATE, ts TIMESTAMP_NTZ, tm TIME,"
            + " bo BOOLEAN, bn BINARY(5), v VARCHAR(5), vt VARIANT, o OBJECT, ar ARRAY)");
        engine.execute("INSERT INTO re SELECT 1.5, '2020-01-01', '2020-01-01 10:00:00', '10:00:00', TRUE,"
            + " TO_BINARY('AB'), '2', PARSE_JSON('3'), OBJECT_CONSTRUCT('a', 1), ARRAY_CONSTRUCT(1)");
        engine.execute("INSERT INTO re SELECT 2.5, '2020-01-02', '2020-01-02 10:00:00', '11:00:00', FALSE,"
            + " TO_BINARY('CD'), '4', PARSE_JSON('5'), OBJECT_CONSTRUCT('a', 2), ARRAY_CONSTRUCT(2)");
    }

    /** The refusal's message on one line, or the first cell's text. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            rs.next();
            return String.valueOf(rs.getValue(0)).replaceAll("\\[SB[0-9]+\\]", "");
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    private static String argumentTypes(final int position, final String function, final String types) {
        return "SQL compilation error: error line 1 at position " + position
            + " Invalid argument types for function '" + function + "': (" + types + ")";
    }

    /** ★ Every member of SUM's refused family, in SUM's words, at the call. */
    @Test
    public void aRefusedArgumentIsRefusedInSumsWords() {
        assertEquals(argumentTypes(21, "SUM", "DATE"),
            outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(d) OVER ()) FROM re"));
        assertEquals(argumentTypes(21, "SUM", "TIMESTAMP_NTZ(9)"),
            outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(ts) OVER ()) FROM re"));
        assertEquals(argumentTypes(21, "SUM", "TIME(9)"),
            outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(tm) OVER ()) FROM re"));
        assertEquals(argumentTypes(21, "SUM", "BOOLEAN"),
            outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(bo) OVER ()) FROM re"));
        assertEquals(argumentTypes(21, "SUM", "BINARY(5)"),
            outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(bn) OVER ()) FROM re"));
        assertEquals(argumentTypes(21, "SUM", "OBJECT"),
            outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(o) OVER ()) FROM re"));
        assertEquals(argumentTypes(21, "SUM", "ARRAY"),
            outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(ar) OVER ()) FROM re"));
        assertEquals(argumentTypes(21, "SUM", "DATE"),
            outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(DATE '2020-01-01') OVER ()) FROM re"),
            "a typed literal is refused like the column");
    }

    /** The members it TAKES: text and a variant divide as doubles, a NULL as an eighteen-digit number. */
    @Test
    public void textAndVariantAreTaken() {
        assertEquals("FLOAT[DOUBLE]", outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(v) OVER ()) FROM re"));
        assertEquals("FLOAT[DOUBLE]", outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(vt) OVER ()) FROM re"));
        assertEquals("FLOAT[DOUBLE]", outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT('2') OVER ()) FROM re"));
        assertEquals("NUMBER(24,6)", outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(NULL) OVER ()) FROM re"));
        assertEquals("NUMBER(18,8)", outcome("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(a) OVER ()) FROM re"));
    }

    /** ★ The anchor is the call, wherever the call stands. */
    @Test
    public void theRefusalIsAnchoredAtTheCall() {
        assertEquals(argumentTypes(7, "SUM", "DATE"), outcome("SELECT RATIO_TO_REPORT(d) OVER () FROM re"));
        assertEquals(argumentTypes(10, "SUM", "DATE"), outcome("SELECT a, RATIO_TO_REPORT(d) OVER () FROM re"));
        assertEquals(argumentTypes(11, "SUM", "DATE"), outcome("SELECT 1 + RATIO_TO_REPORT(d) OVER () FROM re"));
        assertEquals(argumentTypes(7, "SUM", "DATE"),
            outcome("SELECT RATIO_TO_REPORT(d) OVER (PARTITION BY a) FROM re"));
        assertEquals(argumentTypes(7, "SUM", "DATE"),
            outcome("SELECT RATIO_TO_REPORT(MIN(d)) OVER () FROM re GROUP BY d"), "over grouped rows too");
        assertEquals(argumentTypes(7, "SUM", "DATE"), outcome("SELECT RATIO_TO_REPORT(DATE '2020-01-01') OVER ()"),
            "and with no FROM at all");
        assertEquals(argumentTypes(7, "SUM", "DATE"), outcome("SELECT RATIO_TO_REPORT(d) OVER () FROM re WHERE FALSE"),
            "a compile-time refusal fires over zero rows");
        assertEquals(argumentTypes(25, "SUM", "DATE"),
            outcome("SELECT a FROM re QUALIFY RATIO_TO_REPORT(d) OVER () > 0"));
        assertEquals(argumentTypes(36, "SUM", "DATE"),
            outcome("CREATE OR REPLACE VIEW rv AS SELECT RATIO_TO_REPORT(d) OVER () AS r FROM re"),
            "a view body is refused at the body's own offset in the statement");
    }

    /** ★ The arity is SUM's, and the echo re-prints the call under SUM's name. */
    @Test
    public void theArityIsSumsAndTheEchoIsSums() {
        assertEquals("SQL compilation error: error line 1 at position 7 too many arguments for function"
            + " [SUM(1, 2)] expected 1, got 2", outcome("SELECT RATIO_TO_REPORT(1, 2) OVER () FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7 too many arguments for function"
            + " [SUM(RE.A, RE.A)] expected 1, got 2", outcome("SELECT RATIO_TO_REPORT(a, a) OVER () FROM re"),
            "a column echoes qualified and upper-cased");
        assertEquals("SQL compilation error: error line 1 at position 10 too many arguments for function"
            + " [SUM(1, 2)] expected 1, got 2", outcome("SELECT a, RATIO_TO_REPORT(1, 2) OVER () FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7 too many arguments for function"
            + " [SUM(SUM(RE.A), 2)] expected 1, got 2",
            outcome("SELECT RATIO_TO_REPORT(SUM(a), 2) OVER () FROM re GROUP BY a"));
        assertEquals("SQL compilation error: error line 1 at position 7 too many arguments for function"
            + " [SUM(1, 2, 3)] expected 1, got 3", outcome("SELECT RATIO_TO_REPORT(1, 2, 3) OVER () FROM re"));
        assertEquals("SQL compilation error: error line 1 at position 7 not enough arguments for function"
            + " [SUM()], expected 1, got 0", outcome("SELECT RATIO_TO_REPORT() OVER () FROM re"),
            "the empty call takes the other sentence, with its comma");
    }

    /** SUM itself refuses the same members in the same words, plain and windowed. */
    @Test
    public void sumRefusesTheSameFamily() {
        assertEquals(argumentTypes(7, "SUM", "DATE"), outcome("SELECT SUM(d) FROM re"));
        assertEquals(argumentTypes(7, "SUM", "BOOLEAN"), outcome("SELECT SUM(bo) FROM re"));
        assertEquals(argumentTypes(7, "SUM", "TIMESTAMP_NTZ(9)"), outcome("SELECT SUM(ts) FROM re"));
        assertEquals(argumentTypes(7, "SUM", "DATE"), outcome("SELECT SUM(d) OVER () FROM re"));
        assertEquals(argumentTypes(21, "SUM", "BINARY(5)"), outcome("SELECT SYSTEM$TYPEOF(SUM(bn)) FROM re"));
        assertEquals("FLOAT[DOUBLE]", outcome("SELECT SYSTEM$TYPEOF(SUM(v)) FROM re"), "text is taken");
        assertEquals("SQL compilation error: error line 1 at position 7 too many arguments for function"
            + " [SUM(1, 2)] expected 1, got 2", outcome("SELECT SUM(1, 2) FROM re"));
    }
}
