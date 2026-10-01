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
 * An argument a refusal re-prints from the plan, where the plan converts it: a text operand beside a
 * number — a value read as NUMBER(18,5), a literal as 18 digits with its own decimals — the NULLIF that is
 * an IFF, DIV0 and DIV0NULL as the division they are, a conversion of a number as the cast it is, a
 * negative number as one number, a conditional of NULLs as the NULL type's meeting, and a constant argument
 * as the plan folds it. RANDOM's seed, LAST_QUERY_ID's index and GETVARIABLE's name are the sentences that
 * print them; every cell is live-verified.
 */
public class NumericPlanEchoShapesTest extends BaseDatabaseTest {

    private static final String RANDOM = "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '";
    private static final String INDEX = "SQL compilation error:|argument 1 to function LAST_QUERY_ID needs to be constant, found '";
    private static final String NAME = "SQL compilation error:|argument 0 to function GETVARIABLE needs to be constant, found '";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), "
            + "n107 NUMBER(10,7), n20 NUMBER(20,0), v VARIANT)");
        engine.execute("SET SV = 'abc'");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.next() ? String.valueOf(rs.getValue(0)) : "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Arithmetic over a text value or a text literal beside a number. */
    @Test
    public void textOperandsMeetAsThePlanConvertsThem() {
        final String[][] cells = {
            {"SELECT RANDOM(1 + GETVARIABLE('SV'))",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST(1 AS NUMBER(18,5))) + (TO_NUMBER('SV', 18, 5))'"},
            {"SELECT RANDOM(1 + g) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST(1 AS NUMBER(18,5))) + (TO_NUMBER(RT.G, 18, 5))'"},
            {"SELECT RANDOM(g * 2) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(TO_NUMBER(RT.G, 18, 5)) * 2'"},
            {"SELECT RANDOM(n - g) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST(RT.N AS NUMBER(38,5))) - (TO_NUMBER(RT.G, 38, 5))'"},
            {"SELECT RANDOM(g + g) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST(RT.G AS FLOAT)) + (CAST(RT.G AS FLOAT))'"},
            {"SELECT RANDOM(n52 + g) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST(RT.N52 AS NUMBER(18,5))) + (TO_NUMBER(RT.G, 18, 5))'"},
            {"SELECT RANDOM(f + g) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.F + (CAST(RT.G AS FLOAT))'"},
            {"SELECT RANDOM(1.123456 + g) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '1.123456 + (TO_NUMBER(RT.G, 19, 6))'"},
            {"SELECT RANDOM(n52 - g) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST(RT.N52 AS NUMBER(18,5))) - (TO_NUMBER(RT.G, 18, 5))'"},
            {"SELECT RANDOM(g - 1) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(TO_NUMBER(RT.G, 18, 5)) - (CAST(1 AS NUMBER(18,5)))'"},
            {"SELECT RANDOM(g / 2) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST(TO_NUMBER(RT.G, 18, 5) AS NUMBER(24,11))) / 2'"},
            {"SELECT RANDOM(2 / g) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST(2 AS NUMBER(17,11))) / (TO_NUMBER(RT.G, 18, 5))'"},
            {"SELECT RANDOM(g * n) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(TO_NUMBER(RT.G, 18, 5)) * RT.N'"},
            {"SELECT RANDOM(n107 + g) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.N107 + (TO_NUMBER(RT.G, 20, 7))'"},
            {"SELECT RANDOM(n20 + g) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST(RT.N20 AS NUMBER(25,5))) + (TO_NUMBER(RT.G, 25, 5))'"},
            {"SELECT RANDOM(g % 2) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(TO_NUMBER(RT.G, 18, 5)) % (CAST(2 AS NUMBER(18,5)))'"},
            {"SELECT RANDOM('5' + n) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(TO_NUMBER('5')) + RT.N'"},
            {"SELECT RANDOM('5.5' + n) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(TO_NUMBER('5.5', 38, 1)) + (CAST(RT.N AS NUMBER(38,1)))'"},
            {"SELECT RANDOM('5' + n52) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(TO_NUMBER('5', 18, 2)) + RT.N52'"},
            {"SELECT RANDOM(n52 - '5') FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.N52 - (TO_NUMBER('5', 18, 2))'"},
            {"SELECT RANDOM('5' * n) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(TO_NUMBER('5', 18, 0)) * RT.N'"},
            {"SELECT RANDOM('5' / n) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST(TO_NUMBER('5', 18, 0) AS NUMBER(24,6))) / RT.N'"},
            {"SELECT RANDOM('5' + f) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST('5' AS FLOAT)) + RT.F'"},
            {"SELECT RANDOM('12345' + n) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(TO_NUMBER('12345')) + RT.N'"},
            {"SELECT RANDOM('-5' + n) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(TO_NUMBER('-5')) + RT.N'"},
            {"SELECT RANDOM('1e3' + n) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(TO_NUMBER('1e3')) + RT.N'"},
            {"SELECT RANDOM(n + '5') FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.N + (TO_NUMBER('5'))'"},
            {"SELECT RANDOM('5' + g) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(CAST('5' AS FLOAT)) + (CAST(RT.G AS FLOAT))'"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** NULLIF of a text and a number. */
    @Test
    public void nullIfIsTheIffItStandsFor() {
        final String[][] cells = {
            {"SELECT RANDOM(NULLIF(g, 1)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF((TO_NUMBER(RT.G, 18, 5)) = (CAST(1 AS NUMBER(18,5))), SYSTEM$NULL_TO_TEXT(null), RT.G)'"},
            {"SELECT RANDOM(NULLIF(1, g)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF((CAST(1 AS NUMBER(18,5))) = (TO_NUMBER(RT.G, 18, 5)), SYSTEM$NULL_TO_FIXED(null), 1)'"},
            {"SELECT RANDOM(NULLIF(n, g)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF((CAST(RT.N AS NUMBER(38,5))) = (TO_NUMBER(RT.G, 38, 5)), SYSTEM$NULL_TO_FIXED(null), RT.N)'"},
            {"SELECT RANDOM(NULLIF(g, 1.5)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF((TO_NUMBER(RT.G, 18, 5)) = (CAST(1.5 AS NUMBER(18,5))), SYSTEM$NULL_TO_TEXT(null), RT.G)'"},
            {"SELECT RANDOM(NULLIF(g, n52)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF((TO_NUMBER(RT.G, 18, 5)) = (CAST(RT.N52 AS NUMBER(18,5))), SYSTEM$NULL_TO_TEXT(null), RT.G)'"},
            {"SELECT RANDOM(NULLIF(g, n20)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF((TO_NUMBER(RT.G, 25, 5)) = (CAST(RT.N20 AS NUMBER(25,5))), SYSTEM$NULL_TO_TEXT(null), RT.G)'"},
            {"SELECT RANDOM(NULLIF(g, f)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF((CAST(RT.G AS FLOAT)) = RT.F, SYSTEM$NULL_TO_TEXT(null), RT.G)'"},
            {"SELECT RANDOM(NULLIF(g, n107)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF((TO_NUMBER(RT.G, 20, 7)) = RT.N107, SYSTEM$NULL_TO_TEXT(null), RT.G)'"},
            {"SELECT RANDOM(NULLIF(n52, g)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF((CAST(RT.N52 AS NUMBER(18,5))) = (TO_NUMBER(RT.G, 18, 5)), SYSTEM$NULL_TO_FIXED(null), RT.N52)'"},
            {"SELECT RANDOM(NULLIF(n107, g)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF(RT.N107 = (TO_NUMBER(RT.G, 20, 7)), SYSTEM$NULL_TO_FIXED(null), RT.N107)'"},
            {"SELECT RANDOM(NULLIF(f, g)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'NULLIF(RT.F, CAST(RT.G AS FLOAT))'"},
            {"SELECT RANDOM(NULLIF(n, '5')) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'IFF(RT.N = (TO_NUMBER('5')), SYSTEM$NULL_TO_FIXED(null), RT.N)'"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** DIV0 and DIV0NULL, exact, FLOAT, NULL and text operands. */
    @Test
    public void div0IsTheDivisionItStandsFor() {
        final String[][] cells = {
            {"SELECT RANDOM(DIV0(n, 2)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SCALED_ROUND_INT_DIV0(RT.N, 2)'"},
            {"SELECT RANDOM(DIV0(n52, 2)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DIV0(CAST(RT.N52 AS NUMBER(11,8)), 2)'"},
            {"SELECT RANDOM(DIV0(f, 2)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DIV0(RT.F, CAST(2 AS FLOAT))'"},
            {"SELECT RANDOM(DIV0NULL(n, 2)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SCALED_ROUND_INT_DIV0(RT.N, ZEROIFNULL(2))'"},
            {"SELECT RANDOM(DIV0(n52, n52)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DIV0(CAST(RT.N52 AS NUMBER(15,10)), RT.N52)'"},
            {"SELECT RANDOM(DIV0NULL(n52, 3)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DIV0(CAST(RT.N52 AS NUMBER(11,8)), ZEROIFNULL(3))'"},
            {"SELECT RANDOM(DIV0(n, n52)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SCALED_ROUND_INT_DIV0(RT.N, RT.N52)'"},
            {"SELECT RANDOM(DIV0(n52, n)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DIV0(CAST(RT.N52 AS NUMBER(11,8)), RT.N)'"},
            {"SELECT RANDOM(DIV0(1, n)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DIV0(CAST(1 AS NUMBER(7,6)), RT.N)'"},
            {"SELECT RANDOM(DIV0(n, f)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DIV0(CAST(RT.N AS FLOAT), RT.F)'"},
            {"SELECT RANDOM(DIV0NULL(f, 2)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DIV0(RT.F, CAST(ZEROIFNULL(2) AS FLOAT))'"},
            {"SELECT RANDOM(DIV0(n20, 2)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DIV0(CAST(RT.N20 AS NUMBER(26,6)), 2)'"},
            {"SELECT RANDOM(DIV0(n107, 2)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DIV0(CAST(RT.N107 AS NUMBER(15,12)), 2)'"},
            {"SELECT RANDOM(DIV0(NULL, 2)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DIV0(CAST(SYSTEM$NULL_TO_FIXED(null) AS NUMBER(24,6)), 2)'"},
            {"SELECT RANDOM(DIV0(n, NULL)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SCALED_ROUND_INT_DIV0(RT.N, SYSTEM$NULL_TO_FIXED(null))'"},
            {"SELECT RANDOM(DIV0NULL(n, n)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'SCALED_ROUND_INT_DIV0(RT.N, ZEROIFNULL(RT.N))'"},
            {"SELECT RANDOM(DIV0(g, 2)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'DIV0(CAST(TO_NUMBER(RT.G, 18, 5) AS NUMBER(24,11)), 2)'"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** A conversion of a number, and a minus written before a number. */
    @Test
    public void numberConversionsAndNegativeNumbers() {
        final String[][] cells = {
            {"SELECT RANDOM(TO_NUMBER(n52)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CAST(RT.N52 AS NUMBER(38,0))'"},
            {"SELECT RANDOM(TO_NUMBER(f)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CAST(RT.F AS NUMBER(38,0))'"},
            {"SELECT RANDOM(TO_NUMBER(n, 10, 2)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CAST(RT.N AS NUMBER(10,2))'"},
            {"SELECT RANDOM(TO_NUMBER(n52, 10)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CAST(RT.N52 AS NUMBER(10,0))'"},
            {"SELECT RANDOM(TO_NUMBER(g)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'TO_NUMBER(RT.G)'"},
            {"SELECT RANDOM(TO_DECIMAL(n)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.N'"},
            {"SELECT RANDOM(TO_NUMBER(n, 38, 0)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.N'"},
            {"SELECT RANDOM(TO_NUMBER(n) + 1) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(RT.N) + 1'"},
            {"SELECT RANDOM(TO_NUMBER(n20)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CAST(RT.N20 AS NUMBER(38,0))'"},
            {"SELECT RANDOM(TO_NUMBER(n, 38)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.N'"},
            {"SELECT RANDOM(TO_NUMBER(g, 10, 2)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'TO_NUMBER(RT.G, 10, 2)'"},
            {"SELECT RANDOM(TO_NUMERIC(n52)) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'CAST(RT.N52 AS NUMBER(38,0))'"},
            {"SELECT RANDOM(n + -5) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.N + -5'"},
            {"SELECT RANDOM(-n + 0) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(NEGATE(RT.N)) + 0'"},
            {"SELECT RANDOM(-1.5 * n) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '-1.5 * RT.N'"},
            {"SELECT RANDOM(n * -2) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.N * -2'"},
            {"SELECT RANDOM(IFF(TRUE, NULL, NULL) + 1) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(SYSTEM$NULL_TO_FIXED(IFF(CAST(TRUE AS BOOLEAN), CAST(null AS NULL), null))) + 1'"},
            {"SELECT RANDOM(-5 - n) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '-5 - RT.N'"},
            {"SELECT RANDOM(ABS(-5) + n) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '(ABS(-5)) + RT.N'"},
            {"SELECT RANDOM(-(5) + n) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found '-5 + RT.N'"},
            {"SELECT RANDOM(n - -5) FROM rt",
                "SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.N - -5'"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER(1))",
                "SQL compilation error:|argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(1 AS NUMBER(38,0))'"},
            {"SELECT LAST_QUERY_ID(-10001 + 0)",
                "SQL compilation error:|argument 1 to function LAST_QUERY_ID needs to be constant, found '-10001 + 0'"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    private void assertEchoes(final String sentence, final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(sentence + cell[1] + "'", answer(cell[0]), cell[0]);
        }
    }

    /**
     * A conditional whose every branch is an untyped NULL meets its branches in the NULL type, the first converted
     * to it, and COALESCE's chain head once more; converted where it stands, it keeps that text inside the typed
     * NULL.
     */
    @Test
    public void aConditionalOfNullsMeetsInTheNullType() {
        assertEchoes(RANDOM, new String[][] {
            {"SELECT RANDOM(COALESCE(NULL, NULL) + 1) FROM rt",
                "(SYSTEM$NULL_TO_FIXED(IFNULL(CAST(CAST(null AS NULL) AS NULL), null))) + 1"},
            {"SELECT RANDOM(COALESCE(NULL, NULL) + 1)",
                "(SYSTEM$NULL_TO_FIXED(IFNULL(CAST(CAST(null AS NULL) AS NULL), null))) + 1"},
            {"SELECT RANDOM(COALESCE(NULL, NULL, NULL) + 1) FROM rt",
                "(SYSTEM$NULL_TO_FIXED(IFNULL(CAST(CAST(null AS NULL) AS NULL), IFNULL(CAST(null AS NULL), null)))) + 1"},
            {"SELECT RANDOM(COALESCE(COALESCE(NULL, NULL), NULL) + 1) FROM rt",
                "(SYSTEM$NULL_TO_FIXED(IFNULL(CAST(CAST(IFNULL(CAST(CAST(null AS NULL) AS NULL), null) AS NULL) AS NULL), "
                    + "null))) + 1"},
            {"SELECT RANDOM(NVL(NULL, NULL) + 1) FROM rt", "(SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null))) + 1"},
            {"SELECT RANDOM(IFNULL(NULL, NULL) + 1) FROM rt",
                "(SYSTEM$NULL_TO_FIXED(IFNULL(CAST(null AS NULL), null))) + 1"},
            {"SELECT RANDOM(NVL(NVL(NULL, NULL), NULL) + 1) FROM rt",
                "(SYSTEM$NULL_TO_FIXED(NVL(CAST(NVL(CAST(null AS NULL), null) AS NULL), null))) + 1"},
            {"SELECT RANDOM(NULLIF(NULL, NULL) + 1) FROM rt",
                "(SYSTEM$NULL_TO_FIXED(NULLIF(CAST(null AS NULL), null))) + 1"},
            {"SELECT RANDOM(GREATEST(NULL, NULL, NULL) + 1) FROM rt",
                "(SYSTEM$NULL_TO_FIXED(GREATEST(CAST(null AS NULL), null, null))) + 1"},
            {"SELECT RANDOM(DECODE(1, 1, NULL, 2, NULL, NULL) + 1) FROM rt",
                "(SYSTEM$NULL_TO_FIXED(DECODE(1, 1, CAST(null AS NULL), 2, null, null))) + 1"},
            {"SELECT RANDOM(DECODE(n, 1, NULL, 2, NULL) + 1) FROM rt",
                "(SYSTEM$NULL_TO_FIXED(DECODE(RT.N, CAST(1 AS NUMBER(38,0)), CAST(null AS NULL), CAST(2 AS NUMBER(38,0)), "
                    + "null, null))) + 1"},
            {"SELECT RANDOM(CASE WHEN n > 0 THEN NULL WHEN n < 0 THEN NULL ELSE NULL END + 1) FROM rt",
                "(SYSTEM$NULL_TO_FIXED(CASE_FLATTENED(RT.N > 0, CAST(null AS NULL), RT.N < 0, null, null))) + 1"},
            {"SELECT RANDOM(CASE n WHEN 1 THEN NULL END + 1) FROM rt",
                "(SYSTEM$NULL_TO_FIXED(CASE RT.N WHEN CAST(1 AS NUMBER(38,0)) THEN CAST(null AS NULL) ELSE null END)) + 1"},
            {"SELECT RANDOM(f - CASE WHEN n > 0 THEN NULL END) FROM rt",
                "RT.F - (SYSTEM$NULL_TO_REAL(CASE_FLATTENED(RT.N > 0, CAST(null AS NULL), null)))"},
            {"SELECT RANDOM(f + COALESCE(NULL, NULL)) FROM rt",
                "RT.F + (SYSTEM$NULL_TO_REAL(IFNULL(CAST(CAST(null AS NULL) AS NULL), null)))"},
            {"SELECT RANDOM(COALESCE(NULL, NULL) || 'a') FROM rt",
                "(SYSTEM$NULL_TO_TEXT(IFNULL(CAST(CAST(null AS NULL) AS NULL), null))) || 'a'"},
            {"SELECT RANDOM(-COALESCE(NULL, NULL)) FROM rt",
                "NEGATE(SYSTEM$NULL_TO_FIXED(IFNULL(CAST(CAST(null AS NULL) AS NULL), null)))"},
            {"SELECT RANDOM(ABS(COALESCE(NULL, NULL))) FROM rt",
                "ABS(SYSTEM$NULL_TO_FIXED(IFNULL(CAST(CAST(null AS NULL) AS NULL), null)))"},
            {"SELECT RANDOM(COALESCE(NULL, NULL)::INT) FROM rt",
                "SYSTEM$NULL_TO_FIXED(IFNULL(CAST(CAST(null AS NULL) AS NULL), null))"},
            {"SELECT RANDOM(TRY_CAST(NVL(NULL, NULL) AS INT)) FROM rt", "SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null))"},
            {"SELECT RANDOM(NVL(NULL, NULL)::VARCHAR) FROM rt", "SYSTEM$NULL_TO_TEXT(NVL(CAST(null AS NULL), null))"},
            {"SELECT RANDOM(TO_NUMBER(NVL(NULL, NULL))) FROM rt", "SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null))"},
            {"SELECT RANDOM(TO_VARIANT(NVL(NULL, NULL))) FROM rt",
                "SYSTEM$NULL_TO_VARIANT(NVL(CAST(null AS NULL), null))"},
            {"SELECT RANDOM(SQRT(NVL(NULL, NULL))) FROM rt", "SQRT(SYSTEM$NULL_TO_REAL(NVL(CAST(null AS NULL), null)))"},
            {"SELECT RANDOM(UPPER(NVL(NULL, NULL))) FROM rt", "UPPER(SYSTEM$NULL_TO_TEXT(NVL(CAST(null AS NULL), null)))"},
            {"SELECT RANDOM(IFF(n > 0, NVL(NULL, NULL), 1)) FROM rt",
                "IFF(RT.N > 0, SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null)), 1)"},
            {"SELECT RANDOM(GREATEST(NVL(NULL, NULL), 1)) FROM rt",
                "GREATEST(SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null)), 1)"},
            {"SELECT RANDOM(HASH(NVL(NULL, NULL), n)) FROM rt", "HASH(CAST(NVL(CAST(null AS NULL), null) AS NULL), RT.N)"},
            {"SELECT RANDOM(HASH(CASE WHEN n > 0 THEN NULL END, n)) FROM rt",
                "HASH(CAST(CASE_FLATTENED(RT.N > 0, CAST(null AS NULL), null) AS NULL), RT.N)"},
            {"SELECT RANDOM(HASH(YEAR(NULL), n)) FROM rt", "HASH(CAST(null AS NULL), RT.N)"},
            {"SELECT RANDOM(-YEAR(NULL)) FROM rt", "NEGATE(SYSTEM$NULL_TO_FIXED(null))"},
            {"SELECT RANDOM(YEAR(NULL)::INT) FROM rt", "SYSTEM$NULL_TO_FIXED(null)"},
            {"SELECT RANDOM(ABS(YEAR(NULL))) FROM rt", "ABS(SYSTEM$NULL_TO_FIXED(null))"},
            {"SELECT RANDOM(NULL || n) FROM rt", "(SYSTEM$NULL_TO_TEXT(null)) || (CAST(RT.N AS VARCHAR(134217728)))"},
            {"SELECT RANDOM(n || NULL) FROM rt", "(CAST(RT.N AS VARCHAR(134217728))) || (SYSTEM$NULL_TO_TEXT(null))"},
        });
        // Where nothing converts it the conditional is a constant.
        assertEquals("no row", answer("SELECT RANDOM(COALESCE(NULL, NULL)) FROM rt"));
    }

    /**
     * An argument whose every leaf is a literal is echoed as the plan folds it: a conversion of a text or a FLOAT
     * constant carried out, a product with a factor of one or zero folded to the other factor cast to the product's
     * type, and LAST_QUERY_ID's own conversion of the index to a whole number shown. Over a column nothing folds.
     */
    @Test
    public void aConstantArgumentIsEchoedAsThePlanFoldsIt() {
        assertEchoes(INDEX, new String[][] {
            {"SELECT LAST_QUERY_ID(-1 * 1)", "CAST(-1 AS NUMBER(2,0))"},
            {"SELECT LAST_QUERY_ID(1 * -1)", "CAST(-1 AS NUMBER(2,0))"},
            {"SELECT LAST_QUERY_ID(-1 * -1)", "-1 * -1"},
            {"SELECT LAST_QUERY_ID(2 * 3)", "2 * 3"},
            {"SELECT LAST_QUERY_ID(100 * 1)", "CAST(100 AS NUMBER(4,0))"},
            {"SELECT LAST_QUERY_ID(0 * 5)", "CAST(0 AS NUMBER(2,0))"},
            {"SELECT LAST_QUERY_ID(-10001 * 1)", "CAST(-10001 AS NUMBER(6,0))"},
            {"SELECT LAST_QUERY_ID(1 * 1 * 1)", "CAST(CAST(1 AS NUMBER(2,0)) AS NUMBER(3,0))"},
            {"SELECT LAST_QUERY_ID(1 * 1 * 2)", "(CAST(1 AS NUMBER(2,0))) * 2"},
            {"SELECT LAST_QUERY_ID(1 * (2 * 3))", "CAST(2 * 3 AS NUMBER(3,0))"},
            {"SELECT LAST_QUERY_ID(-(1 * 1))", "NEGATE(CAST(1 AS NUMBER(2,0)))"},
            {"SELECT LAST_QUERY_ID((1 * 1) + 0)", "(CAST(1 AS NUMBER(2,0))) + 0"},
            {"SELECT LAST_QUERY_ID(1.5 * 1)", "CAST(CAST(1.5 AS NUMBER(3,1)) AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(1.5 * 0)", "CAST(CAST(0 AS NUMBER(3,1)) AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(-1 * 1.5)", "CAST(-1 * 1.5 AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID('1' * 1)", "CAST(1 AS NUMBER(19,0))"},
            {"SELECT LAST_QUERY_ID('1.5' * 1)", "CAST(CAST(1.5 AS NUMBER(19,1)) AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID('2' * 3)", "2 * 3"},
            {"SELECT LAST_QUERY_ID('1' * '1')", "CAST(1.0 * 1.0 AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(NULL * 1)", "(SYSTEM$NULL_TO_FIXED(null)) * 1"},
            {"SELECT LAST_QUERY_ID(1 / 1)", "CAST((CAST(1 AS NUMBER(7,6))) / 1 AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(1.5 + 0)", "CAST(1.5 + (CAST(0 AS NUMBER(3,1))) AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(1::INT * 1)", "CAST(1 AS NUMBER(38,0))"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('1') * 1)", "CAST(1 AS NUMBER(38,0))"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('1', 10, 1))", "CAST(1 AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID('1'::NUMBER(10,2))", "CAST(1 AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('12.50', 10, 2))", "CAST(12.5 AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('1', 10, 1) * 1)", "CAST(CAST(1 AS NUMBER(11,1)) AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('1', 38, 2) * 1)", "CAST(CAST(1 AS NUMBER(38,2)) AS NUMBER(36,0))"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('1', 10, 1) + 0)", "CAST(1 + (CAST(0 AS NUMBER(10,1))) AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('1') + 0)", "1 + 0"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('2') * TO_NUMBER('3'))", "2 * 3"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('1') / 1)", "CAST(SCALED_ROUND_INT_DIVIDE(1, 1) AS NUMBER(32,0))"},
            {"SELECT LAST_QUERY_ID(-TO_NUMBER('1'))", "NEGATE(1)"},
            {"SELECT LAST_QUERY_ID(-'1')", "CAST(NEGATE(1.0) AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(-'abc')", "CAST(NEGATE(CAST('abc' AS FLOAT)) AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(ABS(TO_NUMBER('-1')))", "ABS(-1)"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER(TO_NUMBER('1')))", "1"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('1')::NUMBER(10,1))", "CAST(CAST(1 AS NUMBER(10,1)) AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('1', 10, 1)::INT)", "CAST(1 AS NUMBER(38,0))"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('abc'))", "TO_NUMBER('abc')"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER(TRIM('1')))", "TO_NUMBER(TRIM('1'))"},
            {"SELECT LAST_QUERY_ID(TO_NUMBER('1', '9'))", "TO_NUMBER('1', '9')"},
            {"SELECT LAST_QUERY_ID(TRIM('1'))", "TO_NUMBER(TRIM('1'), 18, 0)"},
            {"SELECT LAST_QUERY_ID(TO_VARCHAR(1))", "TO_NUMBER(CAST(1 AS VARCHAR(134217728)), 18, 0)"},
            {"SELECT LAST_QUERY_ID('1' || 'x')", "TO_NUMBER('1x', 18, 0)"},
            {"SELECT LAST_QUERY_ID(UPPER('abc'))", "TO_NUMBER('ABC', 18, 0)"},
            {"SELECT LAST_QUERY_ID('abc'::FLOAT)", "CAST(CAST('abc' AS FLOAT) AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(TO_DOUBLE('abc'))", "CAST(CAST('abc' AS FLOAT) AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(1 * 1.0::FLOAT)", "CAST(1.0 * 1.0 AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(1.5::FLOAT + 1)", "CAST(1.5 + 1.0 AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(1 + 1.5::FLOAT)", "CAST(1.0 + 1.5 AS NUMBER(18,0))"},
            {"SELECT LAST_QUERY_ID(TO_VARIANT(1)::NUMBER)", "TO_NUMBER(CAST(1 AS VARIANT))"},
            {"SELECT LAST_QUERY_ID(IFF(TRUE, 1, 2))", "IFF(CAST(TRUE AS BOOLEAN), 1, 2)"},
            {"SELECT LAST_QUERY_ID(n + 1 * 1) FROM rt", "RT.N + (1 * 1)"},
            {"SELECT LAST_QUERY_ID(n + TO_NUMBER('5')) FROM rt", "RT.N + (TO_NUMBER('5'))"},
            {"SELECT LAST_QUERY_ID(1.5 + n) FROM rt", "1.5 + (CAST(RT.N AS NUMBER(38,1)))"},
            {"SELECT LAST_QUERY_ID(n52) FROM rt", "RT.N52"},
        });
        assertEchoes(NAME, new String[][] {
            {"SELECT GETVARIABLE(-1 * 1)", "CAST(CAST(-1 AS NUMBER(2,0)) AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(2 * 3)", "CAST(2 * 3 AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(TO_NUMBER('1'))", "CAST(1 AS VARCHAR(134217728))"},
        });
        assertEchoes(RANDOM, new String[][] {
            {"SELECT RANDOM(n + 1 * 1) FROM rt", "RT.N + (1 * 1)"},
            {"SELECT RANDOM(n * 1) FROM rt", "RT.N * 1"},
        });
    }

    /**
     * A sign as the plan names it: a plus is UNARY PLUS, a minus NEGATE, a text or a VARIANT under either moved to
     * FLOAT first, and a minus before a minus before a number folded into the number; a constant under a plus is
     * that constant.
     */
    @Test
    public void aSignIsNamedAsThePlanHoldsIt() {
        assertEchoes(RANDOM, new String[][] {
            {"SELECT RANDOM(+n) FROM rt", "UNARY PLUS(RT.N)"},
            {"SELECT RANDOM(+(n)) FROM rt", "UNARY PLUS(RT.N)"},
            {"SELECT RANDOM(+n52) FROM rt", "UNARY PLUS(RT.N52)"},
            {"SELECT RANDOM(+f) FROM rt", "UNARY PLUS(RT.F)"},
            {"SELECT RANDOM(+n + 1) FROM rt", "(UNARY PLUS(RT.N)) + 1"},
            {"SELECT RANDOM(-(+n)) FROM rt", "NEGATE(UNARY PLUS(RT.N))"},
            {"SELECT RANDOM(+(n * 2)) FROM rt", "UNARY PLUS(RT.N * 2)"},
            {"SELECT RANDOM(ABS(+n)) FROM rt", "ABS(UNARY PLUS(RT.N))"},
            {"SELECT RANDOM(+ABS(n)) FROM rt", "UNARY PLUS(ABS(RT.N))"},
            {"SELECT RANDOM(+1 + n) FROM rt", "(UNARY PLUS(1)) + RT.N"},
            {"SELECT RANDOM(n + +1) FROM rt", "RT.N + (UNARY PLUS(1))"},
            {"SELECT RANDOM(+1.5 * n) FROM rt", "(UNARY PLUS(1.5)) * RT.N"},
            {"SELECT RANDOM(+(-1) + n) FROM rt", "(UNARY PLUS(-1)) + RT.N"},
            {"SELECT RANDOM(-(+1) + n) FROM rt", "(NEGATE(UNARY PLUS(1))) + RT.N"},
            {"SELECT RANDOM(+TO_NUMBER('1') + n) FROM rt", "(UNARY PLUS(TO_NUMBER('1'))) + RT.N"},
            {"SELECT RANDOM(+g) FROM rt", "UNARY PLUS(CAST(RT.G AS FLOAT))"},
            {"SELECT RANDOM(-g) FROM rt", "NEGATE(CAST(RT.G AS FLOAT))"},
            {"SELECT RANDOM(+v) FROM rt", "UNARY PLUS(CAST(RT.V AS FLOAT))"},
            {"SELECT RANDOM(-v) FROM rt", "NEGATE(CAST(RT.V AS FLOAT))"},
            {"SELECT RANDOM(-TRIM(g)) FROM rt", "NEGATE(CAST(TRIM(RT.G) AS FLOAT))"},
            {"SELECT RANDOM(+(g || g)) FROM rt", "UNARY PLUS(CAST(RT.G || RT.G AS FLOAT))"},
            {"SELECT RANDOM(+g + 1) FROM rt", "(UNARY PLUS(CAST(RT.G AS FLOAT))) + (CAST(1 AS FLOAT))"},
            {"SELECT RANDOM(+'1' + n) FROM rt", "(UNARY PLUS(CAST('1' AS FLOAT))) + (CAST(RT.N AS FLOAT))"},
            {"SELECT RANDOM(-'1' + n) FROM rt", "(NEGATE(CAST('1' AS FLOAT))) + (CAST(RT.N AS FLOAT))"},
            {"SELECT RANDOM(+NULL + n) FROM rt", "(UNARY PLUS(SYSTEM$NULL_TO_FIXED(null))) + RT.N"},
            {"SELECT RANDOM(+NVL(NULL, NULL) + 1) FROM rt",
                "(UNARY PLUS(SYSTEM$NULL_TO_FIXED(NVL(CAST(null AS NULL), null)))) + 1"},
            {"SELECT RANDOM(+CASE WHEN n > 0 THEN NULL END) FROM rt",
                "UNARY PLUS(SYSTEM$NULL_TO_FIXED(CASE_FLATTENED(RT.N > 0, CAST(null AS NULL), null)))"},
            {"SELECT RANDOM(-(-1) + n) FROM rt", "1 + RT.N"},
            {"SELECT RANDOM(-(-(-1)) + n) FROM rt", "-1 + RT.N"},
            {"SELECT RANDOM(-(-1.5) * n) FROM rt", "1.5 * RT.N"},
            {"SELECT RANDOM(-(-n)) FROM rt", "NEGATE(NEGATE(RT.N))"},
            {"SELECT RANDOM(-NULL + n) FROM rt", "(NEGATE(SYSTEM$NULL_TO_FIXED(null))) + RT.N"},
        });
        assertEchoes(NAME, new String[][] {
            {"SELECT GETVARIABLE(+1)", "CAST(1 AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(+(+1))", "CAST(1 AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(+(-1))", "CAST(-1 AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(-(+1))", "CAST(NEGATE(1) AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(-(-1))", "CAST(1 AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(-(-(-1)))", "CAST(-1 AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(+TO_NUMBER('1'))", "CAST(1 AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(+'1.50')", "CAST(1.5 AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(+' 1 ')", "CAST(1.0 AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(+(+'1'))", "CAST(1.0 AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(-'1.50')", "CAST(NEGATE(1.5) AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(+'SV')", "CAST(UNARY PLUS(CAST('SV' AS FLOAT)) AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(+(-1 * 1))", "CAST(UNARY PLUS(CAST(-1 AS NUMBER(2,0))) AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(+1.5 * 1)", "CAST(CAST(1.5 AS NUMBER(4,1)) AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE('NaN'::FLOAT)", "CAST(NaN AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(1e20::FLOAT)", "CAST(1.0E20 AS VARCHAR(134217728))"},
        });
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(38,0)] for predicate [UNARY PLUS(RT.N)]",
            answer("SELECT * FROM rt WHERE +n"));
        assertEquals("SQL compilation error:|Invalid data type [FLOAT] for predicate [UNARY PLUS(CAST(RT.G AS FLOAT))]",
            answer("SELECT * FROM rt WHERE +g"));
        assertEquals("SQL compilation error:|Invalid data type [FLOAT] for predicate [NEGATE(CAST(RT.G AS FLOAT))]",
            answer("SELECT * FROM rt WHERE -g"));
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(38,0)] for predicate [NEGATE(NEGATE(RT.N))]",
            answer("SELECT * FROM rt WHERE -(-n)"));
        final String arity = "SQL compilation error: error line 1 at position 7|too many arguments for function [";
        assertEquals(arity + "ABS(UNARY PLUS(RT.N), 1)] expected 1, got 2", answer("SELECT ABS(+n, 1) FROM rt"));
        assertEquals(arity + "ABS(UNARY PLUS(CAST(RT.G AS FLOAT)), 1)] expected 1, got 2",
            answer("SELECT ABS(+g, 1) FROM rt"));
        assertEquals(arity + "ABS(NEGATE(CAST(RT.G AS FLOAT)), 1)] expected 1, got 2", answer("SELECT ABS(-g, 1) FROM rt"));
        final String object = "SQL compilation error:|invalid type [TO_OBJECT(";
        assertEquals(object + "UNARY PLUS(RT.N))] for parameter 'TO_OBJECT'", answer("SELECT TO_OBJECT(+n) FROM rt"));
        assertEquals(object + "UNARY PLUS(TO_DOUBLE(RT.G)))] for parameter 'TO_OBJECT'",
            answer("SELECT TO_OBJECT(+g) FROM rt"));
        assertEquals(object + "NEGATE(TO_DOUBLE(RT.G)))] for parameter 'TO_OBJECT'", answer("SELECT TO_OBJECT(-g) FROM rt"));
        final String lineLength = "SQL compilation error:|argument 2 to function BASE64_ENCODE needs to be constant, found '";
        assertEquals(lineLength + "UNARY PLUS(RT.N)'", answer("SELECT BASE64_ENCODE('a', +n) FROM rt"));
        assertEquals(lineLength + "NEGATE(CAST(RT.G AS FLOAT))'", answer("SELECT BASE64_ENCODE('a', -g) FROM rt"));
        assertEquals(lineLength + "UNARY PLUS(CAST(RT.G AS FLOAT))'", answer("SELECT BASE64_ENCODE('a', +g) FROM rt"));
        assertEquals(lineLength + "NEGATE(CAST(RT.V AS FLOAT))'", answer("SELECT BASE64_ENCODE('a', -v) FROM rt"));
        assertEquals(lineLength + "RT.N + 1'", answer("SELECT BASE64_ENCODE('a', n + -(-1)) FROM rt"));
    }

    /**
     * A text literal beside a number reads as the number it spells, eighteen digits wide at the scale of its value;
     * one with a blank about it or with no number reads as a text value, NUMBER(18,5).
     */
    @Test
    public void aTextLiteralReadsAtTheScaleOfItsValue() {
        assertEchoes(RANDOM, new String[][] {
            {"SELECT RANDOM(n * '1.50') FROM rt", "RT.N * (TO_NUMBER('1.50', 18, 1))"},
            {"SELECT RANDOM('1.50' * n) FROM rt", "(TO_NUMBER('1.50', 18, 1)) * RT.N"},
            {"SELECT RANDOM(n52 * '1.50') FROM rt", "RT.N52 * (TO_NUMBER('1.50', 18, 1))"},
            {"SELECT RANDOM(n * '1.0') FROM rt", "RT.N * (TO_NUMBER('1.0', 18, 0))"},
            {"SELECT RANDOM(n * '1.5e1') FROM rt", "RT.N * (TO_NUMBER('1.5e1', 18, 0))"},
            {"SELECT RANDOM(n * '1e2') FROM rt", "RT.N * (TO_NUMBER('1e2', 18, 0))"},
            {"SELECT RANDOM(n * ' 1 ') FROM rt", "RT.N * (TO_NUMBER(' 1 ', 18, 5))"},
            {"SELECT RANDOM(n * 'abc') FROM rt", "RT.N * (TO_NUMBER('abc', 18, 5))"},
            {"SELECT RANDOM(n + '1.50') FROM rt", "(CAST(RT.N AS NUMBER(38,1))) + (TO_NUMBER('1.50', 38, 1))"},
            {"SELECT RANDOM(n - '0.10') FROM rt", "(CAST(RT.N AS NUMBER(38,1))) - (TO_NUMBER('0.10', 38, 1))"},
            {"SELECT RANDOM(n + ' 1 ') FROM rt", "(CAST(RT.N AS NUMBER(38,5))) + (TO_NUMBER(' 1 ', 38, 5))"},
            {"SELECT RANDOM(n + '1e2') FROM rt", "RT.N + (TO_NUMBER('1e2'))"},
            {"SELECT RANDOM(n52 + '1.50') FROM rt", "RT.N52 + (TO_NUMBER('1.50', 18, 2))"},
            {"SELECT RANDOM(n / '1.50') FROM rt", "SCALED_ROUND_INT_DIVIDE(RT.N, TO_NUMBER('1.50', 18, 1))"},
        });
        assertEchoes(NAME, new String[][] {
            {"SELECT GETVARIABLE('1.50' * 1)", "CAST(CAST(1.5 AS NUMBER(19,1)) AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(' 1 ' * 1)", "CAST(CAST(1 AS NUMBER(19,5)) AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE('abc' * 1)", "CAST(CAST(TO_NUMBER('abc', 18, 5) AS NUMBER(19,5)) AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE('1.50' / 1)", "CAST((CAST(1.5 AS NUMBER(24,7))) / 1 AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE('1.50' + 1)", "CAST(1.5 + (CAST(1 AS NUMBER(3,1))) AS VARCHAR(134217728))"},
            {"SELECT GETVARIABLE(' 1 ' + 1)", "CAST(1 + (CAST(1 AS NUMBER(18,5))) AS VARCHAR(134217728))"},
        });
    }

    /** A chain of concatenations is held flat whichever way it nests; a CONCAT call is an operand of its own. */
    @Test
    public void aConcatenationChainIsFlat() {
        assertEchoes(RANDOM, new String[][] {
            {"SELECT RANDOM(g || g || g) FROM rt", "RT.G || RT.G || RT.G"},
            {"SELECT RANDOM(g || (g || g)) FROM rt", "RT.G || RT.G || RT.G"},
            {"SELECT RANDOM((g || g) || (g || g)) FROM rt", "RT.G || RT.G || RT.G || RT.G"},
            {"SELECT RANDOM(n || g || g) FROM rt", "(CAST(RT.N AS VARCHAR(134217728))) || RT.G || RT.G"},
            {"SELECT RANDOM(g || g || n) FROM rt", "RT.G || RT.G || (CAST(RT.N AS VARCHAR(134217728)))"},
            {"SELECT RANDOM(g || 1 || g) FROM rt", "RT.G || (CAST(1 AS VARCHAR(134217728))) || RT.G"},
            {"SELECT RANDOM(NULL || n || g) FROM rt",
                "(SYSTEM$NULL_TO_TEXT(null)) || (CAST(RT.N AS VARCHAR(134217728))) || RT.G"},
            {"SELECT RANDOM(n || NULL || g) FROM rt",
                "(CAST(RT.N AS VARCHAR(134217728))) || (SYSTEM$NULL_TO_TEXT(null)) || RT.G"},
            {"SELECT RANDOM(g || NULL || n) FROM rt",
                "RT.G || (SYSTEM$NULL_TO_TEXT(null)) || (CAST(RT.N AS VARCHAR(134217728)))"},
            {"SELECT RANDOM(g || (NULL || n)) FROM rt",
                "RT.G || (SYSTEM$NULL_TO_TEXT(null)) || (CAST(RT.N AS VARCHAR(134217728)))"},
            {"SELECT RANDOM(NULL || NULL || g) FROM rt", "(SYSTEM$NULL_TO_TEXT(null)) || (SYSTEM$NULL_TO_TEXT(null)) || RT.G"},
            {"SELECT RANDOM((g || g) || NULL) FROM rt", "RT.G || RT.G || (SYSTEM$NULL_TO_TEXT(null))"},
            {"SELECT RANDOM(NVL(NULL, NULL) || g || g) FROM rt",
                "(SYSTEM$NULL_TO_TEXT(NVL(CAST(null AS NULL), null))) || RT.G || RT.G"},
            {"SELECT RANDOM(CONCAT(g, g) || g) FROM rt", "(CONCAT(RT.G, RT.G)) || RT.G"},
            {"SELECT RANDOM(g || CONCAT(g, g)) FROM rt", "RT.G || (CONCAT(RT.G, RT.G))"},
            {"SELECT RANDOM(CONCAT(g, g || g)) FROM rt", "CONCAT(RT.G, RT.G || RT.G)"},
            {"SELECT RANDOM(UPPER(g || g) || g) FROM rt", "(UPPER(RT.G || RT.G)) || RT.G"},
        });
        assertEquals("SQL compilation error:|Invalid data type [VARCHAR(15)] for predicate [RT.G || RT.G || RT.G]",
            answer("SELECT * FROM rt WHERE g || (g || g)"));
        assertEquals("SQL compilation error:|invalid type [TO_OBJECT(RT.G || RT.G || RT.G)] for parameter 'TO_OBJECT'",
            answer("SELECT TO_OBJECT(g || g || g) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function "
            + "[ABS(RT.G || RT.G || RT.G, 1)] expected 1, got 2", answer("SELECT ABS(g || g || g, 1) FROM rt"));
        assertEquals("SQL compilation error:|argument 2 to function BASE64_ENCODE needs to be constant, found "
            + "'RT.G || RT.G || RT.G'", answer("SELECT BASE64_ENCODE('a', g || g || g) FROM rt"));
    }

    /** A VARIANT moved to an exact number is TO_NUMBER in the plan, its width spelled unless it is the default. */
    @Test
    public void aVariantMovedToANumberIsToNumber() {
        assertEchoes(RANDOM, new String[][] {
            {"SELECT RANDOM(v::INT) FROM rt", "TO_NUMBER(RT.V)"},
            {"SELECT RANDOM(CAST(v AS NUMBER(38,0))) FROM rt", "TO_NUMBER(RT.V)"},
            {"SELECT RANDOM(v::NUMBER(10,2)) FROM rt", "TO_NUMBER(RT.V, 10, 2)"},
            {"SELECT RANDOM(v::NUMBER(38,2)) FROM rt", "TO_NUMBER(RT.V, 38, 2)"},
            {"SELECT RANDOM(v::NUMBER + 1) FROM rt", "(TO_NUMBER(RT.V)) + 1"},
            {"SELECT RANDOM(TO_NUMBER(v, 10, 2)) FROM rt", "TO_NUMBER(RT.V, 10, 2)"},
            {"SELECT RANDOM(TO_DECIMAL(v)) FROM rt", "TO_NUMBER(RT.V)"},
            {"SELECT RANDOM(v::FLOAT) FROM rt", "CAST(RT.V AS FLOAT)"},
        });
    }
}
