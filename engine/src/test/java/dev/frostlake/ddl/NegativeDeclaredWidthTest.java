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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A NEGATIVE declared width, which Frostlake could not parse at all — three stacked syntax errors where
 * live gives the same per-family range sentence it gives every other bad width.
 *
 * <p>★ THE POSITION IS THE INTERESTING PART: {@code error line 0 at position 0}, a place no statement
 * has. Every other width in this family points at the literal's own offset — {@code VARCHAR(0)} reports
 * position 40 in the same statement shape — so the zero is not a coincidence of this sentence.
 *
 * <p>★ AND IT IS A CONSTANT, NOT A SHIFTED POSITION. It survives a LEADING COMMENT and it survives the
 * declaration moving to a SECOND LINE, both of which move a real position. Live reads the minus in a
 * phase that no longer has a token to point at.
 *
 * <p>★ THE ZERO BELONGS TO THE TYPE-PARAMETER READER ALONE. A negative in another slot still carries a
 * real position — {@code SELECT 1 LIMIT -1} reports position 15 — so this is not a general property of
 * negative numbers in the grammar.
 *
 * <p>★ THE SIGN IS READ OFF THE PARSE TREE, in child order, which is what keeps {@code NUMBER(5,-1)}'s
 * minus attached to the SCALE rather than to the precision.
 */
public class NegativeDeclaredWidthTest extends BaseDatabaseTest {

    /** The refusal for a statement, with its newlines made visible. */
    private String statement(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        } finally {
            try {
                engine.execute("DROP TABLE IF EXISTS negw");
            } catch (final RuntimeException ignored) {
                // cleanup only
            }
        }
    }

    private String query(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            String value = "ACCEPTED";
            while (rs.next()) {
                value = "ACCEPTED: " + String.valueOf(rs.getValue(0));
            }
            return value;
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String impossible(final String detail) {
        return "SQL compilation error: error line 0 at position 0|" + detail;
    }

    /** ★ Each family's own sentence, at the impossible position. */
    @Test
    public void eachFamilyRefusesItsNegativeWidth() {
        assertEquals(impossible("Invalid character length: -1. Must be between 1 and 134,217,728."),
            statement("CREATE OR REPLACE TABLE negw (c VARCHAR(-1))"));
        assertEquals(impossible("Invalid character length: -1. Must be between 1 and 134,217,728."),
            statement("CREATE OR REPLACE TABLE negw (c CHAR(-1))"));
        assertEquals(impossible("Invalid binary length: -1. Must be between 1 and 67,108,864."),
            statement("CREATE OR REPLACE TABLE negw (c BINARY(-1))"));
        assertEquals(impossible("Invalid number precision: -1. Must be between 0 and 38."),
            statement("CREATE OR REPLACE TABLE negw (c NUMBER(-1))"));
        assertEquals(impossible("Invalid timestamp scale: -1. Must be between 0 and 9."),
            statement("CREATE OR REPLACE TABLE negw (c TIMESTAMP_NTZ(-1))"));
        assertEquals(impossible("Invalid time scale: -1. Must be between 0 and 9."),
            statement("CREATE OR REPLACE TABLE negw (c TIME(-1))"));
    }

    /** ★ The SECOND parameter's sign stays with the scale, not the precision. */
    @Test
    public void thesignStaysWithItsOwnParameter() {
        assertEquals(impossible("Invalid number scale: -1. Must be between 0 and 37."),
            statement("CREATE OR REPLACE TABLE negw (c NUMBER(5,-1))"));
    }

    /** A CAST target shares the sentence, in both spellings. */
    @Test
    public void acastTargetSharesIt() {
        assertEquals(impossible("Invalid character length: -1. Must be between 1 and 134,217,728."),
            query("SELECT CAST('x' AS VARCHAR(-1))"));
        assertEquals(impossible("Invalid character length: -1. Must be between 1 and 134,217,728."),
            query("SELECT 'x'::VARCHAR(-1)"));
    }

    /** ★ THE ZERO IS A CONSTANT: neither a leading comment nor a second line moves it. */
    @Test
    public void thezeroPositionIsNotAShiftedPosition() {
        assertEquals(impossible("Invalid character length: -1. Must be between 1 and 134,217,728."),
            statement("/* lead */ CREATE OR REPLACE TABLE negw (c VARCHAR(-1))"));
        assertEquals(impossible("Invalid character length: -1. Must be between 1 and 134,217,728."),
            statement("CREATE OR REPLACE TABLE negw\n  (c VARCHAR(-1))"));
    }

    /** ★ A negative elsewhere still carries a REAL position — the contrast that makes zero meaningful. */
    @Test
    public void anegativeInAnotherSlotKeepsItsPosition() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 15 unexpected '-'.",
            query("SELECT 1 LIMIT -1"));
        assertEquals("ACCEPTED: c", query("SELECT SUBSTR('abc', -1, 2)"),
            "a negative ARGUMENT is ordinary and answers");
    }

    /** The zero-width and valid cells, which must not move. */
    @Test
    public void thezeroAndValidWidthsAreUntouched() {
        assertEquals("SQL compilation error: error line 1 at position 40|Invalid character length: 0."
            + " Must be between 1 and 134,217,728.",
            statement("CREATE OR REPLACE TABLE negw (c VARCHAR(0))"),
            "a zero width points at the literal, where a negative cannot");
        assertEquals("ACCEPTED", statement("CREATE OR REPLACE TABLE negw (c NUMBER(0))"));
        assertEquals("ACCEPTED", statement("CREATE OR REPLACE TABLE negw (c VARCHAR(5))"));
    }
}
