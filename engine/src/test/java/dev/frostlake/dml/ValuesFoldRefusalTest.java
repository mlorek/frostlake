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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A VALUES clause refuses what live's compiler does not fold to a constant — a division, and SQRT, EXP, LN,
 * LOG, POWER, the trigonometric family, SQUARE, CBRT, ROUND, CEIL, TRUNC, SIGN, FACTORIAL, BITAND, HASH and
 * WIDTH_BUCKET — anywhere in the item, in INSERT and in the VALUES table constructor alike, echoing the item
 * as live rewrites it. What live folds — FLOOR, MOD, DIV0, GREATEST, COALESCE, TO_DOUBLE, DEGREES, BITOR,
 * {@code + - * %} — is taken. A name and the value count are refused first. Every cell is live-verified.
 */
public class ValuesFoldRefusalTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE OR REPLACE TABLE tv (v VARCHAR)");
    }

    /** The statement's refusal on one line, or empty when it runs. */
    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String inserted(final String item) {
        return refusal("INSERT INTO tv VALUES (" + item + ")");
    }

    private static String invalid(final String echo) {
        return "SQL compilation error:|Invalid expression [" + echo + "] in VALUES clause";
    }

    @Test
    public void aFunctionLiveDoesNotFoldIsRefused() {
        final String[][] cells = {
            {"SQRT(4)", "SQRT(4.0)"}, {"SQRT(4.5)", "SQRT(4.5)"}, {"SQRT(-4)", "SQRT(-4.0)"},
            {"SQRT(1e2)", "SQRT(100.0)"}, {"SQRT('4')", "SQRT(4.0)"}, {"SQRT(2::FLOAT)", "SQRT(2.0)"},
            {"EXP(1)", "EXP(1.0)"}, {"EXP(1.5)", "EXP(1.5)"}, {"LN(2)", "LN(2.0)"}, {"LOG(10, 100)", "LOG(10.0, 100.0)"},
            {"POWER(2, 3)", "POWER(2.0, 3.0)"}, {"POWER(2.5, 2)", "POWER(2.5, 2.0)"}, {"POW(2, 3)", "POW(2.0, 3.0)"},
            {"SIN(0)", "SIN(0.0)"}, {"COS(0)", "COS(0.0)"}, {"TAN(0)", "TAN(0.0)"}, {"COT(1)", "COT(1.0)"},
            {"ASIN(0.5)", "ASIN(0.5)"}, {"ACOS(0)", "ACOS(0.0)"}, {"ATAN(0)", "ATAN(0.0)"},
            {"ATAN2(1, 1)", "ATAN2(1.0, 1.0)"}, {"SINH(0)", "SINH(0.0)"}, {"COSH(0)", "COSH(0.0)"},
            {"TANH(0)", "TANH(0.0)"}, {"ASINH(0)", "ASINH(0.0)"}, {"ACOSH(1)", "ACOSH(1.0)"}, {"ATANH(0)", "ATANH(0.0)"},
            {"SQUARE(2)", "SQUARE(2.0)"}, {"SQUARE(1.5)", "SQUARE(1.5)"}, {"CBRT(8)", "CBRT(8.0)"},
            {"HAVERSINE(0, 0, 1, 1)", "HAVERSINE(0.0, 0.0, 1.0, 1.0)"},
            {"ROUND(1.5)", "ROUND(1.5)"}, {"ROUND(1.567, 2)", "ROUND(1.567, 2)"}, {"CEIL(1.5)", "CEIL(1.5)"},
            {"CEIL(1.5::FLOAT)", "CEIL(1.5)"}, {"TRUNC(1.5)", "TRUNC(1.5)"}, {"TRUNCATE(1.5)", "TRUNCATE(1.5)"},
            {"TRUNC(1.5::FLOAT)", "TRUNC(1.5)"}, {"SIGN(-1)", "SIGN(-1)"}, {"SIGN(1.5::FLOAT)", "SIGN(1.5)"},
            {"FACTORIAL(3)", "FACTORIAL(3)"}, {"BITAND(1, 3)", "BITAND(1, 3)"}, {"BITXOR(1, 3)", "BITXOR(1, 3)"},
            {"BITNOT(1)", "BITNOT(1)"}, {"BITSHIFTLEFT(1, 2)", "BITSHIFTLEFT(1, 2)"}, {"HASH(1)", "HASH(1)"},
            {"WIDTH_BUCKET(1, 0, 10, 5)", "WIDTH_BUCKET(1, 0, 10, 5)"},
        };
        for (final String[] cell : cells) {
            assertEquals(invalid(cell[1]), inserted(cell[0]), cell[0]);
        }
    }

    @Test
    public void theEchoIsLivesRewrite() {
        final String[][] cells = {
            {"1 / 3", "1 / 3"}, {"1.5 / 2", "1.5 / 2"}, {"1::FLOAT / 3", "1.0 / 3.0"}, {"(1 / 3)", "1 / 3"},
            {"1 / 3 + 1", "(1 / 3) + 1"}, {"1 / 3 * 3", "(1 / 3) * 3"}, {"3 * (1 / 3)", "3 * (1 / 3)"},
            {"SQRT(4) + 1", "(SQRT(4.0)) + 1.0"}, {"SQRT(4) * 2", "(SQRT(4.0)) * 2.0"}, {"2 * SQRT(4)", "2.0 * (SQRT(4.0))"},
            {"SQRT(4) - 1", "(SQRT(4.0)) - 1.0"}, {"SQRT(4) + SQRT(9)", "(SQRT(4.0)) + (SQRT(9.0))"},
            {"-SQRT(4)", "NEGATE(SQRT(4.0))"}, {"ABS(SQRT(4))", "ABS(SQRT(4.0))"}, {"ROUND(SQRT(4))", "ROUND(SQRT(4.0))"},
            {"FLOOR(SQRT(4))", "FLOOR(SQRT(4.0))"}, {"ABS(1 / 3)", "ABS(1 / 3)"}, {"MOD(1 / 3, 2)", "MOD(1 / 3, 2)"},
            {"SQRT(4)::VARCHAR", "CAST(SQRT(4.0) AS VARCHAR(134217728))"},
            {"TO_VARCHAR(1 / 3)", "CAST(1 / 3 AS VARCHAR(134217728))"},
            {"UPPER(TO_VARCHAR(SQRT(4)))", "UPPER(CAST(SQRT(4.0) AS VARCHAR(134217728)))"},
            {"SQRT(4)::NUMBER", "CAST(SQRT(4.0) AS NUMBER(38,0))"},
            {"CONCAT('a', 1 / 2)", "CONCAT('a', CAST(1 / 2 AS VARCHAR(134217728)))"},
            {"IFF(TRUE, SQRT(4), 1)", "SQRT(4.0)"}, {"CASE WHEN TRUE THEN SQRT(4) END", "ENSURE_NULLABLE(SQRT(4.0))"},
            {"NVL(SQRT(4), 1)", "SQRT(4.0)"},
        };
        for (final String[] cell : cells) {
            assertEquals(invalid(cell[1]), inserted(cell[0]), cell[0]);
        }
    }

    @Test
    public void whatLiveFoldsIsTaken() {
        final String[] taken = {
            "FLOOR(1.5)", "MOD(5, 3)", "MOD(5.5, 2)", "DIV0(4, 2)", "DIV0NULL(4, 2)", "GREATEST(1, 2)", "LEAST(1, 2)",
            "COALESCE(1, 2)", "NVL(1, 2)", "ZEROIFNULL(1)", "TO_DOUBLE('2')", "RADIANS(1)", "DEGREES(1)", "2 * 3",
            "2 - 3", "1::FLOAT * 3", "1::FLOAT - 3", "-(1::FLOAT)", "10 % 3", "BITOR(1, 3)",
        };
        for (final String item : taken) {
            assertEquals("", inserted(item), item);
        }
        assertEquals("", refusal("INSERT INTO tv SELECT SQRT(4)"));
    }

    @Test
    public void theTableConstructorRefusesAlikeAfterNamesAndCount() {
        assertEquals(invalid("SQRT(4.0)"), refusal("SELECT $1 FROM VALUES (SQRT(4))"));
        assertEquals(invalid("1 / 3"), refusal("SELECT $1 FROM VALUES (1 / 3)"));
        assertEquals(invalid("SQRT(4.0)"), refusal("INSERT INTO tv SELECT * FROM VALUES (SQRT(4))"));
        assertEquals(invalid("SQRT(4.0)"), refusal("INSERT INTO tv VALUES ('a'), (SQRT(4))"));
        assertEquals(invalid("SQRT(4.0)"), refusal("INSERT INTO tv (v) VALUES (SQRT(4))"));
        assertEquals(invalid("SQRT(4.0)"), refusal("INSERT INTO tv VALUES (SQRT(4)), (1 / 3)"));
        assertEquals(invalid("1 / 3"), refusal("INSERT INTO tv VALUES (1 / 3), (SQRT(4))"));
        assertEquals("SQL compilation error:|Insert value list does not match column list expecting 1 but got 2",
            refusal("INSERT INTO tv VALUES (UPPER('a'), SQRT(4))"));
        assertEquals("SQL compilation error: error line 1 at position 28|invalid identifier 'N'",
            refusal("INSERT INTO tv VALUES (SQRT(n))"));
    }
}
