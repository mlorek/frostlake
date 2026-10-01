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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A comparison across families that do not meet is refused while the statement compiles, naming the right
 * operand from the plan and the type the left one expects: {@code Can not convert parameter 'FT.D' of type
 * [DATE] into expected type [NUMBER(38,0)]}, or {@code incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]}.
 * Every comparison operator shares the rule, and so do IN lists, BETWEEN, a simple CASE, IS DISTINCT FROM
 * and DECODE. A predicate is a BOOLEAN that meets only a BOOLEAN, and a BOOLEAN on the left reads the text on
 * its right as a BOOLEAN. Every cell is live-verified.
 */
public class IncomparableFamiliesTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE ft (b BOOLEAN, bi BINARY, ar ARRAY, ob OBJECT, d DATE, tm TIME, tn TIMESTAMP_NTZ, "
            + "tl TIMESTAMP_LTZ, tz TIMESTAMP_TZ, n NUMBER, f FLOAT, v VARCHAR(10), va VARIANT, g VARCHAR(10), i INT)");
        engine.execute("INSERT INTO ft SELECT TRUE, X'00', ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('a', 1), '2020-01-01'::DATE, "
            + "'10:00:00'::TIME, '2020-01-01 10:00:00'::TIMESTAMP_NTZ, '2020-01-01 10:00:00'::TIMESTAMP_LTZ, "
            + "'2020-01-01 10:00:00 +01:00'::TIMESTAMP_TZ, 1, 1.5, '1', TO_VARIANT(1), 'abc', 3");
        engine.execute("CREATE TABLE re (a INT)");
        engine.execute("CREATE TABLE fte (d DATE)");
    }

    /** Every row's first cell, lower-cased and joined by a bar. */
    private String answer(final String sql) {
        final StringBuilder answer = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            answer.append(String.valueOf(row.getValue(0)).toLowerCase());
        }
        return answer.toString();
    }

    private void assertAnswers(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    private void assertRefused(final String[][] cells) {
        for (final String[] cell : cells) {
            final String sql = cell[0];
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    if (sql.startsWith("SELECT")) {
                        engine.executeQuery(sql);
                    } else {
                        engine.execute(sql);
                    }
                }
            }, sql);
            assertTrue(String.valueOf(refused.getMessage()).contains(cell[1]),
                sql + " should be refused with [" + cell[1] + "] but read: " + refused.getMessage());
        }
    }

    /** Every comparison operator over every pair of families that do not meet. */
    @Test
    public void familiesThatDoNotMeetAreRefusedWhileCompiling() {
        assertRefused(new String[][] {
            {"SELECT b = bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [BOOLEAN]"},
            {"SELECT b = ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [BOOLEAN]"},
            {"SELECT b = ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [BOOLEAN]"},
            {"SELECT b = d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [BOOLEAN]"},
            {"SELECT b = tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [BOOLEAN]"},
            {"SELECT b = tn FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TN' of type [TIMESTAMP_NTZ(9)] into expected type [BOOLEAN]"},
            {"SELECT b = tl FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TL' of type [TIMESTAMP_LTZ(9)] into expected type [BOOLEAN]"},
            {"SELECT b = tz FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TZ' of type [TIMESTAMP_TZ(9)] into expected type [BOOLEAN]"},
            {"SELECT bi = b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [BINARY(8388608)]"},
            {"SELECT bi = ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [BINARY(8388608)]"},
            {"SELECT bi = ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [BINARY(8388608)]"},
            {"SELECT bi = d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [BINARY(8388608)]"},
            {"SELECT bi = tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [BINARY(8388608)]"},
            {"SELECT bi = tn FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TN' of type [TIMESTAMP_NTZ(9)] into expected type [BINARY(8388608)]"},
            {"SELECT bi = tl FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TL' of type [TIMESTAMP_LTZ(9)] into expected type [BINARY(8388608)]"},
            {"SELECT bi = tz FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TZ' of type [TIMESTAMP_TZ(9)] into expected type [BINARY(8388608)]"},
            {"SELECT bi = n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [BINARY(8388608)]"},
            {"SELECT bi = f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [BINARY(8388608)]"},
            {"SELECT bi = v FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.V' of type [VARCHAR(10)] into expected type [BINARY(8388608)]"},
            {"SELECT bi = va FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.VA' of type [VARIANT] into expected type [BINARY(8388608)]"},
            {"SELECT ar = b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [ARRAY]"},
            {"SELECT ar = bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [ARRAY]"},
            {"SELECT ar = ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [ARRAY]"},
            {"SELECT ar = d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [ARRAY]"},
            {"SELECT ar = tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [ARRAY]"},
            {"SELECT ar = tn FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TN' of type [TIMESTAMP_NTZ(9)] into expected type [ARRAY]"},
            {"SELECT ar = tl FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TL' of type [TIMESTAMP_LTZ(9)] into expected type [ARRAY]"},
            {"SELECT ar = tz FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TZ' of type [TIMESTAMP_TZ(9)] into expected type [ARRAY]"},
            {"SELECT ar = n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [ARRAY]"},
            {"SELECT ar = f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [ARRAY]"},
            {"SELECT ar = v FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.V' of type [VARCHAR(10)] into expected type [ARRAY]"},
            {"SELECT ob = b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [OBJECT]"},
            {"SELECT ob = bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [OBJECT]"},
            {"SELECT ob = ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [OBJECT]"},
            {"SELECT ob = d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [OBJECT]"},
            {"SELECT ob = tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [OBJECT]"},
            {"SELECT ob = tn FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TN' of type [TIMESTAMP_NTZ(9)] into expected type [OBJECT]"},
            {"SELECT ob = tl FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TL' of type [TIMESTAMP_LTZ(9)] into expected type [OBJECT]"},
            {"SELECT ob = tz FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TZ' of type [TIMESTAMP_TZ(9)] into expected type [OBJECT]"},
            {"SELECT ob = n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [OBJECT]"},
            {"SELECT ob = f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [OBJECT]"},
            {"SELECT ob = v FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.V' of type [VARCHAR(10)] into expected type [OBJECT]"},
            {"SELECT d = b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [DATE]"},
            {"SELECT d = bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [DATE]"},
            {"SELECT d = ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [DATE]"},
            {"SELECT d = ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [DATE]"},
            {"SELECT d = tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [DATE]"},
            {"SELECT d = n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [DATE]"},
            {"SELECT d = f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [DATE]"},
            {"SELECT tm = b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [TIME(9)]"},
            {"SELECT tm = bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [TIME(9)]"},
            {"SELECT tm = ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [TIME(9)]"},
            {"SELECT tm = ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [TIME(9)]"},
            {"SELECT tm = d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [TIME(9)]"},
            {"SELECT tm = tn FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT tm = tl FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
            {"SELECT tm = tz FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"SELECT tm = n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [TIME(9)]"},
            {"SELECT tm = f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [TIME(9)]"},
            {"SELECT tn = b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [TIMESTAMP_NTZ(9)]"},
            {"SELECT tn = bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [TIMESTAMP_NTZ(9)]"},
            {"SELECT tn = ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [TIMESTAMP_NTZ(9)]"},
            {"SELECT tn = ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [TIMESTAMP_NTZ(9)]"},
            {"SELECT tn = tm FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT tn = n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [TIMESTAMP_NTZ(9)]"},
            {"SELECT tn = f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [TIMESTAMP_NTZ(9)]"},
            {"SELECT tl = b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [TIMESTAMP_LTZ(9)]"},
            {"SELECT tl = bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [TIMESTAMP_LTZ(9)]"},
            {"SELECT tl = ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [TIMESTAMP_LTZ(9)]"},
            {"SELECT tl = ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [TIMESTAMP_LTZ(9)]"},
            {"SELECT tl = tm FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
            {"SELECT tl = n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [TIMESTAMP_LTZ(9)]"},
            {"SELECT tl = f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [TIMESTAMP_LTZ(9)]"},
            {"SELECT tz = b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [TIMESTAMP_TZ(9)]"},
            {"SELECT tz = bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [TIMESTAMP_TZ(9)]"},
            {"SELECT tz = ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [TIMESTAMP_TZ(9)]"},
            {"SELECT tz = ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [TIMESTAMP_TZ(9)]"},
            {"SELECT tz = tm FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"SELECT tz = n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [TIMESTAMP_TZ(9)]"},
            {"SELECT tz = f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [TIMESTAMP_TZ(9)]"},
            {"SELECT n = bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [NUMBER(38,0)]"},
            {"SELECT n = ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [NUMBER(38,0)]"},
            {"SELECT n = ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [NUMBER(38,0)]"},
            {"SELECT n = d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT n = tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [NUMBER(38,0)]"},
            {"SELECT n = tn FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TN' of type [TIMESTAMP_NTZ(9)] into expected type [NUMBER(38,0)]"},
            {"SELECT n = tl FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TL' of type [TIMESTAMP_LTZ(9)] into expected type [NUMBER(38,0)]"},
            {"SELECT n = tz FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TZ' of type [TIMESTAMP_TZ(9)] into expected type [NUMBER(38,0)]"},
            {"SELECT f = bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [FLOAT]"},
            {"SELECT f = ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [FLOAT]"},
            {"SELECT f = ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [FLOAT]"},
            {"SELECT f = d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [FLOAT]"},
            {"SELECT f = tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [FLOAT]"},
            {"SELECT f = tn FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TN' of type [TIMESTAMP_NTZ(9)] into expected type [FLOAT]"},
            {"SELECT f = tl FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TL' of type [TIMESTAMP_LTZ(9)] into expected type [FLOAT]"},
            {"SELECT f = tz FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TZ' of type [TIMESTAMP_TZ(9)] into expected type [FLOAT]"},
            {"SELECT v = bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [VARCHAR(10)]"},
            {"SELECT v = ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [VARCHAR(10)]"},
            {"SELECT v = ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [VARCHAR(10)]"},
            {"SELECT va = bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [VARIANT]"},
            {"SELECT b < bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [BOOLEAN]"},
            {"SELECT b < ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [BOOLEAN]"},
            {"SELECT b < ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [BOOLEAN]"},
            {"SELECT b < d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [BOOLEAN]"},
            {"SELECT b < tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [BOOLEAN]"},
            {"SELECT b < tn FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TN' of type [TIMESTAMP_NTZ(9)] into expected type [BOOLEAN]"},
            {"SELECT b < tl FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TL' of type [TIMESTAMP_LTZ(9)] into expected type [BOOLEAN]"},
            {"SELECT b < tz FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TZ' of type [TIMESTAMP_TZ(9)] into expected type [BOOLEAN]"},
            {"SELECT bi < b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [BINARY(8388608)]"},
            {"SELECT bi < ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [BINARY(8388608)]"},
            {"SELECT bi < ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [BINARY(8388608)]"},
            {"SELECT bi < d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [BINARY(8388608)]"},
            {"SELECT bi < tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [BINARY(8388608)]"},
            {"SELECT bi < tn FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TN' of type [TIMESTAMP_NTZ(9)] into expected type [BINARY(8388608)]"},
            {"SELECT bi < tl FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TL' of type [TIMESTAMP_LTZ(9)] into expected type [BINARY(8388608)]"},
            {"SELECT bi < tz FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TZ' of type [TIMESTAMP_TZ(9)] into expected type [BINARY(8388608)]"},
            {"SELECT bi < n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [BINARY(8388608)]"},
            {"SELECT bi < f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [BINARY(8388608)]"},
            {"SELECT bi < v FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.V' of type [VARCHAR(10)] into expected type [BINARY(8388608)]"},
            {"SELECT bi < va FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.VA' of type [VARIANT] into expected type [BINARY(8388608)]"},
            {"SELECT ar < b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [ARRAY]"},
            {"SELECT ar < bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [ARRAY]"},
            {"SELECT ar < ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [ARRAY]"},
            {"SELECT ar < d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [ARRAY]"},
            {"SELECT ar < tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [ARRAY]"},
            {"SELECT ar < tn FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TN' of type [TIMESTAMP_NTZ(9)] into expected type [ARRAY]"},
            {"SELECT ar < tl FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TL' of type [TIMESTAMP_LTZ(9)] into expected type [ARRAY]"},
            {"SELECT ar < tz FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TZ' of type [TIMESTAMP_TZ(9)] into expected type [ARRAY]"},
            {"SELECT ar < n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [ARRAY]"},
            {"SELECT ar < f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [ARRAY]"},
            {"SELECT ar < v FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.V' of type [VARCHAR(10)] into expected type [ARRAY]"},
            {"SELECT ob < b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [OBJECT]"},
            {"SELECT ob < bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [OBJECT]"},
            {"SELECT ob < ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [OBJECT]"},
            {"SELECT ob < d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [OBJECT]"},
            {"SELECT ob < tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [OBJECT]"},
            {"SELECT ob < tn FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TN' of type [TIMESTAMP_NTZ(9)] into expected type [OBJECT]"},
            {"SELECT ob < tl FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TL' of type [TIMESTAMP_LTZ(9)] into expected type [OBJECT]"},
            {"SELECT ob < tz FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TZ' of type [TIMESTAMP_TZ(9)] into expected type [OBJECT]"},
            {"SELECT ob < n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [OBJECT]"},
            {"SELECT ob < f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [OBJECT]"},
            {"SELECT ob < v FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.V' of type [VARCHAR(10)] into expected type [OBJECT]"},
            {"SELECT d < b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [DATE]"},
            {"SELECT d < bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [DATE]"},
            {"SELECT d < ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [DATE]"},
            {"SELECT d < ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [DATE]"},
            {"SELECT d < tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [DATE]"},
            {"SELECT d < n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [DATE]"},
            {"SELECT d < f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [DATE]"},
            {"SELECT tm < b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [TIME(9)]"},
            {"SELECT tm < bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [TIME(9)]"},
            {"SELECT tm < ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [TIME(9)]"},
            {"SELECT tm < ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [TIME(9)]"},
            {"SELECT tm < d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [TIME(9)]"},
            {"SELECT tm < tn FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT tm < tl FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
            {"SELECT tm < tz FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"SELECT tm < n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [TIME(9)]"},
            {"SELECT tm < f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [TIME(9)]"},
            {"SELECT tn < b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [TIMESTAMP_NTZ(9)]"},
            {"SELECT tn < bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [TIMESTAMP_NTZ(9)]"},
            {"SELECT tn < ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [TIMESTAMP_NTZ(9)]"},
            {"SELECT tn < ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [TIMESTAMP_NTZ(9)]"},
            {"SELECT tn < tm FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT tn < n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [TIMESTAMP_NTZ(9)]"},
            {"SELECT tn < f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [TIMESTAMP_NTZ(9)]"},
            {"SELECT tl < b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [TIMESTAMP_LTZ(9)]"},
            {"SELECT tl < bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [TIMESTAMP_LTZ(9)]"},
            {"SELECT tl < ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [TIMESTAMP_LTZ(9)]"},
            {"SELECT tl < ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [TIMESTAMP_LTZ(9)]"},
            {"SELECT tl < tm FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
            {"SELECT tl < n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [TIMESTAMP_LTZ(9)]"},
            {"SELECT tl < f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [TIMESTAMP_LTZ(9)]"},
            {"SELECT tz < b FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.B' of type [BOOLEAN] into expected type [TIMESTAMP_TZ(9)]"},
            {"SELECT tz < bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [TIMESTAMP_TZ(9)]"},
            {"SELECT tz < ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [TIMESTAMP_TZ(9)]"},
            {"SELECT tz < ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [TIMESTAMP_TZ(9)]"},
            {"SELECT tz < tm FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"SELECT tz < n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [TIMESTAMP_TZ(9)]"},
            {"SELECT tz < f FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.F' of type [FLOAT] into expected type [TIMESTAMP_TZ(9)]"},
            {"SELECT n < bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [NUMBER(38,0)]"},
            {"SELECT n < ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [NUMBER(38,0)]"},
            {"SELECT n < ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [NUMBER(38,0)]"},
            {"SELECT n < d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT n < tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [NUMBER(38,0)]"},
            {"SELECT n < tn FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TN' of type [TIMESTAMP_NTZ(9)] into expected type [NUMBER(38,0)]"},
            {"SELECT n < tl FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TL' of type [TIMESTAMP_LTZ(9)] into expected type [NUMBER(38,0)]"},
            {"SELECT n < tz FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TZ' of type [TIMESTAMP_TZ(9)] into expected type [NUMBER(38,0)]"},
            {"SELECT f < bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [FLOAT]"},
            {"SELECT f < ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [FLOAT]"},
            {"SELECT f < ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [FLOAT]"},
            {"SELECT f < d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [FLOAT]"},
            {"SELECT f < tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [FLOAT]"},
            {"SELECT f < tn FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TN' of type [TIMESTAMP_NTZ(9)] into expected type [FLOAT]"},
            {"SELECT f < tl FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TL' of type [TIMESTAMP_LTZ(9)] into expected type [FLOAT]"},
            {"SELECT f < tz FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TZ' of type [TIMESTAMP_TZ(9)] into expected type [FLOAT]"},
            {"SELECT v < bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [VARCHAR(10)]"},
            {"SELECT v < ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [VARCHAR(10)]"},
            {"SELECT v < ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [VARCHAR(10)]"},
            {"SELECT va < bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [VARIANT]"},
            {"SELECT n <> d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT d <> n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [DATE]"},
            {"SELECT b <> bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [BOOLEAN]"},
            {"SELECT v <> ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [VARCHAR(10)]"},
            {"SELECT ar <> ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [ARRAY]"},
            {"SELECT tm <> tn FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT d <> tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [DATE]"},
            {"SELECT va <> bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [VARIANT]"},
            {"SELECT n != d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT d != n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [DATE]"},
            {"SELECT b != bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [BOOLEAN]"},
            {"SELECT v != ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [VARCHAR(10)]"},
            {"SELECT ar != ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [ARRAY]"},
            {"SELECT tm != tn FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT d != tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [DATE]"},
            {"SELECT va != bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [VARIANT]"},
            {"SELECT n <= d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT d <= n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [DATE]"},
            {"SELECT b <= bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [BOOLEAN]"},
            {"SELECT v <= ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [VARCHAR(10)]"},
            {"SELECT ar <= ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [ARRAY]"},
            {"SELECT tm <= tn FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT d <= tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [DATE]"},
            {"SELECT va <= bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [VARIANT]"},
            {"SELECT n > d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT d > n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [DATE]"},
            {"SELECT b > bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [BOOLEAN]"},
            {"SELECT v > ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [VARCHAR(10)]"},
            {"SELECT ar > ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [ARRAY]"},
            {"SELECT tm > tn FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT d > tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [DATE]"},
            {"SELECT va > bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [VARIANT]"},
            {"SELECT n >= d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT d >= n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [DATE]"},
            {"SELECT b >= bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [BOOLEAN]"},
            {"SELECT v >= ar FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.AR' of type [ARRAY] into expected type [VARCHAR(10)]"},
            {"SELECT ar >= ob FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.OB' of type [OBJECT] into expected type [ARRAY]"},
            {"SELECT tm >= tn FROM ft", "SQL compilation error:\nincompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT d >= tm FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.TM' of type [TIME(9)] into expected type [DATE]"},
            {"SELECT va >= bi FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.BI' of type [BINARY(8388608)] into expected type [VARIANT]"},
            {"SELECT EQUAL_NULL(n, d) FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
        });
    }

    /** The pairs that meet, and the everyday shapes beside them. */
    @Test
    public void familiesThatMeetStillCompare() {
        assertAnswers(new String[][] {
            {"SELECT b = b FROM ft", "true"},
            {"SELECT b = n FROM ft", "true"},
            {"SELECT b = f FROM ft", "true"},
            {"SELECT b = v FROM ft", "true"},
            {"SELECT bi = bi FROM ft", "true"},
            {"SELECT ar = ar FROM ft", "true"},
            {"SELECT ob = ob FROM ft", "true"},
            {"SELECT d = d FROM ft", "true"},
            {"SELECT d = tn FROM ft", "false"},
            {"SELECT d = tl FROM ft", "false"},
            {"SELECT d = tz FROM ft", "false"},
            {"SELECT d = v FROM ft", "false"},
            {"SELECT tm = tm FROM ft", "true"},
            {"SELECT tm = v FROM ft", "false"},
            {"SELECT tn = d FROM ft", "false"},
            {"SELECT tn = tn FROM ft", "true"},
            {"SELECT tn = tl FROM ft", "true"},
            {"SELECT tn = tz FROM ft", "false"},
            {"SELECT tn = v FROM ft", "false"},
            {"SELECT tn = va FROM ft", "false"},
            {"SELECT tl = d FROM ft", "false"},
            {"SELECT tl = tn FROM ft", "true"},
            {"SELECT tl = tl FROM ft", "true"},
            {"SELECT tl = tz FROM ft", "false"},
            {"SELECT tl = v FROM ft", "false"},
            {"SELECT tl = va FROM ft", "false"},
            {"SELECT tz = d FROM ft", "false"},
            {"SELECT tz = tn FROM ft", "false"},
            {"SELECT tz = tl FROM ft", "false"},
            {"SELECT tz = tz FROM ft", "true"},
            {"SELECT tz = v FROM ft", "false"},
            {"SELECT tz = va FROM ft", "false"},
            {"SELECT n = b FROM ft", "true"},
            {"SELECT n = n FROM ft", "true"},
            {"SELECT n = f FROM ft", "false"},
            {"SELECT n = v FROM ft", "true"},
            {"SELECT n = va FROM ft", "true"},
            {"SELECT f = b FROM ft", "true"},
            {"SELECT f = n FROM ft", "false"},
            {"SELECT f = f FROM ft", "true"},
            {"SELECT f = v FROM ft", "false"},
            {"SELECT f = va FROM ft", "false"},
            {"SELECT v = b FROM ft", "false"},
            {"SELECT v = d FROM ft", "false"},
            {"SELECT v = tm FROM ft", "false"},
            {"SELECT v = tn FROM ft", "false"},
            {"SELECT v = tl FROM ft", "false"},
            {"SELECT v = tz FROM ft", "false"},
            {"SELECT v = n FROM ft", "true"},
            {"SELECT v = f FROM ft", "false"},
            {"SELECT v = v FROM ft", "true"},
            {"SELECT v = va FROM ft", "true"},
            {"SELECT va = b FROM ft", "false"},
            {"SELECT va = ar FROM ft", "false"},
            {"SELECT va = ob FROM ft", "false"},
            {"SELECT va = tn FROM ft", "false"},
            {"SELECT va = tl FROM ft", "false"},
            {"SELECT va = tz FROM ft", "false"},
            {"SELECT va = n FROM ft", "true"},
            {"SELECT va = f FROM ft", "false"},
            {"SELECT va = v FROM ft", "true"},
            {"SELECT va = va FROM ft", "true"},
            {"SELECT b < b FROM ft", "false"},
            {"SELECT b < n FROM ft", "false"},
            {"SELECT b < f FROM ft", "false"},
            {"SELECT b < v FROM ft", "false"},
            {"SELECT bi < bi FROM ft", "false"},
            {"SELECT ar < ar FROM ft", "false"},
            {"SELECT ar < va FROM ft", "false"},
            {"SELECT ob < ob FROM ft", "false"},
            {"SELECT d < d FROM ft", "false"},
            {"SELECT d < tn FROM ft", "true"},
            {"SELECT d < tl FROM ft", "true"},
            {"SELECT d < tz FROM ft", "true"},
            {"SELECT d < v FROM ft", "false"},
            {"SELECT tm < tm FROM ft", "false"},
            {"SELECT tm < v FROM ft", "false"},
            {"SELECT tn < d FROM ft", "false"},
            {"SELECT tn < tn FROM ft", "false"},
            {"SELECT tn < tl FROM ft", "false"},
            {"SELECT tn < tz FROM ft", "false"},
            {"SELECT tn < v FROM ft", "false"},
            {"SELECT tn < va FROM ft", "false"},
            {"SELECT tl < d FROM ft", "false"},
            {"SELECT tl < tn FROM ft", "false"},
            {"SELECT tl < tl FROM ft", "false"},
            {"SELECT tl < tz FROM ft", "false"},
            {"SELECT tl < v FROM ft", "false"},
            {"SELECT tl < va FROM ft", "false"},
            {"SELECT tz < d FROM ft", "false"},
            {"SELECT tz < tn FROM ft", "true"},
            {"SELECT tz < tl FROM ft", "true"},
            {"SELECT tz < tz FROM ft", "false"},
            {"SELECT tz < v FROM ft", "false"},
            {"SELECT tz < va FROM ft", "false"},
            {"SELECT n < b FROM ft", "false"},
            {"SELECT n < n FROM ft", "false"},
            {"SELECT n < f FROM ft", "true"},
            {"SELECT n < v FROM ft", "false"},
            {"SELECT n < va FROM ft", "false"},
            {"SELECT f < b FROM ft", "false"},
            {"SELECT f < n FROM ft", "false"},
            {"SELECT f < f FROM ft", "false"},
            {"SELECT f < v FROM ft", "false"},
            {"SELECT f < va FROM ft", "false"},
            {"SELECT v < b FROM ft", "true"},
            {"SELECT v < d FROM ft", "true"},
            {"SELECT v < tm FROM ft", "true"},
            {"SELECT v < tn FROM ft", "true"},
            {"SELECT v < tl FROM ft", "true"},
            {"SELECT v < tz FROM ft", "true"},
            {"SELECT v < n FROM ft", "false"},
            {"SELECT v < f FROM ft", "true"},
            {"SELECT v < v FROM ft", "false"},
            {"SELECT v < va FROM ft", "false"},
            {"SELECT va < ar FROM ft", "true"},
            {"SELECT va < ob FROM ft", "true"},
            {"SELECT va < tn FROM ft", "true"},
            {"SELECT va < tl FROM ft", "true"},
            {"SELECT va < tz FROM ft", "true"},
            {"SELECT va < n FROM ft", "false"},
            {"SELECT va < f FROM ft", "true"},
            {"SELECT va < v FROM ft", "false"},
            {"SELECT va < va FROM ft", "false"},
            {"SELECT n <> v FROM ft", "false"},
            {"SELECT d <> tn FROM ft", "true"},
            {"SELECT n != v FROM ft", "false"},
            {"SELECT d != tn FROM ft", "true"},
            {"SELECT n <= v FROM ft", "true"},
            {"SELECT d <= tn FROM ft", "true"},
            {"SELECT n > v FROM ft", "false"},
            {"SELECT d > tn FROM ft", "false"},
            {"SELECT n >= v FROM ft", "true"},
            {"SELECT d >= tn FROM ft", "false"},
            {"SELECT n = '1' FROM ft", "true"},
            {"SELECT d = '2020-01-01' FROM ft", "true"},
            {"SELECT tn = d FROM ft", "false"},
            {"SELECT tl = tz FROM ft", "false"},
            {"SELECT va = 1 FROM ft", "true"},
            {"SELECT va:x = 'a' FROM ft", "null"},
            {"SELECT n = TRUE FROM ft", "true"},
            {"SELECT b = 1 FROM ft", "true"},
            {"SELECT v = 1 FROM ft", "true"},
            {"SELECT ar = ARRAY_CONSTRUCT(1) FROM ft", "true"},
            {"SELECT ob = OBJECT_CONSTRUCT('a', 1) FROM ft", "true"},
            {"SELECT bi = X'00' FROM ft", "true"},
            {"SELECT f = n FROM ft", "false"},
            {"SELECT n IN (1, '2') FROM ft", "true"},
            {"SELECT d BETWEEN '2020-01-01' AND tn FROM ft", "true"},
            {"SELECT CASE d WHEN '2020-01-01' THEN 1 END FROM ft", "1"},
            {"SELECT DECODE(n, '1', 'x') FROM ft", "x"},
            {"SELECT n IS DISTINCT FROM '1' FROM ft", "false"},
            {"SELECT EQUAL_NULL(n, 1) FROM ft", "true"},
            {"SELECT x.n = 1 FROM ft x", "true"},
            {"SELECT SYSTEM$TYPEOF(1) = 'x'", "false"},
            {"SELECT CURRENT_TIMESTAMP() > d FROM ft", "true"},
            {"SELECT DATEADD(day, 1, d) = tn FROM ft", "false"},
            {"SELECT n = (SELECT 1) FROM ft", "true"},
            {"SELECT COUNT(*) = 0 FROM ft", "false"},
            {"SELECT GET(ar, 0) = 1 FROM ft", "true"},
            {"SELECT ar[0] = 1 FROM ft", "true"},
            {"SELECT TO_VARIANT(d) = d FROM ft", "true"},
            {"SELECT PARSE_JSON('1') = n FROM ft", "true"},
            {"SELECT ARRAY_SIZE(ar) = n FROM ft", "true"},
            {"SELECT LEN(v) > n FROM ft", "false"},
            {"SELECT g IN (TRUE) FROM ft", "false"},
            {"SELECT n = TO_NUMBER('1') FROM ft", "true"},
            {"SELECT d = TO_TIMESTAMP('2020-01-01') FROM ft", "true"},
            {"SELECT tm = '10:00:00' FROM ft", "true"},
            {"SELECT tm = TO_TIME('10:00:00') FROM ft", "true"},
            {"SELECT n = HASH(1) FROM ft", "false"},
            {"SELECT v = TO_VARCHAR(d) FROM ft", "false"},
            {"SELECT d = DATE_TRUNC('day', tn) FROM ft", "true"},
            {"SELECT n = IFF(TRUE, 1, 2) FROM ft", "true"},
            {"SELECT d = IFF(TRUE, d, NULL) FROM ft", "true"},
            {"SELECT n = NULLIF(1, 1) FROM ft", "null"},
            {"SELECT n = ARRAY_CONSTRUCT(1)[0] FROM ft", "true"},
        });
    }

    /** IN, BETWEEN, a simple CASE, IS DISTINCT FROM, DECODE, literals and the clauses a comparison stands in. */
    @Test
    public void membershipRangesCasesAndSearchesShareTheRule() {
        assertRefused(new String[][] {
            {"SELECT n IN (d) FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT n IN (1, d) FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT d IN (n) FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [DATE]"},
            {"SELECT n NOT IN (d) FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT d BETWEEN n AND n FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.N' of type [NUMBER(38,0)] into expected type [DATE]"},
            {"SELECT n BETWEEN d AND d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT CASE n WHEN d THEN 1 END FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT n IS DISTINCT FROM d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT NULLIF(n, d) FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT DECODE(n, d, 1) FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT GREATEST(n, d) FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT IFF(TRUE, n, d) FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT COALESCE(n, d) FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT NVL(n, d) FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT ARRAY_CONSTRUCT() = 1", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [ARRAY]"},
            {"SELECT TO_DATE('2020-01-01') = X'00'", "SQL compilation error:\nCan not convert parameter 'X'00'' of type [BINARY(1)] into expected type [DATE]"},
            {"SELECT X'00' < 1", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [BINARY(1)]"},
            {"SELECT 'a' = ARRAY_CONSTRUCT()", "SQL compilation error:\nCan not convert parameter 'ARRAY_CONSTRUCT()' of type [ARRAY] into expected type [VARCHAR(1)]"},
            {"SELECT 1 FROM re WHERE a = TO_DATE('2020-01-01')", "SQL compilation error:\nCan not convert parameter 'CAST('2020-01-01' AS DATE)' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT n = d::VARCHAR FROM ft", "Numeric value '2020-01-01' is not recognized"},
            {"SELECT n + 0 = d FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT 1 = CURRENT_DATE()", "SQL compilation error:\nCan not convert parameter 'CURRENT_DATE()' of type [DATE] into expected type [NUMBER(1,0)]"},
            {"SELECT CURRENT_DATE() = 1", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [DATE]"},
            {"SELECT TRUE = CURRENT_DATE()", "SQL compilation error:\nCan not convert parameter 'CURRENT_DATE()' of type [DATE] into expected type [BOOLEAN]"},
            {"SELECT TO_DATE('2020-01-01') = X'ab'", "SQL compilation error:\nCan not convert parameter 'X'AB'' of type [BINARY(1)] into expected type [DATE]"},
            {"SELECT TO_DATE('2020-01-01') = X'AB'", "SQL compilation error:\nCan not convert parameter 'X'AB'' of type [BINARY(1)] into expected type [DATE]"},
            {"DELETE FROM re WHERE a = TO_DATE('2020-01-01')", "SQL compilation error:\nCan not convert parameter 'CAST('2020-01-01' AS DATE)' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"SELECT COUNT(*) FROM ft HAVING COUNT(*) = TO_DATE('2020-01-01')", "SQL compilation error:\nCan not convert parameter 'CAST('2020-01-01' AS DATE)' of type [DATE] into expected type [NUMBER(18,0)]"},
            {"UPDATE re SET a = 1 WHERE a = CURRENT_DATE()", "SQL compilation error:\nCan not convert parameter 'CURRENT_DATE()' of type [DATE] into expected type [NUMBER(38,0)]"},
        });
        assertAnswers(new String[][] {
            {"SELECT va:x = d FROM ft", "null"},
            {"SELECT n = NULL FROM ft", "null"},
            {"SELECT NULL = d FROM ft", "null"},
        });
    }

    /** A predicate beside a text, a number, a temporal or a VARIANT, in a comparison and in a conditional. */
    @Test
    public void aPredicateMeetsOnlyABoolean() {
        assertRefused(new String[][] {
            {"SELECT 1 = 1 IS NULL", "SQL compilation error:\nCan not convert parameter '1 IS NULL' of type [BOOLEAN] into expected type [NUMBER(1,0)]"},
            {"SELECT 1 BETWEEN 0 AND 2 IS NULL", "SQL compilation error:\nCan not convert parameter '2 IS NULL' of type [BOOLEAN] into expected type [NUMBER(1,0)]"},
            {"SELECT 1 IS DISTINCT FROM (2 IS NULL)", "SQL compilation error:\nCan not convert parameter '2 IS NULL' of type [BOOLEAN] into expected type [NUMBER(1,0)]"},
            {"SELECT g = (1 = 1) FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT g < (1 = 1) FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT g IN ((1 = 1)) FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT CASE g WHEN (1 = 1) THEN 1 END FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT GREATEST(g, (1 = 1)) FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT NVL(g, 1 = 1) FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT IFF(TRUE, g, 1 = 1) FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT COALESCE(g, 1 = 1) FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT i = (1 = 1) FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [NUMBER(38,0)]"},
            {"SELECT NVL(i, (1 = 1)) FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [NUMBER(38,0)]"},
            {"SELECT (1 = 1) = g FROM ft", "Boolean value 'abc' is not recognized"},
            {"SELECT DECODE(g, 'a', (1 = 1), 'x') FROM ft", "Boolean value 'x' is not recognized"},
            {"SELECT g = ('a' LIKE 'a') FROM ft", "SQL compilation error:\nCan not convert parameter ''a' LIKE 'a'' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT g = (1 IN (1)) FROM ft", "SQL compilation error:\nCan not convert parameter '1 IN (1)' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT i < (g IS NULL) FROM ft", "SQL compilation error:\nCan not convert parameter 'FT.G IS NULL' of type [BOOLEAN] into expected type [NUMBER(38,0)]"},
            {"SELECT f = (1 = 1) FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [FLOAT]"},
            {"SELECT d = (1 = 1) FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [DATE]"},
            {"SELECT va = (1 = 1) FROM ft", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [VARIANT]"},
            {"SELECT DECRYPT(X'00', 'p') = 1", "SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into expected type [BINARY(67108864)]"},
            {"SELECT 1 = X'00'", "SQL compilation error:\nCan not convert parameter 'X'00'' of type [BINARY(1)] into expected type [NUMBER(1,0)]"},
            {"SELECT g = (NOT (1 = 1)) FROM ft", "SQL compilation error:\nCan not convert parameter 'NOT(1 = 1)' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT g = ((1 = 1) AND (2 = 2)) FROM ft", "SQL compilation error:\nCan not convert parameter '(1 = 1) AND (2 = 2)' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT g = CONTAINS('a', 'a') FROM ft", "SQL compilation error:\nCan not convert parameter 'CONTAINS('a', 'a')' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
        });
        assertAnswers(new String[][] {
            {"SELECT NVL((1 = 1), g) FROM ft", "true"},
            {"SELECT IFF(TRUE, (1 = 1), g) FROM ft", "true"},
            {"SELECT b = (1 = 1) FROM ft", "true"},
            {"SELECT g = TRUE FROM ft", "false"},
            {"SELECT i = TRUE FROM ft", "true"},
            {"SELECT g = (NOT TRUE) FROM ft", "false"},
            {"SELECT bi = X'00' FROM ft", "true"},
            {"SELECT (1 = 1) = 1", "true"},
            {"SELECT b = (n > 0) FROM ft", "true"},
        });
    }

    /** A BOOLEAN on the left of a comparison converts the text on its right. */
    @Test
    public void aBooleanReadsTheTextBesideItAsABoolean() {
        assertRefused(new String[][] {
            {"SELECT b = g FROM ft", "Boolean value 'abc' is not recognized"},
            {"SELECT TRUE = g FROM ft", "Boolean value 'abc' is not recognized"},
            {"SELECT (1 = 1) = g FROM ft", "Boolean value 'abc' is not recognized"},
            {"SELECT b < g FROM ft", "Boolean value 'abc' is not recognized"},
            {"SELECT (1 = 1) < 'abc'", "Boolean value 'abc' is not recognized"},
            {"SELECT TRUE = 'abc'", "Boolean value 'abc' is not recognized"},
            {"SELECT GREATEST(g, (1 = 1)) FROM ft WHERE FALSE", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT NVL(g, 1 = 1) FROM ft WHERE FALSE", "SQL compilation error:\nCan not convert parameter '1 = 1' of type [BOOLEAN] into expected type [VARCHAR(10)]"},
            {"SELECT LEAST((1 = 1), g) FROM ft", "Boolean value 'abc' is not recognized"},
        });
        assertAnswers(new String[][] {
            {"SELECT g = TRUE FROM ft", "false"},
            {"SELECT b = v FROM ft", "true"},
            {"SELECT (1 = 1) = v FROM ft", "true"},
            {"SELECT GREATEST((1 = 1), v) FROM ft", "true"},
        });
    }
}
