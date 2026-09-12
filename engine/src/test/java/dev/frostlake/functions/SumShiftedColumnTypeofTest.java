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
import dev.frostlake.storage.Row;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SYSTEM$TYPEOF over {@code SUM(c + k)} — a bare column shifted by a constant — reports the account
 * plan's rewritten intermediate, wider than the ordinary SUM width the column itself declares: over
 * a NUMBER(4,0) holding varying values it is NUMBER(20,0) where a CTAS over the same expression
 * declares NUMBER(17,0). Nineteen digits plus the constant's precision for a scale-0 column, six
 * digits over the ordinary width for a scaled one, only for a constant whose scale falls short of
 * the column's, never for a column holding one value, and never for SUM(DISTINCT …), AVG, a window
 * SUM or any other argument shape.
 */
public class SumShiftedColumnTypeofTest extends BaseDatabaseTest {

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE st (n4_0 NUMBER(4,0), n10_2 NUMBER(10,2), n5_3 NUMBER(5,3), n20_0 NUMBER(20,0), k VARCHAR)");
        engine.execute("INSERT INTO st SELECT 17, 17.25, 1.125, 17, 'a' UNION ALL SELECT 3, 3.5, 2.5, 3, 'b'");
        engine.execute("CREATE TABLE one (n4_0 NUMBER(4,0))");
        engine.execute("INSERT INTO one SELECT 17");
        engine.execute("CREATE TABLE same (n4_0 NUMBER(4,0))");
        engine.execute("INSERT INTO same SELECT 17 UNION ALL SELECT 17");
        engine.execute("CREATE TABLE none (n4_0 NUMBER(4,0))");
        engine.execute("CREATE TABLE parts (n4_0 NUMBER(4,0))");
        engine.execute("INSERT INTO parts SELECT 17");
        engine.execute("INSERT INTO parts SELECT 3");
    }

    /** The first row's declared types, each without its storage tag. */
    private void assertTypes(final String sql, final String... expected) {
        final Row values = engine.executeQuery(sql).getRows().get(0);
        for (int i = 0; i < expected.length; i++) {
            final String described = String.valueOf(values.getValue(i));
            final String type = described.indexOf('[') < 0 ? described : described.substring(0, described.indexOf('['));
            assertEquals(expected[i], type, sql + " cell " + (i + 1));
        }
    }

    private static String cellText(final Object value) {
        return value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : String.valueOf(value);
    }

    @Test
    public void aSumOverAShiftedColumnReportsThePlansWidth() {
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + 1)), SYSTEM$TYPEOF(SUM(n4_0 + 100)), SYSTEM$TYPEOF(SUM(n4_0 + 9999)), "
            + "SYSTEM$TYPEOF(SUM(n4_0 + 99999)), SYSTEM$TYPEOF(SUM(n4_0 - 1)), SYSTEM$TYPEOF(SUM(1 + n4_0)) FROM st",
            "NUMBER(20,0)", "NUMBER(22,0)", "NUMBER(23,0)", "NUMBER(24,0)", "NUMBER(20,0)", "NUMBER(20,0)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + 2)), SYSTEM$TYPEOF(SUM(n4_0 + 10)), SYSTEM$TYPEOF(SUM(n4_0 - 100)), "
            + "SYSTEM$TYPEOF(SUM(n20_0 + 1)), SYSTEM$TYPEOF(SUM(n4_0 + 12345678901234567890)) FROM st",
            "NUMBER(20,0)", "NUMBER(21,0)", "NUMBER(22,0)", "NUMBER(33,0)", "NUMBER(38,0)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n10_2 + 1)), SYSTEM$TYPEOF(SUM(n10_2 + 0.5)), SYSTEM$TYPEOF(SUM(n10_2 + 1.5)), "
            + "SYSTEM$TYPEOF(SUM(n10_2 + 100)), SYSTEM$TYPEOF(SUM(n5_3 + 1)), SYSTEM$TYPEOF(SUM(n5_3 + 0.5)) FROM st",
            "NUMBER(29,2)", "NUMBER(29,2)", "NUMBER(29,2)", "NUMBER(29,2)", "NUMBER(24,3)", "NUMBER(24,3)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + 1::NUMBER(18,0))), SYSTEM$TYPEOF(SUM(n4_0 + 1::NUMBER(4,0))), "
            + "SYSTEM$TYPEOF(SUM(n4_0 + 1::NUMBER(10,0))), SYSTEM$TYPEOF(SUM(n10_2 + 1::NUMBER(4,0))) FROM st",
            "NUMBER(37,0)", "NUMBER(23,0)", "NUMBER(29,0)", "NUMBER(29,2)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + 1)), SYSTEM$TYPEOF(SUM(n10_2 + 1)) FROM st GROUP BY k",
            "NUMBER(20,0)", "NUMBER(29,2)");
        // The plan folds a column-free operand first: a whole-valued decimal, a sign, arithmetic, ABS.
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + 1.0)), SYSTEM$TYPEOF(SUM(n4_0 + 10.0)), SYSTEM$TYPEOF(SUM(n4_0 + 1::NUMBER(3,0))), "
            + "SYSTEM$TYPEOF(SUM(n4_0 + -1)), SYSTEM$TYPEOF(SUM(n4_0 + 2 * 3)), SYSTEM$TYPEOF(SUM(n4_0 + (1 + 1))) FROM st",
            "NUMBER(20,0)", "NUMBER(21,0)", "NUMBER(22,0)", "NUMBER(20,0)", "NUMBER(21,0)", "NUMBER(21,0)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + ABS(1))), SYSTEM$TYPEOF(SUM(n4_0 + ABS(-100))), SYSTEM$TYPEOF(SUM(n10_2 + ABS(1))), "
            + "SYSTEM$TYPEOF(SUM(n10_2 + (1 + 1))), SYSTEM$TYPEOF(SUM(n4_0 + (SELECT 1))) FROM st",
            "NUMBER(21,0)", "NUMBER(22,0)", "NUMBER(29,2)", "NUMBER(29,2)", "NUMBER(20,0)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + 1)) FROM parts", "NUMBER(20,0)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(DISTINCT n4_0 + 1)) FROM st", "NUMBER(20,0)");
    }

    @Test
    public void everyOtherShapeKeepsTheOrdinaryWidth() {
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + 1.5)), SYSTEM$TYPEOF(SUM(n4_0 + 0.001)), SYSTEM$TYPEOF(SUM(n4_0 - 0.5)), "
            + "SYSTEM$TYPEOF(SUM(n10_2 + 0.05)), SYSTEM$TYPEOF(SUM(n10_2 + 0.005)) FROM st",
            "NUMBER(18,1)", "NUMBER(20,3)", "NUMBER(18,1)", "NUMBER(23,2)", "NUMBER(24,3)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + n4_0)), SYSTEM$TYPEOF(SUM(n4_0 + n10_2)), SYSTEM$TYPEOF(SUM(n4_0 * 2)), "
            + "SYSTEM$TYPEOF(SUM(n4_0 / 2)), SYSTEM$TYPEOF(SUM(-n4_0)), SYSTEM$TYPEOF(SUM(n4_0) + 1) FROM st",
            "NUMBER(17,0)", "NUMBER(23,2)", "NUMBER(17,0)", "NUMBER(22,6)", "NUMBER(16,0)", "NUMBER(17,0)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + 1 + 1)), SYSTEM$TYPEOF(SUM((n4_0 + 1) * 2)), SYSTEM$TYPEOF(SUM(2 * n4_0 + 1)), "
            + "SYSTEM$TYPEOF(SUM(ABS(n4_0) + 1)), SYSTEM$TYPEOF(SUM(n4_0 + ABS(1) + 1)), SYSTEM$TYPEOF(SUM(n4_0 + ABS(1.5))) FROM st",
            "NUMBER(18,0)", "NUMBER(18,0)", "NUMBER(18,0)", "NUMBER(17,0)", "NUMBER(18,0)", "NUMBER(18,1)");
        // A function the plan leaves standing is no constant: LENGTH('ab') keeps the ordinary width.
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + LENGTH('ab'))), SYSTEM$TYPEOF(SUM(n10_2 + LENGTH('ab'))), "
            + "SYSTEM$TYPEOF(SUM(n4_0 + 1::NUMBER(3,1))) FROM st",
            "NUMBER(31,0)", "NUMBER(33,2)", "NUMBER(18,1)");
        assertTypes("SELECT SYSTEM$TYPEOF(MAX(n4_0 + 1)), SYSTEM$TYPEOF(AVG(n4_0 + 1)), "
            + "SYSTEM$TYPEOF(AVG(n10_2 + 1)), SYSTEM$TYPEOF(AVG(n5_3 + 1)) FROM st",
            "NUMBER(5,0)", "NUMBER(23,6)", "NUMBER(29,8)", "NUMBER(24,9)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + 1) OVER ()), SYSTEM$TYPEOF(SUM(n10_2 + 1) OVER ()) FROM st",
            "NUMBER(17,0)", "NUMBER(23,2)");
        // A column whose statistics hold one value is not rewritten: one row, every row alike, no row.
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + 1)) FROM one", "NUMBER(17,0)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + 1)) FROM same", "NUMBER(17,0)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n4_0 + 1)) FROM none", "NUMBER(17,0)");
    }

    @Test
    public void theQuerysOwnColumnKeepsTheOrdinaryWidthAndTheValue() {
        engine.execute("CREATE TABLE tc AS SELECT SUM(n4_0 + 1) AS a, SUM(n10_2 + 1) AS b, SUM(n4_0 + 1.5) AS c FROM st");
        final ResultSet described = engine.executeQuery("DESC TABLE tc");
        assertEquals("NUMBER(17,0)", String.valueOf(described.getRows().get(0).getValue(1)));
        assertEquals("NUMBER(23,2)", String.valueOf(described.getRows().get(1).getValue(1)));
        assertEquals("NUMBER(18,1)", String.valueOf(described.getRows().get(2).getValue(1)));
        final Row values = engine.executeQuery(
            "SELECT SUM(n4_0 + 1), SUM(n10_2 + 1), SUM(n5_3 + 1), AVG(n4_0 + 1), SUM(n4_0 - 1), SUM(1 + n4_0) FROM st")
            .getRows().get(0);
        final String[] expected = {"22", "22.75", "5.625", "11.000000", "18", "22"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], cellText(values.getValue(i)), "cell " + (i + 1));
        }
    }
}
