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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bare NULL in arithmetic is a NUMBER(18,0) on the account: {@code n4_0 + NULL} declares
 * NUMBER(19,0), {@code NULL * n10_2} NUMBER(28,2), {@code NULL + NULL} NUMBER(19,0), a table built
 * over them carries those types, and the value stays NULL. Under the remainder operator the NULL is
 * a NUMBER(1,0) ({@code NULL % 7} is NUMBER(2,0)); a FLOAT beside it keeps the FLOAT rule. Beside a
 * DATE, a TIME, a TIMESTAMP or a BOOLEAN the pair is refused at the operator with the NULL spelled as
 * such.
 */
public class NullArithmeticTypingTest extends BaseDatabaseTest {

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE st (n4_0 NUMBER(4,0), n10_2 NUMBER(10,2), n38_12 NUMBER(38,12), f FLOAT, "
            + "i INT, n5_3 NUMBER(5,3), d DATE, ts TIMESTAMP_NTZ, t TIME, b BOOLEAN, n20_0 NUMBER(20,0), "
            + "n38_0 NUMBER(38,0))");
        engine.execute("INSERT INTO st SELECT 17, 17.25, 17.25, 17.25, 17, 1.125, '2020-01-01', "
            + "'2020-01-01 10:00:00', '10:00:00', TRUE, 17, 17");
    }

    private Row row(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        assertEquals(1, result.getRowCount(), sql);
        return result.getRows().get(0);
    }

    private void assertTypes(final String sql, final String... expected) {
        final Row values = row(sql);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], String.valueOf(values.getValue(i)), sql + " cell " + (i + 1));
        }
    }

    private void assertRefused(final String sql, final String position, final String sentence) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        assertTrue(refused.getMessage().contains(position), sql + " -> " + refused.getMessage());
        assertTrue(refused.getMessage().contains(sentence), sql + " -> " + refused.getMessage());
    }

    @Test
    public void aBareNullIsANumber18BesideANumber() {
        assertTypes("SELECT SYSTEM$TYPEOF(n4_0 + NULL), SYSTEM$TYPEOF(n10_2 + NULL), SYSTEM$TYPEOF(n38_12 + NULL), "
            + "SYSTEM$TYPEOF(NULL + n4_0), SYSTEM$TYPEOF(n20_0 + NULL), SYSTEM$TYPEOF(n38_0 + NULL) FROM st",
            "NUMBER(19,0)[SB1]", "NUMBER(19,2)[SB1]", "NUMBER(38,12)[SB1]", "NUMBER(19,0)[SB1]",
            "NUMBER(21,0)[SB1]", "NUMBER(38,0)[SB1]");
        assertTypes("SELECT SYSTEM$TYPEOF(NULL + NULL), SYSTEM$TYPEOF(NULL * n10_2), SYSTEM$TYPEOF(NULL / n10_2), "
            + "SYSTEM$TYPEOF(n10_2 / NULL), SYSTEM$TYPEOF(NULL - n4_0), SYSTEM$TYPEOF(n4_0 - NULL) FROM st",
            "NUMBER(19,0)[SB1]", "NUMBER(28,2)[SB1]", "NUMBER(26,6)[SB1]", "NUMBER(16,8)[SB1]",
            "NUMBER(19,0)[SB1]", "NUMBER(19,0)[SB1]");
        assertTypes("SELECT SYSTEM$TYPEOF(NULL + 1), SYSTEM$TYPEOF(1 + NULL), SYSTEM$TYPEOF(NULL * 2.5), "
            + "SYSTEM$TYPEOF(2.5 * NULL), SYSTEM$TYPEOF(NULL / 3), SYSTEM$TYPEOF(3 / NULL) FROM st",
            "NUMBER(19,0)[SB1]", "NUMBER(19,0)[SB1]", "NUMBER(20,1)[SB1]", "NUMBER(20,1)[SB1]",
            "NUMBER(24,6)[SB1]", "NUMBER(7,6)[SB1]");
        assertTypes("SELECT SYSTEM$TYPEOF(i + NULL), SYSTEM$TYPEOF(n5_3 * NULL), SYSTEM$TYPEOF(n5_3 + NULL), "
            + "SYSTEM$TYPEOF(n38_12 * NULL), SYSTEM$TYPEOF(NULL / n38_12) FROM st",
            "NUMBER(38,0)[SB1]", "NUMBER(23,3)[SB1]", "NUMBER(19,3)[SB1]", "NUMBER(38,12)[SB1]",
            "NUMBER(36,6)[SB1]");
        assertTypes("SELECT SYSTEM$TYPEOF(NULL + 1.5), SYSTEM$TYPEOF(NULL - 100), SYSTEM$TYPEOF(NULL * NULL), "
            + "SYSTEM$TYPEOF(NULL / NULL), SYSTEM$TYPEOF(n10_2 * NULL), SYSTEM$TYPEOF(n4_0 / NULL) FROM st",
            "NUMBER(19,1)[SB1]", "NUMBER(19,0)[SB1]", "NUMBER(36,0)[SB1]", "NUMBER(24,6)[SB1]",
            "NUMBER(28,2)[SB1]", "NUMBER(10,6)[SB1]");
        assertTypes("SELECT SYSTEM$TYPEOF(NULL + 12345678901234567890), SYSTEM$TYPEOF(NULL * 12345678901234567890), "
            + "SYSTEM$TYPEOF(n38_0 * NULL), SYSTEM$TYPEOF(n38_12 - NULL) FROM st",
            "NUMBER(21,0)[SB1]", "NUMBER(38,0)[SB1]", "NUMBER(38,0)[SB1]", "NUMBER(38,12)[SB1]");
        assertTypes("SELECT SYSTEM$TYPEOF(n4_0 + NULL + 1), SYSTEM$TYPEOF((n4_0 + NULL) * 2), "
            + "SYSTEM$TYPEOF(NULL + NULL + n10_2), SYSTEM$TYPEOF(-NULL) FROM st",
            "NUMBER(20,0)[SB1]", "NUMBER(20,0)[SB1]", "NUMBER(22,2)[SB1]", "NUMBER(18,0)[SB1]");
        assertTypes("SELECT SYSTEM$TYPEOF(NULL::NUMBER(5,2) + NULL), SYSTEM$TYPEOF(NULL::NUMBER(38,12) + NULL) FROM st",
            "NUMBER(19,2)[SB1]", "NUMBER(38,12)[SB1]");
    }

    @Test
    public void theRemainderAndTheDivisionFunctionsSeeTheSameNull() {
        assertTypes("SELECT SYSTEM$TYPEOF(n10_2 % NULL), SYSTEM$TYPEOF(NULL % n10_2), SYSTEM$TYPEOF(n4_0 % NULL), "
            + "SYSTEM$TYPEOF(NULL % 7), SYSTEM$TYPEOF(NULL % NULL) FROM st",
            "NUMBER(10,2)[SB1]", "NUMBER(10,2)[SB1]", "NUMBER(4,0)[SB1]", "NUMBER(2,0)[SB1]", "NUMBER(2,0)[SB1]");
        assertTypes("SELECT SYSTEM$TYPEOF(MOD(NULL, 7)), SYSTEM$TYPEOF(MOD(n10_2, NULL)), SYSTEM$TYPEOF(DIV0(NULL, 2)), "
            + "SYSTEM$TYPEOF(DIV0(n10_2, NULL)) FROM st",
            "NUMBER(2,0)[SB1]", "NUMBER(10,2)[SB1]", "NUMBER(24,6)[SB1]", "NUMBER(16,8)[SB1]");
    }

    @Test
    public void aFloatBesideANullKeepsTheFloatRule() {
        assertTypes("SELECT SYSTEM$TYPEOF(f + NULL), SYSTEM$TYPEOF(NULL + f), SYSTEM$TYPEOF(NULL * f), "
            + "SYSTEM$TYPEOF(NULL / f) FROM st",
            "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]", "FLOAT[DOUBLE]");
    }

    @Test
    public void aTableBuiltOverThemCarriesTheTypesAndTheValuesStayNull() {
        engine.execute("CREATE TABLE tn AS SELECT n4_0 + NULL AS a, NULL + NULL AS b, n10_2 * NULL AS c, "
            + "NULL / n4_0 AS d2, f + NULL AS e FROM st");
        final ResultSet described = engine.executeQuery("DESC TABLE tn");
        assertEquals("NUMBER(19,0)", String.valueOf(described.getRows().get(0).getValue(1)));
        assertEquals("NUMBER(19,0)", String.valueOf(described.getRows().get(1).getValue(1)));
        assertEquals("NUMBER(28,2)", String.valueOf(described.getRows().get(2).getValue(1)));
        assertEquals("NUMBER(24,6)", String.valueOf(described.getRows().get(3).getValue(1)));
        assertEquals("FLOAT", String.valueOf(described.getRows().get(4).getValue(1)));
        final Row values = row("SELECT n4_0 + NULL, NULL + NULL, n10_2 * NULL, f + NULL, -NULL, NULL % 7 FROM st");
        for (int i = 0; i < 6; i++) {
            assertNull(values.getValue(i), "cell " + (i + 1));
        }
    }

    @Test
    public void aNullBesideATemporalOrABooleanIsRefusedAtTheOperator() {
        assertRefused("SELECT d + NULL FROM st", "position 9", "Invalid argument types for function '+': (DATE, NULL)");
        assertRefused("SELECT d - NULL FROM st", "position 9", "Invalid argument types for function '-': (DATE, NULL)");
        assertRefused("SELECT d * NULL FROM st", "position 9", "Invalid argument types for function '*': (DATE, NULL)");
        assertRefused("SELECT NULL + d FROM st", "position 12", "Invalid argument types for function '+': (NULL, DATE)");
        assertRefused("SELECT NULL - d FROM st", "position 12", "Invalid argument types for function '-': (NULL, DATE)");
        assertRefused("SELECT ts + NULL FROM st", "position 10",
            "Invalid argument types for function '+': (TIMESTAMP_NTZ(9), NULL)");
        assertRefused("SELECT t + NULL FROM st", "position 9", "Invalid argument types for function '+': (TIME(9), NULL)");
        assertRefused("SELECT b + NULL FROM st", "position 9", "Invalid argument types for function '+': (BOOLEAN, NULL)");
        assertRefused("SELECT NULL + TRUE FROM st", "position 12",
            "Invalid argument types for function '+': (NULL, BOOLEAN)");
        // A TYPED null is not the bare word: d + NULL::NUMBER is a DATE.
        assertTypes("SELECT SYSTEM$TYPEOF(d + NULL::NUMBER) FROM st", "DATE[SB4]");
    }
}
