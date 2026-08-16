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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TRY_DECRYPT_RAW — the lenient sibling of DECRYPT_RAW: identical round-trip on good inputs, SQL
 * NULL (never an error) when the ciphertext, key or tag do not line up.
 */
public class TryDecryptRawTest extends BaseDatabaseTest {

    private static final String KEY = "TO_BINARY('000102030405060708090A0B0C0D0E0F', 'HEX')";
    private static final String WRONG_KEY = "TO_BINARY('FF0102030405060708090A0B0C0D0EFF', 'HEX')";
    private static final String IV = "TO_BINARY('0102030405060708090A0B0C', 'HEX')";

    private Object one(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void roundTripRecoversThePlaintext() {
        final Object hex = one("WITH e AS (SELECT ENCRYPT_RAW(TO_BINARY('CAFEBABE', 'HEX'), "
            + KEY + ", " + IV + ") AS o)"
            + " SELECT TO_VARCHAR(TRY_DECRYPT_RAW(TO_BINARY(o:ciphertext::VARCHAR, 'HEX'), " + KEY + ","
            + " TO_BINARY(o:iv::VARCHAR, 'HEX'), TO_BINARY('', 'HEX'), 'AES-GCM', TO_BINARY(o:tag::VARCHAR, 'HEX')), 'HEX') FROM e");
        assertEquals("CAFEBABE", String.valueOf(hex));
    }

    @Test
    public void wrongKeyYieldsNullNotAnError() {
        assertNull(one("WITH e AS (SELECT ENCRYPT_RAW(TO_BINARY('CAFEBABE', 'HEX'), "
            + KEY + ", " + IV + ") AS o)"
            + " SELECT TRY_DECRYPT_RAW(TO_BINARY(o:ciphertext::VARCHAR, 'HEX'), " + WRONG_KEY + ","
            + " TO_BINARY(o:iv::VARCHAR, 'HEX'), TO_BINARY('', 'HEX'), 'AES-GCM', TO_BINARY(o:tag::VARCHAR, 'HEX')) FROM e"));
    }

    @Test
    public void threeArgFormIsAcceptedButCannotAuthenticate() {
        // live: TRY_DECRYPT_RAW(ct, key, iv) compiles, and yields NULL — GCM needs the tag
        assertNull(one("WITH e AS (SELECT ENCRYPT_RAW(TO_BINARY('CAFEBABE', 'HEX'), "
            + KEY + ", " + IV + ") AS o)"
            + " SELECT TRY_DECRYPT_RAW(TO_BINARY(o:ciphertext::VARCHAR, 'HEX'), " + KEY + ", TO_BINARY(o:iv::VARCHAR, 'HEX')) FROM e"));
    }

    @Test
    public void varcharArgumentIsRejectedNotSwallowed() {
        // live: a VARCHAR where BINARY is required is "Invalid argument types" — a compile error
        // even for TRY_, not a NULL. The key here is a hex VARCHAR, not TO_BINARY(...).
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TRY_DECRYPT_RAW(TO_BINARY('CAFE','HEX'),"
                    + " '000102030405060708090A0B0C0D0E0F', TO_BINARY('0102030405060708090A0B0C','HEX'))");
            }
        });
        assertTrue(String.valueOf(ex.getMessage()).contains("Invalid argument types"), ex.getMessage());
    }

    @Test
    public void garbageCiphertextYieldsNull() {
        assertNull(one("SELECT TRY_DECRYPT_RAW(TO_BINARY('DEADBEEF', 'HEX'), " + KEY + ", "
            + IV + ", TO_BINARY('', 'HEX'), 'AES-GCM', TO_BINARY('00000000000000000000000000000000', 'HEX'))"));
    }
}
