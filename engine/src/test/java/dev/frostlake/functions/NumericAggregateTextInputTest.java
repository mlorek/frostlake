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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A numeric aggregate over TEXT that is not a number. Live refuses the statement as the rows are read;
 * answering zero over the values it could not convert is a silently wrong answer, which is worse.
 *
 * <p>★ THE REFUSAL CARRIES NO COMPILATION PREFIX, which is the tell that this is the CONVERSION
 * failing at row time rather than the plan being rejected. So an empty group is never refused, and a
 * column that merely COULD hold bad text is not enough — the rule is per value.
 *
 * <p>★ THE VALUE NAMED IS THE FIRST IN SCAN ORDER, even for the aggregates that sort their input.
 * Over 'z' then 'a' every one of them names 'z'; written the other way round, 'a'. The conversion
 * happens as the rows arrive, before any ordering.
 *
 * <p>★ WHICH AGGREGATES CONVERT is the whole rule. SUM, AVG, MEDIAN, the two PERCENTILEs, STDDEV and
 * VARIANCE compute over their input and so must read it as a number; MIN, MAX, COUNT, MODE and
 * ARRAY_AGG hand back an input and answer over text quite happily.
 *
 * <p>★ INSIDE A VARIANT IT IS A DIFFERENT SENTENCE — the member is named as JSON, keeping its quotes
 * or its braces — and a JSON boolean converts to 1 rather than being refused.
 *
 * <p>NOT COVERED HERE: the WIDTH these aggregates present a converted VARCHAR at, which diverges on
 * its own account, and a BOOLEAN or DATE argument, which live refuses at compile time in the
 * function's own vocabulary.
 */
public class NumericAggregateTextInputTest extends BaseDatabaseTest {

    private static final String NOT_A_NUMBER = "Numeric value '%s' is not recognized";

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE letters (t VARCHAR(10))");
        engine.execute("INSERT INTO letters VALUES ('a'), ('b'), ('c')");
        engine.execute("CREATE OR REPLACE TABLE digits (t VARCHAR(10))");
        engine.execute("INSERT INTO digits VALUES ('1'), ('2'), ('8')");
    }

    /** One statement's refusal, or its rows joined. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder answered = new StringBuilder("ACCEPTED");
            while (rs.next()) {
                answered.append(' ').append(String.valueOf(rs.getValue(0)));
            }
            return answered.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String unrecognised(final String value) {
        return String.format(NOT_A_NUMBER, value);
    }

    @Test
    void everyAggregateThatComputesOverItsInputRefusesText() {
        assertEquals(unrecognised("a"), outcome("SELECT SUM(t) FROM letters"));
        assertEquals(unrecognised("a"), outcome("SELECT AVG(t) FROM letters"));
        assertEquals(unrecognised("a"), outcome("SELECT MEDIAN(t) FROM letters"));
        assertEquals(unrecognised("a"),
            outcome("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY t) FROM letters"));
        assertEquals(unrecognised("a"),
            outcome("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY t) FROM letters"));
        assertEquals(unrecognised("a"), outcome("SELECT STDDEV(t) FROM letters"));
        assertEquals(unrecognised("a"), outcome("SELECT VARIANCE(t) FROM letters"));
    }

    @Test
    void anAggregateThatHandsBackAnInputAnswersOverText() {
        assertEquals("ACCEPTED a", outcome("SELECT MIN(t) FROM letters"));
        assertEquals("ACCEPTED c", outcome("SELECT MAX(t) FROM letters"));
        assertEquals("ACCEPTED 3", outcome("SELECT COUNT(t) FROM letters"));
        assertEquals("ACCEPTED [\"a\",\"b\",\"c\"]", outcome("SELECT ARRAY_AGG(t) FROM letters"));
    }

    @Test
    void theValueNamedIsTheFirstInScanOrderNotInSortedOrder() {
        engine.execute("CREATE OR REPLACE TABLE za (t VARCHAR(10))");
        engine.execute("INSERT INTO za VALUES ('z'), ('a')");
        engine.execute("CREATE OR REPLACE TABLE az (t VARCHAR(10))");
        engine.execute("INSERT INTO az VALUES ('a'), ('z')");
        assertEquals(unrecognised("z"), outcome("SELECT SUM(t) FROM za"));
        assertEquals(unrecognised("a"), outcome("SELECT SUM(t) FROM az"));
        // The sorting aggregates name the same value, so the conversion runs before the sort.
        assertEquals(unrecognised("z"), outcome("SELECT MEDIAN(t) FROM za"));
        assertEquals(unrecognised("a"), outcome("SELECT MEDIAN(t) FROM az"));
        assertEquals(unrecognised("z"),
            outcome("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY t) FROM za"));
    }

    @Test
    void oneBadValueRefusesTheWholeAggregate() {
        engine.execute("CREATE OR REPLACE TABLE mixed (t VARCHAR(10))");
        engine.execute("INSERT INTO mixed VALUES ('1'), ('a'), ('2')");
        assertEquals(unrecognised("a"), outcome("SELECT SUM(t) FROM mixed"));
        assertEquals(unrecognised("a"), outcome("SELECT MEDIAN(t) FROM mixed"));
        // The values it COULD read are answered by the aggregates that never convert.
        assertEquals("ACCEPTED 3", outcome("SELECT COUNT(t) FROM mixed"));
    }

    @Test
    void nullsAreSkippedBeforeTheConversionSeesAnything() {
        engine.execute("CREATE OR REPLACE TABLE nulled (t VARCHAR(10))");
        engine.execute("INSERT INTO nulled VALUES (NULL), ('q'), (NULL)");
        assertEquals(unrecognised("q"), outcome("SELECT SUM(t) FROM nulled"));
        assertEquals(unrecognised("q"), outcome("SELECT MEDIAN(t) FROM nulled"));
    }

    @Test
    void textThatDoesConvertIsStillAnswered() {
        assertEquals("ACCEPTED 2.000", outcome("SELECT MEDIAN(t) FROM digits"));
        assertEquals("ACCEPTED 2.000",
            outcome("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY t) FROM digits"));
        assertEquals("ACCEPTED 2",
            outcome("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY t) FROM digits"));
        assertEquals("ACCEPTED 3", outcome("SELECT COUNT(t) FROM digits"));
    }

    @Test
    void theConversionIsTheOrdinaryOneWithItsOrdinaryEdges() {
        engine.execute("CREATE OR REPLACE TABLE odd (t VARCHAR(10))");
        engine.execute("INSERT INTO odd VALUES (' 2 '), ('1e2'), (''), ('-')");
        // Surrounding spaces are trimmed and an exponent is read.
        assertEquals("ACCEPTED 2.000", outcome("SELECT MEDIAN(t) FROM odd WHERE t = ' 2 '"));
        assertEquals("ACCEPTED 100.000", outcome("SELECT MEDIAN(t) FROM odd WHERE t = '1e2'"));
        // An empty string and a lone sign are not numbers, and are named as they stand.
        assertEquals(unrecognised(""), outcome("SELECT SUM(t) FROM odd WHERE t = ''"));
        assertEquals(unrecognised("-"), outcome("SELECT SUM(t) FROM odd WHERE t = '-'"));
    }

    @Test
    void theRefusalSurvivesAGroupByAndAWindow() {
        assertEquals(unrecognised("a"),
            outcome("SELECT SUM(t) FROM letters GROUP BY t ORDER BY 1"));
        assertEquals(unrecognised("a"), outcome("SELECT SUM(t) OVER () FROM letters"));
        assertEquals(unrecognised("a"), outcome("SELECT MEDIAN(t) OVER () FROM letters"));
    }

    @Test
    void insideAVariantItIsTheCastSentenceInstead() {
        engine.execute("CREATE OR REPLACE TABLE kinds (v VARIANT, o VARIANT)");
        engine.execute("INSERT INTO kinds SELECT TO_VARIANT(TRUE), PARSE_JSON('{\"k\":1}')");
        assertEquals("Failed to cast variant value \"a\" to REAL",
            outcome("SELECT SUM(TO_VARIANT(t)) FROM letters"));
        assertEquals("ACCEPTED 7.0", outcome("SELECT SUM(TO_VARIANT('7')) FROM kinds"));
        // A JSON boolean is a number in there, and an object is not.
        assertEquals("ACCEPTED 1.0", outcome("SELECT SUM(v) FROM kinds"));
        assertEquals("Failed to cast variant value {\"k\":1} to REAL",
            outcome("SELECT SUM(o) FROM kinds"));
    }
}
