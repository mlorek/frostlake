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
import dev.frostlake.values.BinaryValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A BINARY argument contributes its BYTES, never its hex rendering.
 *
 * <p>Every expected value below is what a LIVE Snowflake account returned, pinned as a
 * literal. That is the gap that let the family diverge: the round trip
 * {@code HEX_DECODE_STRING(HEX_ENCODE(x))} passed regardless, because both directions applied the
 * same wrong step, and the VARCHAR path most tests use was always correct.
 *
 * <p>The VARCHAR-argument cases and the no-format {@code TO_VARCHAR} case are the BOUNDARY — hex is
 * the right rendering there — and are asserted alongside so a fix cannot invert them.
 */
public class BinaryArgumentByteSemanticsTest extends BaseDatabaseTest {

    private Object value(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private String scalar(final String sql) {
        final Object v = value(sql);
        return v == null ? null : v.toString();
    }

    // ── the filed defect: encoders over a BINARY argument ────────────────────

    @Test
    public void hexEncodeEncodesTheBytesOfABinaryArgument() {
        // Encoding the hex TEXT instead yielded 343834353443 — valid-looking hex, entirely wrong.
        assertEquals("48454C", scalar("SELECT HEX_ENCODE(TO_BINARY('48454C','HEX'))"));
        assertEquals("0CC175B9C0F1B6A831C399E269772661", scalar("SELECT HEX_ENCODE(MD5_BINARY('a'))"));
        assertEquals("CA978112CA1BBDCAFAC231B39A23DC4DA786EFF8147C4E72B9807785AFEE48BB",
            scalar("SELECT HEX_ENCODE(SHA2_BINARY('a'))"));
        assertEquals("86F7E437FAA5A7FCE15D1DDCB9EAEAEA377667B8",
            scalar("SELECT HEX_ENCODE(SHA1_BINARY('a'))"));
        assertEquals("051068656C6C6F", scalar("SELECT HEX_ENCODE(COMPRESS('hello','snappy'))"));
    }

    @Test
    public void base64EncodeEncodesTheBytesOfABinaryArgument() {
        // Base64 of the hex TEXT instead yielded MDUxMDY4NjU2QzZDNkY=.
        assertEquals("BRBoZWxsbw==", scalar("SELECT BASE64_ENCODE(COMPRESS('hello','snappy'))"));
        assertEquals("DMF1ucDxtqgxw5niaXcmYQ==", scalar("SELECT BASE64_ENCODE(MD5_BINARY('a'))"));
        assertEquals("eJzLSM3JyQcABiwCFQ==", scalar("SELECT BASE64_ENCODE(COMPRESS('hello','zlib'))"));
    }

    /** The boundary: a VARCHAR argument is encoded as its own UTF-8 text, and always was. */
    @Test
    public void encodersStillEncodeTheTextOfANonBinaryArgument() {
        assertEquals("68656C6C6F", scalar("SELECT HEX_ENCODE('hello')"));
        assertEquals("aGVsbG8=", scalar("SELECT BASE64_ENCODE('hello')"));
        assertEquals("313233", scalar("SELECT HEX_ENCODE(123)"));
        assertEquals("74727565", scalar("SELECT HEX_ENCODE(TRUE)"));
        assertEquals("323032342D30312D3031", scalar("SELECT HEX_ENCODE('2024-01-01'::DATE)"));
        assertEquals("", scalar("SELECT HEX_ENCODE(TO_BINARY('','HEX'))"));
        assertNull(scalar("SELECT HEX_ENCODE(NULL)"));
        assertNull(scalar("SELECT BASE64_ENCODE(NULL)"));
    }

    /** The boundary: rendering a BINARY as hex text is RIGHT here, and must stay that way. */
    @Test
    public void toVarcharWithoutAFormatStillRendersBinaryAsHex() {
        assertEquals("051068656C6C6F", scalar("SELECT TO_VARCHAR(COMPRESS('hello','snappy'))"));
        assertEquals("48454C4C4F", scalar("SELECT TO_CHAR(TO_BINARY('48454C4C4F','HEX'))"));
        assertEquals("48454C4C4F", scalar("SELECT TO_BINARY('48454C4C4F','HEX')::VARCHAR"));
    }

    /** The round trip that hid the defect — it passed before the fix and must pass after it. */
    @Test
    public void textRoundTripsStillHold() {
        assertEquals("hi", scalar("SELECT HEX_DECODE_STRING(HEX_ENCODE('hi'))"));
        assertEquals("hi", scalar("SELECT BASE64_DECODE_STRING(BASE64_ENCODE('hi'))"));
        assertEquals("48454C",
            scalar("SELECT HEX_ENCODE(HEX_DECODE_BINARY(HEX_ENCODE(TO_BINARY('48454C','HEX'))))"));
        assertEquals("48454C",
            scalar("SELECT HEX_ENCODE(BASE64_DECODE_BINARY(BASE64_ENCODE(TO_BINARY('48454C','HEX'))))"));
    }

    // ── the same mistake in the digest family ────────────────────────────────

    @Test
    public void digestsHashTheBytesOfABinaryArgument() {
        // 0x61 is 'a', so each of these equals the digest of the one-character string.
        assertEquals("0cc175b9c0f1b6a831c399e269772661", scalar("SELECT MD5(TO_BINARY('61','HEX'))"));
        assertEquals("86f7e437faa5a7fce15d1ddcb9eaeaea377667b8", scalar("SELECT SHA1(TO_BINARY('61','HEX'))"));
        assertEquals("ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            scalar("SELECT SHA2(TO_BINARY('61','HEX'))"));
        assertEquals(scalar("SELECT MD5('a')"), scalar("SELECT MD5(TO_BINARY('61','HEX'))"));
        assertEquals("0CC175B9C0F1B6A831C399E269772661",
            scalar("SELECT HEX_ENCODE(MD5_BINARY(TO_BINARY('61','HEX')))"));
        assertEquals("1F40FC92DA241694750979EE6CF582F2D5D7D28E18335DE05ABC54D0560E0F5302860C652BF08D5"
                + "60252AA5E74210546F369FBBBCE8C12CFC7957B2652FE9A75",
            scalar("SELECT HEX_ENCODE(SHA2_BINARY(TO_BINARY('61','HEX'), 512))"));
    }

    /** A TIMESTAMP is digested as Snowflake's output text, not java.time's {@code T}-separated form. */
    @Test
    public void digestsHashTheSnowflakeRenderingOfATemporal() {
        assertEquals("550566bf6e75f4bcc181bd762df0728a",
            scalar("SELECT MD5('2024-01-02 03:04:05.000'::TIMESTAMP_NTZ)"));
        assertEquals(scalar("SELECT MD5('2024-01-02 03:04:05.000')"),
            scalar("SELECT MD5('2024-01-02 03:04:05.000'::TIMESTAMP_NTZ)"));
        assertEquals("323032342D30312D30322030333A30343A30352E303030",
            scalar("SELECT HEX_ENCODE('2024-01-02 03:04:05.000'::TIMESTAMP_NTZ)"));
    }

    // ── string functions: BYTE semantics, and a BINARY result ────────────────

    @Test
    public void lengthFunctionsCountBytesNotHexDigits() {
        assertEquals("3", scalar("SELECT LEN(TO_BINARY('48454C','HEX'))"));
        assertEquals("3", scalar("SELECT LENGTH(TO_BINARY('48454C','HEX'))"));
        assertEquals("3", scalar("SELECT OCTET_LENGTH(TO_BINARY('48454C','HEX'))"));
        assertEquals("24", scalar("SELECT BIT_LENGTH(TO_BINARY('48454C','HEX'))"));
        assertEquals("16", scalar("SELECT LEN(MD5_BINARY('a'))"));
    }

    @Test
    public void substrSlicesBytesAndYieldsBinary() {
        // Slicing the hex rendering returned 84 — half of each of two different bytes.
        assertEquals("454C", scalar("SELECT SUBSTR(TO_BINARY('48454C4C4F','HEX'), 2, 2)"));
        assertEquals("454C4C4F", scalar("SELECT SUBSTR(TO_BINARY('48454C4C4F','HEX'), 2)"));
        assertEquals("4C4F", scalar("SELECT SUBSTR(TO_BINARY('48454C4C4F','HEX'), -2, 2)"));
        assertEquals("4845", scalar("SELECT SUBSTR(TO_BINARY('48454C4C4F','HEX'), 0, 2)"));
        assertEquals("48454C4C4F", scalar("SELECT SUBSTR(TO_BINARY('48454C4C4F','HEX'), 1, 99)"));
        assertEquals("4C", scalar("SELECT SUBSTRING(TO_BINARY('48454C4C4F','HEX'), 3, 1)"));
        assertEquals("", scalar("SELECT SUBSTR(TO_BINARY('48454C4C4F','HEX'), 9, 2)"));
        assertEquals("", scalar("SELECT SUBSTR(TO_BINARY('48454C4C4F','HEX'), -99, 2)"));
        assertInstanceOf(BinaryValue.class, value("SELECT SUBSTR(TO_BINARY('48454C4C4F','HEX'), 2, 2)"));
    }

    /** The same window rules on the VARCHAR side, where a negative start used to throw. */
    @Test
    public void substrWindowRulesMatchOnVarchar() {
        assertEquals("lo", scalar("SELECT SUBSTR('hello', -2, 2)"));
        assertEquals("he", scalar("SELECT SUBSTR('hello', 0, 2)"));
        assertEquals("", scalar("SELECT SUBSTR('hello', -99, 2)"));
        assertEquals("", scalar("SELECT SUBSTR('hello', 2, -1)"));
        assertEquals("ello", scalar("SELECT SUBSTR('hello', 2)"));
        assertEquals("hello", scalar("SELECT SUBSTR('hello', 1, 99)"));
    }

    @Test
    public void leftRightAndReverseWorkOnBytes() {
        assertEquals("4845", scalar("SELECT LEFT(TO_BINARY('48454C4C4F','HEX'), 2)"));
        assertEquals("4C4F", scalar("SELECT RIGHT(TO_BINARY('48454C4C4F','HEX'), 2)"));
        assertEquals("48454C4C4F", scalar("SELECT LEFT(TO_BINARY('48454C4C4F','HEX'), 99)"));
        assertEquals("", scalar("SELECT LEFT(TO_BINARY('48454C4C4F','HEX'), 0)"));
        // Reversing the hex rendering swapped the nibbles inside each byte too (F4C4C45484).
        assertEquals("4F4C4C4548", scalar("SELECT REVERSE(TO_BINARY('48454C4C4F','HEX'))"));
        assertEquals("CCBBAA", scalar("SELECT REVERSE(TO_BINARY('AABBCC','HEX'))"));
        assertInstanceOf(BinaryValue.class, value("SELECT LEFT(TO_BINARY('48454C4C4F','HEX'), 2)"));
        assertInstanceOf(BinaryValue.class, value("SELECT REVERSE(TO_BINARY('AABBCC','HEX'))"));
    }

    @Test
    public void padsRepeatThePadPatternByteWise() {
        assertEquals("0102014845", scalar("SELECT LPAD(TO_BINARY('4845','HEX'), 5, TO_BINARY('0102','HEX'))"));
        assertEquals("4845010201", scalar("SELECT RPAD(TO_BINARY('4845','HEX'), 5, TO_BINARY('0102','HEX'))"));
        assertEquals("00004845", scalar("SELECT LPAD(TO_BINARY('4845','HEX'), 4, TO_BINARY('00','HEX'))"));
        assertEquals("48450000", scalar("SELECT RPAD(TO_BINARY('4845','HEX'), 4, TO_BINARY('00','HEX'))"));
        assertEquals("ABABAB", scalar("SELECT LPAD(TO_BINARY('','HEX'), 3, TO_BINARY('AB','HEX'))"));
        // Longer than the target: truncated to the LEADING bytes, by both pads.
        assertEquals("4845", scalar("SELECT LPAD(TO_BINARY('48454C','HEX'), 2, TO_BINARY('00','HEX'))"));
        assertEquals("4845", scalar("SELECT RPAD(TO_BINARY('48454C','HEX'), 2, TO_BINARY('00','HEX'))"));
        assertInstanceOf(BinaryValue.class,
            value("SELECT LPAD(TO_BINARY('4845','HEX'), 4, TO_BINARY('00','HEX'))"));
    }

    @Test
    public void positionSearchesByByte() {
        // Searching the hex renderings found the digit pair at character 3, not the byte at 2.
        assertEquals("2", scalar("SELECT POSITION(TO_BINARY('45','HEX'), TO_BINARY('48454C','HEX'))"));
        assertEquals("3", scalar("SELECT POSITION(TO_BINARY('4C','HEX'), TO_BINARY('48454C4C4F','HEX'))"));
        assertEquals("4", scalar("SELECT POSITION(TO_BINARY('4C','HEX'), TO_BINARY('48454C4C4F','HEX'), 4)"));
        assertEquals("0", scalar("SELECT POSITION(TO_BINARY('99','HEX'), TO_BINARY('48454C4C4F','HEX'))"));
        assertEquals("1", scalar("SELECT POSITION(TO_BINARY('','HEX'), TO_BINARY('4845','HEX'))"));
        assertEquals("3", scalar("SELECT CHARINDEX(TO_BINARY('4C','HEX'), TO_BINARY('48454C4C4F','HEX'))"));
    }

    @Test
    public void concatenationOfBinariesStaysBinary() {
        assertEquals("48454C4F", scalar("SELECT TO_BINARY('4845','HEX') || TO_BINARY('4C4F','HEX')"));
        assertEquals("48454C4F", scalar("SELECT CONCAT(TO_BINARY('4845','HEX'), TO_BINARY('4C4F','HEX'))"));
        // The value only stays right downstream because the result is BINARY, not its hex text.
        assertEquals("48454C4F",
            scalar("SELECT HEX_ENCODE(TO_BINARY('4845','HEX') || TO_BINARY('4C4F','HEX'))"));
        assertEquals("4", scalar("SELECT LENGTH(TO_BINARY('4845','HEX') || TO_BINARY('4C4F','HEX'))"));
        assertInstanceOf(BinaryValue.class,
            value("SELECT TO_BINARY('4845','HEX') || TO_BINARY('4C4F','HEX')"));
    }

    // ── TO_VARCHAR(<binary>, <format>) names the target ENCODING ─────────────

    @Test
    public void toVarcharWithABinaryFormatEncodesTheBytes() {
        assertEquals("HELLO", scalar("SELECT TO_VARCHAR(TO_BINARY('48454C4C4F','HEX'),'UTF-8')"));
        assertEquals("SEVMTE8=", scalar("SELECT TO_VARCHAR(TO_BINARY('48454C4C4F','HEX'),'BASE64')"));
        assertEquals("48454C4C4F", scalar("SELECT TO_VARCHAR(TO_BINARY('48454C4C4F','HEX'),'HEX')"));
        assertEquals("SEVMTE8=", scalar("SELECT TO_CHAR(TO_BINARY('48454C4C4F','HEX'),'BASE64')"));
        // Case-insensitive, and UTF8 is accepted alongside UTF-8.
        assertEquals("HELLO", scalar("SELECT TO_VARCHAR(TO_BINARY('48454C4C4F','HEX'),'utf-8')"));
        assertEquals("HELLO", scalar("SELECT TO_VARCHAR(TO_BINARY('48454C4C4F','HEX'),'UTF8')"));
        assertEquals("SEVMTE8=", scalar("SELECT TO_VARCHAR(TO_BINARY('48454C4C4F','HEX'),'base64')"));
    }

    // ── the composition that matters: bytes survive a BINARY column ──────────

    @Test
    public void aBinaryColumnEncodesItsStoredBytes() {
        engine.executeUpdate("CREATE TABLE test_db.test_schema.b159 (id INT, b BINARY)");
        engine.executeUpdate("INSERT INTO test_db.test_schema.b159 SELECT 1, TO_BINARY('00FF','HEX')");
        engine.executeUpdate("INSERT INTO test_db.test_schema.b159 SELECT 2, TO_BINARY('0100','HEX')");
        engine.executeUpdate("INSERT INTO test_db.test_schema.b159 SELECT 3, TO_BINARY('FF','HEX')");

        assertEquals("00FF", scalar("SELECT HEX_ENCODE(b) FROM test_db.test_schema.b159 WHERE id = 1"));
        assertEquals("AP8=", scalar("SELECT BASE64_ENCODE(b) FROM test_db.test_schema.b159 WHERE id = 1"));
        assertEquals("/w==", scalar("SELECT BASE64_ENCODE(b) FROM test_db.test_schema.b159 WHERE id = 3"));
        assertEquals("2", scalar("SELECT LENGTH(b) FROM test_db.test_schema.b159 WHERE id = 2"));
        assertEquals("00594fd4f42ba43fc1ca0427a0576295",
            scalar("SELECT MD5(b) FROM test_db.test_schema.b159 WHERE id = 3"));
        // Unsigned byte order: 00FF < 0100 < FF, which the hex rendering happens to agree with.
        assertEquals("00FF", scalar("SELECT MIN(b) FROM test_db.test_schema.b159"));
        assertEquals("FF", scalar("SELECT MAX(b) FROM test_db.test_schema.b159"));

        // A sliced BINARY written back to a BINARY column keeps its bytes.
        engine.executeUpdate("INSERT INTO test_db.test_schema.b159 "
            + "SELECT 4, SUBSTR(TO_BINARY('48454C4C4F','HEX'), 2, 2)");
        assertEquals("454C", scalar("SELECT HEX_ENCODE(b) FROM test_db.test_schema.b159 WHERE id = 4"));
        assertEquals("2", scalar("SELECT LENGTH(b) FROM test_db.test_schema.b159 WHERE id = 4"));
    }
}
