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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What SHOW COLUMNS' {@code fixed} cell means for a BINARY, which is not what it looks like.
 *
 * <p>Every binary reports the type BINARY — a VARBINARY column DESCRIBEs as {@code BINARY(4)} — so the
 * two spellings are told apart by this one flag, and it reads the SPELLING rather than the family.
 * Frostlake used to answer it from the family, so every binary in the engine read fixed true.
 *
 * <p>The rule is not "declared versus derived" either, which is the trap this class exists to pin: a
 * CAST to BINARY is derived and reads TRUE, while a declared VARBINARY column reads FALSE. It is
 * whether the type was spelled BINARY.
 */
public class BinaryFixedFlagTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute(
            "CREATE TABLE bx (s VARCHAR(4), bin BINARY(4), binx BINARY, n NUMBER, vb VARBINARY(4))");
        engine.execute(
            "INSERT INTO bx VALUES ('4142', TO_BINARY('41424344'), TO_BINARY('4142'), 1, TO_BINARY('4142'))");
    }

    /** The descriptor a one-column view over the expression declares. */
    private String declaredType(final String expression) {
        engine.execute("CREATE OR REPLACE VIEW bx_v AS SELECT " + expression + " AS c FROM bx");
        return descriptorOfBxView();
    }

    /** The same, for a whole select body rather than one item. */
    private String declaredTypeOfBody(final String select) {
        engine.execute("CREATE OR REPLACE VIEW bx_v AS " + select);
        return descriptorOfBxView();
    }

    private String descriptorOfBxView() {
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.bx_v");
        rs.next();
        return String.valueOf(rs.getValue("data_type"));
    }

    private void assertFixed(final boolean expected, final String expression) {
        final String descriptor = declaredType(expression);
        assertTrue(descriptor.contains("\"fixed\":" + expected),
            expression + " should read fixed " + expected + ", but its descriptor is " + descriptor);
    }

    /** A column declared BINARY is fixed, with or without a width. */
    @Test
    public void aDeclaredBinaryColumnIsFixed() {
        assertFixed(true, "bin");
        assertFixed(true, "binx");
        assertFixed(true, "(bin)");
    }

    /** A column declared VARBINARY is NOT, though it reports the very same type name. */
    @Test
    public void aDeclaredVarbinaryColumnIsNot() {
        assertEquals("""
            {"type":"BINARY","length":4,"byteLength":4,"nullable":true,"fixed":false}""",
            declaredType("vb"));
    }

    /**
     * A CAST decides it by the WORD CAST TO, not by the operand: a cast to BINARY is fixed even over a
     * concatenation, and a cast to VARBINARY is not even from a declared BINARY column.
     */
    @Test
    public void aCastIsJudgedByTheSpellingItCastsTo() {
        assertFixed(true, "CAST(bin AS BINARY)");
        assertFixed(true, "CAST(s AS BINARY)");
        assertFixed(true, "CAST(bin || bin AS BINARY)");
        assertFixed(true, "CAST(TO_BINARY(s, 'HEX') AS BINARY)");
        assertFixed(false, "CAST(bin AS VARBINARY)");
        assertFixed(false, "CAST(vb AS VARBINARY)");
    }

    /** Concatenation is a width nobody declared, in either spelling. */
    @Test
    public void aConcatenationIsNotFixed() {
        assertFixed(false, "bin || bin");
        assertFixed(false, "binx || binx");
        assertFixed(false, "bin || X'41'");
    }

    /**
     * NO function produces a fixed binary — the conversions, the decoders, the hashes and the crypto
     * pair alike. TO_BINARY(s) and CAST(s AS BINARY) convert identically and differ ONLY here.
     */
    @Test
    public void noBinaryReturningFunctionIsFixed() {
        assertFixed(false, "TO_BINARY(s, 'HEX')");
        assertFixed(false, "TRY_TO_BINARY(s, 'HEX')");
        assertFixed(false, "HEX_DECODE_BINARY('414243')");
        assertFixed(false, "TRY_HEX_DECODE_BINARY(s)");
        assertFixed(false, "BASE64_DECODE_BINARY('QUJD')");
        assertFixed(false, "TRY_BASE64_DECODE_BINARY('QUJD')");
        assertFixed(false, "SHA1_BINARY(s)");
        assertFixed(false, "SHA2_BINARY(s)");
        assertFixed(false, "MD5_BINARY(s)");
        assertFixed(false, "COMPRESS(s, 'ZLIB')");
        assertFixed(false, "DECOMPRESS_BINARY(COMPRESS(s, 'ZLIB'), 'ZLIB')");
        assertFixed(false, "ENCRYPT(bin, 'k')");
        assertFixed(false, "DECRYPT(ENCRYPT(bin, 'k'), 'k')");
        assertFixed(false, "AS_BINARY(TO_VARIANT(bin))");
        assertFixed(false, "STRING_AS_BINARY(s)");
    }

    /** Nor is a literal, which is the shape that reads most like a declaration and is not one. */
    @Test
    public void aBinaryLiteralIsNotFixed() {
        assertEquals("""
            {"type":"BINARY","length":2,"byteLength":2,"nullable":true,"fixed":false}""",
            declaredType("X'4142'"));
    }

    /**
     * A UNION keeps it when the branches agree on the WIDTH — a declared BINARY(4) beside a declared
     * VARBINARY(4) is still fixed, which is why the flag cannot simply be ANDed across the branches.
     */
    @Test
    public void aUnionOfEquallyWideBranchesStaysFixed() {
        assertTrue(declaredTypeOfBody("SELECT bin AS c FROM bx UNION ALL SELECT bin FROM bx")
            .contains("\"fixed\":true"));
        assertTrue(declaredTypeOfBody("SELECT bin AS c FROM bx UNION ALL SELECT vb FROM bx")
            .contains("\"fixed\":true"));
        assertTrue(declaredTypeOfBody("SELECT bin AS c FROM bx UNION SELECT bin FROM bx")
            .contains("\"fixed\":true"));
    }

    /** The string family is unaffected: it reads false throughout, CHAR included. */
    @Test
    public void theStringFamilyStillReadsFalse() {
        assertFixed(false, "s");
        assertFixed(false, "CAST(s AS CHAR(4))");
        assertFixed(false, "TO_CHAR(bin)");
    }
}
