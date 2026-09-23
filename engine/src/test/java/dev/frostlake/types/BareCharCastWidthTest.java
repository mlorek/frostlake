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

package dev.frostlake.types;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CHAR, CHARACTER or NCHAR written without a length is one character wide, as a column declared so is. A cast to
 * it is typed VARCHAR(1), holds its operand to one character whatever the operand's family — {@code 'ab'::CHAR} and
 * {@code 12::CHAR} are refused as too long, and TRY_CAST answers NULL — and a table built over it stores VARCHAR(1).
 * The VARYING spellings keep no width at all. Every cell is live-verified.
 */
public class BareCharCastWidthTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (s VARCHAR, n INT)");
        engine.execute("INSERT INTO t VALUES ('ab', 12), ('c', 3)");
    }

    /** Every row's first cell, joined. */
    private String answer(final String sql) {
        final List<String> cells = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            cells.add(String.valueOf(row.getValue(0)));
        }
        return String.join(" | ", cells);
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    private void assertTooLong(final String sql, final String value) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(String.valueOf(refused.getMessage()).contains(
            "String '" + value + "' is too long and would be truncated"), sql + ": " + refused.getMessage());
    }

    @Test
    public void aBareCharIsTypedOneCharacterWide() {
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(NULL::CHAR)", "VARCHAR(1)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NULL::CHARACTER)", "VARCHAR(1)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NULL::NCHAR)", "VARCHAR(1)[LOB]"},
            {"SELECT SYSTEM$TYPEOF('ab'::CHAR)", "VARCHAR(1)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TRY_CAST('ab' AS CHAR))", "VARCHAR(1)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NULL::CHAR(3))", "VARCHAR(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NULL::CHAR VARYING)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NULL::NCHAR VARYING)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, 'x', NULL::CHAR))", "VARCHAR(1)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE(NULL::CHAR, 'abc'))", "VARCHAR(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF('a'::CHAR || 'b')", "VARCHAR(2)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, 'ab'::CHAR))", "VARCHAR(134217728)[LOB]"},
        });
    }

    @Test
    public void aBareCharHoldsItsOperandToOneCharacter() {
        assertCells(new String[][] {
            {"SELECT 'a'::CHAR", "a"},
            {"SELECT NULL::CHAR", "null"},
            {"SELECT 1::CHAR", "1"},
            {"SELECT 'ab'::CHAR VARYING", "ab"},
            {"SELECT 'abc'::NCHAR VARYING", "abc"},
            {"SELECT TRY_CAST('ab' AS CHAR)", "null"},
            {"SELECT TRY_CAST(s AS CHAR) FROM t ORDER BY n", "c | null"},
        });
        assertTooLong("SELECT 'ab'::CHAR", "ab");
        assertTooLong("SELECT 'ab'::CHARACTER", "ab");
        assertTooLong("SELECT 'ab'::NCHAR", "ab");
        assertTooLong("SELECT CAST('ab' AS CHAR)", "ab");
        assertTooLong("SELECT 12::CHAR", "12");
        assertTooLong("SELECT TRUE::CHAR", "true");
        assertTooLong("SELECT TO_CHAR(12)::CHAR", "12");
        assertTooLong("SELECT s::CHAR FROM t ORDER BY n", "ab");
        assertTooLong("SELECT n::CHAR FROM t ORDER BY n", "12");
    }

    @Test
    public void aTableOverABareCharStoresOneCharacter() {
        engine.execute("CREATE TABLE cc AS SELECT 'a'::CHAR AS a, NULL::CHAR AS b, 'a'::CHAR(3) AS c, NULL::NCHAR AS d");
        assertEquals("A:TEXT:1 B:TEXT:1 C:TEXT:3 D:TEXT:1",
            answer("SELECT LISTAGG(column_name || ':' || data_type || ':'"
                + " || COALESCE(character_maximum_length::VARCHAR, 'null'), ' ')"
                + " WITHIN GROUP (ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name = 'CC'"));
    }
}
