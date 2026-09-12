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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A temporal column DEFAULT is judged on its static type: a cast is its target, whatever it casts, and a
 * clock call carries its precision. Between two temporal types the flavour and the fractional precision
 * decide — a DATE fits everything, one flavour may narrow but never widen, a TIMESTAMP_TZ fits an NTZ or
 * an LTZ column at any precision, and every other change of flavour needs equal precisions. Every cell is
 * live-verified.
 */
public class TemporalDefaultTypeTest extends BaseDatabaseTest {

    private static final String MISMATCH = "Default value data type does not match data type for column C";

    /** Column type, DEFAULT, and Y where the account creates the table. */
    private static final String[][] CELLS = {
        {"TIMESTAMP_NTZ(3)", "SYSDATE()::TIMESTAMP_NTZ(3)", "Y"},
        {"TIMESTAMP_NTZ(3)", "CURRENT_TIMESTAMP()::TIMESTAMP_NTZ(3)", "Y"},
        {"DATE", "SYSDATE()::DATE", "Y"},
        {"TIMESTAMP_NTZ(3)", "SYSDATE()", "."},
        {"TIMESTAMP_NTZ(3)", "CURRENT_TIMESTAMP()", "."},
        {"TIMESTAMP_NTZ(9)", "SYSDATE()::TIMESTAMP_NTZ(3)", "Y"},
        {"TIMESTAMP_NTZ(3)", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ(3)", "Y"},
        {"TIMESTAMP_NTZ(3)", "CAST(SYSDATE() AS TIMESTAMP_NTZ(3))", "Y"},
        {"TIMESTAMP_NTZ(3)", "SYSDATE()::TIMESTAMP_NTZ", "."},
        {"TIMESTAMP_NTZ(3)", "SYSDATE()::TIMESTAMP_NTZ(6)", "."},
        {"TIMESTAMP_NTZ(3)", "SYSDATE()::TIMESTAMP_NTZ(0)", "Y"},
        {"TIMESTAMP_NTZ(3)", "SYSDATE()::TIMESTAMP_LTZ(3)", "Y"},
        {"TIMESTAMP_NTZ(3)", "SYSDATE()::DATE", "Y"},
        {"DATE", "CURRENT_DATE()", "Y"},
        {"DATE", "CURRENT_TIMESTAMP()::DATE", "Y"},
        {"TIMESTAMP_LTZ(3)", "CURRENT_TIMESTAMP()::TIMESTAMP_LTZ(3)", "Y"},
        {"TIMESTAMP_LTZ(3)", "CURRENT_TIMESTAMP()", "."},
        {"TIMESTAMP_NTZ(3)", "TO_TIMESTAMP_NTZ(SYSDATE())::TIMESTAMP_NTZ(3)", "Y"},
        {"TIMESTAMP_NTZ(3)", "TO_TIMESTAMP_NTZ(SYSDATE())", "."},
        {"VARCHAR(10)", "CURRENT_USER()::VARCHAR(10)", "Y"},
        {"NUMBER(5,2)", "UNIFORM(1, 9, RANDOM())::NUMBER(5,2)", "Y"},
        {"TIMESTAMP_NTZ(3)", "(SYSDATE())::TIMESTAMP_NTZ(3)", "Y"},
        {"TIMESTAMP_NTZ(3)", "SYSDATE()::TIMESTAMP_NTZ(3)::TIMESTAMP_NTZ(9)", "."},
        {"TIMESTAMP_NTZ(3)", "SYSDATE()::TIMESTAMP(3)", "Y"},
        {"NUMBER(5,2)", "UNIFORM(1, 9, RANDOM())", "Y"},
        {"VARCHAR(10)", "CURRENT_USER()", "Y"},
        {"TIMESTAMP_NTZ(3)", "TO_TIMESTAMP_NTZ('2024-01-01 10:00:00')", "."},
        {"TIMESTAMP_NTZ(3)", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ(9)", "."},
        {"TIMESTAMP_NTZ(3)", "'2024-01-01 10:00:00'", "."},
        {"NUMBER(5,2)", "1.5::NUMBER(10,2)", "Y"},
        {"NUMBER(5,2)", "ABS(-1.5)::NUMBER(5,2)", "Y"},
        {"NUMBER(5,2)", "ABS(-1.5)", "Y"},
        {"DATE", "CURRENT_DATE", "Y"},
        {"TIME(3)", "CURRENT_TIME()", "."},
        {"TIME(3)", "CURRENT_TIME()::TIME(3)", "Y"},
        {"TIME(9)", "CURRENT_TIME()", "Y"},
        {"TIME(3)", "'10:00:00'::TIME(6)", "."},
        {"TIME", "CURRENT_TIME()", "Y"},
        {"TIME(3)", "'10:00:00'::TIME(0)", "Y"},
        {"TIMESTAMP_NTZ(3)", "CURRENT_TIME()", "."},
        {"TIME(3)", "CURRENT_TIMESTAMP()::TIME(3)", "Y"},
        {"TIMESTAMP_TZ(3)", "CURRENT_TIMESTAMP()::TIMESTAMP_TZ(3)", "Y"},
        {"TIMESTAMP_TZ(3)", "CURRENT_TIMESTAMP()", "."},
        {"TIMESTAMP_NTZ(3)", "CURRENT_TIMESTAMP()::TIMESTAMP_TZ(3)", "Y"},
        {"TIMESTAMP_TZ(3)", "SYSDATE()::TIMESTAMP_NTZ(3)", "Y"},
        {"TIMESTAMP_NTZ(3)", "LOCALTIMESTAMP()", "."},
        {"TIMESTAMP_NTZ(3)", "LOCALTIMESTAMP()::TIMESTAMP_NTZ(3)", "Y"},
        {"TIMESTAMP_NTZ", "SYSDATE()", "Y"},
        {"TIMESTAMP_LTZ", "CURRENT_TIMESTAMP()", "Y"},
        {"TIMESTAMP_NTZ(3)", "CURRENT_TIMESTAMP(3)", "Y"},
        {"TIMESTAMP_LTZ(3)", "CURRENT_TIMESTAMP(3)", "Y"},
        {"TIMESTAMP_NTZ(3)", "DATEADD(day, 1, SYSDATE()::TIMESTAMP_NTZ(3))", "."},
        {"TIMESTAMP_NTZ(3)", "CURRENT_DATE()", "Y"},
        {"DATE", "SYSDATE()", "."},
        {"TIMESTAMP_NTZ(3)", "CURRENT_TIMESTAMP", "."},
        {"TIMESTAMP_NTZ(3)", "TO_TIMESTAMP_NTZ('2024-01-01 10:00:00.123')::TIMESTAMP_NTZ(3)", "Y"},
        {"TIMESTAMP_NTZ(3)", "NULL::TIMESTAMP_NTZ(9)", "."},
        {"TIMESTAMP_NTZ(3)", "IFF(TRUE, SYSDATE()::TIMESTAMP_NTZ(3), NULL)", "Y"},
        {"TIMESTAMP_NTZ(3)", "'2024-01-01 10:00:00'::TIMESTAMP_NTZ", "."},
        {"TIMESTAMP_NTZ(9)", "SYSDATE()", "Y"},
        {"TIMESTAMP_NTZ(3)", "SYSDATE()::TIMESTAMP_NTZ(3) NOT NULL", "Y"},
        {"TIMESTAMP_NTZ(9)", "CURRENT_TIME()", "Y"},
        {"TIMESTAMP_NTZ(9)", "'10:00:00'::TIME(0)", "."},
        {"TIMESTAMP_NTZ", "CURRENT_TIME()", "Y"},
        {"TIME(9)", "SYSDATE()::TIMESTAMP_NTZ(0)", "."},
        {"TIMESTAMP_NTZ(9)", "DATEADD(day, 1, SYSDATE()::TIMESTAMP_NTZ(3))", "Y"},
        {"TIMESTAMP_NTZ(3)", "DATEADD(day, 1, CURRENT_DATE())", "Y"},
        {"DATE", "DATEADD(day, 1, CURRENT_DATE())", "Y"},
        {"TIMESTAMP_NTZ(3)", "TIMESTAMPADD(hour, 1, SYSDATE()::TIMESTAMP_NTZ(3))", "."},
        {"TIMESTAMP_NTZ(3)", "COALESCE(SYSDATE()::TIMESTAMP_NTZ(3), SYSDATE()::TIMESTAMP_NTZ(3))", "Y"},
        {"TIMESTAMP_NTZ(3)", "TO_TIMESTAMP_NTZ('2024-01-01', 'YYYY-MM-DD')", "."},
        {"TIMESTAMP_NTZ(3)", "CASE WHEN TRUE THEN SYSDATE()::TIMESTAMP_NTZ(3) END", "Y"},
        {"TIMESTAMP_NTZ(3)", "GREATEST(SYSDATE()::TIMESTAMP_NTZ(3), SYSDATE()::TIMESTAMP_NTZ(3))", "Y"},
        {"TIME(3)", "TIME_FROM_PARTS(1, 2, 3)", "."},
        {"TIMESTAMP_NTZ(3)", "TIMESTAMP_NTZ_FROM_PARTS(2024, 1, 1, 0, 0, 0)", "."},
        {"TIME(3)", "'10:00:00'::TIME(3)", "Y"},
        {"TIME(3)", "CURRENT_DATE()", "Y"},
        {"TIMESTAMP_NTZ(3)", "'10:00:00'::TIME(3)", "Y"},
        {"TIMESTAMP_NTZ(9)", "'10:00:00'::TIME(9)", "Y"},
        {"TIMESTAMP_NTZ(6)", "'10:00:00'::TIME(3)", "."},
        {"TIMESTAMP_LTZ(9)", "CURRENT_TIME()", "Y"},
        {"TIMESTAMP_NTZ(9)", "CURRENT_TIME()::TIME(0)", "."},
        {"TIMESTAMP_NTZ(0)", "'10:00:00'::TIME(0)", "Y"},
        {"TIMESTAMP_NTZ(9)", "'10:00:00'::TIME", "Y"},
        {"TIMESTAMP_NTZ(9)", "TIME_FROM_PARTS(1, 2, 3)", "Y"},
        {"TIMESTAMP_NTZ(3)", "CURRENT_TIMESTAMP()::TIMESTAMP_TZ(9)", "Y"},
        {"TIMESTAMP_NTZ(3)", "CONVERT_TIMEZONE('UTC', 'America/New_York', SYSDATE()::TIMESTAMP_NTZ(3))", "."},
        {"TIMESTAMP_NTZ(3)", "CURRENT_TIMESTAMP(6)", "."},
        {"TIMESTAMP_NTZ(6)", "CURRENT_TIMESTAMP(3)", "."},
        {"TIME(3)", "CURRENT_TIME(3)", "Y"},
        {"TIMESTAMP_LTZ(3)", "LOCALTIMESTAMP(3)", "Y"},
    };

    private static final String[] FLAVOURS = {"TIMESTAMP_NTZ", "TIMESTAMP_LTZ", "TIMESTAMP_TZ", "TIME"};

    private static final int[] PRECISIONS = {0, 3, 9};

    /**
     * One row per DEFAULT type and one column per column type, both in FLAVOURS x PRECISIONS order; the
     * last row is a DATE. Y where the account creates the table.
     */
    private static final String[] MATRIX = {
        "YYYY..Y.....", // TIMESTAMP_NTZ(0)
        ".YY.Y..Y....", // TIMESTAMP_NTZ(3)
        "..Y..Y..Y...", // TIMESTAMP_NTZ(9)
        "Y..YYYY.....", // TIMESTAMP_LTZ(0)
        ".Y..YY.Y....", // TIMESTAMP_LTZ(3)
        "..Y..Y..Y...", // TIMESTAMP_LTZ(9)
        "YYYYYYYYY...", // TIMESTAMP_TZ(0)
        "YYYYYY.YY...", // TIMESTAMP_TZ(3)
        "YYYYYY..Y...", // TIMESTAMP_TZ(9)
        "Y..Y..Y..YYY", // TIME(0)
        ".Y..Y..Y..YY", // TIME(3)
        "..Y..Y..Y..Y", // TIME(9)
        "YYYYYYYYYYYY", // DATE
    };

    private final List<String> mismatches = new ArrayList<>();

    private int tables;

    private void check(final String columnType, final String defaultSql, final boolean accepted) {
        tables++;
        final String sql = "CREATE TABLE t" + tables + " (c " + columnType + " DEFAULT " + defaultSql + ")";
        String refusal = null;
        try {
            engine.execute(sql);
        } catch (final RuntimeException e) {
            refusal = String.valueOf(e.getMessage());
        }
        if (accepted && refusal != null) {
            mismatches.add(sql + " -> refused: " + refusal);
        } else if (!accepted && (refusal == null || !refusal.contains(MISMATCH))) {
            mismatches.add(sql + " -> " + (refusal == null ? "created" : refusal));
        }
    }

    private static String temporalLiteral(final String flavour, final int precision) {
        final String text = "TIME".equals(flavour) ? "'10:00:00'" : "'2024-01-01 10:00:00'";
        return text + "::" + flavour + "(" + precision + ")";
    }

    @Test
    public void aDefaultIsJudgedOnItsStaticType() {
        for (final String[] cell : CELLS) {
            check(cell[0], cell[1], "Y".equals(cell[2]));
        }
        assertEquals("", String.join("\n", mismatches));
    }

    @Test
    public void theFlavourAndPrecisionMatrix() {
        for (int row = 0; row < MATRIX.length; row++) {
            final String source = row == MATRIX.length - 1 ? "'2024-01-01'::DATE"
                : temporalLiteral(FLAVOURS[row / PRECISIONS.length], PRECISIONS[row % PRECISIONS.length]);
            for (int col = 0; col < FLAVOURS.length * PRECISIONS.length; col++) {
                final String columnType =
                    FLAVOURS[col / PRECISIONS.length] + "(" + PRECISIONS[col % PRECISIONS.length] + ")";
                check(columnType, source, MATRIX[row].charAt(col) == 'Y');
            }
        }
        assertEquals("", String.join("\n", mismatches));
    }

    @Test
    public void aCastDefaultFillsTheRow() {
        engine.execute("CREATE TABLE d1 (c TIMESTAMP_NTZ(3) DEFAULT SYSDATE()::TIMESTAMP_NTZ(3))");
        engine.execute("INSERT INTO d1 (c) VALUES (DEFAULT)");
        assertEquals(1, engine.executeQuery("SELECT c FROM d1 WHERE c IS NOT NULL").getRows().size());
        engine.execute("CREATE TABLE d2 (c TIMESTAMP_NTZ(3) DEFAULT SYSDATE()::TIMESTAMP_NTZ(3)) AS "
            + "SELECT SYSDATE()::TIMESTAMP_NTZ(3) AS c");
        assertEquals(1, engine.executeQuery("SELECT c FROM d2").getRows().size());
    }
}
