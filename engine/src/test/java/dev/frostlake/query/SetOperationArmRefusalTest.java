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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Arms of a set operation whose declared types cannot be brought together. Live refuses them at COMPILE
 * time; Frostlake folded them to the first arm's type and answered.
 *
 * <p>Two sentences cover it, the same pair a CONDITIONAL uses for its branches — the two surfaces share
 * one fold, so they share its refusals:
 *
 * <pre>
 *   d UNION tm    inconsistent data type for result columns for set operator input branches,
 *                 expected TIME(9), got DATE
 *   tm UNION ts   incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]
 * </pre>
 *
 * <p><b>"expected" is the arm the fold FAILED ON and "got" is the running fold of everything before
 * it</b> — not the last arm, and not the first. Two arms cannot tell those readings apart; three can,
 * and {@code i UNION v UNION bn} settles it live with "expected BINARY(5), got NUMBER(38,5)", where
 * NUMBER(38,5) is no single arm's type but what {@code i} and {@code v} folded to before the BINARY
 * broke it. Frostlake's fold used to break one arm earlier on that shape; it no longer does, and
 * {@code query/MixedArmStringFoldTest} asserts that whole family — every order of a string beside two
 * disagreeing non-strings — rather than leaving it to this file.
 *
 * <p>The TIME sentence names the TIME first however the arms were written, so it is stable under
 * reordering where the other one is not.
 *
 * <p><b>What is deliberately NOT refused.</b> A VARIANT absorbs whatever stands beside it, and the
 * unmeasured pairings keep their undetermined answer rather than gaining a refusal nobody measured.
 * That restraint is what kept the vendor suite green: its loaders validate with EXCEPT, and #360
 * showed a refusal reaching one of them through exactly that route.
 */
public class SetOperationArmRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE sa (i INT, n NUMBER(10,2), v VARCHAR(5),"
            + " bn BINARY(5), d DATE, tm TIME, ts TIMESTAMP_NTZ, tz TIMESTAMP_TZ,"
            + " tl TIMESTAMP_LTZ, bo BOOLEAN, va VARIANT, ob OBJECT, ar ARRAY)");
        engine.execute("INSERT INTO sa (i) VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE sae (d DATE, tm TIME)");
    }

    /** The declared type of the set operation's first column, or the refusal. */
    private String outcome(final String sql) {
        try {
            final DataType type = engine.executeQuery(sql).getColumns().get(0).getDataType();
            if (type == null) {
                return "null";
            }
            if (type instanceof NumericType && !"FLOAT".equals(type.getName())) {
                return type.getName() + "(" + ((NumericType) type).getPrecision() + ","
                    + ((NumericType) type).getScale() + ")";
            }
            if (type instanceof StringType) {
                return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
            }
            return type.getName();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Two arms of the named columns, joined by the operator. */
    private String twoArms(final String left, final String op, final String right) {
        return outcome("SELECT " + left + " FROM sa " + op + " SELECT " + right + " FROM sa");
    }

    /** The "inconsistent data type" sentence. */
    private String inconsistent(final String expected, final String got) {
        return "SQL compilation error: inconsistent data type for result columns for set operator"
            + " input branches, expected " + expected + ", got " + got;
    }

    /** The families that cannot fold, each in the order the sentence reports. */
    @Test
    public void unfoldableArmsAreRefused() {
        assertEquals(inconsistent("VARCHAR(5)", "BINARY(5)"), twoArms("bn", "UNION", "v"));
        assertEquals(inconsistent("TIME(9)", "DATE"), twoArms("d", "UNION", "tm"));
        assertEquals(inconsistent("DATE", "TIME(9)"), twoArms("tm", "UNION", "d"));
        assertEquals(inconsistent("BOOLEAN", "NUMBER(10,2)"), twoArms("n", "UNION", "bo"));
        assertEquals(inconsistent("TIMESTAMP_TZ(9)", "TIMESTAMP_NTZ(9)"),
            twoArms("ts", "UNION", "tz"));
        assertEquals(inconsistent("TIMESTAMP_TZ(9)", "TIMESTAMP_LTZ(9)"),
            twoArms("tl", "UNION", "tz"));
        assertEquals(inconsistent("ARRAY", "OBJECT"), twoArms("ob", "UNION", "ar"));
        assertEquals(inconsistent("NUMBER(38,0)", "OBJECT"), twoArms("ob", "UNION", "i"));
    }

    /** The TIME-beside-a-TIMESTAMP pair has its own sentence, and names the TIME first either way. */
    @Test
    public void timeBesideATimestampHasItsOwnSentence() {
        assertEquals("SQL compilation error: incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]",
            twoArms("tm", "UNION", "ts"));
        assertEquals("SQL compilation error: incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]",
            twoArms("ts", "UNION", "tm"), "the TIME is named first however the arms were written");
        assertEquals("SQL compilation error: incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]",
            twoArms("tm", "UNION", "tz"));
    }

    /** Every operator carries the rule, not only UNION. */
    @Test
    public void everySetOperatorRefusesAlike() {
        assertEquals(inconsistent("TIME(9)", "DATE"), twoArms("d", "UNION ALL", "tm"));
        assertEquals(inconsistent("TIME(9)", "DATE"), twoArms("d", "INTERSECT", "tm"));
        assertEquals(inconsistent("TIME(9)", "DATE"), twoArms("d", "EXCEPT", "tm"));
        assertEquals(inconsistent("TIME(9)", "DATE"), twoArms("d", "MINUS", "tm"));
    }

    /** THREE arms, where the fold runs on until it breaks. */
    @Test
    public void aThirdArmIsFoldedIntoTheRunningType() {
        assertEquals(inconsistent("BOOLEAN", "NUMBER(10,2)"),
            outcome("SELECT n FROM sa UNION SELECT bo FROM sa UNION SELECT n FROM sa"),
            "it breaks at the SECOND arm and never reads the third");
        assertEquals(inconsistent("BOOLEAN", "NUMBER(10,2)"),
            outcome("SELECT n FROM sa UNION SELECT n FROM sa UNION SELECT bo FROM sa"),
            "and here the first two fold before the third breaks it");
        assertEquals(inconsistent("TIME(9)", "DATE"),
            outcome("SELECT d FROM sa UNION SELECT d FROM sa UNION SELECT tm FROM sa"));
        assertEquals(inconsistent("DATE", "TIME(9)"),
            outcome("SELECT tm FROM sa UNION SELECT d FROM sa UNION SELECT d FROM sa"));
    }

    /** A column other than the first is checked just the same. */
    @Test
    public void everyColumnIsChecked() {
        assertEquals(inconsistent("TIME(9)", "DATE"),
            outcome("SELECT i, d FROM sa UNION SELECT i, tm FROM sa"));
    }

    /** The refusal is COMPILE-time: an empty input refuses identically. */
    @Test
    public void anEmptyInputRefusesJustTheSame() {
        assertEquals(inconsistent("TIME(9)", "DATE"),
            outcome("SELECT d FROM sae UNION SELECT tm FROM sae"));
    }

    /** Everything that DOES fold must keep folding, which is most of the surface. */
    @Test
    public void thePairsThatFoldAreUnchanged() {
        assertEquals("BOOLEAN", twoArms("bo", "UNION", "n"),
            "the reverse of a refused pair, and it folds");
        assertEquals("NUMBER(38,2)", twoArms("i", "UNION", "n"));
        assertEquals("NUMBER(38,5)", twoArms("v", "UNION", "i"));
        assertEquals("TIMESTAMP_NTZ", twoArms("d", "UNION", "ts"));
        assertEquals("TIMESTAMP_LTZ", twoArms("ts", "UNION", "tl"));
        assertEquals("NUMBER(18,5)", outcome("SELECT 1 FROM sa UNION SELECT v FROM sa"));
        assertEquals("DATE", outcome("SELECT d FROM sa UNION SELECT NULL FROM sa"));
    }

    /** And a VARIANT beside anything is NOT a refusal — it absorbs what stands next to it. */
    @Test
    public void aVariantArmIsNeverRefused() {
        assertEquals("VARIANT", twoArms("va", "UNION", "i"));
        assertEquals("OBJECT", twoArms("ob", "UNION", "va"));
    }
}
