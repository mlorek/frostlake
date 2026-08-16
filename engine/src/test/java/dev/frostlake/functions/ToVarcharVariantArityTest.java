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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TO_CHAR / TO_VARCHAR take a FORMAT only for the NUMBER and temporal inputs they are defined over. A
 * VARCHAR first argument already made the second one an arity error; a VARIANT did not, so
 * {@code TO_VARCHAR(src:score, '0.000')} was accepted and the format applied where live refuses it at
 * COMPILE time.
 *
 * <p><b>It is the VARIANT-ness of the first argument that decides, not what the member holds.</b> All
 * six spellings refuse on live and now here — a decimal member, a text member, an OBJECT member, an
 * ARRAY member, the whole object, and a bare PARSE_JSON — which is what rules out "only a number takes
 * a format" as the reading.
 *
 * <p>The accepted way to format one is to cast first, and {@code TO_VARCHAR(src:score::NUMBER(10,2),
 * '0.000')} still answers on both engines. Every non-variant overload keeps its format too: a NUMBER,
 * a DATE and a TIMESTAMP are asserted below, because a careless version of this would have taken the
 * format away from all of them.
 *
 * <p>The refusal is compared by its SENTENCE, not its bracketed echo. The echo now qualifies the
 * column on both engines, but live REWRITES the colon path into the call its plan uses —
 * {@code GET(VC.SRC, 'score')} where this prints {@code (VC.SRC:score)} — which is the wider question
 * of reproducing a typed plan, tracked on its own.
 */
public class ToVarcharVariantArityTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE vc (src VARIANT, n NUMBER(10,2), d DATE,"
            + " ts TIMESTAMP_NTZ, s VARCHAR(10))");
        engine.execute("INSERT INTO vc SELECT"
            + " PARSE_JSON('{\"score\": 7.5, \"txt\": \"ab\", \"ob\": {\"k\": 1}, \"ar\": [1,2]}'),"
            + " 7.50, '2020-01-01', '2020-01-01 10:00:00', 'hi'");
    }

    /** The expression's value, or the refusal. */
    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT " + expr + " AS c FROM vc");
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
        } catch (final RuntimeException refused) {
            return "ERR " + String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** That the call is refused as an arity error, by the parts of the sentence both engines share. */
    private void refusedForArity(final String expr, final int got) {
        final String answer = outcome(expr);
        assertTrue(answer.startsWith("ERR SQL compilation error: error line 1 at position 7"
            + " too many arguments for function ["), expr + " => " + answer);
        assertTrue(answer.endsWith("] expected 1, got " + got), expr + " => " + answer);
    }

    /** A format over a VARIANT is refused, whatever the member holds. */
    @Test
    public void aFormatOverAVariantIsRefused() {
        refusedForArity("TO_VARCHAR(src:score, '0.000')", 2);
        refusedForArity("TO_CHAR(src:score, '0.000')", 2);
        refusedForArity("TO_VARCHAR(src:txt, '0.000')", 2);
        refusedForArity("TO_VARCHAR(src:ob, '0.000')", 2);
        refusedForArity("TO_VARCHAR(src:ar, '0.000')", 2);
        refusedForArity("TO_VARCHAR(src, '0.000')", 2);
        refusedForArity("TO_VARCHAR(PARSE_JSON('7.5'), '0.000')", 2);
    }

    /** A VARCHAR first argument refuses the same way, which it did before but unpositioned. */
    @Test
    public void aFormatOverAStringIsRefusedToo() {
        refusedForArity("TO_VARCHAR(s, '0.000')", 2);
    }

    /** The ONE-argument form still answers over every variant spelling. */
    @Test
    public void theSingleArgumentFormIsUnchanged() {
        assertEquals("7.5", outcome("TO_VARCHAR(src:score)"));
        assertEquals("7.5", outcome("TO_CHAR(src:score)"));
        assertEquals("{\"ar\":[1,2],\"ob\":{\"k\":1},\"score\":7.5,\"txt\":\"ab\"}",
            outcome("TO_VARCHAR(src)"));
    }

    /** Every NON-variant overload keeps its format — the controls for the change. */
    @Test
    public void theOtherOverloadsKeepTheirFormat() {
        assertEquals(" 7.500", outcome("TO_VARCHAR(n, '0.000')"));
        assertEquals(" 7.500", outcome("TO_CHAR(n, '0.000')"));
        assertEquals("2020/01/01", outcome("TO_VARCHAR(d, 'YYYY/MM/DD')"));
        assertEquals("2020-01-01 10:00", outcome("TO_VARCHAR(ts, 'YYYY-MM-DD HH24:MI')"));
        assertEquals("7.50", outcome("TO_VARCHAR(n)"));
    }

    /** Casting first is how a variant member IS formatted, and it must keep working. */
    @Test
    public void castingFirstStillFormats() {
        assertEquals(" 7.500", outcome("TO_VARCHAR(src:score::NUMBER(10,2), '0.000')"));
    }

    /** A third argument is refused with the arity the two-argument form would have had. */
    @Test
    public void aThirdArgumentNamesTheOtherArity() {
        final String answer = outcome("TO_VARCHAR(src:score, '0.000', 'x')");
        assertTrue(answer.endsWith("] expected 2, got 3"), answer);
    }
}
