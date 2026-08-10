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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An arithmetic or concatenation column carries a KNOWN precision, scale or length, and a view
 * declares it. Writing {@code L} for an operand's leading digits, all live-measured:
 * {@code +} and {@code -} answer {@code max(L1,L2) + 1} leading digits over {@code max(s1,s2)} scale,
 * {@code *} adds both precisions and both scales, and {@code /} is driven by the DIVIDEND alone —
 * its scale gains six digits, which is why dividing two integers answers NUMBER(7,6).
 *
 * <p>MODULO keeps {@code max(s1,s2)} scale over {@code max(L1, L2, 2)} leading digits — the floor of
 * TWO leading digits is the whole of what made it look ruleless at first.
 */
public class BinaryOperationStaticTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE bo_s (a NUMBER(5,1), b NUMBER(3,2), f FLOAT,"
            + " s VARCHAR(4), t VARCHAR(9), d DATE, d2 DATE, i NUMBER(38,0),"
            + " bn BINARY(4), bn2 BINARY(9))");
    }

    /** The type a view declares for one expression over the base table. */
    private String declaredType(final String expression) {
        engine.execute("CREATE OR REPLACE VIEW bo_v AS SELECT " + expression + " AS c FROM bo_s");
        final ResultSet rs = engine.executeQuery("DESCRIBE VIEW bo_v");
        return cell(rs, rs.getRows().get(0), "type");
    }

    /** Addition and subtraction carry one extra leading digit for the carry. */
    @Test
    public void additionCarriesOneExtraLeadingDigit() {
        assertEquals("NUMBER(2,0)", declaredType("1 + 1"));
        assertEquals("NUMBER(2,0)", declaredType("1 - 1"));
        assertEquals("NUMBER(7,2)", declaredType("a + b"));
        assertEquals("NUMBER(7,2)", declaredType("a - b"));
        assertEquals("NUMBER(6,1)", declaredType("a + 1"));
    }

    /** Multiplication adds both precisions and both scales. */
    @Test
    public void multiplicationAddsPrecisionsAndScales() {
        assertEquals("NUMBER(2,0)", declaredType("2 * 3"));
        assertEquals("NUMBER(8,3)", declaredType("a * b"));
        assertEquals("NUMBER(6,1)", declaredType("a * 2"));
    }

    /** Division is driven by the dividend, whose scale gains six digits. */
    @Test
    public void divisionGainsSixDigitsOfScale() {
        assertEquals("NUMBER(7,6)", declaredType("7 / 2"));
        assertEquals("NUMBER(13,7)", declaredType("a / b"));
    }

    /** Precision saturates at 38 rather than overflowing. */
    @Test
    public void precisionSaturatesAtThirtyEight() {
        assertEquals("NUMBER(38,0)", declaredType("i + 1"));
        assertEquals("NUMBER(38,0)", declaredType("i * 1"));
        assertEquals("NUMBER(38,6)", declaredType("i / 1"));
    }

    /** FLOAT is contagious through the exact numerics. */
    @Test
    public void floatIsContagious() {
        assertEquals("FLOAT", declaredType("f + a"));
        assertEquals("FLOAT", declaredType("f * f"));
    }

    /** Concatenated lengths add, saturating at a VARCHAR's maximum. */
    @Test
    public void concatenationAddsLengths() {
        assertEquals("VARCHAR(2)", declaredType("'a' || 'b'"));
        assertEquals("VARCHAR(13)", declaredType("s || t"));
        assertEquals("VARCHAR(7)", declaredType("s || 'xyz'"));
    }

    /** A non-string operand converts to text at FULL WIDTH, in either order — its own width is lost. */
    @Test
    public void aConvertedOperandGivesTheFullWidthVarchar() {
        assertEquals("VARCHAR(16777216)", declaredType("a || s"));
        assertEquals("VARCHAR(16777216)", declaredType("s || a"));
        assertEquals("VARCHAR(16777216)", declaredType("d || s"));
        assertEquals("VARCHAR(16777216)", declaredType("s || d"));
    }

    /** Two binaries add their lengths the way two strings do. */
    @Test
    public void twoBinariesAddTheirLengths() {
        assertEquals("BINARY(8)", declaredType("bn || bn"));
        assertEquals("BINARY(13)", declaredType("bn || bn2"));
    }

    /** A DATE shifted by a number of days is still a DATE — and addition commutes. */
    @Test
    public void aShiftedDateIsStillADate() {
        assertEquals("DATE", declaredType("d + 1"));
        assertEquals("DATE", declaredType("d - 1"));
        assertEquals("DATE", declaredType("1 + d"));
        assertEquals("DATE", declaredType("d + i"));
        assertEquals("DATE", declaredType("d + a"));
    }

    /** A DATE difference is a count of days, nine digits wide. */
    @Test
    public void aDateDifferenceIsADayCount() {
        assertEquals("NUMBER(9,0)", declaredType("d - d2"));
    }

    /** A unary sign adds no digit of its own. */
    @Test
    public void aUnarySignAddsNoDigit() {
        assertEquals("NUMBER(5,1)", declaredType("-a"));
    }

    /** Modulo keeps the wider operand's leading digits over the wider scale. */
    @Test
    public void moduloKeepsTheWiderOperandsWidth() {
        assertEquals("NUMBER(6,2)", declaredType("a % b"));
        assertEquals("NUMBER(38,0)", declaredType("i % 1"));
        // Unlike addition, modulo adds no carry digit: a's own four leading digits are kept.
        assertEquals("NUMBER(5,1)", declaredType("a % 2"));
    }

    /** But never fewer than TWO leading digits, however narrow both operands are. */
    @Test
    public void moduloNeverHasFewerThanTwoLeadingDigits() {
        assertEquals("NUMBER(2,0)", declaredType("1 % 1"));
        assertEquals("NUMBER(3,1)", declaredType("1.5 % 1"));
    }
}
