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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * ENCRYPT and DECRYPT's additional authenticated data and encryption method, and the order the RAW functions
 * check their arguments in (live-verified). The AAD is bound into a GCM tag, so a mismatch fails the decryption;
 * the fourth argument names the method, whose spelling, algorithm, mode and padding are refused each with its
 * own sentence; every mode reads back what it wrote, and a mode other than GCM takes no AAD.
 */
public class EncryptDecryptMethodTest extends BaseDatabaseTest {

    private static final String FAILED = "Decryption failed. Check encrypted data, key, AAD, or AEAD tag.";
    private static final String NOT_THE_METHODS_INPUT =
        "Encrypted data input does not comply with the expected input of the selected encryption method";

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    /** The one row's cells, as text. */
    private List<String> row(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> cells = new ArrayList<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            cells.add(String.valueOf(rs.getRows().get(0).getValue(i)));
        }
        return cells;
    }

    @Test
    public void theAadIsBoundIntoTheTag() {
        assertEquals(FAILED, refusal("SELECT DECRYPT(ENCRYPT('a', 'p', 'aad'), 'p')"));
        assertEquals(FAILED, refusal("SELECT DECRYPT(ENCRYPT('a', 'p'), 'p', 'aad')"));
        assertEquals(FAILED, refusal("SELECT DECRYPT(ENCRYPT('a', 'p', 'aad'), 'p', 'aae')"));
        assertEquals(List.of("a", "a", "a", "a", "a", "a"), row("""
            SELECT TO_VARCHAR(DECRYPT(ENCRYPT('a', 'p', 'aad'), 'p', 'aad'), 'UTF-8'),
                TO_VARCHAR(DECRYPT(ENCRYPT('a', 'p', X'00'), 'p', X'00'), 'UTF-8'),
                TO_VARCHAR(DECRYPT(ENCRYPT('a', 'p', 'aad'), 'p', TO_BINARY('aad', 'UTF-8')), 'UTF-8'),
                TO_VARCHAR(DECRYPT(ENCRYPT('a', 'p', ''), 'p'), 'UTF-8'),
                TO_VARCHAR(DECRYPT(ENCRYPT('a', 'p', NULL), 'p'), 'UTF-8'),
                TO_VARCHAR(TRY_DECRYPT(ENCRYPT('a', 'p', 'aad'), 'p', 'aad'), 'UTF-8')"""));
        assertEquals(List.of("null"), row("SELECT TRY_DECRYPT(ENCRYPT('a', 'p', 'aad'), 'p')"));
    }

    @Test
    public void everyModeReadsBackWhatItWrote() {
        assertEquals(List.of("a", "a", "a", "a", "a", "a"), row("""
            SELECT TO_VARCHAR(DECRYPT(ENCRYPT('a', 'p', NULL, 'AES-GCM'), 'p', NULL, 'AES-GCM'), 'UTF-8'),
                TO_VARCHAR(DECRYPT(ENCRYPT('a', 'p', NULL, 'aes-gcm'), 'p', NULL, 'Aes-Gcm'), 'UTF-8'),
                TO_VARCHAR(DECRYPT(ENCRYPT('a', 'p', 'aad', 'AES-GCM'), 'p', 'aad', 'AES-GCM'), 'UTF-8'),
                TO_VARCHAR(DECRYPT(ENCRYPT('a', 'p', NULL, 'AES-CBC'), 'p', NULL, 'AES-CBC'), 'UTF-8'),
                TO_VARCHAR(DECRYPT(ENCRYPT('a', 'p', NULL, 'AES-ECB'), 'p', NULL, 'AES-ECB'), 'UTF-8'),
                TO_VARCHAR(DECRYPT(ENCRYPT('a', 'p', NULL, 'AES-CTR'), 'p', NULL, 'AES-CTR'), 'UTF-8')"""));
        assertEquals(List.of("abcdefghijklmnopqrstuvwxyz", "abcdefghijklmnopqrstuvwxyz"), row("""
            SELECT TO_VARCHAR(DECRYPT(ENCRYPT('abcdefghijklmnopqrstuvwxyz', 'p', NULL, 'AES-CFB'), 'p', NULL, 'AES-CFB'),
                    'UTF-8'),
                TO_VARCHAR(DECRYPT(ENCRYPT('abcdefghijklmnopqrstuvwxyz', 'p', NULL, 'AES-OFB'), 'p', NULL, 'AES-OFB'),
                    'UTF-8')"""));
        assertEquals(List.of("29", "29", "28", "29", "32", "48", "16", "17", "17", "17", "33", "32"), row("""
            SELECT LENGTH(ENCRYPT('a', 'p')), LENGTH(ENCRYPT('a', 'p', NULL, 'AES-GCM')), LENGTH(ENCRYPT('', 'p')),
                LENGTH(ENCRYPT('a', 'p', NULL, NULL)), LENGTH(ENCRYPT('a', 'p', NULL, 'AES-CBC')),
                LENGTH(ENCRYPT('abcdefghijklmnop', 'p', NULL, 'AES-CBC')), LENGTH(ENCRYPT('a', 'p', NULL, 'AES-ECB')),
                LENGTH(ENCRYPT('a', 'p', NULL, 'AES-CTR')), LENGTH(ENCRYPT('a', 'p', NULL, 'AES-OFB')),
                LENGTH(ENCRYPT('a', 'p', NULL, 'AES-CFB')), LENGTH(ENCRYPT('abcdefghijklmnopq', 'p', NULL, 'AES-CTR')),
                LENGTH(ENCRYPT(X'61', 'p', NULL, 'AES-CBC'))"""));
        assertEquals(List.of("32", "32", "32", "29", "32", "16"), row("""
            SELECT LENGTH(ENCRYPT('a', 'p', NULL, 'AES-CBC/pad:PKCS')), LENGTH(ENCRYPT('a', 'p', NULL, 'AES-CBC/pad:pkcs')),
                LENGTH(ENCRYPT('abcdefghijklmnop', 'p', NULL, 'aes-cbc/pad:none')),
                LENGTH(ENCRYPT('a', 'p', NULL, 'AES-GCM/pad:PKCS')), LENGTH(ENCRYPT('', 'p', NULL, 'AES-CBC')),
                LENGTH(ENCRYPT('', 'p', NULL, 'AES-CBC/pad:NONE'))"""));
    }

    @Test
    public void aMethodIsReadPartByPart() {
        assertEquals("Malformed encryption method parameter: nonsense", refusal("SELECT ENCRYPT('a', 'p', NULL, 'nonsense')"));
        assertEquals("Malformed encryption method parameter: AES", refusal("SELECT ENCRYPT('a', 'p', NULL, 'AES')"));
        assertEquals("Malformed encryption method parameter: 5", refusal("SELECT ENCRYPT('a', 'p', NULL, 5)"));
        assertEquals("Malformed encryption method parameter: AES-CBC/PAD:pkcs",
            refusal("SELECT ENCRYPT('a', 'p', NULL, 'AES-CBC/PAD:pkcs')"));
        assertEquals("Malformed encryption method parameter: AES-CBC/", refusal("SELECT ENCRYPT('a', 'p', NULL, 'AES-CBC/')"));
        assertEquals("Malformed encryption method parameter: AES_X-CBC", refusal("SELECT ENCRYPT('a', 'p', NULL, 'AES_X-CBC')"));
        assertEquals("Malformed encryption method parameter: AES-CBC/pad:PKCS/x",
            refusal("SELECT ENCRYPT('a', 'p', NULL, 'AES-CBC/pad:PKCS/x')"));
        assertEquals("Unsupported encryption algorithm: DES", refusal("SELECT ENCRYPT('a', 'p', NULL, 'des-cbc')"));
        assertEquals("Unsupported encryption algorithm: AES1", refusal("SELECT ENCRYPT('a', 'p', NULL, 'AES1-CBC')"));
        assertEquals("Unsupported encryption algorithm: DES", refusal("SELECT ENCRYPT('a', 'p', NULL, 'Des-xyz/pad:zero')"));
        assertEquals("Unsupported encryption mode: XYZ", refusal("SELECT ENCRYPT('a', 'p', NULL, 'AES-xyz/pad:zero')"));
        assertEquals("Unsupported encryption mode: GCM2", refusal("SELECT ENCRYPT('a', 'p', NULL, 'AES-GCM2')"));
        assertEquals("Unsupported encryption padding: ZERO", refusal("SELECT ENCRYPT('a', 'p', NULL, 'AES-CBC/pad:zero')"));
        assertEquals("Unsupported encryption padding: ZERO", refusal("SELECT ENCRYPT('a', 'p', NULL, 'AES-GCM/pad:ZERO')"));
        assertEquals("Malformed encryption method parameter: nonsense", refusal("SELECT DECRYPT(X'00', 'p', NULL, 'nonsense')"));
        assertEquals("Unsupported encryption algorithm: DES", refusal("SELECT DECRYPT(X'00', 'p', NULL, 'des-cbc')"));
        assertEquals("Malformed encryption method parameter: nonsense", refusal("SELECT DECRYPT(X'00', 'p', 'aad', 'nonsense')"));
        assertEquals(List.of("null", "null"),
            row("SELECT TRY_DECRYPT(X'00', 'p', NULL, 'nonsense'), TRY_DECRYPT(X'00', 'p', NULL, 'AES-GCM')"));
    }

    @Test
    public void onlyGcmTakesAnAad() {
        assertEquals("Encryption mode CBC does not support AAD", refusal("SELECT ENCRYPT('a', 'p', 'aad', 'AES-CBC')"));
        assertEquals("Encryption mode CBC does not support AAD", refusal("SELECT ENCRYPT('a', 'p', '', 'AES-CBC')"));
        assertEquals("Encryption mode ECB does not support AAD", refusal("SELECT ENCRYPT('a', 'p', 'aad', 'AES-ECB')"));
        assertEquals("Encryption mode CTR does not support AAD", refusal("SELECT ENCRYPT('a', 'p', 'aad', 'AES-CTR')"));
        assertEquals("Unsupported encryption padding: ZERO", refusal("SELECT ENCRYPT('a', 'p', 'aad', 'AES-CBC/pad:ZERO')"));
        assertEquals("Unsupported encryption algorithm: DES", refusal("SELECT ENCRYPT('a', 'p', 'aad', 'DES-GCM')"));
        assertEquals("Encryption mode CBC does not support AAD",
            refusal("SELECT DECRYPT(X'0000000000000000000000000000000000000000000000000000000000000000', 'p', 'aad', 'AES-CBC')"));
        assertEquals(NOT_THE_METHODS_INPUT, refusal("SELECT DECRYPT(X'00', 'p', 'aad', 'AES-CBC')"));
    }

    @Test
    public void eachModeHasItsOwnInputShape() {
        final String sixteen = "X'00000000000000000000000000000000'";
        final String seventeen = "X'0000000000000000000000000000000000'";
        assertEquals(NOT_THE_METHODS_INPUT, refusal("SELECT DECRYPT(X'000000000000000000000000000000000000000000000000000000', 'p')"));
        assertEquals(FAILED, refusal("SELECT DECRYPT(X'00000000000000000000000000000000000000000000000000000000', 'p')"));
        assertEquals(NOT_THE_METHODS_INPUT, refusal("SELECT DECRYPT(X'00', 'p', NULL, 'AES-CBC')"));
        assertEquals(FAILED, refusal("SELECT DECRYPT(" + sixteen + ", 'p', NULL, 'AES-CBC')"));
        assertEquals(FAILED, refusal("SELECT DECRYPT(" + seventeen + ", 'p', NULL, 'AES-CBC')"));
        assertEquals(FAILED, refusal("SELECT DECRYPT(X'', 'p', NULL, 'AES-ECB')"));
        assertEquals(FAILED, refusal("SELECT DECRYPT(" + sixteen + ", 'p', NULL, 'AES-ECB')"));
        assertEquals(NOT_THE_METHODS_INPUT, refusal("SELECT DECRYPT(X'00', 'p', NULL, 'AES-CTR')"));
        assertEquals(List.of("0", "1", "1", "0", "16", "0"), row("SELECT LENGTH(DECRYPT(" + sixteen + ", 'p', NULL, 'AES-CTR')), "
            + "LENGTH(DECRYPT(" + seventeen + ", 'p', NULL, 'AES-OFB')), LENGTH(DECRYPT(" + seventeen + ", 'p', NULL, 'AES-CFB')), "
            + "LENGTH(DECRYPT(" + sixteen + ", 'p', NULL, 'AES-CBC/pad:NONE')), "
            + "LENGTH(DECRYPT(" + sixteen + ", 'p', NULL, 'AES-ECB/pad:NONE')), LENGTH(DECRYPT(X'', 'p', NULL, 'AES-ECB/pad:NONE'))"));
        assertEquals("Data size (1 bytes) needs to be a multiple of block size (16 bytes) if padding is disabled",
            refusal("SELECT DECRYPT(" + seventeen + ", 'p', NULL, 'AES-CBC/pad:NONE')"));
        assertEquals("Data size (17 bytes) needs to be a multiple of block size (16 bytes) if padding is disabled",
            refusal("SELECT DECRYPT(" + seventeen + ", 'p', NULL, 'AES-ECB/pad:NONE')"));
        assertEquals("Data size (1 bytes) needs to be a multiple of block size (16 bytes) if padding is disabled",
            refusal("SELECT ENCRYPT('a', 'p', NULL, 'AES-CBC/pad:NONE')"));
        assertEquals(FAILED, refusal("SELECT DECRYPT(ENCRYPT('a', 'p', NULL, 'AES-GCM'), 'q', NULL, 'AES-GCM')"));
        assertEquals(FAILED, refusal("SELECT DECRYPT(ENCRYPT('a', 'p', NULL, 'AES-CBC'), 'p')"));
        assertEquals(List.of("true", "16"), row("""
            SELECT DECRYPT(ENCRYPT('a', 'p', NULL, 'AES-CTR'), 'q', NULL, 'AES-CTR') IS NOT NULL,
                LENGTH(DECRYPT(ENCRYPT('abc', 'p', NULL, 'AES-ECB/pad:PKCS'), 'p', NULL, 'AES-ECB/pad:NONE'))"""));
    }

    @Test
    public void rawFunctionsCheckMethodThenKeyThenNonceThenTag() {
        final String key = "X'00112233445566778899AABBCCDDEEFF'";
        final String tag = "X'000102030405060708090A0B0C0D0E0F'";
        assertEquals("Key size of 8 bits not found for encryption algorithm AES", refusal("SELECT DECRYPT_RAW(X'00', X'00', X'00')"));
        assertEquals("Key size of 16 bits not found for encryption algorithm AES",
            refusal("SELECT DECRYPT_RAW(X'00', X'0011', X'00')"));
        assertEquals("Key size of 8 bits not found for encryption algorithm AES",
            refusal("SELECT DECRYPT_RAW(X'00', X'00', X'00', NULL, 'AES-GCM', X'00')"));
        assertEquals("IV/Nonce of size 8 bits needs to be of size of 96 bits for encryption mode GCM",
            refusal("SELECT DECRYPT_RAW(X'00', " + key + ", X'00')"));
        assertEquals("IV/Nonce of size 8 bits needs to be of size of 96 bits for encryption mode GCM",
            refusal("SELECT DECRYPT_RAW(X'00', " + key + ", X'00', NULL, 'AES-GCM', " + tag + ")"));
        assertEquals("Malformed encryption method parameter: nonsense",
            refusal("SELECT DECRYPT_RAW(X'00', X'00', X'00', NULL, 'nonsense')"));
        assertEquals("Key size of 8 bits not found for encryption algorithm AES",
            refusal("SELECT DECRYPT_RAW(X'00', X'00', X'000102030405060708090A0B0C0D0E0F', NULL, 'AES-CBC')"));
        assertEquals("Decryption mode requires an AEAD tag as parameter",
            refusal("SELECT DECRYPT_RAW(X'00', " + key + ", X'000102030405060708090A0B', X'01', 'AES-GCM')"));
        assertEquals("Key size of 8 bits not found for encryption algorithm AES", refusal("SELECT ENCRYPT_RAW(X'00', X'00', X'00')"));
        assertEquals("Key size of 136 bits not found for encryption algorithm AES",
            refusal("SELECT ENCRYPT_RAW(X'00', X'00112233445566778899AABBCCDDEEFF00', X'000102030405060708090A0B')"));
        assertEquals("IV/Nonce of size 8 bits needs to be of size of 96 bits for encryption mode GCM",
            refusal("SELECT ENCRYPT_RAW(X'00', " + key + ", X'00')"));
        assertEquals("Unsupported encryption algorithm: DES", refusal("SELECT ENCRYPT_RAW(X'00', X'00', X'00', NULL, 'des-cbc')"));
        assertEquals(List.of("null"), row("SELECT TRY_DECRYPT_RAW(X'00', X'00', X'00')"));
    }
}
