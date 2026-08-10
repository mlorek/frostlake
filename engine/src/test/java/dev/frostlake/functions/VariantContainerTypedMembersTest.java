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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Extended scalar types keep their type INSIDE a variant container. Snowflake's VARIANT is a superset
 * of JSON: a DATE, TIME, TIMESTAMP or BINARY placed in an OBJECT or ARRAY still reports its own type,
 * while JSON alone could only carry the rendered text.
 *
 * <p>Live-measured on the account: {@code TYPEOF(OBJECT_CONSTRUCT('d', <date>):d)} is {@code DATE},
 * {@code ('t', <time>)} is {@code TIME}, {@code ('ts', <ts>)} is {@code TIMESTAMP_NTZ},
 * {@code ('b', <binary>)} is {@code BINARY}, and {@code ARRAY_CONSTRUCT(<binary>)[0]} is
 * {@code BINARY}. Frostlake reported VARCHAR for every one of them.
 *
 * <p>The other half of the contract is that NOTHING about the text changes: the JSON rendering,
 * {@code ::VARCHAR}, TO_JSON and OBJECT_KEYS must be exactly what they always were — the type rides
 * alongside the text, it does not re-encode it.
 */
public class VariantContainerTypedMembersTest extends BaseDatabaseTest {

    private static final String BIN = "TO_BINARY('CAFE', 'HEX')";

    private String one(final String sql) {
        return String.valueOf(engine.executeQuery("SELECT " + sql).getRows().get(0).getValue(0));
    }

    @Test
    public void objectMembersKeepTheirExtendedType() {
        assertEquals("DATE", one("TYPEOF(OBJECT_CONSTRUCT('d', '2026-01-02'::DATE):d)"));
        assertEquals("TIME", one("TYPEOF(OBJECT_CONSTRUCT('t', '12:30:00'::TIME):t)"));
        assertEquals("TIMESTAMP_NTZ",
            one("TYPEOF(OBJECT_CONSTRUCT('ts', '2026-01-02 03:04:05'::TIMESTAMP_NTZ):ts)"));
        assertEquals("BINARY", one("TYPEOF(OBJECT_CONSTRUCT('b', " + BIN + "):b)"));
    }

    @Test
    public void arrayElementsKeepTheirExtendedType() {
        assertEquals("BINARY", one("TYPEOF(ARRAY_CONSTRUCT(" + BIN + ")[0])"));
        assertEquals("DATE", one("TYPEOF(ARRAY_CONSTRUCT('2026-01-02'::DATE)[0])"));
    }

    @Test
    public void jsonNativeMembersAreUnaffected() {
        assertEquals("INTEGER", one("TYPEOF(OBJECT_CONSTRUCT('n', 42):n)"));
        assertEquals("VARCHAR", one("TYPEOF(OBJECT_CONSTRUCT('s', 'plain'):s)"));
        assertEquals("BOOLEAN", one("TYPEOF(OBJECT_CONSTRUCT('b', TRUE):b)"));
    }

    @Test
    public void theTextFormIsUnchanged() {
        // the type rides ALONGSIDE the text; every text-producing path must be untouched
        assertEquals("{\"b\":\"CAFE\"}", one("TO_JSON(OBJECT_CONSTRUCT('b', " + BIN + "))"));
        assertEquals("{\"d\":\"2026-01-02\"}",
            one("TO_JSON(OBJECT_CONSTRUCT('d', '2026-01-02'::DATE))"));
        assertEquals("CAFE", one("OBJECT_CONSTRUCT('b', " + BIN + "):b::VARCHAR"));
        assertEquals("b", one("ARRAY_TO_STRING(OBJECT_KEYS(OBJECT_CONSTRUCT('b', " + BIN + ")), ',')"));
    }

    @Test
    public void typedAccessorsReadTheMembers() {
        assertEquals("CAFE",
            one("TO_VARCHAR(AS_BINARY(OBJECT_CONSTRUCT('b', " + BIN + "):b), 'HEX')"));
        assertEquals("true", one("IS_BINARY(OBJECT_CONSTRUCT('b', " + BIN + "):b)"));
        assertEquals("2026-01-02", one("AS_DATE(OBJECT_CONSTRUCT('d', '2026-01-02'::DATE):d)"));
        assertEquals("true", one("IS_DATE(OBJECT_CONSTRUCT('d', '2026-01-02'::DATE):d)"));
    }

    @Test
    public void encryptRawMembersAreBinary() {
        final String key = "TO_BINARY('000102030405060708090A0B0C0D0E0F', 'HEX')";
        final String iv = "TO_BINARY('0102030405060708090A0B0C', 'HEX')";
        final String cte = "WITH e AS (SELECT ENCRYPT_RAW(TO_BINARY('CAFEBABE', 'HEX'), "
            + key + ", " + iv + ") AS o) SELECT ";
        assertEquals("BINARY", String.valueOf(engine.executeQuery(
            cte + "TYPEOF(o:ciphertext) FROM e").getRows().get(0).getValue(0)));
        assertEquals("BINARY", String.valueOf(engine.executeQuery(
            cte + "TYPEOF(o:iv) FROM e").getRows().get(0).getValue(0)));
        assertEquals("BINARY", String.valueOf(engine.executeQuery(
            cte + "TYPEOF(o:tag) FROM e").getRows().get(0).getValue(0)));
        // AS_BINARY over a member now returns the bytes, and the hex text is what it always was
        assertEquals(String.valueOf(engine.executeQuery(
                cte + "o:ciphertext::VARCHAR FROM e").getRows().get(0).getValue(0)),
            String.valueOf(engine.executeQuery(
                cte + "TO_VARCHAR(AS_BINARY(o:ciphertext), 'HEX') FROM e").getRows().get(0).getValue(0)));
    }

    @Test
    public void aStoredVariantKeepsItsMemberTypes() {
        engine.execute("CREATE TABLE vt (v VARIANT)");
        engine.execute("INSERT INTO vt SELECT OBJECT_CONSTRUCT('b', " + BIN + ", 'd', '2026-01-02'::DATE)");
        assertEquals("BINARY",
            String.valueOf(engine.executeQuery("SELECT TYPEOF(v:b) FROM vt").getRows().get(0).getValue(0)));
        assertEquals("DATE",
            String.valueOf(engine.executeQuery("SELECT TYPEOF(v:d) FROM vt").getRows().get(0).getValue(0)));
        assertEquals("CAFE",
            String.valueOf(engine.executeQuery("SELECT v:b::VARCHAR FROM vt").getRows().get(0).getValue(0)));
    }

    @Test
    public void parsedJsonTextStillReportsVarchar() {
        // A variant PARSED FROM TEXT never had an extended type to keep — the text is all there is,
        // and live agrees: PARSE_JSON('{"b":"CAFE"}'):b is a VARCHAR, not a BINARY.
        assertEquals("VARCHAR", one("TYPEOF(PARSE_JSON('{\"b\":\"CAFE\"}'):b)"));
        assertEquals("VARCHAR", one("TYPEOF(PARSE_JSON('{\"d\":\"2026-01-02\"}'):d)"));
    }
}
