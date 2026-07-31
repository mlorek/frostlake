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
import dev.frostlake.types.BinaryType;
import dev.frostlake.values.BinaryValue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BINARY as a real typed runtime value ({@link BinaryValue}): declared BINARY columns, hex-literal
 * and cast production, hex-semantics comparisons against strings, grouping/distinct/join/order/PK
 * behavior over byte equality, byte-count length functions, the *_DECODE_BINARY family, and the
 * hex rendering of binary inside semi-structured values.
 */
public class BinaryTypedValuesTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void binaryColumnsCarryTheBinaryType() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "asserts the ENGINE's own ResultSet column metadata; over JDBC a Snowflake BINARY column "
            + "arrives through the driver, and the live harness reconstructs a BinaryValue from the "
            + "bytes without the declared BinaryType — the value shape is checked, the metadata is not");
        engine.execute("CREATE TABLE bt_meta (b BINARY, vb VARBINARY)");
        engine.execute("INSERT INTO bt_meta VALUES (x'AB', x'CD')");
        final ResultSet rs = engine.executeQuery("SELECT b, vb FROM bt_meta");
        assertTrue(rs.getColumns().get(0).getDataType() instanceof BinaryType,
            "declared BINARY column should surface as BinaryType");
        assertTrue(rs.getRows().get(0).getValue(0) instanceof BinaryValue,
            "stored cell should be a BinaryValue");
    }

    @Test
    public void comparisonsAgainstStringsAreRejectedLikeSnowflake() {
        // Live-verified: there is NO implicit VARCHAR-to-BINARY conversion in comparisons —
        // Snowflake rejects them at compile time; use TO_BINARY explicitly.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                scalar("SELECT x'AB' = 'AB'");
            }
        });
        assertTrue(e.getMessage().contains("Can not convert parameter"), e.getMessage());
        assertTrue(e.getMessage().contains("[BINARY"), e.getMessage());
        assertEquals(Boolean.TRUE, scalar("SELECT x'AB' = TO_BINARY('AB', 'HEX')"));
        assertEquals(Boolean.TRUE, scalar("SELECT x'01' < x'02'"));
    }


    @Test
    public void groupByAndDistinctUseByteEquality() {
        engine.execute("CREATE TABLE bt_grp (b BINARY)");
        engine.execute("INSERT INTO bt_grp VALUES (x'AB'), (x'ab'), (x'CD')");
        assertEquals(2, engine.executeQuery("SELECT DISTINCT b FROM bt_grp").getRowCount());
        final ResultSet grouped = engine.executeQuery(
            "SELECT b, COUNT(*) AS n FROM bt_grp GROUP BY b ORDER BY b");
        assertEquals(2, grouped.getRowCount());
        assertEquals("AB", String.valueOf(grouped.getRows().get(0).getValue(0)));
        assertEquals(2L, ((Number) grouped.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void joinsMatchOnByteEquality() {
        engine.execute("CREATE TABLE bt_l (id INTEGER, b BINARY)");
        engine.execute("CREATE TABLE bt_r (b BINARY, tag VARCHAR)");
        engine.execute("INSERT INTO bt_l VALUES (1, x'0102'), (2, x'0304')");
        engine.execute("INSERT INTO bt_r VALUES (x'0102', 'match')");
        final ResultSet rs = engine.executeQuery(
            "SELECT l.id, r.tag FROM bt_l l JOIN bt_r r ON l.b = r.b");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void binaryPrimaryKeysUseByteIdentity() {
        // PK constraints are informational by default (Snowflake parity): the duplicate row is
        // accepted, and byte identity — not hex-text case — is what DISTINCT collapses on.
        engine.execute("CREATE TABLE bt_pk (b BINARY PRIMARY KEY)");
        engine.execute("INSERT INTO bt_pk VALUES (x'FF01')");
        engine.execute("INSERT INTO bt_pk VALUES (x'ff01')");
        assertEquals(2, engine.executeQuery("SELECT * FROM bt_pk").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT DISTINCT b FROM bt_pk").getRowCount());
    }

    @Test
    public void orderByUsesUnsignedByteOrder() {
        engine.execute("CREATE TABLE bt_ord (b BINARY)");
        engine.execute("INSERT INTO bt_ord VALUES (x'FF'), (x'01'), (x'7F')");
        final ResultSet rs = engine.executeQuery("SELECT b FROM bt_ord ORDER BY b");
        assertEquals("01", String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals("7F", String.valueOf(rs.getRows().get(1).getValue(0)));
        assertEquals("FF", String.valueOf(rs.getRows().get(2).getValue(0)),
            "0xFF is the largest unsigned byte");
    }

    @Test
    public void lengthFamilyCountsBytesNotHexCharacters() {
        assertEquals(4, ((Number) scalar("SELECT LENGTH(TO_BINARY('SNOW', 'UTF-8'))")).intValue());
        assertEquals(4L, ((Number) scalar("SELECT OCTET_LENGTH(TO_BINARY('SNOW', 'UTF-8'))")).longValue());
        assertEquals(32L, ((Number) scalar("SELECT BIT_LENGTH(TO_BINARY('SNOW', 'UTF-8'))")).longValue());
    }

    @Test
    public void decodeBinaryFamilyProducesBinary() {
        assertEquals("534E4F57", String.valueOf(scalar("SELECT HEX_DECODE_BINARY('534e4f57')")));
        assertEquals("534E4F57", String.valueOf(scalar("SELECT BASE64_DECODE_BINARY('U05PVw==')")));
        assertNull(scalar("SELECT TRY_HEX_DECODE_BINARY('zz')"));
        assertNull(scalar("SELECT TRY_BASE64_DECODE_BINARY('!not-base64!')"));
        assertEquals(Boolean.TRUE,
            scalar("SELECT HEX_DECODE_BINARY('534E4F57') = TO_BINARY('SNOW', 'UTF-8')"));
    }

    @Test
    public void binaryEmbedsInSemiStructuredAsHexText() {
        assertEquals("[\"AB12\"]", String.valueOf(scalar("SELECT ARRAY_CONSTRUCT(x'AB12')")));
    }

    @Test
    public void castBinaryToVarcharYieldsHexText() {
        assertEquals("AB12", scalar("SELECT x'AB12'::VARCHAR"));
        assertEquals("AB12", String.valueOf(scalar("SELECT 'ab12'::BINARY")));
    }
}
