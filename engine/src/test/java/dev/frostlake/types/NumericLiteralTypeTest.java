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

package dev.frostlake.types;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A numeric literal carries its OWN precision and scale, not a blanket NUMBER(38,0).
 *
 * <p>This is a data-integrity test before it is a metadata one. The inferred type becomes a derived
 * relation's real column type, so while it said NUMBER(38,0) the engine did not merely misreport
 * {@code 0.05} — {@code CREATE TABLE t AS SELECT x FROM (SELECT 0.05 AS x FROM s)} STORED it as
 * {@code 0}, and {@code 100.25} as {@code 100}. Every expectation here was measured on a real Snowflake
 * account by declaring the literal into a table and reading INFORMATION_SCHEMA back.
 */
public class NumericLiteralTypeTest extends BaseDatabaseTest {

    private void assertType(final int precision, final int scale, final String literal) {
        final NumericType type = NumericLiteralTypes.forDecimal(new BigDecimal(literal));
        assertEquals(precision, type.getPrecision(), literal + " precision");
        assertEquals(scale, type.getScale(), literal + " scale");
    }

    /** Integer digits plus scale, and the sign is not a digit. */
    @Test
    public void aLiteralReportsItsOwnPrecisionAndScale() {
        assertType(2, 1, "1.5");
        assertType(5, 2, "100.25");
        assertType(1, 0, "1");
        assertType(3, 0, "100");
        assertType(20, 0, "12345678901234567890");
        assertType(2, 1, "-1.5");
    }

    /** A value below one still reports its leading zero — 0.05 is NUMBER(3,2), not NUMBER(2,2). */
    @Test
    public void aValueBelowOneKeepsItsLeadingZero() {
        assertType(2, 1, "0.5");
        assertType(3, 2, "0.05");
        assertType(11, 10, "0.0000000001");
        assertType(2, 1, ".5");
    }

    /** Trailing zeros are not significant: 1.50 is NUMBER(2,1) and 0.000 is NUMBER(1,0). */
    @Test
    public void trailingZerosDoNotCount() {
        assertType(2, 1, "1.50");
        assertType(1, 0, "0.000");
        assertType(1, 0, "0");
    }

    /** An exponent is folded in before measuring, so 1.5e2 is the NUMBER(3,0) holding 150. */
    @Test
    public void anExponentIsFoldedInFirst() {
        assertType(3, 0, "1.5e2");
        assertType(1, 0, "5e0");
    }

    /** A long integer literal arrives unboxed rather than as a BigDecimal. */
    @Test
    public void anIntegerValueIsMeasuredToo() {
        assertEquals(3, NumericLiteralTypes.of(Long.valueOf(100L)).getPrecision());
        assertEquals(0, NumericLiteralTypes.of(Long.valueOf(100L)).getScale());
    }

    /** REGRESSION: the derived-table round trip that silently rounded the value away. */
    @Test
    public void aDecimalSurvivesADerivedTable() {
        engine.execute("CREATE TABLE num_src (id INTEGER)");
        engine.execute("INSERT INTO num_src VALUES (1)");
        engine.execute("CREATE TABLE num_out AS SELECT x FROM (SELECT 0.05 AS x FROM num_src)");
        assertEquals("0.05", String.valueOf(
            engine.executeQuery("SELECT x FROM num_out").getRows().get(0).getValue(0)));
        assertEquals("3", String.valueOf(engine.executeQuery(
            "SELECT numeric_precision FROM test_db.information_schema.columns"
                + " WHERE table_name = 'NUM_OUT'").getRows().get(0).getValue(0)));
        assertEquals("2", String.valueOf(engine.executeQuery(
            "SELECT numeric_scale FROM test_db.information_schema.columns"
                + " WHERE table_name = 'NUM_OUT'").getRows().get(0).getValue(0)));
    }

    /**
     * REGRESSION: a cast target's declared scale is part of the type. Live, {@code 1.5::NUMBER(5,2)} is
     * NUMBER(5,2) holding 1.50; dropping the parameters stored 2.
     */
    @Test
    public void aCastTargetKeepsItsDeclaredScale() {
        engine.execute("CREATE TABLE cast_src (id INTEGER)");
        engine.execute("INSERT INTO cast_src VALUES (1)");
        engine.execute("CREATE TABLE cast_out AS SELECT x FROM"
            + " (SELECT 1.5::NUMBER(5,2) AS x FROM cast_src)");
        assertEquals("1.50", String.valueOf(
            engine.executeQuery("SELECT x FROM cast_out").getRows().get(0).getValue(0)));
    }
}
