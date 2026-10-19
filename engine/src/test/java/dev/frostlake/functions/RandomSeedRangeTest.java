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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RANDOM's seed has to fit a range, and the range is its family's own. The engine converted every seed to a 64-bit
 * number and wrapped what did not fit.
 *
 * <p>On the account a seed is rounded half away from zero, then: a NUMBER takes the whole signed 64-bit range and
 * past it is {@code Numeric value '…' is not recognized}, naming the rounded number; text that is no number, or one
 * past 64 bits, is {@code not recognized}, and one past eighteen digits {@code out of range}, both quoting the text;
 * a FLOAT becomes a FIXED[SB8](18,0) and past eighteen digits, or NaN or an infinity, is {@code Number out of
 * representable range}; a VARIANT number takes eighteen digits in the same words over the plain type FIXED, and a
 * VARIANT text that is no seed is {@code Failed to cast variant value}. The call is read plain: wrapped in a test the
 * account folds it away and judges nothing. All live-verified.
 */
public class RandomSeedRangeTest extends BaseDatabaseTest {

    /** The refusal of a seed, or "ok" for a seed that gives a number. */
    private String seeded(final String seed) {
        try {
            engine.executeQuery("SELECT RANDOM(" + seed + ")").getRows().get(0).getValue(0);
            return "ok";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private static String notRecognized(final String value) {
        return "Numeric value '" + value + "' is not recognized";
    }

    private static String outOfRange(final String value) {
        return "Numeric value '" + value + "' is out of range";
    }

    @Test
    public void aNumberTakesTheWholeSixtyFourBitRange() {
        for (final String seed : new String[] {"9223372036854775807", "-9223372036854775808", "9223372036854775807.4",
            "9223372036854775807.49", "-9223372036854775808.4", "9223372036854775806.5", "1e18",
            "1000000000000000000::NUMBER(38,0)", "12::NUMBER(38,10)", "0.5", "-1.5"}) {
            assertEquals("ok", seeded(seed), seed);
        }
        assertEquals(notRecognized("9223372036854775808"), seeded("9223372036854775807.5"), "the rounded number is named");
        assertEquals(notRecognized("9223372036854775808"), seeded("9223372036854775808"));
        assertEquals(notRecognized("-9223372036854775809"), seeded("-9223372036854775809"));
        assertEquals(notRecognized("-9223372036854775809"), seeded("-9223372036854775808.5"));
        assertEquals(notRecognized("99999999999999999999"), seeded("99999999999999999999"));
        assertEquals(notRecognized("10000000000000000000"), seeded("1e19"));
        assertEquals(notRecognized("9300000000000000000"), seeded("9.3e18"));
    }

    @Test
    public void textTakesEighteenDigits() {
        for (final String seed : new String[] {"'999999999999999999'", "'-999999999999999999'", "'999999999999999999.4'",
            "' 12 '", "'1.5'", "'+5'", "'1e3'", "'1E3'", "'.5'", "'5.'"}) {
            assertEquals("ok", seeded(seed), seed);
        }
        for (final String text : new String[] {"1000000000000000000", "-1000000000000000000", "999999999999999999.5",
            "-999999999999999999.5", "9223372036854775807", "9223372036854775806.5", "-9223372036854775808", "1e18"}) {
            assertEquals(outOfRange(text), seeded("'" + text + "'"), text);
        }
        for (final String text : new String[] {"9223372036854775808", "9999999999999999999", "-9223372036854775809",
            "99999999999999999999", "1e400", "12abc", "0x10", "1,000", "-", "abc", ""}) {
            assertEquals(notRecognized(text), seeded("'" + text + "'"), text);
        }
    }

    @Test
    public void aFloatTakesEighteenDigits() {
        for (final String seed : new String[] {"9.99e17::FLOAT", "-9.99e17::FLOAT", "999999999999999900::FLOAT",
            "9.999999999999999e17::FLOAT", "5e17::FLOAT", "1.5::FLOAT"}) {
            assertEquals("ok", seeded(seed), seed);
        }
        final String past = "Number out of representable range: type FIXED[SB8](18,0){not null}, value ";
        assertEquals(past + "1e+18", seeded("1e18::FLOAT"));
        assertEquals(past + "1e+18", seeded("999999999999999999::FLOAT"), "the nearest double is 1e18");
        assertEquals(past + "-1e+18", seeded("-1e18::FLOAT"));
        assertEquals(past + "9.2e+18", seeded("9.2e18::FLOAT"));
        assertEquals(past + "-9.2e+18", seeded("-9.2e18::FLOAT"));
        assertEquals(past + "nan", seeded("'NaN'::FLOAT"));
        assertEquals(past + "inf", seeded("'inf'::FLOAT"));
    }

    @Test
    public void aVariantTakesEighteenDigitsAndItsTextIsReadAsText() {
        for (final String seed : new String[] {"PARSE_JSON('999999999999999999')", "PARSE_JSON('-999999999999999999')",
            "PARSE_JSON('999999999999999999.4')", "PARSE_JSON('1.5')", "PARSE_JSON('1e17')", "PARSE_JSON('9.99e17')",
            "PARSE_JSON('\"999999999999999999\"')", "PARSE_JSON('\"12\"')", "PARSE_JSON('\"1.5\"')", "PARSE_JSON('\"1e3\"')",
            "PARSE_JSON('true')", "TO_VARIANT(12)", "TO_VARIANT(999999999999999999)", "TO_VARIANT(9.99e17::FLOAT)"}) {
            assertEquals("ok", seeded(seed), seed);
        }
        final String past = "Number out of representable range: type FIXED, value ";
        assertEquals(past + "1000000000000000000", seeded("PARSE_JSON('1000000000000000000')"));
        assertEquals(past + "-1000000000000000000", seeded("PARSE_JSON('-1000000000000000000')"));
        assertEquals(past + "1000000000000000000", seeded("PARSE_JSON('999999999999999999.5')"), "the rounded number");
        assertEquals(past + "-1000000000000000000", seeded("PARSE_JSON('-999999999999999999.5')"));
        assertEquals(past + "9223372036854775807", seeded("PARSE_JSON('9223372036854775807.4')"));
        assertEquals(past + "1000000000000000000", seeded("TO_VARIANT(1000000000000000000)"));
        assertEquals(past + "1e+18", seeded("PARSE_JSON('1e18')"));
        assertEquals(past + "1e+19", seeded("PARSE_JSON('1e19')"));
        assertEquals(past + "1e+18", seeded("TO_VARIANT(1e18::FLOAT)"));
        assertEquals(past + "inf", seeded("PARSE_JSON('1e400')"));
        for (final String member : new String[] {"\"1000000000000000000\"", "\"9223372036854775807\"",
            "\"99999999999999999999\"", "\"abc\"", "\"\"", "[1]", "{\"a\":1}"}) {
            assertEquals("Failed to cast variant value " + member + " to FIXED", seeded("PARSE_JSON('" + member + "')"),
                member);
        }
        assertEquals("Invalid parameter value: NULL. Reason: seed must not be NULL", seeded("PARSE_JSON('null')"));
        assertEquals("Invalid parameter value: NULL. Reason: seed must not be NULL", seeded("NULL"));
    }

    @Test
    public void everyFamilyRoundsHalfAwayFromZero() {
        assertEquals("true|true|true|true|true|true|true|true|true|true", String.join("|", cells(
            "SELECT RANDOM(0.5) = RANDOM(1), RANDOM(-0.5) = RANDOM(-1), RANDOM(1.5) = RANDOM(2), RANDOM(2.5) = RANDOM(3),"
                + " RANDOM('1.5') = RANDOM(2), RANDOM(1.5::FLOAT) = RANDOM(2), RANDOM(2.5::FLOAT) = RANDOM(3),"
                + " RANDOM(PARSE_JSON('1.5')) = RANDOM(2), RANDOM(PARSE_JSON('\"12\"')) = RANDOM(12),"
                + " RANDOM(' 12 ') = RANDOM(12)")));
    }

    private String[] cells(final String sql) {
        final List<Object> values = engine.executeQuery(sql).getRows().get(0).getValues();
        final String[] out = new String[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = String.valueOf(values.get(i));
        }
        return out;
    }
}
