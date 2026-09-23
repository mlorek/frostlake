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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bare NULL the plan does not convert keeps a generator's argument constant, and a call that folds to one is
 * one: a date or time call over NULL, a conditional whose every value is NULL where what it tests is constant,
 * the value NVL2 tests and HASH's arguments. Converted by what holds it — an operator, a typed argument — the NULL
 * is no constant, and its echo is the typed null. The typed nulls carry their own widths. Each expected answer is
 * the account's own.
 */
public class ConstantArgumentNullFoldTest extends BaseDatabaseTest {

    private static final String RANDOM = "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE rt (n INT, d DATE)");
    }

    /** The refusal as one line, each line break as |, or ACCEPTED. */
    private String answer(final String sql) {
        try {
            engine.executeQuery(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String random(final String argument) {
        return answer("SELECT RANDOM(" + argument + ") FROM rt");
    }

    private String value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void anUnconvertedNullIsAConstant() {
        assertEquals("ACCEPTED", random("COALESCE(NULL, NULL)"));
        assertEquals("ACCEPTED", random("COALESCE(NULL, NULL, NULL)"));
        assertEquals("ACCEPTED", random("NVL(NULL, NULL)"));
        assertEquals("ACCEPTED", random("IFNULL(NULL, NULL)"));
        assertEquals("ACCEPTED", random("GREATEST(NULL, NULL)"));
        assertEquals("ACCEPTED", random("NULLIF(NULL, NULL)"));
        assertEquals("ACCEPTED", random("NVL2(NULL, 1, 2)"));
        assertEquals("ACCEPTED", random("NVL2(1, 1, 2)"));
        assertEquals("ACCEPTED", random("NVL2(NULL, NULL, NULL)"));
        assertEquals("ACCEPTED", random("IFF(TRUE, NULL, NULL)"));
        assertEquals("ACCEPTED", random("CASE WHEN TRUE THEN NULL ELSE NULL END"));
        assertEquals("ACCEPTED", random("DECODE(1, 1, NULL, NULL)"));
        assertEquals("ACCEPTED", random("HASH(NULL)"));
        assertEquals("ACCEPTED", random("HASH(NULL, 1)"));
    }

    @Test
    public void aDateCallOverNullFoldsToNull() {
        assertEquals("ACCEPTED", random("YEAR(NULL)"));
        assertEquals("ACCEPTED", random("MONTH(NULL)"));
        assertEquals("ACCEPTED", random("DAYOFWEEK(NULL)"));
        assertEquals("ACCEPTED", random("HOUR(NULL)"));
        assertEquals("ACCEPTED", random("EXTRACT(year FROM NULL)"));
        assertEquals("ACCEPTED", random("DATE_PART(year, NULL)"));
        assertEquals("ACCEPTED", random("LAST_DAY(NULL)"));
        assertEquals("ACCEPTED", random("DATE_TRUNC('day', NULL)"));
        assertEquals("ACCEPTED", random("DATEADD(day, 1, NULL)"));
        assertEquals("ACCEPTED", random("DATEADD(day, n, NULL)"), "the fold wins over a column beside it");
        assertEquals("ACCEPTED", random("DATEDIFF(day, NULL, d)"));
        assertEquals("ACCEPTED", random("ADD_MONTHS(NULL, 1)"));
        assertEquals("ACCEPTED", random("TRUNC(NULL)"));
        assertEquals("NULL[LOB]", value("SELECT SYSTEM$TYPEOF(YEAR(NULL))"));
        assertEquals("NULL[LOB]", value("SELECT SYSTEM$TYPEOF(LAST_DAY(NULL))"));
        assertEquals("NULL[LOB]", value("SELECT SYSTEM$TYPEOF(TRUNC(NULL))"));
        assertEquals("ACCEPTED", answer("SELECT 1 FROM rt WHERE YEAR(NULL)"));
        assertEquals("ACCEPTED", answer("SELECT 1 FROM rt WHERE TRUNC(NULL)"));
    }

    @Test
    public void aConvertedNullIsNoConstant() {
        assertEquals(RANDOM + "(SYSTEM$NULL_TO_FIXED(null)) + 1'", random("YEAR(NULL) + 1"));
        assertEquals(RANDOM + "(SYSTEM$NULL_TO_FIXED(null)) * RT.N'", random("YEAR(NULL) * n"));
        assertEquals(RANDOM + "EXTRACT(year from SYSTEM$NULL_TO_DATE(null))'", random("YEAR(NULL::DATE)"));
        assertEquals(RANDOM + "IFNULL(SYSTEM$NULL_TO_FIXED(null), 1)'", random("IFNULL(NULL, 1)"));
        assertEquals(RANDOM + "ABS(SYSTEM$NULL_TO_FIXED(null))'", random("ABS(NULL)"));
        assertEquals(RANDOM + "IFF((SYSTEM$NULL_TO_FIXED(null)) = 1, CAST(null AS NULL), null)'", random("NULLIF(NULL, 1)"));
        assertEquals(RANDOM + "IFF(RT.N > 1, CAST(null AS NULL), null)'", random("IFF(n > 1, NULL, NULL)"));
        assertEquals(RANDOM + "IFF(RT.N IS NOT NULL, CAST(null AS NULL), null)'", random("NVL2(n, NULL, NULL)"));
        assertEquals(RANDOM + "IFF(CAST(null AS NULL) IS NOT NULL, SYSTEM$NULL_TO_FIXED(null), 1)'",
            random("NVL2(NULL, NULL, 1)"));
        assertEquals(RANDOM + "IFF(CAST(null AS NULL) IS NOT NULL, RT.N, 2)'", random("NVL2(NULL, n, 2)"));
        assertEquals(RANDOM + "CASE_FLATTENED(RT.N > 1, CAST(null AS NULL), null)'",
            random("CASE WHEN n > 1 THEN NULL END"));
        assertEquals(RANDOM + "HASH(CAST(null AS NULL), RT.N)'", random("HASH(NULL, n)"));
        assertTrue(random("COALESCE(NULL, NULL) + 1").startsWith(RANDOM), random("COALESCE(NULL, NULL) + 1"));
    }

    @Test
    public void theTypedNullsCarryTheirOwnWidths() {
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(2,0)] for predicate [ABS(SYSTEM$NULL_TO_FIXED(null))]",
            answer("SELECT 1 FROM rt WHERE ABS(NULL)"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(18,0)] for predicate [CEIL(SYSTEM$NULL_TO_FIXED(null))]",
            answer("SELECT 1 FROM rt WHERE CEIL(NULL)"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(18,0)] for predicate [FLOOR(SYSTEM$NULL_TO_FIXED(null))]",
            answer("SELECT 1 FROM rt WHERE FLOOR(NULL)"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(18,0)] for predicate "
                + "[ROUND(SYSTEM$NULL_TO_FIXED(null), 1)]",
            answer("SELECT 1 FROM rt WHERE ROUND(NULL, 1)"));
        assertEquals("NUMBER(2,0)[SB1]", value("SELECT SYSTEM$TYPEOF(ABS(NULL))"));
        assertEquals("NUMBER(24,6)[SB1]", value("SELECT SYSTEM$TYPEOF(NULL / 2)"));
    }
}
