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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * INSERT(base, pos, len, insert) — Snowflake's string function: remove {@code len} characters from
 * {@code base} starting at 1-based {@code pos} and put {@code insert} in their place. {@code INSERT} is a
 * keyword (the INSERT statement) that also names this function, so the grammar allows it as a function name.
 *
 * <p>It answers {@code SUBSTR(base, 1, pos - 1) || insert || SUBSTR(base, pos + len)}, SUBSTR's windows
 * included, NULL for any NULL argument, and splices the BYTES of two binaries into a BINARY as wide as the
 * base twice over plus the insertion (live-verified).
 */
public class InsertFunctionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE bt (i NUMBER(5,0), bn BINARY)");
        engine.execute("INSERT INTO bt SELECT 1, TO_BINARY('6162') UNION ALL SELECT 2, TO_BINARY('63')");
    }

    private String insert(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    /** Every row's first cell, a bar between rows. */
    private String column(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(row.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void replacesCharacters() {
        // Remove 3 chars ("bcd") at position 2 and put "zzz" there.
        assertEquals("azzzef", insert("SELECT INSERT('abcdef', 2, 3, 'zzz')"));
    }

    @Test
    public void insertsWhenLengthIsZero() {
        // Length 0 inserts without removing anything.
        assertEquals("abXYZcdef", insert("SELECT INSERT('abcdef', 3, 0, 'XYZ')"));
    }

    @Test
    public void replacesTheWholeString() {
        assertEquals("X", insert("SELECT INSERT('abc', 1, 3, 'X')"));
    }

    @Test
    public void appendsWhenPositionPastEnd() {
        assertEquals("abcX", insert("SELECT INSERT('abc', 5, 0, 'X')"));
    }

    @Test
    public void nestedInsertCalls() {
        // A common pattern: build a timestamp by inserting separators.
        assertEquals("12-34:5678", insert("SELECT INSERT(INSERT('12345678', 5, 0, ':'), 3, 0, '-')"));
    }

    @Test
    public void nullBaseIsNull() {
        assertNull(engine.executeQuery("SELECT INSERT(NULL, 1, 1, 'x')").getRows().get(0).getValue(0));
    }

    @Test
    public void lowercaseName() {
        assertEquals("azzzef", insert("SELECT insert('abcdef', 2, 3, 'zzz')"));
    }

    /** A position at or before 0 keeps the base behind the insertion; a negative one counts from the end. */
    @Test
    public void theBaseIsCutWithSubstrWindows() {
        assertEquals("xabc", insert("SELECT INSERT('abc', 0, 1, 'x')"));
        assertEquals("xabc", insert("SELECT INSERT('abc', -1, 1, 'x')"));
        assertEquals("xc", insert("SELECT INSERT('abc', -2, 1, 'x')"));
        assertEquals("abcx", insert("SELECT INSERT('abc', 4, 1, 'x')"));
        assertEquals("abcx", insert("SELECT INSERT('abc', 5, 1, 'x')"));
        assertEquals("axabc", insert("SELECT INSERT('abc', 2, -1, 'x')"));
        assertEquals("axbc", insert("SELECT INSERT('abc', 2, 0, 'x')"));
        assertEquals("ax", insert("SELECT INSERT('abc', 2, 10, 'x')"));
        assertEquals("5bc", insert("SELECT INSERT('abc', 1, 1, 5)"));
    }

    /** Any NULL argument answers NULL — the insertion and the position included. */
    @Test
    public void aNullArgumentIsANullAnswer() {
        assertEquals("null", insert("SELECT INSERT('abc', 1, 1, NULL)"));
        assertEquals("null", insert("SELECT INSERT('abc', NULL, 1, 'x')"));
        assertEquals("null | null", column("SELECT INSERT(bn, 1, 1, NULL) FROM bt ORDER BY i"));
    }

    /** Two binaries splice their bytes, through the same windows. */
    @Test
    public void twoBinariesSpliceTheirBytes() {
        assertEquals("616262 | 63", column("SELECT HEX_ENCODE(INSERT(bn, 1, 1, bn)) FROM bt ORDER BY i"));
        assertEquals("7A62 | 7A", column("SELECT HEX_ENCODE(INSERT(bn, 1, 1, TO_BINARY('7a'))) FROM bt ORDER BY i"));
        assertEquals("61626162 | 6363", column("SELECT HEX_ENCODE(INSERT(bn, 3, 1, bn)) FROM bt ORDER BY i"));
        assertEquals("61626162 | 6363", column("SELECT HEX_ENCODE(INSERT(bn, 0, 1, bn)) FROM bt ORDER BY i"));
        assertEquals("61626162 | 6363", column("SELECT HEX_ENCODE(INSERT(bn, -1, 1, bn)) FROM bt ORDER BY i"));
        assertEquals("617A62 | 637A",
            column("SELECT HEX_ENCODE(INSERT(bn, 2, 0, TO_BINARY('7a'))) FROM bt ORDER BY i"));
        assertEquals("617A6162 | 637A63",
            column("SELECT HEX_ENCODE(INSERT(bn, 2, -1, TO_BINARY('7a'))) FROM bt ORDER BY i"));
        assertEquals("6362", insert("SELECT HEX_ENCODE(INSERT(X'6162', 1, 1, X'63'))"));
    }

    /** The result is as wide as the base twice over plus the insertion, a text or a binary alike. */
    @Test
    public void theResultDeclaresTheBaseTwiceAndTheInsertion() {
        assertEquals("BINARY(25165824)[LOB] | BINARY(25165824)[LOB]",
            column("SELECT SYSTEM$TYPEOF(INSERT(bn, 1, 1, bn)) FROM bt ORDER BY i"));
        assertEquals("BINARY(5)[LOB]", insert("SELECT SYSTEM$TYPEOF(INSERT(X'6162', 1, 1, X'63'))"));
        assertEquals("VARCHAR(8)[LOB]", insert("SELECT SYSTEM$TYPEOF(INSERT('abc', 1, 1, 'xy'))"));
        assertEquals("VARCHAR(134217728)[LOB]", insert("SELECT SYSTEM$TYPEOF(INSERT('abc', 1, 1, NULL))"));
        assertEquals("VARCHAR(134217728)[LOB]", insert("SELECT SYSTEM$TYPEOF(INSERT(NULL, 1, 1, 'x'))"));
    }
}
