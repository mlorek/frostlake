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

/**
 * FX, the exact-match modifier of a numeric format model: FX toggles the exact mode for the elements after
 * it and FM the compact one. An exact text is read as the model prints it in fill mode — a sign position,
 * the whole field at its width with leading positions as spaces or zeros, as many fraction characters as
 * positions, an exponent at its width, literals character by character — and white space is skipped only
 * where the mode is lax. TO_CHAR prints nothing for FX. Every cell is live-verified.
 */
public class ToNumberExactModeTest extends BaseDatabaseTest {

    private String number(final String text, final String format) {
        return String.valueOf(engine.executeQuery("SELECT TO_VARCHAR(TO_NUMBER('" + text.replace("'", "''") + "', '"
            + format.replace("'", "''") + "'))").getRows().get(0).getValue(0));
    }

    private void assertRefused(final String text, final String format) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_NUMBER('" + text.replace("'", "''") + "', '"
                    + format.replace("'", "''") + "')");
            }
        });
        assertEquals("Can't parse '" + text + "' as number with format '" + format + "'", refused.getMessage());
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void anExactTextIsReadAsTheModelPrintsIt() {
        assertEquals("1", number(" 1", "FX9"));
        assertEquals("-1", number("-1", "FX9"));
        assertEquals("1", number("+1", "FX9"));
        assertEquals("0", number(" 0", "FX9"));
        assertEquals("0", number(" 0", "FX0"));
        assertEquals("12", number(" 12", "FX99"));
        assertEquals("1", number("  1", "FX99"));
        assertEquals("1", number(" 01", "FX99"));
        assertEquals("-1", number("-01", "FX99"));
        assertEquals("-1", number(" -1", "FX99"));
        assertEquals("12", number(" 012", "FX999"));
        assertEquals("12", number("  12", "FX999"));
        assertEquals("1", number(" 01", "FX09"));
        assertEquals("2", number(" 1.5", "FX9.9"));
        assertEquals("1", number(" 1.0", "FX9.9"));
        assertEquals("1", number(" 0.5", "FX9.9"));
        assertEquals("2", number(" 1.50", "FX9.99"));
        assertEquals("2", number(" 1.5 ", "FX9.99"));
        assertEquals("12", number(" 12.30", "FX99.99"));
        assertEquals("12", number(" 12.3 ", "FX99.99"));
        assertEquals("1", number(" 1.", "FX9."));
        assertEquals("1234", number(" 1,234", "FX9,999"));
        assertEquals("1", number("     1", "FX9,999"));
        assertEquals("1234", number(" 01,234", "FX99,999"));
        assertEquals("1234", number(" 1,234", "FX9G999"));
        assertEquals("12", number(" 1,2", "FX9G9"));
        assertEquals("123457", number(" 123,456.78", "FX999,999.99"));
        assertEquals("1", number("+1", "FXS9"));
        assertEquals("1", number(" +1", "FXS99"));
        assertEquals("1", number("+01", "FXS99"));
        assertEquals("1", number("1 ", "FX9MI"));
        assertEquals("-1", number("1-", "FX9MI"));
        assertEquals("-12", number("12-", "FX99MI"));
        assertEquals("1", number(" 1 ", "FX99MI"));
        assertEquals("-1", number(" 1-", "FX99MI"));
        assertEquals("1", number(" $1", "FX$9"));
        assertEquals("12", number(" $12", "FX$99"));
        assertEquals("1", number("  $1", "FX$99"));
        assertEquals("1", number("$1", "FXFM$9"));
        assertEquals("1", number(" 50%", "FX99%"));
        assertEquals("0", number("  5%", "FX99%"));
        assertEquals("100", number(" 1.0E+02", "FX9.9EEEE"));
        assertEquals("2", number(" 1.5E+00", "FX9.9EEEE"));
        assertEquals("2", number("+1.5E+00", "FXS9.9EEEE"));
        assertEquals("255", number("FF", "FXXX"));
        assertEquals("255", number("FF", "FXFMXX"));
        assertEquals("1", number(" 1", "FXTM9"));
        assertEquals("1", number(" 1", "FXB9"));
        assertEquals("1", number("x 1", "FX\"x\"9"));
        assertEquals("1", number(" 1x", "FX9\"x\""));
        assertEquals("1", number(" 0 1", "FX9 9"));
        assertEquals("1", number("  1", "FX 9"));
        assertEquals("1", number("1", "FXFM9"));
        assertEquals("-1", number("-1", "FXFM9"));
        assertEquals("1", number("1", "FMFX9"));
        assertEquals("1", number("1", "FXFM99"));
        assertEquals("1", number("01", "FXFM99"));
        assertEquals("1", number("1", "FXFM9FM"));
        assertEquals("2", number("1.5", "FXFM9.99"));
        assertEquals("1", number("1", "FXFX9"));
        assertEquals("1", number(" 1", "FXFX9"));
        assertEquals("1", number(" 1 ", "FX9FX"));
    }

    @Test
    public void anyOtherTextIsRefused() {
        assertRefused("1", "FX9");
        assertRefused("1", "fx9");
        assertRefused(" -1", "FX9");
        assertRefused("12", "FX9");
        assertRefused(" 1 ", "FX9");
        assertRefused("", "FX9");
        assertRefused("  ", "FX9");
        assertRefused("12", "FX99");
        assertRefused(" 1", "FX99");
        assertRefused("- 1", "FX99");
        assertRefused("  -1", "FX99");
        assertRefused("  1", "FX09");
        assertRefused(" 1.5", "FX9.99");
        assertRefused(" 1.", "FX9.9");
        assertRefused(" .5", "FX9.9");
        assertRefused("1,234", "FX9,999");
        assertRefused("   1,234", "FX99,999");
        assertRefused("  1 234", "FX99,999");
        assertRefused(" 12", "FX9G9");
        assertRefused("   123.40", "FX999,999.99");
        assertRefused("123,456.78", "FX999,999.99");
        assertRefused("+ 1", "FXS99");
        assertRefused(" 1.5E+0", "FX9.9EEEE");
        assertRefused("1.5E+00", "FX9.9EEEE");
        assertRefused(" $ 1", "FX$99");
        assertRefused("50%", "FX99%");
        assertRefused(" 5%", "FX99%");
        assertRefused(" ff", "FXXX");
        assertRefused(" FF", "FXXX");
        assertRefused("  FF", "FXXX");
        assertRefused(" FF", "FXFMXX");
        assertRefused("1", "FXTM9");
        assertRefused("1 ", "FXTM9");
        assertRefused("  ", "FXB9");
        assertRefused("x1", "FX\"x\"9");
        assertRefused(" 1", "FX 9");
        assertRefused(" 1", "FXFM9");
        assertRefused("1", "FXFM9 ");
        assertRefused(" 1.5", "FXFM9.99");
        assertRefused("1", "FX9FX");
    }

    @Test
    public void theTryAndDoubleSpellingsReadTheSameWay() {
        assertEquals("null", scalar("SELECT TRY_TO_NUMBER('1', 'FX9')"));
        assertEquals("1", scalar("SELECT TO_VARCHAR(TRY_TO_NUMBER(' 1', 'FX9'))"));
        assertEquals("1", scalar("SELECT TO_VARCHAR(TO_DECIMAL(' 1', 'FX9'))"));
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_DOUBLE('1', 'FX9')");
            }
        });
        assertEquals("Can't parse '1' as number with format 'FX9'", refused.getMessage());
    }

    @Test
    public void toCharPrintsNothingForFx() {
        assertEquals("[ 1]", scalar("SELECT '[' || TO_CHAR(1, 'FX9') || ']'"));
        assertEquals("[ 12]", scalar("SELECT '[' || TO_CHAR(12, 'FX99') || ']'"));
        assertEquals("[ 1.5 ]", scalar("SELECT '[' || TO_CHAR(1.5, 'FX9.99') || ']'"));
        assertEquals("[ 12.3 ]", scalar("SELECT '[' || TO_CHAR(12.3, 'FX99.99') || ']'"));
        assertEquals("[1]", scalar("SELECT '[' || TO_CHAR(1, 'FXFM9') || ']'"));
        assertEquals("[ 1]", scalar("SELECT '[' || TO_CHAR(1, 'FX9FX') || ']'"));
        assertEquals("[ 1]", scalar("SELECT '[' || TO_CHAR(1, '9FX') || ']'"));
    }
}
