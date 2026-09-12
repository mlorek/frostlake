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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MOD, DIV0 and DIV0NULL declare exactly what the operator they stand for declares — MOD the type of
 * {@code a % b}, DIV0 and DIV0NULL the type of {@code a / b} — and their values carry that type's
 * scale, a zero divisor's zero included; a FLOAT operand makes either a double. A NULL dividend is
 * NULL whatever the divisor, and MOD by zero is refused.
 */
public class DivisionFamilyTypingTest extends BaseDatabaseTest {

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE st (n4_0 NUMBER(4,0), n10_2 NUMBER(10,2), n38_12 NUMBER(38,12), f FLOAT, "
            + "i INT, n5_3 NUMBER(5,3))");
        engine.execute("INSERT INTO st SELECT 17, 17.25, 17.25, 17.25, 17, 1.125");
    }

    private Row row(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        assertEquals(1, result.getRowCount(), sql);
        return result.getRows().get(0);
    }

    private String text(final String sql) {
        return String.valueOf(row(sql).getValue(0));
    }

    /** The declared type without its storage tag. */
    private String typeOnly(final String sql) {
        final String described = text(sql);
        return described.indexOf('[') < 0 ? described : described.substring(0, described.indexOf('['));
    }

    private void assertSameType(final String call, final String operator) {
        assertEquals(text("SELECT SYSTEM$TYPEOF(" + operator + ") FROM st"),
            text("SELECT SYSTEM$TYPEOF(" + call + ") FROM st"), call + " should declare what " + operator + " declares");
    }

    /** A cell's text: a BigDecimal's digits in place (a zero at scale 8 is 0.00000000, not 0E-8). */
    private static String cellText(final Object value) {
        return value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : String.valueOf(value);
    }

    private void assertCells(final String sql, final String... expected) {
        final Row values = row(sql);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], cellText(values.getValue(i)), sql + " cell " + (i + 1));
        }
    }

    private void assertRefused(final String sql, final String sentence) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        assertTrue(refused.getMessage().contains(sentence), sql + " -> " + refused.getMessage());
    }

    @Test
    public void modDeclaresTheRemainderOperatorsType() {
        assertCells("SELECT SYSTEM$TYPEOF(MOD(n4_0, 7)), SYSTEM$TYPEOF(MOD(n10_2, 7)), SYSTEM$TYPEOF(MOD(n38_12, 7)) FROM st",
            "NUMBER(4,0)[SB1]", "NUMBER(10,2)[SB2]", "NUMBER(38,12)[SB8]");
        assertCells("SELECT SYSTEM$TYPEOF(MOD(7, n10_2)), SYSTEM$TYPEOF(MOD(n4_0, n10_2)), SYSTEM$TYPEOF(MOD(n10_2, 2.5)), "
            + "SYSTEM$TYPEOF(MOD(n10_2, n5_3)) FROM st",
            "NUMBER(10,2)[SB2]", "NUMBER(10,2)[SB2]", "NUMBER(10,2)[SB2]", "NUMBER(11,3)[SB2]");
        assertEquals("NUMBER(38,12)", typeOnly("SELECT SYSTEM$TYPEOF(MOD(n38_12, n38_12)) FROM st"));
        assertEquals("NUMBER(11,3)[SB2]", text("SELECT SYSTEM$TYPEOF(MOD(n5_3, n10_2)) FROM st"));
        assertEquals("NUMBER(38,12)", typeOnly("SELECT SYSTEM$TYPEOF(MOD(n38_12, n5_3)) FROM st"));
        assertEquals("NUMBER(7,3)", typeOnly("SELECT SYSTEM$TYPEOF(MOD(n4_0, n5_3)) FROM st"));
        assertCells("SELECT SYSTEM$TYPEOF(MOD(f, 2)), SYSTEM$TYPEOF(MOD(n4_0, f)), SYSTEM$TYPEOF(MOD(i, 3)) FROM st",
            "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "NUMBER(38,0)[SB1]");
        assertSameType("MOD(n10_2, 7)", "n10_2 % 7");
        assertSameType("MOD(n4_0, n10_2)", "n4_0 % n10_2");
        assertSameType("MOD(n10_2, n5_3)", "n10_2 % n5_3");
        assertSameType("MOD(5, 3)", "5 % 3");
        assertSameType("MOD(5.5, 2)", "5.5 % 2");
        assertEquals("NUMBER(10,2)", typeOnly("SELECT SYSTEM$TYPEOF(MOD(n10_2, 0)) FROM st"));
        assertEquals("NUMBER(11,2)[SB2]", text("SELECT SYSTEM$TYPEOF(MOD(n10_2, 7) + 1) FROM st"));
    }

    @Test
    public void div0DeclaresTheDivisionsType() {
        assertCells("SELECT SYSTEM$TYPEOF(DIV0(n4_0, 2)), SYSTEM$TYPEOF(DIV0(n10_2, 2)) FROM st",
            "NUMBER(10,6)[SB4]", "NUMBER(16,8)[SB4]");
        assertCells("SELECT SYSTEM$TYPEOF(DIV0(7, n10_2)), SYSTEM$TYPEOF(DIV0(n4_0, n10_2)), SYSTEM$TYPEOF(DIV0(n10_2, n5_3)) FROM st",
            "NUMBER(9,6)[SB4]", "NUMBER(12,6)[SB4]", "NUMBER(19,8)[SB8]");
        // A dividend at scale 12 is the division operator's own high-scale question; DIV0 declares
        // whatever the operator does, which is what the parity cells below hold it to.
        assertSameType("DIV0(n38_12, 2)", "n38_12 / 2");
        assertCells("SELECT SYSTEM$TYPEOF(DIV0(f, 2)), SYSTEM$TYPEOF(DIV0(n4_0, f)), SYSTEM$TYPEOF(DIV0(i, 3)) FROM st",
            "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "NUMBER(38,6)[SB4]");
        assertCells("SELECT SYSTEM$TYPEOF(DIV0NULL(n4_0, 2)), SYSTEM$TYPEOF(DIV0NULL(n10_2, 2)), "
            + "SYSTEM$TYPEOF(DIV0NULL(f, 2)) FROM st",
            "NUMBER(10,6)[SB4]", "NUMBER(16,8)[SB4]", "FLOAT[DOUBLE]");
        assertSameType("DIV0NULL(n38_12, 2)", "n38_12 / 2");
        assertSameType("DIV0(n38_12, n5_3)", "n38_12 / n5_3");
        assertSameType("DIV0(n4_0, 2)", "n4_0 / 2");
        assertSameType("DIV0(n10_2, n4_0)", "n10_2 / n4_0");
        assertSameType("DIV0(7, n10_2)", "7 / n10_2");
        assertSameType("DIV0(n38_12, n38_12)", "n38_12 / n38_12");
        assertSameType("DIV0(n5_3, n38_12)", "n5_3 / n38_12");
        assertSameType("DIV0(1, 3)", "1 / 3");
        assertSameType("DIV0(5.5, 2)", "5.5 / 2");
        assertSameType("DIV0NULL(n10_2, n5_3)", "n10_2 / n5_3");
        assertEquals("NUMBER(16,8)", typeOnly("SELECT SYSTEM$TYPEOF(DIV0(n10_2, 0)) FROM st"));
        assertEquals("NUMBER(17,8)[SB4]", text("SELECT SYSTEM$TYPEOF(DIV0(n10_2, 2) + 1) FROM st"));
    }

    @Test
    public void aTableBuiltOverThemCarriesTheType() {
        engine.execute("CREATE TABLE tc AS SELECT MOD(n10_2, 7) AS m, DIV0(n4_0, 2) AS d, DIV0NULL(n10_2, 2) AS dn FROM st");
        final ResultSet described = engine.executeQuery("DESC TABLE tc");
        assertEquals("NUMBER(10,2)", String.valueOf(described.getRows().get(0).getValue(1)));
        assertEquals("NUMBER(10,6)", String.valueOf(described.getRows().get(1).getValue(1)));
        assertEquals("NUMBER(16,8)", String.valueOf(described.getRows().get(2).getValue(1)));
    }

    @Test
    public void theValuesCarryTheDerivedScale() {
        assertCells("SELECT MOD(n4_0, 7), MOD(n10_2, 7), MOD(n38_12, 7), MOD(7, n10_2), MOD(n10_2, n5_3), MOD(5.5, 2) FROM st",
            "3", "3.25", "3.250000000000", "7.00", "0.375", "1.5");
        assertCells("SELECT MOD(n4_0, n5_3), MOD(n5_3, n4_0), MOD(17.25, 3), MOD(n38_12, 3), MOD(-7, 3), MOD(7.5, -2) FROM st",
            "0.125", "1.125", "2.25", "2.250000000000", "-1", "1.5");
        assertCells("SELECT DIV0(n4_0, 2), DIV0(n10_2, 2), DIV0(n38_12, 2), DIV0(1, 3), DIV0(i, 3) FROM st",
            "8.500000", "8.62500000", "8.625000000000", "0.333333", "5.666667");
        assertCells("SELECT DIV0(17, 3), DIV0(17.25, 3), DIV0(n4_0, 3), DIV0(n4_0, n5_3), DIV0(n10_2, n5_3) FROM st",
            "5.666667", "5.75000000", "5.666667", "15.111111", "15.33333333");
        assertCells("SELECT DIV0(n38_12, n5_3), DIV0(n5_3, n38_12) FROM st", "15.333333333333", "0.065217391");
        assertCells("SELECT MOD(f, 2), DIV0(f, 2), DIV0(f, 0), DIV0NULL(f, NULL), DIV0NULL(f, 0) FROM st",
            "1.25", "8.625", "0.0", "0.0", "0.0");
    }

    @Test
    public void aZeroDivisorAnswersZeroAtTheDerivedScale() {
        assertCells("SELECT DIV0(n10_2, 0), DIV0NULL(n10_2, NULL), DIV0NULL(n4_0, 0), DIV0(n4_0, 0) FROM st",
            "0.00000000", "0.00000000", "0.000000", "0.000000");
        assertCells("SELECT DIV0(n38_12, 0), DIV0(i, 0), DIV0(1, 0), DIV0(5.5, 0), DIV0(n5_3, 0) FROM st",
            "0.000000000000", "0.000000", "0.000000", "0.0000000", "0.000000000");
        assertCells("SELECT DIV0(n10_2, 0.0), DIV0(n10_2, 0.000), DIV0NULL(n10_2, 0) FROM st",
            "0.00000000", "0.00000000", "0.00000000");
        final Row nulls = row("SELECT DIV0(NULL, 0), DIV0NULL(NULL, NULL), DIV0NULL(NULL, 0), DIV0(NULL, 2), MOD(NULL, 0) FROM st");
        for (int i = 0; i < 5; i++) {
            assertNull(nulls.getValue(i), "cell " + (i + 1));
        }
        assertRefused("SELECT MOD(n10_2, 0) FROM st", "Division by zero");
        assertRefused("SELECT MOD(n4_0, 0) FROM st", "Division by zero");
        assertRefused("SELECT MOD(f, 0) FROM st", "Division by zero");
    }
}
