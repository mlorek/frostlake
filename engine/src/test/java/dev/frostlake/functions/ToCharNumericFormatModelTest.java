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
 * TO_CHAR of a number prints its model element by element (live-verified): hexadecimal digits,
 * exponents, the zero-blanking B, the percentage, literals, the text-minimal forms, and where the sign,
 * the dollar and MI land — each shown inside brackets so the padding is part of the answer.
 */
public class ToCharNumericFormatModelTest extends BaseDatabaseTest {

    private String printed(final String value, final String model) {
        return String.valueOf(engine.executeQuery("SELECT '[' || TO_CHAR(" + value + ", '"
            + model.replace("'", "''") + "') || ']'").getRows().get(0).getValue(0));
    }

    private String refusal(final String model) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TO_CHAR(1, '" + model + "')");
            }
        });
        return String.valueOf(refused.getMessage());
    }

    @Test
    public void hexadecimalDigitsTakeNoSignPlace() {
        assertEquals("[FF]", printed("255", "XX"));
        assertEquals("[ F]", printed("15", "XX"));
        assertEquals("[##]", printed("256", "XX"));
        assertEquals("[ff]", printed("255", "xx"));
        assertEquals("[00FF]", printed("255", "0XXX"));
        assertEquals("[0a]", printed("10", "0x"));
        assertEquals("[ 0]", printed("0", "XX"));
        assertEquals("[FF]", printed("255", "FMXXXX"));
        assertEquals("[##]", printed("255.7", "XX"));
    }

    @Test
    public void aNegativeHexadecimalIsItsComplementUnlessASignIsWritten() {
        assertEquals("[FF]", printed("-1", "XX"));
        assertEquals("[+FF]", printed("255", "SXX"));
        assertEquals("[-FF]", printed("-255", "SXX"));
        assertEquals("[FF ]", printed("255", "XXMI"));
        assertEquals("[FF-]", printed("-255", "XXMI"));
    }

    @Test
    public void anExponentNormalisesTheValue() {
        assertEquals("[ 1.5E+00]", printed("1.5", "9.9EEEE"));
        assertEquals("[ 1.5E0   ]", printed("1.5", "9.9EE"));
        assertEquals("[ 1.5E+0]", printed("1.5", "9.9EEE"));
        assertEquals("[-1.23E+02]", printed("-123.456", "9.99EEEE"));
        assertEquals("[ 0. E+00]", printed("0", "9.9EEEE"));
        assertEquals("[ 1.2E-04]", printed("0.00012", "9.9EEEE"));
        assertEquals("[ 1.5e+00]", printed("1.5", "9.9eeee"));
        assertEquals("[ 12.3E+03]", printed("12345", "99.9EEEE"));
        assertEquals("[1.5E+00]", printed("1.5", "FM9.9EEEE"));
        assertEquals("[ 2E+00]", printed("1.5", "9EEEE"));
        assertEquals("[ 1. E+##]", printed("1e120", "9.9EEEE"));
        assertEquals("[+1.5E+00]", printed("1.5", "S9.9EEEE"));
        assertEquals("[1.5E+00-]", printed("-1.5", "9.9EEEEMI"));
        assertEquals("[ 1E21  ]", printed("1e21", "9EE"));
        assertEquals("[-1E-3  ]", printed("-0.001", "9EE"));
        assertEquals("[ 1.2E+003]", printed("1234", "9.9EEEEE"));
    }

    @Test
    public void blankPrintsAZeroWholePartAsSpaces() {
        assertEquals("[ 1]", printed("1", "B9"));
        assertEquals("[  ]", printed("0", "B9"));
        assertEquals("[  .5]", printed("0.5", "B9.9"));
        assertEquals("[ -.5]", printed("-0.5", "B9.9"));
        assertEquals("[   .  ]", printed("0", "B99.99"));
        assertEquals("[]", printed("0", "FMB9"));
        assertEquals("[ 5]", printed("5", "9B"));
        assertEquals("[  $]", printed("0", "B$9"));
    }

    @Test
    public void percentMultipliesByAHundred() {
        assertEquals("[  5%]", printed("0.05", "99%"));
        assertEquals("[25%]", printed("0.25", "TM9%"));
        assertEquals("[ #.#%]", printed("0.5", "9.9%"));
        assertEquals("[ -5%]", printed("-0.05", "99%"));
        assertEquals("[% ##]", printed("1", "%99"));
        assertEquals("[5%]", printed("0.05", "FM99%"));
        assertEquals("[ 12.3%]", printed("0.123", "99.9%"));
    }

    @Test
    public void literalsPrintAsWrittenAndTheSignFloatsPastThem() {
        assertEquals("[x 1]", printed("1", "FX\"x\"9"));
        assertEquals("[x 1]", printed("1", "\"x\"9"));
        assertEquals("[ 1x]", printed("1", "9\"x\""));
        assertEquals("[ 1x2]", printed("12", "9\"x\"9"));
        assertEquals("[x-1]", printed("-1", "\"x\"9"));
        assertEquals("[ab1]", printed("1", "FM\"ab\"9"));
        assertEquals("[ - 1]", printed("1", "9-9"));
        assertEquals("[ --1]", printed("-1", "9-9"));
        assertEquals("[( 1)]", printed("1", "(9)"));
        assertEquals("[ 1  2]", printed("12", "9  9"));
        assertEquals("[ 1]", printed("1", "FM9 9"));
        assertEquals("[ 1]", printed("1", "_9"));
        assertEquals("[,]", printed("1", ","));
        assertEquals("[]", printed("1", ""));
    }

    @Test
    public void textMinimalFormsNeverPad() {
        assertEquals("[1]", printed("1", "FXTM9"));
        assertEquals("[1.5]", printed("1.5", "TM9"));
        assertEquals("[-1.5]", printed("-1.5", "TM"));
        assertEquals("[1.5E0]", printed("1.5", "TME"));
        assertEquals("[1.2345E4]", printed("12345", "TME"));
        assertEquals("[1E2]", printed("100", "TME"));
        assertEquals("[0E0]", printed("0", "TME"));
        assertEquals("[1E-1]", printed("0.1", "TME"));
        assertEquals("[1.5e0]", printed("1.5", "tme"));
        assertEquals("[$12.5]", printed("12.5", "$TM9"));
        assertEquals("[1x]", printed("1", "TM9\"x\""));
        assertEquals("[1000000000000000000000000000000]", printed("1e30", "TM"));
        assertEquals("[1E300]", printed("1e300::FLOAT", "TM"));
        assertEquals("[1.5]", printed("1.5::FLOAT", "TM"));
    }

    @Test
    public void theSignAndTheDollarLandWhereLivePutsThem() {
        assertEquals("[12+]", printed("12", "99S"));
        assertEquals("[- 7]", printed("-7", "MI99"));
        assertEquals("[ 7-]", printed("-7", "99MI"));
        assertEquals("[ -$7]", printed("-7", "$99"));
        assertEquals("[-$7]", printed("-7", "FM$99"));
        assertEquals("[ 12$]", printed("12", "99$"));
        assertEquals("[ 1$2]", printed("12", "9$9"));
        assertEquals("[1+2]", printed("12", "9S9"));
        assertEquals("[-##]", printed("-123", "99"));
        assertEquals("[##-]", printed("-123", "99MI"));
        assertEquals("[ -1,234]", printed("-1234", "99,999"));
        assertEquals("[ 00,012]", printed("12", "00,000"));
        assertEquals("[-0012]", printed("-12", "0999"));
    }

    @Test
    public void aModelThatIsNoneIsRefusedAsAnOutputModel() {
        assertEquals("Bad output format model '9Q' for FIXED: invalid numeric format keyword: 'Q'", refusal("9Q"));
        assertEquals("Bad output format model '9PR' for FIXED: invalid numeric format keyword: 'PR'", refusal("9PR"));
        assertEquals("Bad output format model '99SS' for FIXED: format element occurs more than once: 'S'",
            refusal("99SS"));
        assertEquals("Bad output format model 'S9MI' for FIXED: format element conflicts with preceding element(s): 'MI'",
            refusal("S9MI"));
        assertEquals("Bad output format model '9X' for FIXED: cannot mix hexadecimal and decimal format elements: '9X'",
            refusal("9X"));
        assertEquals("Bad output format model 'FM' for FIXED: no digit format elements in a numeric format: 'FM'",
            refusal("FM"));
        assertEquals("Bad output format model '9+' for FIXED: invalid character in the format string: '+'",
            refusal("9+"));
    }
}
