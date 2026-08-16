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
 * How WIDE a FLOAT is rendered as text — TO_VARCHAR, and every checksum taken over one.
 *
 * <p>★ THE WIDTH GROWS WITH THE MAGNITUDE. Ten significant digits below 10, then one more per decade,
 * capped at fifteen. A single width fits neither end: {@code %.10g} matched live for small values and
 * dropped real digits from large ones.
 *
 * <p>★ A LADDER BUILT ON SQRT(2) CANNOT SEE THE CAP. That value's fifteenth significant digit is a zero,
 * so its fourteen- and fifteen-digit renderings are the same text once trailing zeros go. EXP(30) is what
 * separates them — {@code 10686474581524.5} keeps a digit that fourteen would have dropped — which is why
 * it is pinned here beside the ladder.
 *
 * <p>★ THE NOTATION FOLLOWS THE WIDTH. {@code %g} turns scientific once the exponent reaches the
 * precision, so fifteen digits from 1e5 up puts the plain range at an exponent of −4 through 14:
 * {@code 2.5::FLOAT * 1e14} stays plain and 1e15 is the first to leave.
 */
public class FloatTextWidthTest extends BaseDatabaseTest {

    private String text(final String expression) {
        final ResultSet rs = engine.executeQuery("SELECT TO_VARCHAR(" + expression + ")");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    private String ladder(final int exponent) {
        return text("SQRT(2) * 1e" + exponent);
    }

    @Test
    void belowTenItIsTenSignificantDigits() {
        assertEquals("1.414213562", text("SQRT(2)"));
        assertEquals("0.1414213562", ladder(-1));
        assertEquals("0.01414213562", ladder(-2));
        assertEquals("0.001414213562", ladder(-3));
        assertEquals("0.0001414213562", ladder(-4));
    }

    @Test
    void aSmallMagnitudeKeepsTenDigitsIntoScientificNotation() {
        assertEquals("1.414213562e-05", ladder(-5));
        assertEquals("1.414213562e-08", ladder(-8));
        assertEquals("1.414213562e-12", ladder(-12));
        assertEquals("1.414213562e-16", ladder(-16));
    }

    @Test
    void eachDecadeAddsADigit() {
        assertEquals("14.142135624", ladder(1));
        assertEquals("141.421356237", ladder(2));
        assertEquals("1414.213562373", ladder(3));
        assertEquals("14142.135623731", ladder(4));
    }

    @Test
    void theWidthStopsGrowingAtFifteen() {
        assertEquals("141421.35623731", ladder(5));
        assertEquals("1414213.5623731", ladder(6));
        assertEquals("14142135.623731", ladder(7));
        assertEquals("14142135623.731", ladder(10));
        assertEquals("14142135623731", ladder(13));
        assertEquals("141421356237310", ladder(14));
    }

    @Test
    void theFifteenthDigitIsRealWhereTheValueHasOne() {
        assertEquals("10686474581524.5", text("EXP(30)"));
    }

    @Test
    void theNotationLeavesPlainAtTenToTheFifteenth() {
        assertEquals("250000000000000", text("2.5::FLOAT * 1e14"));
        assertEquals("1e+15", text("1e15::FLOAT"));
        assertEquals("1e+16", text("1e16::FLOAT"));
        assertEquals("1.4142135623731e+15", ladder(15));
        assertEquals("1.4142135623731e+16", ladder(16));
    }

    @Test
    void theLowNotationBoundaryIsUnmoved() {
        assertEquals("0.0001", text("1e-4::FLOAT"));
        assertEquals("1e-05", text("1e-5::FLOAT"));
        assertEquals("2.5e-06", text("2.5::FLOAT / 1e6"));
    }

    @Test
    void aValueWithFewerDigitsThanTheWidthIsUnpadded() {
        assertEquals("2.5", text("2.5::FLOAT"));
        assertEquals("100", text("100.0::FLOAT"));
        assertEquals("2500000", text("2.5::FLOAT * 1e6"));
    }

    @Test
    void theFamiliarConstantsAreUnchanged() {
        assertEquals("3.141592654", text("PI()"));
        assertEquals("2.718281828", text("EXP(1)"));
        assertEquals("0.6931471806", text("LN(2)"));
        assertEquals("0.3333333333", text("1::FLOAT / 3"));
    }
}
