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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DECRYPT refuses what it cannot read back in live's own two sentences — input too short to hold an IV
 * and a tag, and input whose tag does not verify — and an unsized BINARY it returns is named at the 64MB
 * maximum when an operator or a numeric function refuses it. Live-verified.
 */
public class DecryptRefusalTest extends BaseDatabaseTest {

    private static final String MALFORMED =
        "Encrypted data input does not comply with the expected input of the selected encryption method";
    private static final String FAILED = "Decryption failed. Check encrypted data, key, AAD, or AEAD tag.";

    /** The one cell of a single-row query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    /** The message a refused statement carries. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    /** A binary literal of the given byte count, 00 01 02 …. */
    private static String bytes(final int count) {
        final StringBuilder literal = new StringBuilder("X'");
        for (int i = 0; i < count; i++) {
            literal.append(String.format("%02X", i));
        }
        return literal.append('\'').toString();
    }

    private static String invalidTypes(final int position, final String function, final String types) {
        return "SQL compilation error: error line 1 at position " + position
            + "\nInvalid argument types for function '" + function + "': (" + types + ")";
    }

    /** Fewer than 28 bytes cannot hold an IV and a tag; from 28 on the tag is checked. */
    @Test
    public void shortInputIsNotTheMethodsInput() {
        assertEquals(MALFORMED, refusal("SELECT DECRYPT(X'00', 'p')"));
        assertEquals(MALFORMED, refusal("SELECT DECRYPT(X'', 'p')"));
        assertEquals(MALFORMED, refusal("SELECT DECRYPT(" + bytes(12) + ", 'p')"));
        assertEquals(MALFORMED, refusal("SELECT DECRYPT(" + bytes(27) + ", 'p')"));
        assertEquals(MALFORMED, refusal("SELECT LENGTH(DECRYPT(X'00', 'p'))"));
        assertEquals(FAILED, refusal("SELECT DECRYPT(" + bytes(28) + ", 'p')"));
        assertEquals(FAILED, refusal("SELECT DECRYPT(" + bytes(48) + ", 'p')"));
    }

    /** A wrong passphrase fails the tag, and TRY_DECRYPT answers NULL for either refusal. */
    @Test
    public void aWrongPassphraseFailsTheDecryption() {
        assertEquals(FAILED, refusal("SELECT DECRYPT(ENCRYPT('a', 'p'), 'q')"));
        assertEquals(FAILED, refusal("SELECT DECRYPT(ENCRYPT('a', 'p'), '')"));
        assertTrue(Boolean.parseBoolean(scalar("SELECT TRY_DECRYPT(X'00', 'p') IS NULL")));
        assertTrue(Boolean.parseBoolean(scalar("SELECT TRY_DECRYPT(" + bytes(40) + ", 'p') IS NULL")));
        assertEquals("abc", scalar("SELECT TO_VARCHAR(DECRYPT(ENCRYPT('abc', 'p'), 'p'), 'UTF-8')"));
        assertEquals("28", scalar("SELECT LENGTH(ENCRYPT('', 'p'))"));
        assertEquals("0", scalar("SELECT LENGTH(DECRYPT(ENCRYPT('', 'p'), 'p'))"));
    }

    /** The unsized BINARY DECRYPT returns is refused before any row, named at the 64MB maximum. */
    @Test
    public void theUnsizedResultIsRefusedByType() {
        engine.execute("CREATE OR REPLACE TABLE bt (b BINARY, n INT)");
        assertEquals(invalidTypes(7, "ABS", "BINARY(67108864)"), refusal("SELECT ABS(DECRYPT(X'00', 'p'))"));
        assertEquals(invalidTypes(7, "CEIL", "BINARY(67108864)"), refusal("SELECT CEIL(DECRYPT(X'00', 'p'))"));
        assertEquals(invalidTypes(7, "MOD", "BINARY(67108864), NUMBER(1,0)"),
            refusal("SELECT MOD(DECRYPT(X'00', 'p'), 2)"));
        assertEquals(invalidTypes(27, "+", "BINARY(67108864), NUMBER(1,0)"), refusal("SELECT DECRYPT(X'00', 'p') + 1"));
        assertEquals(invalidTypes(9, "-", "NUMBER(1,0), BINARY(67108864)"), refusal("SELECT 1 - DECRYPT(X'00', 'p')"));
        assertEquals(invalidTypes(27, "*", "BINARY(67108864), NUMBER(1,0)"), refusal("SELECT DECRYPT(X'00', 'p') * 2"));
        assertEquals(invalidTypes(27, "||", "BINARY(67108864), VARCHAR(1)"),
            refusal("SELECT DECRYPT(X'00', 'p') || 'a'"));
        assertEquals(invalidTypes(25, "+", "BINARY(67108864), NUMBER(1,0)"), refusal("SELECT ENCRYPT('a', 'p') + 1"));
        assertEquals(invalidTypes(23, "+", "BINARY(67108864), NUMBER(1,0)"), refusal("SELECT TO_BINARY('00') + 1"));
    }

    /** A sized BINARY keeps its own width in the same sentences, a column its 8MB. */
    @Test
    public void aSizedBinaryKeepsItsWidth() {
        engine.execute("CREATE OR REPLACE TABLE bt (b BINARY, n INT)");
        assertEquals(invalidTypes(9, "+", "BINARY(8388608), NUMBER(1,0)"), refusal("SELECT b + 1 FROM bt"));
        assertEquals(invalidTypes(13, "+", "BINARY(1), NUMBER(1,0)"), refusal("SELECT X'00' + 1"));
        assertEquals(invalidTypes(23, "+", "BINARY(16), NUMBER(1,0)"), refusal("SELECT MD5_BINARY('a') + 1"));
        assertEquals(invalidTypes(7, "ABS", "BINARY(8388608)"), refusal("SELECT ABS(b) FROM bt"));
    }
}
