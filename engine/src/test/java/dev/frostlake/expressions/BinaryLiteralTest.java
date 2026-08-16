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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hex BINARY literal, {@code X'..'}: typed BINARY of its own byte count wherever a type is spelled —
 * SYSTEM$TYPEOF, argument-type refusals, a CTAS or view column — with spaces inside the quotes dropped
 * and any other malformed body refused as written. Every cell is live-verified.
 */
public class BinaryLiteralTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** A literal is BINARY of its own byte count, the empty one BINARY(1), and a fold takes the wider. */
    @Test
    public void aHexLiteralIsTypedByItsByteCount() {
        assertEquals("BINARY(1)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(X'00')"));
        assertEquals("BINARY(2)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(X'0102')"));
        assertEquals("BINARY(3)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(X'010203')"));
        assertEquals("BINARY(1)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(X'')"));
        assertEquals("BINARY(1)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(x'ab')"));
        assertEquals("BINARY(2)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(IFF(TRUE, X'00', X'0102'))"));
        assertEquals("BINARY(3)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(COALESCE(X'00', X'010203'))"));
        assertEquals("BINARY(10)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(X'0102030405060708090A')"));
        assertEquals("BINARY(2)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(X'' || X'')"));
    }

    /** An argument-type refusal names the literal's BINARY(n), where an untyped literal once printed NULL. */
    @Test
    public void argumentTypeRefusalsNameTheLiteralsWidth() {
        assertRefused("SELECT ROUND(2.5, 0, X'00')",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ROUND': (NUMBER(2,1), NUMBER(1,0), BINARY(1))");
        assertRefused("SELECT ROUND(2.5, 0, X'0102')",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ROUND': (NUMBER(2,1), NUMBER(1,0), BINARY(2))");
        assertRefused("SELECT ROUND(2.5, 0, X'')",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ROUND': (NUMBER(2,1), NUMBER(1,0), BINARY(1))");
        assertRefused("SELECT UPPER(X'00')",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'UPPER': (BINARY(1))");
        assertRefused("SELECT ABS(X'0102')",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ABS': (BINARY(2))");
        assertRefused("SELECT X'00' || 'a'",
            "SQL compilation error: error line 1 at position 13\nInvalid argument types for function '||': (BINARY(1), VARCHAR(1))");
        assertRefused("SELECT SQRT(X'00')",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SQRT': (BINARY(1))");
    }

    /** An odd digit count, a letter past F or a tab is refused at compile time, echoing the literal as written. */
    @Test
    public void aMalformedLiteralIsRefusedAsWritten() {
        assertRefused("SELECT X'0'",
            "SQL compilation error: Invalid binary literal X'0'; should contain pairs of hexadecimal digits.");
        assertRefused("SELECT X'GG'",
            "SQL compilation error: Invalid binary literal X'GG'; should contain pairs of hexadecimal digits.");
        assertRefused("SELECT x'0'",
            "SQL compilation error: Invalid binary literal x'0'; should contain pairs of hexadecimal digits.");
        assertRefused("SELECT X'0G'",
            "SQL compilation error: Invalid binary literal X'0G'; should contain pairs of hexadecimal digits.");
        assertRefused("SELECT X'000'",
            "SQL compilation error: Invalid binary literal X'000'; should contain pairs of hexadecimal digits.");
        assertRefused("SELECT x'gg'",
            "SQL compilation error: Invalid binary literal x'gg'; should contain pairs of hexadecimal digits.");
        assertRefused("SELECT X'0' || 'a'",
            "SQL compilation error: Invalid binary literal X'0'; should contain pairs of hexadecimal digits.");
        assertRefused("SELECT 1 FROM (SELECT 1) WHERE FALSE AND X'0' = X'00'",
            "SQL compilation error: Invalid binary literal X'0'; should contain pairs of hexadecimal digits.");
        assertRefused("SELECT X'1' AS a, X'2' AS b",
            "SQL compilation error: Invalid binary literal X'1'; should contain pairs of hexadecimal digits.");
        assertRefused("SELECT X'00\t11'",
            "SQL compilation error: Invalid binary literal X'00\t11'; should contain pairs of hexadecimal digits.");
    }

    /** Spaces fall away wherever they sit, even inside a pair; the value compares, encodes and measures as its bytes. */
    @Test
    public void spacesInsideTheQuotesAreDropped() {
        assertEquals("00",
            rows("SELECT HEX_ENCODE(X'00 ')"));
        assertEquals("",
            rows("SELECT HEX_ENCODE(X' ')"));
        assertEquals("AB",
            rows("SELECT HEX_ENCODE(X'ab')"));
        assertEquals("",
            rows("SELECT HEX_ENCODE(X'')"));
        assertEquals("0",
            rows("SELECT LENGTH(X'')"));
        assertEquals("false",
            rows("SELECT X'00' = X''"));
        assertEquals("",
            rows("SELECT HEX_ENCODE(X'')"));
        assertEquals("0011",
            rows("SELECT HEX_ENCODE(X'00 11')"));
        assertEquals("00",
            rows("SELECT HEX_ENCODE(X'0 0')"));
        assertEquals("01",
            rows("SELECT HEX_ENCODE(X' 0 1 ')"));
        assertEquals("2",
            rows("SELECT LENGTH(X'0102')"));
        assertEquals("true",
            rows("SELECT X'00' = X'00'"));
        assertEquals("4142",
            rows("SELECT HEX_ENCODE(X'4142')"));
        assertEquals("4142",
            rows("SELECT TO_VARCHAR(X'4142')"));
        assertEquals("4142",
            rows("SELECT X'4142'::VARCHAR"));
    }

    /** A CTAS and a view declare the literal's width. */
    @Test
    public void aDerivedColumnDeclaresTheLiteralsWidth() {
        engine.execute("CREATE OR REPLACE TABLE tb (b BINARY(1), v VARBINARY)");
        engine.execute("INSERT INTO tb VALUES (X'01', X'0102')");
        engine.execute("CREATE OR REPLACE TABLE cb AS SELECT X'0102' AS b, X'' AS e, IFF(TRUE, X'00', X'010203') AS f");
        assertTrue(rows("DESC TABLE cb").startsWith("B, BINARY(2), COLUMN"));
        assertTrue(rows("SHOW COLUMNS IN TABLE cb").contains("{\"type\":\"BINARY\",\"length\":2,\"byteLength\":2,\"nullable\":true,\"fixed\":false}"));
        engine.execute("CREATE OR REPLACE VIEW vb AS SELECT X'0102' AS b");
        assertTrue(rows("DESC VIEW vb").startsWith("B, BINARY(2), COLUMN"));
    }
}
