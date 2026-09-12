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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numeric INPUT format model of TO_NUMBER / TO_DECIMAL / TO_NUMERIC, TO_DOUBLE and TO_DECFLOAT: a model
 * that is not one is refused before the text is read, naming the target in the account's words (FIXED,
 * REAL, DECFLOAT) with one sentence per fault, and a text is read against the model's elements, its width
 * included. The TRY_ twins answer NULL for both. Every cell is live-verified.
 */
public class NumericInputFormatModelTest extends BaseDatabaseTest {

    private String failureOf(final String sql) {
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(failure.getMessage());
    }

    private void assertRefused(final String sql, final String message) {
        final String failure = failureOf(sql);
        assertTrue(failure.endsWith(message), sql + " -> " + failure);
    }

    /** TO_NUMBER('1', model) refused with the FIXED sentence for the model. */
    private void assertBadModel(final String model, final String detail) {
        assertRefused("SELECT TO_NUMBER('1', '" + model.replace("'", "''") + "')",
            "Bad input format model '" + model + "' for FIXED: " + detail);
    }

    private String value(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private void assertCannotParse(final String text, final String model) {
        assertRefused("SELECT TO_NUMBER('" + text + "', '" + model + "')",
            "Can't parse '" + text + "' as number with format '" + model + "'");
    }

    @Test
    public void anUnknownKeywordIsRefusedNamingTheTarget() {
        assertRefused("SELECT TO_DECFLOAT('1e5', '9e9')",
            "Bad input format model '9e9' for DECFLOAT: invalid numeric format keyword: 'e9'");
        assertRefused("SELECT TO_DECFLOAT('12', 'abc')",
            "Bad input format model 'abc' for DECFLOAT: invalid numeric format keyword: 'abc'");
        assertRefused("SELECT TO_NUMBER('1e5', '9e9')",
            "Bad input format model '9e9' for FIXED: invalid numeric format keyword: 'e9'");
        assertRefused("SELECT TO_DECIMAL('1e5', '9e9')",
            "Bad input format model '9e9' for FIXED: invalid numeric format keyword: 'e9'");
        assertRefused("SELECT TO_NUMERIC('1e5', '9e9')",
            "Bad input format model '9e9' for FIXED: invalid numeric format keyword: 'e9'");
        assertRefused("SELECT TO_DOUBLE('1e5', '9e9')",
            "Bad input format model '9e9' for REAL: invalid numeric format keyword: 'e9'");
    }

    /** The keyword is the run of letters, digits, _ $ and % that starts where no element does. */
    @Test
    public void theKeywordIsARunOfIdentifierCharacters() {
        assertBadModel("9e9.9", "invalid numeric format keyword: 'e9'");
        assertBadModel("9q9", "invalid numeric format keyword: 'q9'");
        assertBadModel("9Q", "invalid numeric format keyword: 'Q'");
        assertBadModel("Q9,9", "invalid numeric format keyword: 'Q9'");
        assertBadModel("9E", "invalid numeric format keyword: 'E'");
        assertBadModel("9EEEEEEEE", "invalid numeric format keyword: 'E'");
        assertBadModel("PR", "invalid numeric format keyword: 'PR'");
        assertBadModel("91", "invalid numeric format keyword: '1'");
        assertBadModel("912", "invalid numeric format keyword: '12'");
        assertBadModel("9a_b", "invalid numeric format keyword: 'a_b'");
        assertBadModel("9q$", "invalid numeric format keyword: 'q$'");
        assertBadModel("9a%", "invalid numeric format keyword: 'a%'");
        assertBadModel("9a b", "invalid numeric format keyword: 'a'");
        assertBadModel("9MIq", "invalid numeric format keyword: 'q'");
        assertBadModel("TMq", "invalid numeric format keyword: 'q'");
        assertBadModel("TME(6)", "invalid numeric format keyword: '6'");
        assertBadModel("AU", "invalid numeric format keyword: 'AU'");
        assertBadModel("9|abc", "invalid numeric format keyword: 'abc'");
    }

    @Test
    public void theOtherFaultsHaveTheirOwnSentences() {
        assertBadModel("9+", "invalid character in the format string: '+'");
        assertBadModel("9!", "invalid character in the format string: '!'");
        assertRefused("SELECT TO_NUMBER('1', '9\\t')", "invalid character in the format string: '\\011'");
        assertRefused("SELECT TO_NUMBER('1', '9é')", "invalid character in the format string: '\\303'");
        assertBadModel("9\"", "missing closing \" in the literal: '9\"'");
        assertBadModel("9|\"x", "missing closing \" in the literal: '\"x'");
        assertBadModel("9SS", "format element occurs more than once: 'S'");
        assertBadModel("9ss", "format element occurs more than once: 's'");
        assertBadModel("9.9.9", "format element occurs more than once: '.'");
        assertBadModel("9EEEEEEEEEEEEEE", "format element occurs more than once: 'EEEEEEE'");
        assertBadModel("TMTM", "format element occurs more than once: 'TM'");
        assertBadModel("9D9.9", "format element conflicts with preceding element(s): '.'");
        assertBadModel("9.9D", "format element conflicts with preceding element(s): 'D'");
        assertBadModel("SMI9", "format element conflicts with preceding element(s): 'MI'");
        assertBadModel("9EEEEEEEEE", "format element conflicts with preceding element(s): 'EE'");
        assertBadModel("TM9TME", "format element conflicts with preceding element(s): 'TME'");
        assertBadModel("9EE.9", "digit position after an exponent format element: '9EE.9'");
        assertBadModel("9EE9EE", "digit position after an exponent format element: '9EE9EE'");
        assertBadModel("9X", "cannot mix hexadecimal and decimal format elements: '9X'");
        assertBadModel("TMX", "cannot mix hexadecimal and decimal format elements: 'TMX'");
        assertBadModel("99X|q", "cannot mix hexadecimal and decimal format elements: '99X'");
        assertBadModel("XEE", "hexadecimal exponents are not supported: 'XEE'");
        assertBadModel("X,X", "hexadecimal digit group separators are not supported: 'X,X'");
        assertBadModel("0.0X", "hexadecimal fractions are not supported: '0.0X'");
        assertBadModel("TM99", "cannot mix TM and digit-based numeric format elements: 'TM99'");
        assertBadModel("9|TM99", "cannot mix TM and digit-based numeric format elements: 'TM99'");
        assertBadModel("$", "no digit format elements in a numeric format: '$'");
        assertBadModel("G", "no digit format elements in a numeric format: 'G'");
        assertBadModel(",", "missing required input format element(s)");
        assertBadModel("", "missing required input format element(s)");
        assertBadModel("|9", "missing required input format element(s)");
        assertBadModel("9||", "missing required input format element(s)");
        assertBadModel("TM9(", "TM9 requires parameter (number or ALL) after '('");
        assertBadModel("TM9(6, 3)", "TM9 requires numeric value after comma");
        assertBadModel("TM9(6,3", "TM9 missing closing ')'");
        assertBadModel("AUTO9", "bad AUTO format specification");
        assertRefused("SELECT TO_DOUBLE('1', '9SS')",
            "Bad input format model '9SS' for REAL: format element occurs more than once: 'S'");
        assertRefused("SELECT TO_DOUBLE('ff', 'XX')",
            "Bad input format model 'XX' for REAL: missing required input format element(s)");
        assertRefused("SELECT TO_DECFLOAT('1', '9+')",
            "Bad input format model '9+' for DECFLOAT: invalid character in the format string: '+'");
    }

    @Test
    public void theModelIsCheckedBeforeTheText() {
        assertRefused("SELECT TO_NUMBER('abc', '9e9')",
            "Bad input format model '9e9' for FIXED: invalid numeric format keyword: 'e9'");
        engine.execute("CREATE OR REPLACE TABLE nfm (s VARCHAR)");
        assertEquals(0, engine.executeQuery("SELECT TO_NUMBER(s, '9e9') FROM nfm").getRows().size());
    }

    @Test
    public void theTryTwinsAnswerNull() {
        assertNull(engine.executeQuery("SELECT TRY_TO_NUMBER('1e5', '9e9')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TRY_TO_DECIMAL('1e5', '9e9')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TRY_TO_NUMERIC('1e5', '9e9')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TRY_TO_DOUBLE('1e5', '9e9')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TRY_TO_DECFLOAT('1e5', '9e9')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TRY_TO_NUMBER('1', '9SS')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TRY_TO_NUMBER('123', '99')").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TRY_TO_NUMBER('abc', '999')").getRows().get(0).getValue(0));
    }

    @Test
    public void validModelsStillRead() {
        assertEquals("1", value("SELECT TO_NUMBER('1', '9|')"));
        assertEquals("1", value("SELECT TO_NUMBER('1', 'auto')"));
        assertEquals("1", value("SELECT TO_NUMBER('1', 'TM9(6,3)')"));
        assertEquals("1", value("SELECT TO_NUMBER('1', 'fm9')"));
        assertEquals("1", value("SELECT TO_NUMBER('1', 'B9')"));
        assertEquals("1", value("SELECT TO_NUMBER('1', '9G9,9')"));
        assertEquals("1", value("SELECT TO_NUMBER('1', '9MI9')"));
        assertEquals("255", value("SELECT TO_NUMBER('ff', 'XX')"));
    }

    /** A text wider than the model is refused, echoed exactly as written. */
    @Test
    public void theModelsWidthIsApplied() {
        assertRefused("SELECT TO_DECFLOAT('123', '99')", "Can't parse '123' as number with format '99'");
        assertRefused("SELECT TO_DOUBLE('123', '99')", "Can't parse '123' as number with format '99'");
        assertCannotParse("123", "99");
        assertCannotParse("0012", "99");
        assertCannotParse("12,345", "9,999");
        assertCannotParse("123.5", "99.9");
        assertCannotParse("$123", "$99");
        assertCannotParse("12", "0000");
        assertCannotParse("12", "9099");
        assertCannotParse("fff", "XX");
        assertRefused("SELECT TO_NUMBER(' 123 ', '99')", "Can't parse ' 123 ' as number with format '99'");
        assertRefused("SELECT TO_NUMBER('  abc  ', '999')", "Can't parse '  abc  ' as number with format '999'");
        assertEquals("123", value("SELECT TO_NUMBER('123', '9099')"));
        assertEquals("12", value("SELECT TO_NUMBER('012', '000')"));
        assertEquals("123", value("SELECT TO_DECFLOAT('123', '999')::NUMBER"));
    }

    @Test
    public void signsAndWhiteSpace() {
        assertEquals("-12", value("SELECT TO_NUMBER('-12', '99')"));
        assertEquals("12", value("SELECT TO_NUMBER('+12', '99')"));
        assertEquals("-12", value("SELECT TO_NUMBER('- 12', '99')"));
        assertEquals("12", value("SELECT TO_NUMBER(' 12', '99')"));
        assertEquals("12", value("SELECT TO_NUMBER('12 ', '99')"));
        assertCannotParse("12-", "99");
        assertCannotParse("--12", "99");
        assertCannotParse("1 2", "99");
        assertEquals("-1", value("SELECT TO_NUMBER('1-', '9S')"));
        assertCannotParse("-1", "9S");
        assertEquals("12", value("SELECT TO_NUMBER('+12', 'S99')"));
        assertEquals("-12", value("SELECT TO_NUMBER('12-', '99MI')"));
        assertEquals("-101", value("SELECT TO_NUMBER('-101', 'MI999|999MI')"));
        assertEquals("-102", value("SELECT TO_NUMBER('102-', 'MI999|999MI')"));
    }

    @Test
    public void separatorsPointsAndCurrency() {
        assertEquals("1234", value("SELECT TO_NUMBER('1234', '9,999')"));
        assertEquals("1234", value("SELECT TO_NUMBER('12,34', '99,99')"));
        assertEquals("1234", value("SELECT TO_NUMBER('1,,234', '9,,999')"));
        assertCannotParse("1,23", "9,999");
        assertCannotParse(",123", "9,999");
        assertCannotParse("123,", "9,999");
        assertEquals("12.50", value("SELECT TO_NUMBER('12.5', '99.99', 10, 2)"));
        assertEquals("0.5", value("SELECT TO_NUMBER('.5', 'B9.9', 10, 1)"));
        assertEquals("5", value("SELECT TO_NUMBER('5', '9.9')"));
        assertEquals("1.50", value("SELECT TO_NUMBER('1.50', '9.90', 10, 2)"));
        assertCannotParse(".5", "9.9");
        assertCannotParse("1.", "9");
        assertCannotParse("1.5", "9.90");
        assertCannotParse("12.50", "$99.99");
        assertEquals("-12", value("SELECT TO_NUMBER('-$12', '$99')"));
        assertEquals("12", value("SELECT TO_NUMBER('$ 12', '$99')"));
        assertCannotParse("$-12", "$99");
        assertEquals("12", value("SELECT TO_NUMBER('12$', '99$')"));
        assertEquals("0.50", value("SELECT TO_NUMBER('50%', '99%', 10, 2)"));
        assertCannotParse("50", "99%");
    }

    @Test
    public void literalsAndSpaces() {
        assertEquals("1", value("SELECT TO_NUMBER('-1', '-9')"));
        assertEquals("11", value("SELECT TO_NUMBER('1-1', '9-9')"));
        assertEquals("12", value("SELECT TO_NUMBER('1  2', '9 9')"));
        assertEquals("12", value("SELECT TO_NUMBER('1 2', '9_9')"));
        assertEquals("1", value("SELECT TO_NUMBER('x1', '\"x\"9')"));
        assertCannotParse("12", "9 9");
        assertCannotParse("X1", "\"x\"9");
        assertCannotParse("1", " 9");
    }

    @Test
    public void exponentsHexadecimalAndTextMinimal() {
        assertEquals("100000", value("SELECT TO_NUMBER('1e5', '9EEEE')"));
        assertEquals("1500", value("SELECT TO_NUMBER('1.5e3', '9.9EE')"));
        assertEquals("0.015", value("SELECT TO_NUMBER('1.5e-2', '9.9EE', 10, 3)"));
        assertCannotParse("100000", "9EEEE");
        assertCannotParse("15e3", "9.9EEEE");
        assertCannotParse("1.5e1234", "9.9EE");
        assertCannotParse("1e5", "999");
        assertEquals("255", value("SELECT TO_NUMBER(' ff ', 'XX')"));
        assertCannotParse("-ff", "XX");
        assertCannotParse("ff", "0XX");
        assertEquals("100000", value("SELECT TO_NUMBER('1e5', 'TM')"));
        assertEquals("1500", value("SELECT TO_NUMBER('1.5e3', 'TME')"));
        assertEquals("1234", value("SELECT TO_NUMBER('1,234', 'TM9(ALL,3)')"));
        assertEquals("1.5", value("SELECT TO_NUMBER('$1.5', '$TM9', 10, 1)"));
        assertEquals("1", value("SELECT TO_NUMBER('1', 'TM$')"));
        assertEquals("1", value("SELECT TO_NUMBER('+1', 'STM9')"));
        assertEquals("100000", value("SELECT TO_NUMBER('1e5', 'AUTO')"));
        assertEquals("123", value("SELECT TO_NUMBER('123', '99|999')"));
        assertCannotParse("1e5", "TM9");
        assertCannotParse("1", "TME");
        assertCannotParse("1,234", "TM9");
        assertCannotParse("1$", "TM$");
        assertCannotParse("1", "STM9");
        assertCannotParse("-1", "TMS");
        assertCannotParse("1,234", "AUTO");
        assertCannotParse("1234", "99|999");
    }
}
