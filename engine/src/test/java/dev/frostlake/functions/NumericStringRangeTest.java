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
 * Reading a NUMBER out of a STRING that is too wide for one, which live splits into TWO sentences
 * depending on how far past the range the value is:
 *
 * <pre>
 *   '123456789012'::NUMBER(10,0)    Numeric value '123456789012' is out of range
 *   '999…9'(39)::NUMBER(10,0)       Numeric value '999…9' is not recognized
 *   '999…9'(39)::NUMBER(38,0)       Numeric value '999…9' is not recognized
 * </pre>
 *
 * <p>★ THE TARGET DECIDES ONLY THE FIRST. A value that overflows the column it was going into is out
 * of range; a value that no NUMBER at all could hold was never READ as a number, and live says so in
 * the same words it uses for {@code 'abc'} — the same sentence, at row time, with no compilation
 * prefix on either engine.
 *
 * <p>★ THE ECHO IS THE TEXT AS WRITTEN, keeping the leading zeros and the sign that do not count
 * toward the thirty-eight digits, so {@code '0000' + 38 nines} converts while {@code '0000' + 39}
 * is refused and prints all forty-three characters back.
 *
 * <p>★ DECIMALS ROUND RATHER THAN OVERFLOW, because the width is measured at the scale being read at:
 * a string of thirty-nine decimals is 1, not an error.
 *
 * <p>★ TO_DOUBLE IS EXEMPT — the approximate family has no thirty-eight digit ceiling — and every
 * TRY_ spelling answers NULL where its strict twin raises.
 *
 * <p>NOT COVERED HERE: the IMPLICIT path, where a VARCHAR meets a NUMBER in a comparison or in
 * arithmetic. Live refuses those with the same sentence; Frostlake still coerces silently, which is
 * the wider string-coercion surface rather than this one.
 */
public class NumericStringRangeTest extends BaseDatabaseTest {

    private static final String N39 = "9".repeat(39);
    private static final String N38 = "9".repeat(38);

    /** One statement's answer, or its refusal — the shape both backends give. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String unreadable(final String text) {
        return "Numeric value '" + text + "' is not recognized";
    }

    @Test
    void aStringPastEveryNumberIsNotRecognized() {
        assertEquals(unreadable(N39), answer("SELECT TO_NUMBER('" + N39 + "')"));
        assertEquals(unreadable(N39), answer("SELECT TO_DECIMAL('" + N39 + "')"));
        assertEquals(unreadable(N39), answer("SELECT TO_NUMERIC('" + N39 + "')"));
        assertEquals(unreadable(N39), answer("SELECT TO_NUMBER('" + N39 + "', 38, 0)"));
        assertEquals(unreadable(N39), answer("SELECT '" + N39 + "'::NUMBER(38,0)"));
        assertEquals(unreadable(N39), answer("SELECT '" + N39 + "'::NUMBER(10,0)"));
    }

    @Test
    void oneDigitFewerConverts() {
        assertEquals(N38, answer("SELECT TO_NUMBER('" + N38 + "')"));
    }

    @Test
    void overflowingOnlyTheTargetIsTheOtherSentence() {
        assertEquals("Numeric value '123456789012' is out of range",
            answer("SELECT '123456789012'::NUMBER(10,0)"));
    }

    @Test
    void theEchoKeepsTheZerosAndTheSignThatDoNotCount() {
        assertEquals(unreadable("0000" + N39), answer("SELECT TO_NUMBER('0000" + N39 + "')"));
        assertEquals(N38, answer("SELECT TO_NUMBER('0000" + N38 + "')"));
        assertEquals(unreadable("+" + N39), answer("SELECT TO_NUMBER('+" + N39 + "')"));
        assertEquals(unreadable("-" + N39), answer("SELECT TO_NUMBER('-" + N39 + "')"));
        assertEquals(unreadable(N39 + ".5"), answer("SELECT TO_NUMBER('" + N39 + ".5')"));
    }

    @Test
    void decimalsRoundRatherThanOverflow() {
        assertEquals("1", answer("SELECT TO_NUMBER('0." + N39 + "')"));
        assertEquals("2", answer("SELECT TO_NUMBER('1." + N38 + "')"));
    }

    @Test
    void anExponentIsReadAndThenMeasured() {
        assertEquals("100000", answer("SELECT TO_NUMBER('1e5')"));
        assertEquals(unreadable("1e39"), answer("SELECT TO_NUMBER('1e39')"));
    }

    @Test
    void theApproximateFamilyHasNoCeiling() {
        assertEquals("1.0E39", answer("SELECT TO_DOUBLE('" + N39 + "')"));
    }

    @Test
    void theTrySpellingsAnswerNull() {
        assertEquals("null", answer("SELECT TRY_TO_NUMBER('" + N39 + "')"));
        assertEquals("null", answer("SELECT TRY_TO_DECIMAL('" + N39 + "')"));
        assertEquals("null", answer("SELECT TRY_CAST('123456789012' AS NUMBER(10,0))"));
    }

    @Test
    void textThatIsNoNumberAtAllKeepsTheSameSentence() {
        assertEquals(unreadable("abc"), answer("SELECT TO_NUMBER('abc')"));
        assertEquals(unreadable(""), answer("SELECT TO_NUMBER('')"));
    }
}
