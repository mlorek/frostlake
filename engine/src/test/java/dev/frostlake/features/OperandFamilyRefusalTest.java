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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An operand family an operator does not take is refused when the statement is COMPILED, positioned on
 * the operator — so it fails over a table with no rows as surely as over a populated one. Arithmetic
 * reads numbers, texts, VARIANTs and (for + and -) temporal values; the logical operators read a
 * boolean, a number or a text they convert where they read it. A text that will not convert is a
 * run-time error on both sides, not a compile-time one. Live-verified.
 */
public class OperandFamilyRefusalTest extends BaseDatabaseTest {

    /** Asserts a statement is refused with a message carrying {@code fragment}. */
    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refused.getMessage() != null && refused.getMessage().contains(fragment),
            sql + " should be refused with \"" + fragment + "\" but read: " + refused.getMessage());
    }

    /** The single cell of a single-row query, as upper-cased text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount());
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString().toUpperCase();
    }

    /** Arithmetic does not read a BOOLEAN, a BINARY, an ARRAY or an OBJECT. */
    @Test
    public void arithmeticRefusesTheFamiliesItCannotRead() {
        assertRefused("SELECT TRUE + 1", "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))");
        assertRefused("SELECT 1 + TRUE", "Invalid argument types for function '+': (NUMBER(1,0), BOOLEAN)");
        assertRefused("SELECT TRUE * 2", "Invalid argument types for function '*': (BOOLEAN, NUMBER(1,0))");
        assertRefused("SELECT X'00' + 1", "Invalid argument types for function '+': (BINARY(1), NUMBER(1,0))");
        assertRefused("SELECT ARRAY_CONSTRUCT() + 1",
            "Invalid argument types for function '+': (ARRAY, NUMBER(1,0))");
        assertRefused("SELECT OBJECT_CONSTRUCT() + 1",
            "Invalid argument types for function '+': (OBJECT, NUMBER(1,0))");
        assertRefused("SELECT TO_DATE('2020-01-01') + TRUE",
            "Invalid argument types for function '+': (DATE, BOOLEAN)");
    }

    /** A temporal value is shifted, never scaled: only + and - take one. */
    @Test
    public void scalingRefusesATemporalOperand() {
        assertRefused("SELECT TO_DATE('2020-01-01') / 1",
            "Invalid argument types for function '/': (DATE, NUMBER(1,0))");
        assertRefused("SELECT TO_DATE('2020-01-01') * 1",
            "Invalid argument types for function '*': (DATE, NUMBER(1,0))");
        assertRefused("SELECT 1 % TO_DATE('2020-01-01')",
            "Invalid argument types for function '%': (NUMBER(1,0), DATE)");
        assertEquals("2020-01-02", answer("SELECT TO_DATE('2020-01-01') + 1"));
    }

    /** AND, OR and NOT read a boolean, a number or a text — and nothing else. */
    @Test
    public void logicalOperatorsRefuseTheFamiliesTheyCannotRead() {
        assertRefused("SELECT X'00' AND TRUE",
            "Invalid argument types for function 'AND': (BINARY(1), BOOLEAN)");
        assertRefused("SELECT TRUE AND X'00'",
            "Invalid argument types for function 'AND': (BOOLEAN, BINARY(1))");
        assertRefused("SELECT ARRAY_CONSTRUCT() OR TRUE",
            "Invalid argument types for function 'OR': (ARRAY, BOOLEAN)");
        assertRefused("SELECT TRUE AND TO_DATE('2020-01-01')",
            "Invalid argument types for function 'AND': (BOOLEAN, DATE)");
        assertRefused("SELECT NOT X'00'", "Invalid argument types for function 'NOT': (BINARY(1))");
        assertRefused("SELECT NOT OBJECT_CONSTRUCT()",
            "Invalid argument types for function 'NOT': (OBJECT)");
        assertEquals("TRUE", answer("SELECT 1 AND TRUE"));
        assertEquals("TRUE", answer("SELECT TRUE AND 1"));
    }

    /** The refusal is a COMPILE-time one: it lands over a table with no rows at all. */
    @Test
    public void theRefusalLandsOverNoRows() {
        engine.execute("CREATE OR REPLACE TABLE operand_families (b BINARY(1), f BOOLEAN)");
        assertRefused("SELECT b + 1 FROM operand_families",
            "Invalid argument types for function '+': (BINARY(1), NUMBER(1,0))");
        assertRefused("SELECT b AND TRUE FROM operand_families",
            "Invalid argument types for function 'AND': (BINARY(1), BOOLEAN)");
        assertRefused("SELECT NOT b FROM operand_families",
            "Invalid argument types for function 'NOT': (BINARY(1))");
        assertRefused("SELECT f + 1 FROM operand_families",
            "Invalid argument types for function '+': (BOOLEAN, NUMBER(1,0))");
    }

    /** A text that will not convert is a run-time error, never a compile-time refusal of its family. */
    @Test
    public void anUnreadableTextIsARunTimeError() {
        assertRefused("SELECT 'a' + 1", "Numeric value 'a' is not recognized");
        assertRefused("SELECT 1 + 'a'", "Numeric value 'a' is not recognized");
        assertRefused("SELECT 'a' AND TRUE", "Boolean value 'a' is not recognized");
        assertEquals("2", answer("SELECT '1' + 1"));
    }
}
