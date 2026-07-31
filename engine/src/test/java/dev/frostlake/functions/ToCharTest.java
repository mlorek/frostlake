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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TO_CHAR / TO_VARCHAR numeric format model: digit placeholders (0/9), group separator, decimal
 * places (with HALF_UP rounding), leading '$', and the sign controls S / MI and FM. All expected
 * values are live-verified against Snowflake: results right-align to the template width plus a
 * leading sign position, '0' placeholders zero-pad, insignificant trailing fraction digits in '9'
 * positions render as spaces, overflow renders '#', and PR is rejected exactly as Snowflake does.
 */
public class ToCharTest extends BaseDatabaseTest {

    private String toChar(final String sql) {
        final Object v = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void currencyGroupedTwoDecimals() {
        assertEquals(" $1,234.50", toChar("SELECT TO_CHAR(1234.5, '$9,999.00')"),
            "the leading sign position renders blank for a positive value (live-verified)");
    }

    @Test
    public void negativeGetsLeadingMinusOutsideCurrency() {
        assertEquals("-$1,234.50", toChar("SELECT TO_CHAR(-1234.5, '$9,999.00')"));
    }

    @Test
    public void zeroPlaceholderPadsIntegers() {
        assertEquals(" 0005", toChar("SELECT TO_CHAR(5, '0000')"));
    }

    @Test
    public void fillModeRoundsToScale() {
        assertEquals("1,234.57", toChar("SELECT TO_CHAR(1234.567, 'FM9,999.00')"));
    }

    @Test
    public void fewerDecimalPlaces() {
        assertEquals(" 1,234.5", toChar("SELECT TO_CHAR(1234.5, '9,999.9')"));
    }

    @Test
    public void trailingMinusForNegative() {
        assertEquals("  42-", toChar("SELECT TO_CHAR(-42, '9999MI')"));
        assertEquals("  42 ", toChar("SELECT TO_CHAR(42, '9999MI')"),
            "a positive MI value renders a trailing blank");
    }

    @Test
    public void explicitLeadingSign() {
        assertEquals("  +42", toChar("SELECT TO_CHAR(42, 'S9999')"));
        assertEquals("  -42", toChar("SELECT TO_CHAR(-42, 'S9999')"));
    }

    @Test
    public void prKeywordIsRejectedLikeSnowflake() {
        // Live-verified: PR (Oracle's angle-bracket element) is not part of Snowflake's model.
        final RuntimeException rejected = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_CHAR(-42, '9999PR')");
            }
        });
        assertTrue(rejected.getMessage().contains("invalid numeric format keyword: 'PR'"),
            rejected.getMessage());
    }

    @Test
    public void plainDigitsPadToTheTemplateWidthWithASignSlot() {
        assertEquals("    5", toChar("SELECT TO_CHAR(5, '9999')"));
        assertEquals("   -5", toChar("SELECT TO_CHAR(-5, '9999')"));
        assertEquals("5", toChar("SELECT TO_CHAR(5, 'FM9999')"));
    }

    @Test
    public void insignificantTrailingFractionNinesRenderAsSpaces() {
        assertEquals("  0.5 ", toChar("SELECT TO_CHAR(0.5, '99.99')"));
        assertEquals(" 123.5", toChar("SELECT TO_CHAR(123.456, '999.9')"));
    }

    @Test
    public void overflowRendersHashesAcrossTheTemplate() {
        assertEquals(" ###", toChar("SELECT TO_CHAR(12345, '999')"));
    }

    @Test
    public void toVarcharSynonymAppliesTheFormat() {
        assertEquals(" $1,234.50", toChar("SELECT TO_VARCHAR(1234.5, '$9,999.00')"));
    }

    @Test
    public void noFormatStringifiesVerbatim() {
        assertEquals("42", toChar("SELECT TO_CHAR(42)"));
        assertEquals("hello", toChar("SELECT TO_CHAR('hello')"));
    }
}
