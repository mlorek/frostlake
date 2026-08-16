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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A conditional whose branches cannot be brought together is refused at COMPILE time, and live words it
 * by naming the offending operand and the type the FIRST branch expects:
 *
 * <pre>
 *   COALESCE(d, i)   Can not convert parameter 'IC.I' of type [NUMBER(38,0)] into expected type [DATE]
 *   COALESCE(i, d)   Can not convert parameter 'IC.D' of type [DATE] into expected type [NUMBER(38,0)]
 * </pre>
 *
 * <p>So the sentence turns around with the branches — the first one sets what is expected. ONE pair is
 * worded differently and does NOT turn around: a TIME beside a TIMESTAMP reads
 * {@code incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]}, with the TIME first however it was
 * written. A TIME beside a DATE is not that case and takes the ordinary sentence.
 *
 * <p>What CONVERTS is as much the point as what does not: a VARIANT absorbs every scalar but a BINARY,
 * a string meets a number, a temporal or a boolean, and a number meets a boolean. Those must keep
 * running — a conversion that can only fail at ROW time is still accepted here.
 *
 * <p>The SCALAR families are the whole scope. Live is just as strict about OBJECT and ARRAY beside a
 * scalar, but refusing those broke a vendor loader that the engine suite could not see, so that half is
 * measured and left to its own task rather than guessed at here.
 */
public class IncompatibleBranchTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ic (i INT, v VARCHAR(5), d DATE, tm TIME,"
            + " ts TIMESTAMP_NTZ, bo BOOLEAN, bn BINARY(5), va VARIANT, ob OBJECT, ar ARRAY,"
            + " tl TIMESTAMP_LTZ, vn VARCHAR(5))");
        engine.execute("INSERT INTO ic SELECT 1, 'ab', '2020-01-01', '10:00:00',"
            + " '2020-01-01 10:00:00', TRUE, TO_BINARY('AB'), TO_VARIANT(1),"
            + " OBJECT_CONSTRUCT('k', 1), ARRAY_CONSTRUCT(1, 2), '2020-01-01 10:00:00', '123'");
    }

    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT " + expr + " FROM ic");
            rs.next();
            return "OK";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private void cannotConvert(final String expr, final String parameter, final String from,
                               final String into) {
        final String answer = outcome(expr);
        assertTrue(answer.contains("Can not convert parameter '" + parameter + "' of type [" + from
            + "] into expected type [" + into + "]"), expr + " => " + answer);
    }

    private void runs(final String expr) {
        assertTrue("OK".equals(outcome(expr)), expr + " => " + outcome(expr));
    }

    /** A temporal beside a number, in either order — the first branch sets what is expected. */
    @Test
    public void aTemporalBesideANumberIsRefusedBothWays() {
        cannotConvert("COALESCE(d, i)", "IC.I", "NUMBER(38,0)", "DATE");
        cannotConvert("COALESCE(i, d)", "IC.D", "DATE", "NUMBER(38,0)");
        cannotConvert("COALESCE(ts, i)", "IC.I", "NUMBER(38,0)", "TIMESTAMP_NTZ(9)");
        cannotConvert("COALESCE(i, ts)", "IC.TS", "TIMESTAMP_NTZ(9)", "NUMBER(38,0)");
        cannotConvert("COALESCE(tm, i)", "IC.I", "NUMBER(38,0)", "TIME(9)");
        cannotConvert("COALESCE(i, tm)", "IC.TM", "TIME(9)", "NUMBER(38,0)");
    }

    /** A boolean beside a temporal or a binary, and a binary beside anything else. */
    @Test
    public void aBooleanAndABinaryMeetVeryLittle() {
        cannotConvert("COALESCE(bo, d)", "IC.D", "DATE", "BOOLEAN");
        cannotConvert("COALESCE(d, bo)", "IC.BO", "BOOLEAN", "DATE");
        cannotConvert("COALESCE(tm, bo)", "IC.BO", "BOOLEAN", "TIME(9)");
        cannotConvert("COALESCE(bo, bn)", "IC.BN", "BINARY(5)", "BOOLEAN");
        cannotConvert("COALESCE(bn, bo)", "IC.BO", "BOOLEAN", "BINARY(5)");
        cannotConvert("COALESCE(bn, i)", "IC.I", "NUMBER(38,0)", "BINARY(5)");
        cannotConvert("COALESCE(bn, d)", "IC.D", "DATE", "BINARY(5)");
        cannotConvert("COALESCE(v, bn)", "IC.BN", "BINARY(5)", "VARCHAR(5)");
    }

    /** A TIME beside a DATE takes the ORDINARY sentence, and it does turn around. */
    @Test
    public void aTimeBesideADateTakesTheOrdinarySentence() {
        cannotConvert("COALESCE(d, tm)", "IC.TM", "TIME(9)", "DATE");
        cannotConvert("COALESCE(tm, d)", "IC.D", "DATE", "TIME(9)");
    }

    /** A TIME beside a TIMESTAMP is the one pair worded differently — and it never turns around. */
    @Test
    public void aTimeBesideATimestampHasItsOwnSentence() {
        final String ntz = "incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]";
        assertTrue(outcome("COALESCE(tm, ts)").contains(ntz), outcome("COALESCE(tm, ts)"));
        assertTrue(outcome("COALESCE(ts, tm)").contains(ntz), outcome("COALESCE(ts, tm)"));
        final String ltz = "incompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]";
        assertTrue(outcome("COALESCE(tm, tl)").contains(ltz), outcome("COALESCE(tm, tl)"));
        assertTrue(outcome("COALESCE(tl, tm)").contains(ltz), outcome("COALESCE(tl, tm)"));
    }

    /** The other conditionals answer the same way — IFF, CASE, NVL, GREATEST, LEAST. */
    @Test
    public void everyConditionalAnswersTheSameWay() {
        cannotConvert("IFF(bo, d, tm)", "IC.TM", "TIME(9)", "DATE");
        cannotConvert("CASE WHEN bo THEN d ELSE tm END", "IC.TM", "TIME(9)", "DATE");
        cannotConvert("NVL(d, i)", "IC.I", "NUMBER(38,0)", "DATE");
        cannotConvert("GREATEST(d, i)", "IC.I", "NUMBER(38,0)", "DATE");
        cannotConvert("LEAST(bo, d)", "IC.D", "DATE", "BOOLEAN");
    }

    /** A VARIANT absorbs every scalar — and only a BINARY is refused. */
    @Test
    public void aVariantAbsorbsEverythingButABinary() {
        runs("COALESCE(va, i)");
        runs("COALESCE(i, va)");
        runs("COALESCE(va, v)");
        runs("COALESCE(v, va)");
        runs("COALESCE(va, bo)");
        runs("COALESCE(va, ob)");
        runs("COALESCE(ar, va)");
        runs("COALESCE(va, va)");
        runs("IFF(bo, va, i)");
        cannotConvert("COALESCE(va, bn)", "IC.BN", "BINARY(5)", "VARIANT");
    }

    /** And the pairings that DO convert keep running, so the refusal cannot have widened. */
    @Test
    public void theConvertiblePairingsStillRun() {
        runs("COALESCE(d, d)");
        runs("COALESCE(tm, tm)");
        runs("COALESCE(d, ts)");
        // A CONVERTIBLE string: 'ab' would compile just as happily and then fail on the VALUE, which
        // is a different question (the conversion is accepted here either way).
        runs("COALESCE(vn, i)");
        runs("COALESCE(i, vn)");
        runs("COALESCE(bo, i)");
        runs("COALESCE(i, bo)");
        runs("COALESCE(i, NULL)");
        runs("COALESCE(NULL, d)");
        runs("COALESCE(v, v)");
    }
}
