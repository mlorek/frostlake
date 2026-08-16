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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which operand families {@code ||} takes, and how wide the answer is.
 *
 * <p>The families do NOT split along the semi-structured line, which is the surprise: a VARIANT
 * concatenates perfectly well and converts to full-width text, while the CONTAINER families — OBJECT,
 * ARRAY and the geo pair — are refused against everything, including against a VARIANT and against each
 * other. BINARY is its own island: it joins only another BINARY, and their lengths add.
 */
public class ConcatOperandFamilyTest extends BaseDatabaseTest {

    private static final String FULL_WIDTH_TEXT = """
        {"type":"TEXT","length":16777216,"byteLength":67108864,"nullable":true,"fixed":false}""";

    @Override
    protected void setupTest() {
        engine.execute("""
            CREATE TABLE cf_t (
                s VARCHAR(4), n NUMBER(5,1), d DATE, b BOOLEAN,
                v VARIANT, o OBJECT, a ARRAY, bin BINARY(4))""");
    }

    private String declaredType(final String expression) {
        engine.execute("CREATE OR REPLACE VIEW cf_v AS SELECT " + expression + " AS c FROM cf_t");
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.cf_v");
        rs.next();
        return String.valueOf(rs.getValue("data_type"));
    }

    private String refusalOf(final String sql) {
        final RuntimeException e = assertThrown(sql);
        final String flat = e.getMessage().replace('\n', ' ');
        final int at = flat.indexOf("Invalid argument types");
        return at < 0 ? flat : flat.substring(at);
    }

    private RuntimeException assertThrown(final String sql) {
        return org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
    }

    /** A scalar beside a string gives the full-width VARCHAR, whichever scalar and whichever order. */
    @Test
    public void everyScalarFamilyBesideAStringGivesFullWidthText() {
        assertEquals(FULL_WIDTH_TEXT, declaredType("n || s"));
        assertEquals(FULL_WIDTH_TEXT, declaredType("d || s"));
        assertEquals(FULL_WIDTH_TEXT, declaredType("b || s"));
        assertEquals(FULL_WIDTH_TEXT, declaredType("v || s"));
        assertEquals(FULL_WIDTH_TEXT, declaredType("v || v"));
    }

    /**
     * The container families are refused against everything — a string, a number, a VARIANT and each
     * other. VARIANT is emphatically not one of them.
     */
    @Test
    public void aContainerOperandIsRefusedAgainstEverything() {
        assertEquals("Invalid argument types for function '||': (OBJECT, VARCHAR(4))",
            refusalOf("SELECT o || s AS c FROM cf_t"));
        assertEquals("Invalid argument types for function '||': (VARCHAR(4), OBJECT)",
            refusalOf("SELECT s || o AS c FROM cf_t"));
        assertEquals("Invalid argument types for function '||': (ARRAY, VARCHAR(4))",
            refusalOf("SELECT a || s AS c FROM cf_t"));
        assertEquals("Invalid argument types for function '||': (OBJECT, NUMBER(5,1))",
            refusalOf("SELECT o || n AS c FROM cf_t"));
        assertEquals("Invalid argument types for function '||': (OBJECT, VARIANT)",
            refusalOf("SELECT o || v AS c FROM cf_t"));
        assertEquals("Invalid argument types for function '||': (OBJECT, OBJECT)",
            refusalOf("SELECT o || o AS c FROM cf_t"));
        assertEquals("Invalid argument types for function '||': (ARRAY, ARRAY)",
            refusalOf("SELECT a || a AS c FROM cf_t"));
    }

    /**
     * And the refusal is a COMPILE-TIME one, so a view over such a body cannot be created. Frostlake
     * used to raise the right sentence without the compile-time marker, and the view was accepted —
     * with no columns at all.
     */
    @Test
    public void aViewOverAContainerConcatenationCannotBeCreated() {
        final RuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute("CREATE OR REPLACE VIEW cf_bad AS SELECT o || s AS c FROM cf_t");
                }
            });
        assertTrue(e.getMessage().contains("Invalid argument types for function '||'"),
            "view creation gave: " + e.getMessage());
        assertTrue(e.getMessage().contains("SQL compilation error"),
            "the refusal must carry the compile-time marker: " + e.getMessage());
    }

    /** BINARY joins only BINARY; anything else beside it is refused. */
    @Test
    public void binaryJoinsOnlyBinary() {
        assertEquals("Invalid argument types for function '||': (BINARY(4), VARCHAR(4))",
            refusalOf("SELECT bin || s AS c FROM cf_t"));
        assertEquals("Invalid argument types for function '||': (BINARY(4), NUMBER(5,1))",
            refusalOf("SELECT bin || n AS c FROM cf_t"));
    }

    /**
     * Two binaries add their lengths, and a VIEW declares a sum past the 8MB column default at that
     * default — the plan's own width is the sum (see BinaryConcatenationWidthTest).
     */
    @Test
    public void twoBinariesAddTheirLengthsAndAViewSettlesTheSum() {
        assertTrue(declaredType("bin || bin").contains("\"length\":8"),
            "BINARY(4) || BINARY(4) should be 8 bytes: " + declaredType("bin || bin"));
        assertTrue(declaredType("CAST(s AS BINARY(8388608)) || bin").contains("\"length\":8388608"),
            "a view declares an overlong binary pair at the column default");
        assertTrue(declaredType("CAST(s AS BINARY(8388608)) || CAST(s AS BINARY(8388608))")
                .contains("\"length\":8388608"),
            "and two 8MB binaries the same");
    }

    /** The string counterpart saturates the same way, at a VARCHAR's own maximum. */
    @Test
    public void twoStringsSaturateAtTheVarcharMaximum() {
        assertEquals(FULL_WIDTH_TEXT, declaredType("CAST(s AS VARCHAR(16777216)) || s"));
    }
}
