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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A column's DEFAULT must be COERCIBLE to the column's type at CREATE TABLE, and the rule is
 * directional rather than symmetric: a STRING literal is refused by the numeric and datetime families
 * but taken by VARCHAR, BOOLEAN and VARIANT, while a NUMERIC literal is the exact mirror image. So
 * {@code BOOLEAN DEFAULT 'true'} and {@code DATE DEFAULT 7} are both legal, and
 * {@code NUMBER DEFAULT '7'} and {@code VARCHAR DEFAULT 7} are both refused.
 *
 * <p>TRUE, FALSE, NULL and a hex literal are taken by every family, and OBJECT, ARRAY and GEOGRAPHY
 * judge nothing at all. A BINARY column is the odd one out — it takes no default whatsoever, and says
 * so in its own sentence.
 *
 * <p>All live-measured. Two things are deliberately NOT asserted here: live also judges a COMPUTED
 * default by its expression type (it refuses {@code NUMBER DEFAULT UPPER('a')} while taking
 * {@code NUMBER DEFAULT LENGTH('abc')}), and ALTER TABLE ADD COLUMN judges the same defaults far more
 * strictly in the INSERT surface's vocabulary. Both are tracked as their own gaps.
 */
public class ColumnDefaultTypeMatchTest extends BaseDatabaseTest {

    private String mismatch(final String column) {
        return "SQL compilation error:\nDefault value data type does not match data type for column "
            + column;
    }

    private void assertRefused(final String columnDef, final String expected) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE OR REPLACE TABLE dft_t (" + columnDef + ")");
            }
        }, columnDef);
        assertEquals(expected, ex.getMessage(), columnDef);
    }

    private void assertAccepted(final String columnDef) {
        engine.execute("CREATE OR REPLACE TABLE dft_t (" + columnDef + ")");
    }

    /** The numeric and datetime families refuse a string default. */
    @Test
    public void aStringDefaultIsRefusedByTheNumericAndDatetimeFamilies() {
        assertRefused("c NUMBER(5,1) DEFAULT 'abc'", mismatch("C"));
        assertRefused("c NUMBER(5,1) DEFAULT '7'", mismatch("C"));
        assertRefused("c FLOAT DEFAULT '7'", mismatch("C"));
        assertRefused("c DATE DEFAULT '2026-01-01'", mismatch("C"));
        assertRefused("c TIME DEFAULT '12:00:00'", mismatch("C"));
        assertRefused("c TIMESTAMP_NTZ DEFAULT '2026-01-01 00:00:00'", mismatch("C"));
    }

    /** And VARCHAR, BOOLEAN and VARIANT refuse a numeric one. */
    @Test
    public void aNumericDefaultIsRefusedByTheStringFamilies() {
        assertRefused("c VARCHAR(9) DEFAULT 7", mismatch("C"));
        assertRefused("c BOOLEAN DEFAULT 1", mismatch("C"));
        assertRefused("c VARIANT DEFAULT 7", mismatch("C"));
    }

    /** Each family takes the literal kind the other refuses. */
    @Test
    public void eachFamilyTakesTheOtherKind() {
        assertAccepted("c NUMBER(5,1) DEFAULT 7");
        assertAccepted("c DATE DEFAULT 7");
        assertAccepted("c TIME DEFAULT 7");
        assertAccepted("c VARCHAR(9) DEFAULT 'z'");
        assertAccepted("c BOOLEAN DEFAULT 'true'");
        assertAccepted("c VARIANT DEFAULT 'z'");
    }

    /** TRUE, FALSE and NULL cross every family. */
    @Test
    public void booleanAndNullDefaultsCrossEveryFamily() {
        assertAccepted("c NUMBER(5,1) DEFAULT TRUE");
        assertAccepted("c DATE DEFAULT TRUE");
        assertAccepted("c VARCHAR(9) DEFAULT FALSE");
        assertAccepted("c NUMBER(5,1) DEFAULT NULL");
        assertAccepted("c DATE DEFAULT NULL");
        assertAccepted("c VARCHAR(9) DEFAULT NULL");
    }

    /** The semi-structured and geospatial families judge nothing. */
    @Test
    public void objectArrayAndGeographyJudgeNothing() {
        assertAccepted("c OBJECT DEFAULT 7");
        assertAccepted("c ARRAY DEFAULT 7");
        assertAccepted("c GEOGRAPHY DEFAULT 7");
    }

    /** A BINARY column takes no default at all, and says so in its own sentence. */
    @Test
    public void aBinaryColumnTakesNoDefaultAtAll() {
        final String refusal = "SQL compilation error: Default values are not allowed"
            + " on 'BINARY' columns.";
        assertRefused("c BINARY(4) DEFAULT X'AB'", refusal);
        assertRefused("c BINARY(4) DEFAULT 'abc'", refusal);
        assertRefused("c BINARY(4) DEFAULT NULL", refusal);
    }

    /** A cast is the way to spell what the bare literal cannot. */
    @Test
    public void aCastCarriesTheLiteralAcross() {
        assertAccepted("c DATE DEFAULT '2026-01-01'::DATE");
        assertAccepted("c NUMBER DEFAULT '7'::NUMBER");
        assertAccepted("c VARCHAR(9) DEFAULT 7::VARCHAR");
    }

    /** A length that overflows the declared one is NOT a DDL-time concern. */
    @Test
    public void anOverlongStringDefaultIsAcceptedAtDdlTime() {
        assertAccepted("c VARCHAR(2) DEFAULT 'toolong'");
    }

    /** A COMPUTED default is judged by the expression's type, on the same table as a literal's. */
    @Test
    public void aComputedDefaultIsJudgedByItsExpressionType() {
        assertRefused("c NUMBER DEFAULT UPPER('a')", mismatch("C"));
        assertRefused("c NUMBER DEFAULT 'a' || 'b'", mismatch("C"));
        assertAccepted("c NUMBER DEFAULT LENGTH('abc')");
        assertAccepted("c NUMBER DEFAULT 1 + 1");
    }

    /** The mirror direction: a string column refuses a computed NUMBER. */
    @Test
    public void aStringColumnRefusesAComputedNumber() {
        assertRefused("c VARCHAR(9) DEFAULT 1 + 1", mismatch("C"));
        assertRefused("c VARCHAR(9) DEFAULT LENGTH('abc')", mismatch("C"));
        assertAccepted("c VARCHAR(9) DEFAULT UPPER('a')");
        assertAccepted("c VARCHAR(9) DEFAULT 'a' || 'b'");
    }

    /** A cast retypes the expression, so it lands on the other side of the same table. */
    @Test
    public void aCastRetypesTheDefault() {
        assertAccepted("c NUMBER DEFAULT '7'::NUMBER");
        assertRefused("c NUMBER DEFAULT 7::VARCHAR", mismatch("C"));
        assertAccepted("c VARCHAR(9) DEFAULT 7::VARCHAR");
        assertRefused("c VARCHAR(9) DEFAULT '7'::NUMBER", mismatch("C"));
    }

    /** A temporal default WIDENS but never narrows: DATE reaches TIMESTAMP, TIMESTAMP reaches neither
     *  DATE nor TIME. */
    @Test
    public void aTimestampDefaultDoesNotNarrowIntoADateOrTime() {
        assertAccepted("c TIMESTAMP_NTZ DEFAULT CURRENT_DATE()");
        assertRefused("c DATE DEFAULT CURRENT_TIMESTAMP()", mismatch("C"));
        assertRefused("c TIME DEFAULT CURRENT_TIMESTAMP()", mismatch("C"));
        assertAccepted("c DATE DEFAULT CURRENT_DATE()");
    }

    /** VARCHAR is the one family that refuses every temporal default. */
    @Test
    public void aStringColumnRefusesEveryTemporalDefault() {
        assertRefused("c VARCHAR(9) DEFAULT CURRENT_DATE()", mismatch("C"));
        assertRefused("c VARCHAR(9) DEFAULT CURRENT_TIMESTAMP()", mismatch("C"));
    }

    /** While BOOLEAN and the numeric family take one. */
    @Test
    public void booleanAndNumericColumnsTakeATemporalDefault() {
        assertAccepted("c BOOLEAN DEFAULT CURRENT_DATE()");
        assertAccepted("c NUMBER(5,1) DEFAULT CURRENT_DATE()");
        assertAccepted("c VARIANT DEFAULT CURRENT_TIMESTAMP()");
    }
}
