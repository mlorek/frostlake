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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A bare NUMBER / DECIMAL / NUMERIC is NUMBER(38,0) in Snowflake, so every conversion to it with no
 * explicit scale rounds the value to a whole number: TO_NUMBER(405.958) -> 406, 405.958::NUMBER -> 406,
 * etc. An explicit (precision, scale) — or a NUMBER(p,s) cast — still rounds to that scale instead.
 */
public class NumberDefaultScaleTest extends BaseDatabaseTest {

    private double num(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    private String str(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void toNumberFamilyDefaultsToScaleZero() {
        assertEquals(406.0, num("SELECT TO_NUMBER(405.958)"), 1e-9);
        assertEquals(406.0, num("SELECT TO_DECIMAL(405.958)"), 1e-9);
        assertEquals(406.0, num("SELECT TO_NUMERIC(405.958)"), 1e-9);
        assertEquals(103.0, num("SELECT TO_NUMBER('102.811')"), 1e-9);
    }

    @Test
    public void bareCastDefaultsToScaleZero() {
        assertEquals(406.0, num("SELECT 405.958::NUMBER"), 1e-9);
        assertEquals(406.0, num("SELECT CAST(405.958 AS NUMBER)"), 1e-9);
        assertEquals(406.0, num("SELECT 405.958::DECIMAL"), 1e-9);
        assertEquals(406.0, num("SELECT 405.958::NUMERIC"), 1e-9);
    }

    @Test
    public void tryCastBareNumberDefaultsToScaleZero() {
        assertEquals(406.0, num("SELECT TRY_CAST('405.958' AS NUMBER)"), 1e-9);
        assertEquals(406.0, num("SELECT TRY_CAST('405.958' AS DECIMAL)"), 1e-9);
    }

    @Test
    public void halfUpRoundsAwayFromZero() {
        assertEquals(3.0, num("SELECT TO_NUMBER(2.5)"), 1e-9);
        assertEquals(-3.0, num("SELECT TO_NUMBER(-2.5)"), 1e-9);
    }

    @Test
    public void explicitScaleIsPreserved() {
        assertEquals(405.958, num("SELECT TO_NUMBER(405.958, 38, 3)"), 1e-9);
        assertEquals(405.96, num("SELECT 405.958::NUMBER(10,2)"), 1e-9);
        assertEquals(405.96, num("SELECT TRY_CAST('405.958' AS NUMBER(10,2))"), 1e-9);
    }

    @Test
    public void formatStringImpliesItsFractionalScale() {
        // A format model such as '9,999.99' has two fractional digit placeholders, so the scale is 2.
        assertEquals(405.96, num("SELECT TO_NUMBER('405.958', '9,999.99')"), 1e-9);
        assertEquals(406.0, num("SELECT TO_NUMBER('405.958', '9,999')"), 1e-9);
    }

    @Test
    public void roundedValueFeedsObjectConstruct() {
        // A value rounded via TO_NUMBER lands in the object as an integer, not a decimal.
        assertEquals("{\"score\":406}", str("SELECT OBJECT_CONSTRUCT('score', TO_NUMBER(405.958))"));
    }
}
