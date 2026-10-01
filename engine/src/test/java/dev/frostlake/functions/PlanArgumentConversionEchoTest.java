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
 * The argument conversions RANDOM's constant-argument sentence re-prints from the plan: a FLOAT function's
 * arguments are cast to FLOAT, the rounding family reads a text as a FLOAT, the values of a conditional meet in
 * one number, a bare NULL beside an exact number is its typed null, and a division rescales that null as it
 * rescales any dividend. Each expected answer is the account's own.
 */
public class PlanArgumentConversionEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
    }

    /** RANDOM's refusal of its argument, as the plan echoes it; or the refusal as one line, or ACCEPTED. */
    private String found(final String argument) {
        try {
            engine.executeQuery("SELECT RANDOM(" + argument + ") FROM rt");
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            final String message = String.valueOf(refused.getMessage()).replace('\n', '|');
            final String prefix = "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '";
            return message.startsWith(prefix) && message.endsWith("'")
                ? message.substring(prefix.length(), message.length() - 1) : message;
        }
    }

    @Test
    public void aFloatFunctionCastsItsArgumentsToFloat() {
        assertEquals("SQRT(CAST(RT.N AS FLOAT))", found("SQRT(n)"));
        assertEquals("SQRT(RT.F)", found("SQRT(f)"));
        assertEquals("SQRT(CAST(RT.N52 AS FLOAT))", found("SQRT(n52)"));
        assertEquals("SQRT(CAST(RT.G AS FLOAT))", found("SQRT(g)"));
        assertEquals("SQRT(CAST(RT.V AS FLOAT))", found("SQRT(v)"));
        assertEquals("(CAST(1 AS FLOAT)) + (SQRT(CAST(RT.N AS FLOAT)))", found("1 + SQRT(n)"));
        assertEquals("SQRT(CAST((CAST(RT.N AS NUMBER(38,1))) + 2.5 AS FLOAT))", found("SQRT(n + 2.5)"));
        assertEquals("(SQRT(CAST(4 AS FLOAT))) + (CAST(RT.N AS FLOAT))", found("SQRT(4) + n"));
        assertEquals("POWER(CAST(RT.N AS FLOAT), CAST(2 AS FLOAT))", found("POWER(n, 2)"));
        assertEquals("POW(CAST(RT.N AS FLOAT), CAST(2 AS FLOAT))", found("POW(n, 2)"));
        assertEquals("POWER(RT.F, CAST(RT.N AS FLOAT))", found("POWER(f, n)"));
        assertEquals("LOG(CAST(2 AS FLOAT), CAST(RT.N AS FLOAT))", found("LOG(2, n)"));
        assertEquals("ATAN2(CAST(RT.N AS FLOAT), CAST(1 AS FLOAT))", found("ATAN2(n, 1)"));
        assertEquals("HAVERSINE(CAST(RT.N AS FLOAT), CAST(1 AS FLOAT), CAST(2 AS FLOAT), CAST(3 AS FLOAT))",
            found("HAVERSINE(n, 1, 2, 3)"));
        assertEquals("LN(CAST(RT.N AS FLOAT))", found("LN(n)"));
        assertEquals("EXP(CAST(UPPER(CAST(RT.N AS VARCHAR(134217728))) AS FLOAT))", found("EXP(UPPER(n))"));
        assertEquals("SIN(CAST(RT.N AS FLOAT))", found("SIN(n)"));
        assertEquals("DEGREES(CAST(RT.N AS FLOAT))", found("DEGREES(n)"));
        assertEquals("SQUARE(CAST(RT.N AS FLOAT))", found("SQUARE(n)"));
        assertEquals("ABS(RT.N)", found("ABS(n)"), "an exact function keeps its argument");
    }

    @Test
    public void theRoundingFamilyReadsATextAsAFloat() {
        assertEquals("ABS(CAST(RT.G AS FLOAT))", found("ABS(g)"));
        assertEquals("CEIL(CAST(RT.G AS FLOAT))", found("CEIL(g)"));
        assertEquals("FLOOR(CAST(RT.G AS FLOAT))", found("FLOOR(g)"));
        assertEquals("ROUND(CAST(RT.G AS FLOAT))", found("ROUND(g)"));
        assertEquals("SIGN(CAST(RT.G AS FLOAT))", found("SIGN(g)"));
        assertEquals("ROUND(TO_NUMBER(RT.G, 18, 5), 1)", found("ROUND(g, 1)"));
        assertEquals("ABS(CAST(RT.V AS FLOAT))", found("ABS(v)"));
    }

    @Test
    public void theValuesOfAConditionalMeetInOneNumber() {
        assertEquals("NULLIF(RT.F, CAST(1 AS FLOAT))", found("NULLIF(f, 1)"));
        assertEquals("NULLIF(CAST(RT.N AS NUMBER(38,1)), 1.5)", found("NULLIF(n, 1.5)"));
        assertEquals("NULLIF(RT.N52, CAST(1 AS NUMBER(5,2)))", found("NULLIF(n52, 1)"));
        assertEquals("NULLIF(RT.F, CAST(RT.N AS FLOAT))", found("NULLIF(f, n)"));
        assertEquals("IFF(RT.N > 0, RT.F, CAST(1 AS FLOAT))", found("IFF(n > 0, f, 1)"));
        assertEquals("IFF(RT.N > 0, CAST(1 AS FLOAT), RT.F)", found("IFF(n > 0, 1, f)"));
        assertEquals("IFNULL(RT.F, CAST(1 AS FLOAT))", found("COALESCE(f, 1)"));
        assertEquals("IFNULL(CAST(RT.N AS NUMBER(38,1)), 1.5)", found("COALESCE(n, 1.5)"));
        assertEquals("IFNULL(CAST(RT.N AS FLOAT), IFNULL(CAST(1.5 AS FLOAT), RT.F))", found("COALESCE(n, 1.5, f)"));
        assertEquals("IFNULL(CAST(RT.N AS NUMBER(38,2)), IFNULL(RT.N52, CAST(1 AS NUMBER(5,2))))",
            found("COALESCE(n, n52, 1)"));
        assertEquals("NVL(CAST(RT.N AS FLOAT), RT.F)", found("NVL(n, f)"));
        assertEquals("IFNULL(CAST(1 AS FLOAT), RT.F)", found("IFNULL(1, f)"));
        assertEquals("GREATEST(CAST(RT.N AS FLOAT), RT.F)", found("GREATEST(n, f)"));
        assertEquals("GREATEST(CAST(1 AS NUMBER(38,2)), RT.N52, CAST(RT.N AS NUMBER(38,2)))", found("GREATEST(1, n52, n)"));
        assertEquals("LEAST(CAST(1 AS FLOAT), CAST(RT.N52 AS FLOAT), RT.F)", found("LEAST(1, n52, f)"));
        assertEquals("CASE_FLATTENED(RT.N > 0, RT.F, CAST(1 AS FLOAT))", found("CASE WHEN n > 0 THEN f ELSE 1 END"));
        assertEquals("CASE_FLATTENED(RT.N > 0, CAST(RT.N AS NUMBER(38,1)), 1.5)",
            found("CASE WHEN n > 0 THEN n ELSE 1.5 END"));
        assertEquals("DECODE(RT.N, CAST(1 AS NUMBER(38,0)), RT.F, CAST(2 AS FLOAT))", found("DECODE(n, 1, f, 2)"));
        assertEquals("IFF(RT.N IS NOT NULL, RT.F, CAST(1 AS FLOAT))", found("NVL2(n, f, 1)"));
        assertEquals("IFF(RT.G IS NOT NULL, CAST(RT.N AS NUMBER(38,1)), 1.5)", found("NVL2(g, n, 1.5)"));
        assertEquals("IFF(RT.N IS NOT NULL, 1, 2)", found("NVL2(n, 1, 2)"));
    }

    @Test
    public void aBareNullBesideANumberIsItsTypedNull() {
        assertEquals("(CAST(SYSTEM$NULL_TO_FIXED(null) AS NUMBER(24,6))) / 2", found("NULL / 2"));
        assertEquals("(CAST(SYSTEM$NULL_TO_FIXED(null) AS NUMBER(28,8))) / RT.N52", found("NULL / n52"));
        assertEquals("(CAST(SYSTEM$NULL_TO_FIXED(null) AS NUMBER(24,6))) / (SYSTEM$NULL_TO_FIXED(null))",
            found("NULL / NULL"));
        assertEquals("SCALED_ROUND_INT_DIVIDE(RT.N, SYSTEM$NULL_TO_FIXED(null))", found("n / NULL"));
        assertEquals("(SYSTEM$NULL_TO_REAL(null)) / RT.F", found("NULL / f"));
        assertEquals("(SYSTEM$NULL_TO_FIXED(null)) % 2", found("NULL % 2"));
        assertEquals("(SYSTEM$NULL_TO_FIXED(null)) - RT.N52", found("NULL - n52"));
        assertEquals("RT.N52 + (SYSTEM$NULL_TO_FIXED(null))", found("n52 + NULL"));
        assertEquals("NEGATE(SYSTEM$NULL_TO_FIXED(null))", found("-NULL"));
        assertEquals("ARRAY_SIZE(SYSTEM$NULL_TO_ARRAY(null))", found("ARRAY_SIZE(NULL)"));
        assertEquals("PARSE_JSON(SYSTEM$NULL_TO_TEXT(null))", found("PARSE_JSON(NULL)"));
        assertEquals("TYPEOF(SYSTEM$NULL_TO_VARIANT(null))", found("TYPEOF(NULL)"));
        assertEquals("DECODE(CAST(null AS NULL), null, 1, SYSTEM$NULL_TO_FIXED(null))", found("DECODE(NULL, NULL, 1)"));
    }
}
