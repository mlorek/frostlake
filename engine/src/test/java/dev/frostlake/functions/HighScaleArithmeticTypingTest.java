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
 * The account caps a product's and a quotient's scale where the ideal algebra runs past what fits:
 * a product declares {@code min(s1 + s2, max(s1, s2, 12))} at {@code l1 + l2} integer digits, a
 * quotient {@code max(s1, min(s1 + 6, 12))} at {@code l1 + s2} integer digits, both within 38 digits —
 * so a NUMBER(38,12) times 1.5 stays NUMBER(38,12), a NUMBER(38,35) squared stays NUMBER(38,35), and
 * a NUMBER(20,10) squared is NUMBER(32,12). The VALUE carries the declared scale, rounded half up,
 * and a table built over the expression declares it.
 */
public class HighScaleArithmeticTypingTest extends BaseDatabaseTest {

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE st (n38_12 NUMBER(38,12), n38_35 NUMBER(38,35), n10_2 NUMBER(10,2), "
            + "n20_10 NUMBER(20,10), n30_20 NUMBER(30,20), n38_0 NUMBER(38,0), n38_37 NUMBER(38,37), n19_0 NUMBER(19,0))");
        engine.execute("INSERT INTO st SELECT 17.25, 1.25, 17.25, 17.25, 17.25, 17, 1.25, 17");
    }

    private Row row(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        assertEquals(1, result.getRowCount(), sql);
        return result.getRows().get(0);
    }

    private static String cellText(final Object value) {
        return value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : String.valueOf(value);
    }

    /** The declared types of a select list, each without its storage tag. */
    private void assertTypes(final String sql, final String... expected) {
        final Row values = row(sql);
        for (int i = 0; i < expected.length; i++) {
            final String described = String.valueOf(values.getValue(i));
            final String type = described.indexOf('[') < 0 ? described : described.substring(0, described.indexOf('['));
            assertEquals(expected[i], type, sql + " cell " + (i + 1));
        }
    }

    private void assertCells(final String sql, final String... expected) {
        final Row values = row(sql);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], cellText(values.getValue(i)), sql + " cell " + (i + 1));
        }
    }

    @Test
    public void aProductsScaleIsCapped() {
        assertTypes("SELECT SYSTEM$TYPEOF(n38_12 * 1.5), SYSTEM$TYPEOF((n38_12 + 1) * (n38_12 + 1)), "
            + "SYSTEM$TYPEOF(n38_12 * n38_12), SYSTEM$TYPEOF(n38_12 * n38_12 * n38_12), SYSTEM$TYPEOF(n38_12 * 1.5 + 1) FROM st",
            "NUMBER(38,12)", "NUMBER(38,12)", "NUMBER(38,12)", "NUMBER(38,12)", "NUMBER(38,12)");
        assertTypes("SELECT SYSTEM$TYPEOF(n38_35 * n38_35), SYSTEM$TYPEOF(n38_35 * 2), SYSTEM$TYPEOF(n38_35 * n38_12), "
            + "SYSTEM$TYPEOF(n38_35 * 1.5), SYSTEM$TYPEOF(n38_35 * n38_35 * n38_35) FROM st",
            "NUMBER(38,35)", "NUMBER(38,35)", "NUMBER(38,35)", "NUMBER(38,35)", "NUMBER(38,35)");
        assertTypes("SELECT SYSTEM$TYPEOF(n30_20 * n30_20), SYSTEM$TYPEOF(n20_10 * n20_10), SYSTEM$TYPEOF(n20_10 * n30_20), "
            + "SYSTEM$TYPEOF(n20_10 * 1.5), SYSTEM$TYPEOF(n38_37 * 2) FROM st",
            "NUMBER(38,20)", "NUMBER(32,12)", "NUMBER(38,20)", "NUMBER(22,11)", "NUMBER(38,37)");
        assertTypes("SELECT SYSTEM$TYPEOF(n10_2 * n10_2), SYSTEM$TYPEOF(n19_0 * n19_0), SYSTEM$TYPEOF(n19_0 * n20_10), "
            + "SYSTEM$TYPEOF(n38_0 * n38_0), SYSTEM$TYPEOF(n38_0 * 1.5), SYSTEM$TYPEOF(n38_12 * 0.0000001) FROM st",
            "NUMBER(20,4)", "NUMBER(38,0)", "NUMBER(38,10)", "NUMBER(38,0)", "NUMBER(38,1)", "NUMBER(38,12)");
        assertTypes("SELECT SYSTEM$TYPEOF(1.123456789012345678901234567890123456 * 1.123456789012345678901234567890123456), "
            + "SYSTEM$TYPEOF(12345678901234567890123456.123456789012 * 3), SYSTEM$TYPEOF(SUM(n38_12) * 2) FROM st",
            "NUMBER(38,36)", "NUMBER(38,12)", "NUMBER(38,12)");
    }

    @Test
    public void aQuotientsScaleIsCapped() {
        assertTypes("SELECT SYSTEM$TYPEOF(n38_12 / 0.5), SYSTEM$TYPEOF(n38_12 / n38_12), SYSTEM$TYPEOF(n38_0 / 3), "
            + "SYSTEM$TYPEOF(1 / n38_12), SYSTEM$TYPEOF(n20_10 / n20_10), SYSTEM$TYPEOF(n38_37 / 2) FROM st",
            "NUMBER(38,12)", "NUMBER(38,12)", "NUMBER(38,6)", "NUMBER(19,6)", "NUMBER(32,12)", "NUMBER(38,37)");
        assertTypes("SELECT SYSTEM$TYPEOF(n10_2 / 0.5), SYSTEM$TYPEOF(n38_12 / 7.5), SYSTEM$TYPEOF(n38_12 / n10_2), "
            + "SYSTEM$TYPEOF(n10_2 / n38_12), SYSTEM$TYPEOF(n30_20 / n30_20), SYSTEM$TYPEOF(n38_35 / n38_35) FROM st",
            "NUMBER(17,8)", "NUMBER(38,12)", "NUMBER(38,12)", "NUMBER(28,8)", "NUMBER(38,20)", "NUMBER(38,35)");
        assertTypes("SELECT SYSTEM$TYPEOF(n30_20 / 3), SYSTEM$TYPEOF(n38_35 / 2), SYSTEM$TYPEOF(n38_12 / 0.5 / 0.5) FROM st",
            "NUMBER(30,20)", "NUMBER(38,35)", "NUMBER(38,12)");
        assertTypes("SELECT SYSTEM$TYPEOF(SUM(n38_12) / COUNT(n38_12)), SYSTEM$TYPEOF(AVG(n38_12)) FROM st",
            "NUMBER(38,12)", "NUMBER(38,12)");
    }

    @Test
    public void theValueCarriesTheDeclaredScale() {
        assertCells("SELECT n38_12 * 1.5, n38_12 / 0.5, (n38_12 + 1) * (n38_12 + 1), n38_12 * n38_12 FROM st",
            "25.875000000000", "34.500000000000", "333.062500000000", "297.562500000000");
        assertCells("SELECT n38_35 * n38_35, n38_35 * 2, n38_35 / 2, n38_35 * n38_12, n38_35 * 1.5 FROM st",
            "1.56250000000000000000000000000000000", "2.50000000000000000000000000000000000",
            "0.62500000000000000000000000000000000", "21.56250000000000000000000000000000000",
            "1.87500000000000000000000000000000000");
        assertCells("SELECT n30_20 * n30_20, n30_20 / 3, n20_10 * n20_10, n20_10 * n30_20, n20_10 * 1.5 FROM st",
            "297.56250000000000000000", "5.75000000000000000000", "297.562500000000",
            "297.56250000000000000000", "25.87500000000");
        assertCells("SELECT n38_12 / n38_12, n38_0 / 3, 1 / n38_12, n20_10 / n20_10, n38_37 * 2, n38_37 / 2 FROM st",
            "1.000000000000", "5.666667", "0.057971", "1.000000000000",
            "2.5000000000000000000000000000000000000", "0.6250000000000000000000000000000000000");
        assertCells("SELECT n38_12 / 7.5, n38_12 / n10_2, n10_2 / n38_12, n38_0 * 1.5, n19_0 * n19_0 FROM st",
            "2.300000000000", "1.000000000000", "1.00000000", "25.5", "289");
        assertCells("SELECT 12345678901234567890123456.123456789012 * 3, "
            + "1.123456789012345678901234567890123456 * 1.123456789012345678901234567890123456, "
            + "n38_35 * n38_35 * n38_35, n38_12 * 0.001, n38_12 * 0.0000001 FROM st",
            "37037036703703703670370368.370370367036", "1.262155156777930194552964487342813594",
            "1.95312500000000000000000000000000000", "0.017250000000", "0.000001725000");
    }

    @Test
    public void aTableBuiltOverThemDeclaresTheCappedTypes() {
        engine.execute("CREATE TABLE tc AS SELECT n38_12 * 1.5 AS a, n38_12 / 0.5 AS b, n38_35 * n38_35 AS c, "
            + "n20_10 * n20_10 AS d2 FROM st");
        final ResultSet described = engine.executeQuery("DESC TABLE tc");
        assertEquals("NUMBER(38,12)", String.valueOf(described.getRows().get(0).getValue(1)));
        assertEquals("NUMBER(38,12)", String.valueOf(described.getRows().get(1).getValue(1)));
        assertEquals("NUMBER(38,35)", String.valueOf(described.getRows().get(2).getValue(1)));
        assertEquals("NUMBER(32,12)", String.valueOf(described.getRows().get(3).getValue(1)));
        assertCells("SELECT a, b, c, d2 FROM tc", "25.875000000000", "34.500000000000",
            "1.56250000000000000000000000000000000", "297.562500000000");
    }
}
