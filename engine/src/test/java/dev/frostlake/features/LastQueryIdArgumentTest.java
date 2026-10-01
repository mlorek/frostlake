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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LAST_QUERY_ID's index (live-verified): a positive index counts from the session's first statement,
 * 0 and NULL name none, a numeric text is read as a number, and the index must be a constant no further
 * than 10,000 either way — both judged while the statement compiles, a non-constant index echoed as the
 * plan holds it.
 */
public class LastQueryIdArgumentTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(refused.getMessage());
    }

    private String answer(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private static String notConstant(final String found) {
        return "SQL compilation error:\nargument 1 to function LAST_QUERY_ID needs to be constant, found '"
            + found + "'";
    }

    @Test
    public void anIndexCountsFromEitherEndOfTheSession() {
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(1) IS NOT NULL, 'yes', 'no')"));
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(1) = LAST_QUERY_ID('1'), 'yes', 'no')"));
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(1) <> LAST_QUERY_ID(-1), 'yes', 'no')"));
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(-1) = LAST_QUERY_ID('-1'), 'yes', 'no')"));
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(-1) = LAST_QUERY_ID(-1.0), 'yes', 'no')"));
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(-1) = LAST_QUERY_ID(-(1)), 'yes', 'no')"));
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(1) = LAST_QUERY_ID(+1), 'yes', 'no')"));
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(1) = LAST_QUERY_ID(1E0), 'yes', 'no')"));
    }

    @Test
    public void zeroAndNullNameNoStatement() {
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(0) IS NULL, 'yes', 'no')"));
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(-0) IS NULL, 'yes', 'no')"));
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(NULL) IS NULL, 'yes', 'no')"));
    }

    @Test
    public void anIndexPastTenThousandIsRefusedWhileCompiling() {
        final String exceeds = "SQL compilation error:\nValue for parameter 1 exceeds maximum allowable value (10,000).";
        assertEquals(exceeds, refusal("SELECT LAST_QUERY_ID(10001)"));
        assertEquals(exceeds, refusal("SELECT LAST_QUERY_ID(-10001)"));
        assertEquals(exceeds, refusal("SELECT LAST_QUERY_ID('10001')"));
        assertEquals(exceeds, refusal("SELECT LAST_QUERY_ID(99999999999999999999)"));
        assertEquals(exceeds, refusal("SELECT 1, LAST_QUERY_ID(-10001)"));
        assertEquals(exceeds, refusal("SELECT LAST_QUERY_ID(10001) FROM (SELECT 1 AS x) WHERE FALSE"));
    }

    @Test
    public void aNonConstantIndexIsRefusedAsThePlanHoldsIt() {
        assertEquals(notConstant("CAST(1.5 AS NUMBER(18,0))"), refusal("SELECT LAST_QUERY_ID(1.5)"));
        assertEquals(notConstant("CAST(-1.5 AS NUMBER(18,0))"), refusal("SELECT LAST_QUERY_ID(-1.5)"));
        assertEquals(notConstant("TO_NUMBER('abc', 18, 0)"), refusal("SELECT LAST_QUERY_ID('abc')"));
        assertEquals(notConstant("1 + 0"), refusal("SELECT LAST_QUERY_ID(1 + 0)"));
        assertEquals(notConstant("10001 - 1"), refusal("SELECT LAST_QUERY_ID(10001 - 1)"));
        assertEquals(notConstant("CAST(1 AS NUMBER(38,0))"), refusal("SELECT LAST_QUERY_ID(1::INT)"));
        assertEquals(notConstant("CAST(1 AS NUMBER(38,0))"), refusal("SELECT LAST_QUERY_ID(CAST(1 AS INT))"));
        assertEquals(notConstant("SYSTEM$NULL_TO_FIXED(null)"), refusal("SELECT LAST_QUERY_ID(NULL::INT)"));
        // Through RESULT_SCAN the call keeps its own refusal.
        assertEquals(notConstant("CAST(1.5 AS NUMBER(18,0))"),
            refusal("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(1.5)))"));
    }

    /**
     * A conversion the plan carries out while the statement compiles is a constant index: of a text constant to a
     * whole number, of a number or a text to a FLOAT, and a product with a factor of one where the kept factor already
     * has the product's type.
     */
    @Test
    public void aConversionOfAConstantIsAConstantIndex() {
        final String[][] cells = {
            {"TO_NUMBER('1')", "1"}, {"TO_NUMBER('-1')", "-1"}, {"TO_NUMBER('1.4')", "1"}, {"TO_NUMBER('1.5')", "2"},
            {"TO_NUMBER('1', 10, 0)", "1"}, {"TO_NUMBER('1', 18)", "1"}, {"TO_DECIMAL('1')", "1"},
            {"TO_NUMERIC('1')", "1"}, {"TRY_TO_NUMBER('1')", "1"}, {"'1'::NUMBER", "1"}, {"'1'::INT", "1"},
            {"CAST('1' AS INT)", "1"}, {"TRY_CAST('1' AS INT)", "1"}, {"TO_NUMBER(' 1 ')", "1"},
            {"TO_NUMBER('1' || '')", "1"}, {"TO_NUMBER(UPPER('1'))", "1"}, {"TO_NUMBER(LOWER('1'))", "1"},
            {"TO_NUMBER(CONCAT('1', ''))", "1"}, {"CONCAT('1', '')", "1"}, {"TO_NUMBER('1e0')", "1"},
            {"1.0::FLOAT", "1"}, {"'1'::FLOAT", "1"}, {"1::FLOAT", "1"}, {"CAST(1 AS FLOAT)", "1"},
            {"TO_DOUBLE('1')", "1"}, {"TO_DOUBLE(1)", "1"}, {"1.5::FLOAT", "2"}, {"TO_NUMBER(1.5::FLOAT)", "2"},
            {"TO_NUMBER('1'::FLOAT)", "1"}, {"TO_NUMBER(TO_DOUBLE('2'))", "2"}, {"CAST(1.5::FLOAT AS INT)", "2"},
            {"TO_NUMBER('1')::FLOAT", "1"}, {"TO_NUMBER('1', 10, 1)::FLOAT", "1"},
            {"1 * TO_NUMBER('2')", "2"}, {"TO_NUMBER('1') * TO_NUMBER('1')", "1"}, {"TO_NUMBER('-1') * 1", "-1"},
            {"TO_NUMBER(' 2 ') * 1", "2"},
        };
        for (final String[] cell : cells) {
            assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(" + cell[0] + ") = LAST_QUERY_ID(" + cell[1]
                + "), 'yes', 'no')"), cell[0]);
        }
        // A TRY conversion that fails is its target's NULL, and a zero names no statement.
        for (final String index : new String[] {"TRY_TO_NUMBER('abc')", "TRY_CAST('abc' AS INT)",
                "TRY_TO_DOUBLE('abc')", "TO_NUMBER('0')", "TO_NUMBER('0') * 7", "TRY_TO_NUMBER('abc') * 1"}) {
            assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(" + index + ") IS NULL, 'yes', 'no')"), index);
        }
        final String exceeds = "SQL compilation error:\nValue for parameter 1 exceeds maximum allowable value (10,000).";
        assertEquals(exceeds, refusal("SELECT LAST_QUERY_ID(TO_NUMBER('10001'))"));
        assertEquals(exceeds, refusal("SELECT LAST_QUERY_ID(TO_NUMBER('-10001'))"));
        assertEquals(exceeds, refusal("SELECT LAST_QUERY_ID(TO_NUMBER('1' || '0001'))"));
    }

    /**
     * A FLOAT constant is a constant index only where it converts to NUMBER(18,0): NaN, the infinities and anything
     * from 1E18 on either way are refused as not constant, their conversion echoed.
     */
    @Test
    public void aFloatConstantIsAnIndexOnlyWhereItConvertsToEighteenDigits() {
        final String[][] refused = {
            {"'NaN'::FLOAT", "CAST(NaN AS NUMBER(18,0))"}, {"TO_DOUBLE('NaN')", "CAST(NaN AS NUMBER(18,0))"},
            {"TRY_TO_DOUBLE('NaN')", "CAST(NaN AS NUMBER(18,0))"}, {"TRY_CAST('NaN' AS FLOAT)", "CAST(NaN AS NUMBER(18,0))"},
            {"'inf'::FLOAT", "CAST(Infinity AS NUMBER(18,0))"}, {"'-inf'::FLOAT", "CAST(-Infinity AS NUMBER(18,0))"},
            {"TO_DOUBLE('-Infinity')", "CAST(-Infinity AS NUMBER(18,0))"}, {"1e18::FLOAT", "CAST(1.0E18 AS NUMBER(18,0))"},
            {"999999999999999999::FLOAT", "CAST(1.0E18 AS NUMBER(18,0))"}, {"'1e18'::FLOAT", "CAST(1.0E18 AS NUMBER(18,0))"},
            {"'-1e18'::FLOAT", "CAST(-1.0E18 AS NUMBER(18,0))"}, {"1e20::FLOAT", "CAST(1.0E20 AS NUMBER(18,0))"},
            {"1.5e18::FLOAT", "CAST(1.5E18 AS NUMBER(18,0))"}, {"TO_DOUBLE(1e20)", "CAST(1.0E20 AS NUMBER(18,0))"},
            {"TO_DOUBLE('123456789012345678901')", "CAST(1.2345678901234568E20 AS NUMBER(18,0))"},
            {"+'NaN'::FLOAT", "CAST(NaN AS NUMBER(18,0))"}, {"ABS('NaN'::FLOAT)", "CAST(ABS(NaN) AS NUMBER(18,0))"},
            {"'NaN'::FLOAT + 1", "CAST(NaN + 1.0 AS NUMBER(18,0))"},
            {"TO_NUMBER(1e20::FLOAT, 18, 0)", "CAST(1.0E20 AS NUMBER(18,0))"},
        };
        for (final String[] cell : refused) {
            assertEquals(notConstant(cell[1]), refusal("SELECT LAST_QUERY_ID(" + cell[0] + ")"), cell[0]);
        }
        final String exceeds = "SQL compilation error:\nValue for parameter 1 exceeds maximum allowable value (10,000).";
        for (final String index : new String[] {"9.99e17::FLOAT", "999999999999999872::FLOAT", "'-9.99e17'::FLOAT",
                "1e17::FLOAT", "10000.5::FLOAT", "TO_NUMBER(1e20::FLOAT)", "1e20::FLOAT::INT"}) {
            assertEquals(exceeds, refusal("SELECT LAST_QUERY_ID(" + index + ")"), index);
        }
        for (final String index : new String[] {"TO_DOUBLE('1e-300')", "TO_DOUBLE('-0')", "10000.4::FLOAT",
                "'-10000.4'::FLOAT"}) {
            assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(" + index + ") IS NULL, 'yes', 'no')"), index);
        }
    }

    /**
     * A plus before a constant is that constant, typed as the plus is; before anything else it stays, named
     * UNARY PLUS. A minus before a plus is an operator of its own.
     */
    @Test
    public void aPlusBeforeAConstantIsThatConstant() {
        final String[][] cells = {
            {"+TO_NUMBER('1')", "1"}, {"+'1'::INT", "1"}, {"+1.5::FLOAT", "2"}, {"+CONCAT('1', '')", "1"},
            {"+'1'", "1"}, {"+'1.5'", "2"}, {"+' 1 '", "1"}, {"+UPPER('1')", "1"}, {"+('1' || '')", "1"},
            {"+(+1)", "1"}, {"+(-1)", "-1"}, {"-(-1)", "1"}, {"+(1)", "1"}, {"1 * +TO_NUMBER('2')", "2"},
        };
        for (final String[] cell : cells) {
            assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(" + cell[0] + ") = LAST_QUERY_ID(" + cell[1]
                + "), 'yes', 'no')"), cell[0]);
        }
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(+TRY_TO_NUMBER('abc')) IS NULL, 'yes', 'no')"));
        assertEquals("yes", answer("SELECT IFF(LAST_QUERY_ID(+'1e2') IS NOT DISTINCT FROM LAST_QUERY_ID(100), 'yes', 'no')"));
        final String[][] refused = {
            {"-(+1)", "NEGATE(1)"}, {"-(+1.5)", "CAST(NEGATE(1.5) AS NUMBER(18,0))"}, {"-(+(-1))", "NEGATE(-1)"},
            {"-(+TO_NUMBER('1'))", "NEGATE(1)"}, {"+(-TO_NUMBER('1'))", "UNARY PLUS(NEGATE(1))"},
            {"+1.5", "CAST(1.5 AS NUMBER(18,0))"}, {"+TO_NUMBER('1', 10, 1)", "CAST(1 AS NUMBER(18,0))"},
            {"+(TO_NUMBER('1') * 1)", "UNARY PLUS(CAST(1 AS NUMBER(38,0)))"},
            {"+(-1 * 1)", "UNARY PLUS(CAST(-1 AS NUMBER(2,0)))"}, {"+(1 * 2)", "UNARY PLUS(CAST(2 AS NUMBER(2,0)))"},
            {"+(1.5 * 1)", "CAST(UNARY PLUS(CAST(1.5 AS NUMBER(3,1))) AS NUMBER(18,0))"},
            {"+(1.5::FLOAT * 1)", "CAST(UNARY PLUS(1.5 * 1.0) AS NUMBER(18,0))"},
            {"+'abc'", "CAST(UNARY PLUS(CAST('abc' AS FLOAT)) AS NUMBER(18,0))"},
            {"+TRIM('1')", "CAST(UNARY PLUS(CAST(TRIM('1') AS FLOAT)) AS NUMBER(18,0))"},
            {"+NULL", "UNARY PLUS(SYSTEM$NULL_TO_FIXED(null))"},
            {"+TO_NUMBER('1') * 1", "CAST(1 AS NUMBER(38,0))"}, {"+1 * 1", "CAST(1 AS NUMBER(3,0))"},
            {"+(-1) * 1", "CAST(-1 AS NUMBER(3,0))"}, {"+12 * 1", "CAST(12 AS NUMBER(3,0))"},
            {"+1.5 * 1", "CAST(CAST(1.5 AS NUMBER(4,1)) AS NUMBER(18,0))"},
            {"1 * +1.5", "CAST(CAST(1.5 AS NUMBER(4,1)) AS NUMBER(18,0))"},
            {"+1.25 * 1", "CAST(CAST(1.25 AS NUMBER(5,2)) AS NUMBER(18,0))"},
            {"+TO_NUMBER('1.5', 10, 1) * 1", "CAST(CAST(1.5 AS NUMBER(11,1)) AS NUMBER(18,0))"},
            {"+CAST(1.5 AS NUMBER(10,1)) * 1",
                "CAST(CAST(UNARY PLUS(CAST(1.5 AS NUMBER(10,1))) AS NUMBER(11,1)) AS NUMBER(18,0))"},
            {"+1.5 + 0", "CAST(1.5 + (CAST(0 AS NUMBER(3,1))) AS NUMBER(18,0))"},
            {"+'1' * 1", "CAST(1.0 * 1.0 AS NUMBER(18,0))"}, {"+1.5::FLOAT + 1", "CAST(1.5 + 1.0 AS NUMBER(18,0))"},
        };
        for (final String[] cell : refused) {
            assertEquals(notConstant(cell[1]), refusal("SELECT LAST_QUERY_ID(" + cell[0] + ")"), cell[0]);
        }
        assertEquals("SQL compilation error:\nValue for parameter 1 exceeds maximum allowable value (10,000).",
            refusal("SELECT LAST_QUERY_ID(+TO_NUMBER('10001'))"));
    }

    /**
     * A text constant beside a number reads as the number it spells: in a product or a quotient eighteen digits wide
     * at the scale of its value, in a sum, a difference or a remainder at its own width; a text with a blank about it
     * or with no number reads as NUMBER(18,5), and two texts, or a text beside a FLOAT, meet as FLOAT.
     */
    @Test
    public void aTextConstantReadsAsTheNumberItSpells() {
        final String[][] refused = {
            {"'1.50' * 1", "CAST(CAST(1.5 AS NUMBER(19,1)) AS NUMBER(18,0))"},
            {"1 * '1.50'", "CAST(CAST(1.5 AS NUMBER(19,1)) AS NUMBER(18,0))"},
            {"'-1.50' * 1", "CAST(CAST(-1.5 AS NUMBER(19,1)) AS NUMBER(18,0))"},
            {"'01.10' * 1", "CAST(CAST(1.1 AS NUMBER(19,1)) AS NUMBER(18,0))"},
            {"'1.0' * 1", "CAST(1 AS NUMBER(19,0))"}, {"'0.0' * 1", "CAST(0 AS NUMBER(19,0))"},
            {"'1e2' * 1", "CAST(100 AS NUMBER(19,0))"}, {"'1E2' * 1", "CAST(100 AS NUMBER(19,0))"},
            {"'1.5e1' * 1", "CAST(15 AS NUMBER(19,0))"}, {"'15e-1' * 1", "CAST(CAST(1.5 AS NUMBER(19,1)) AS NUMBER(18,0))"},
            {"'1e-1' * 1", "CAST(CAST(0.1 AS NUMBER(19,1)) AS NUMBER(18,0))"}, {"'0e0' * 5", "CAST(0 AS NUMBER(19,0))"},
            {"'1e20' * 1", "CAST(100000000000000000000 AS NUMBER(22,0))"},
            {"'1234567890123456789' * 1", "CAST(1234567890123456789 AS NUMBER(20,0))"},
            {"'1e2' * 2", "100 * 2"}, {"'1.50' * 1.0", "CAST(CAST(1.5 AS NUMBER(19,1)) AS NUMBER(18,0))"},
            {"'1.50' * TO_NUMBER('1')", "CAST(CAST(1.5 AS NUMBER(38,1)) AS NUMBER(37,0))"},
            {"' 1 ' * 1", "CAST(CAST(1 AS NUMBER(19,5)) AS NUMBER(18,0))"},
            {"'1 ' * 1", "CAST(CAST(1 AS NUMBER(19,5)) AS NUMBER(18,0))"},
            {"' 1.5 ' * 1", "CAST(CAST(1.5 AS NUMBER(19,5)) AS NUMBER(18,0))"},
            {"' 1.123456 ' * 1", "CAST(CAST(1.12346 AS NUMBER(19,5)) AS NUMBER(18,0))"},
            {"' 1 ' * 2", "CAST(CAST(2 AS NUMBER(19,5)) AS NUMBER(18,0))"},
            {"' 0 ' * 5", "CAST(CAST(0 AS NUMBER(19,5)) AS NUMBER(18,0))"},
            {"' 99999999999999 ' * 1",
                "CAST(CAST(TO_NUMBER(' 99999999999999 ', 18, 5) AS NUMBER(19,5)) AS NUMBER(18,0))"},
            {"'abc' * 1", "CAST(CAST(TO_NUMBER('abc', 18, 5) AS NUMBER(19,5)) AS NUMBER(18,0))"},
            {"'' * 1", "CAST(CAST(TO_NUMBER('', 18, 5) AS NUMBER(19,5)) AS NUMBER(18,0))"},
            {"'abc' * 2", "CAST((TO_NUMBER('abc', 18, 5)) * 2 AS NUMBER(18,0))"},
            {"'1e2' * '1'", "CAST(100.0 * 1.0 AS NUMBER(18,0))"}, {"'1e2' * '1e1'", "CAST(100.0 * 10.0 AS NUMBER(18,0))"},
            {"'1e2' * 1.5::FLOAT", "CAST(100.0 * 1.5 AS NUMBER(18,0))"},
            {"' 1.123456 ' * 1.5::FLOAT", "CAST(1.123456 * 1.5 AS NUMBER(18,0))"},
            {"'1.50' / 1", "CAST((CAST(1.5 AS NUMBER(24,7))) / 1 AS NUMBER(18,0))"},
            {"1 / '1.50'", "CAST((CAST(1 AS NUMBER(9,7))) / 1.5 AS NUMBER(18,0))"},
            {"' 1 ' / 2", "CAST((CAST(1 AS NUMBER(24,11))) / 2 AS NUMBER(18,0))"},
            {"'abc' / 1", "CAST((CAST(TO_NUMBER('abc', 18, 5) AS NUMBER(24,11))) / 1 AS NUMBER(18,0))"},
            {"'1e2' / 1", "CAST((CAST(100 AS NUMBER(24,6))) / 1 AS NUMBER(18,0))"},
            {"'1.50' / '2'", "CAST(1.5 / 2.0 AS NUMBER(18,0))"}, {"'2' / 1.5::FLOAT", "CAST(2.0 / 1.5 AS NUMBER(18,0))"},
            {"'1.50' + 1", "CAST(1.5 + (CAST(1 AS NUMBER(3,1))) AS NUMBER(18,0))"},
            {"1 + '1.50'", "CAST((CAST(1 AS NUMBER(3,1))) + 1.5 AS NUMBER(18,0))"},
            {"'1.50' - 1", "CAST(1.5 - (CAST(1 AS NUMBER(3,1))) AS NUMBER(18,0))"},
            {"'1.50' + 0.25", "CAST(1.5 + 0.25 AS NUMBER(18,0))"}, {"'1.50' + 1.25", "CAST(1.5 + 1.25 AS NUMBER(18,0))"},
            {"'0.05' + 1", "CAST(0.05 + (CAST(1 AS NUMBER(4,2))) AS NUMBER(18,0))"},
            {"'12.5' + 1", "CAST(12.5 + (CAST(1 AS NUMBER(3,1))) AS NUMBER(18,0))"},
            {"'1e-1' + 1", "CAST(0.1 + (CAST(1 AS NUMBER(3,1))) AS NUMBER(18,0))"},
            {"'1' + 1.5", "CAST(1 + 1.5 AS NUMBER(18,0))"}, {"'1' + 1", "1 + 1"}, {"1 - '1'", "1 - 1"},
            {"'1e2' - 1", "100 - 1"},
            {"'1.50' + TO_NUMBER('1')", "CAST(1.5 + (CAST(1 AS NUMBER(38,1))) AS NUMBER(37,0))"},
            {"' 1 ' + 1", "CAST(1 + (CAST(1 AS NUMBER(18,5))) AS NUMBER(18,0))"},
            {"' 1.5 ' + 1", "CAST(1.5 + (CAST(1 AS NUMBER(18,5))) AS NUMBER(18,0))"},
            {"'abc' + 1", "CAST((TO_NUMBER('abc', 18, 5)) + (CAST(1 AS NUMBER(18,5))) AS NUMBER(18,0))"},
            {"'1.5' + '1'", "CAST(1.5 + 1.0 AS NUMBER(18,0))"},
            {"'1.50' % 1", "CAST(1.5 % (CAST(1 AS NUMBER(3,1))) AS NUMBER(18,0))"},
        };
        for (final String[] cell : refused) {
            assertEquals(notConstant(cell[1]), refusal("SELECT LAST_QUERY_ID(" + cell[0] + ")"), cell[0]);
        }
    }

    @Test
    public void aBooleanIndexIsAnArgumentTypeAtTheCall() {
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'LAST_QUERY_ID': (BOOLEAN)", refusal("SELECT LAST_QUERY_ID(TRUE)"));
    }
}
