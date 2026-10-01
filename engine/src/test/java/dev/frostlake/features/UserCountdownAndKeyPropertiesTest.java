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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The user properties that count down — DAYS_TO_EXPIRY, MINS_TO_UNLOCK, MINS_TO_BYPASS_MFA — and the two public
 * keys, RSA_PUBLIC_KEY and RSA_PUBLIC_KEY_2, as CREATE USER, ALTER USER SET / UNSET, SHOW USERS and DESCRIBE USER
 * handle them. Every key here is generated for the run and thrown away.
 *
 * <p>A countdown reads as the time left, so the assertions allow for the moment between the statement that sets
 * it and the one that reads it.
 */
public class UserCountdownAndKeyPropertiesTest extends BaseDatabaseTest {

    private static final String KEY_TIME = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{1,3}";

    private String refusalOf(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return error.getMessage();
    }

    /** Every countdown cell of the user's SHOW USERS row is NULL. */
    private void assertCleared(final String user) {
        final ResultSet rs = engine.executeQuery("SHOW USERS LIKE '" + user + "'");
        assertEquals(1, rs.getRowCount(), "SHOW USERS LIKE '" + user + "'");
        final Row row = rs.getRows().get(0);
        for (final String column : new String[] {"days_to_expiry", "mins_to_unlock", "mins_to_bypass_mfa",
                "expires_at_time", "locked_until_time"}) {
            assertNull(cell(rs, row, column), column);
        }
    }

    private String shownCell(final String user, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW USERS LIKE '" + user + "'");
        assertEquals(1, rs.getRowCount(), "SHOW USERS LIKE '" + user + "'");
        return cell(rs, rs.getRows().get(0), column);
    }

    private Map<String, String> described(final String user) {
        final ResultSet rs = engine.executeQuery("DESCRIBE USER " + user);
        final Map<String, String> properties = new HashMap<>();
        for (final Row row : rs.getRows()) {
            properties.put(cell(rs, row, "property"), cell(rs, row, "value"));
        }
        return properties;
    }

    private static PublicKey key(final String algorithm, final int bits) throws GeneralSecurityException {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        if ("EC".equals(algorithm)) {
            generator.initialize(new ECGenParameterSpec("secp256r1"));
        } else if (bits > 0) {
            generator.initialize(bits);
        }
        return generator.generateKeyPair().getPublic();
    }

    private static String body(final PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static String fingerprint(final PublicKey key) throws GeneralSecurityException {
        return "SHA256:" + Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(key.getEncoded()));
    }

    private static void assertBetween(final double low, final double high, final String text) {
        assertNotNull(text);
        final double value = Double.parseDouble(text);
        assertTrue(value >= low && value <= high, text + " is not within [" + low + ", " + high + "]");
    }

    // ── the countdowns ────────────────────────────────────────────────────────────────────────────

    @Test
    public void theCountdownsReadAsTheTimeLeft() {
        engine.execute("CREATE USER cd_user DAYS_TO_EXPIRY = 30 MINS_TO_UNLOCK = 10 MINS_TO_BYPASS_MFA = 5");
        final ResultSet rs = engine.executeQuery("SHOW USERS LIKE 'CD_USER'");
        final Row row = rs.getRows().get(0);
        // Whole seconds left as a fraction of a day, and whole minutes left.
        assertBetween(29.99, 30.0, cell(rs, row, "days_to_expiry"));
        assertBetween(9, 10, cell(rs, row, "mins_to_unlock"));
        assertBetween(4, 5, cell(rs, row, "mins_to_bypass_mfa"));
        assertNotNull(cell(rs, row, "expires_at_time"));
        assertNotNull(cell(rs, row, "locked_until_time"));
        assertEquals("false", cell(rs, row, "disabled"));
        assertEquals("false", cell(rs, row, "snowflake_lock"));
        final Map<String, String> described = described("cd_user");
        assertBetween(29.99, 30.0, described.get("DAYS_TO_EXPIRY"));
        assertBetween(9, 10, described.get("MINS_TO_UNLOCK"));
        assertBetween(4, 5, described.get("MINS_TO_BYPASS_MFA"));
        assertTrue(described.get("LOCK_DETAILS").startsWith("{\"isAdminLocked\":true,"), described.get("LOCK_DETAILS"));
    }

    @Test
    public void unsetZeroAndNullClearACountdown() {
        engine.execute("CREATE USER cd_clear DAYS_TO_EXPIRY = 5 MINS_TO_UNLOCK = 5 MINS_TO_BYPASS_MFA = 5");
        engine.execute("ALTER USER cd_clear UNSET DAYS_TO_EXPIRY, MINS_TO_UNLOCK, MINS_TO_BYPASS_MFA");
        assertCleared("CD_CLEAR");
        engine.execute("ALTER USER cd_clear SET DAYS_TO_EXPIRY = 5 MINS_TO_UNLOCK = 5 MINS_TO_BYPASS_MFA = 5");
        engine.execute("ALTER USER cd_clear SET DAYS_TO_EXPIRY = 0 MINS_TO_UNLOCK = 0 MINS_TO_BYPASS_MFA = 0");
        assertCleared("CD_CLEAR");
        engine.execute("ALTER USER cd_clear SET DAYS_TO_EXPIRY = 5 MINS_TO_UNLOCK = 5 MINS_TO_BYPASS_MFA = 5");
        engine.execute("ALTER USER cd_clear SET DAYS_TO_EXPIRY = NULL MINS_TO_UNLOCK = NULL MINS_TO_BYPASS_MFA = NULL");
        final Map<String, String> described = described("cd_clear");
        assertEquals("null", described.get("DAYS_TO_EXPIRY"));
        assertEquals("null", described.get("MINS_TO_UNLOCK"));
        assertEquals("null", described.get("MINS_TO_BYPASS_MFA"));
        assertTrue(described.get("LOCK_DETAILS").startsWith("{\"isAdminLocked\":false,"), described.get("LOCK_DETAILS"));
    }

    /** A negative expiry makes an expired user; a negative lock or bypass clears it instead. */
    @Test
    public void aNegativeExpiryIsKeptAndANegativeLockClears() {
        engine.execute("CREATE USER cd_negative");
        engine.execute("ALTER USER cd_negative SET DAYS_TO_EXPIRY = -1 MINS_TO_UNLOCK = -5 MINS_TO_BYPASS_MFA = -2");
        final ResultSet rs = engine.executeQuery("SHOW USERS LIKE 'CD_NEGATIVE'");
        final Row row = rs.getRows().get(0);
        assertBetween(-1.001, -1.0, cell(rs, row, "days_to_expiry"));
        assertNotNull(cell(rs, row, "expires_at_time"));
        assertNull(cell(rs, row, "mins_to_unlock"));
        assertNull(cell(rs, row, "mins_to_bypass_mfa"));
        assertNull(cell(rs, row, "locked_until_time"));
        // Written with a blank after the sign, and with a leading zero.
        engine.execute("ALTER USER cd_negative SET DAYS_TO_EXPIRY = - 5");
        assertBetween(-5.001, -5.0, shownCell("CD_NEGATIVE", "days_to_expiry"));
        engine.execute("ALTER USER cd_negative SET DAYS_TO_EXPIRY = 07");
        assertBetween(6.99, 7.0, shownCell("CD_NEGATIVE", "days_to_expiry"));
    }

    @Test
    public void aCountdownTakesOnlyAWholeNumber() {
        engine.execute("CREATE USER cd_values");
        assertEquals("SQL compilation error:\ninvalid value [1.5] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER cd_values SET DAYS_TO_EXPIRY = 1.5"));
        assertEquals("SQL compilation error:\ninvalid value ['5'] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER cd_values SET DAYS_TO_EXPIRY = '5'"));
        assertEquals("SQL compilation error:\ninvalid value [abc] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER cd_values SET DAYS_TO_EXPIRY = abc"));
        assertEquals("SQL compilation error:\ninvalid value [TRUE] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER cd_values SET DAYS_TO_EXPIRY = TRUE"));
        assertEquals("SQL compilation error:\ninvalid value [1e2] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER cd_values SET DAYS_TO_EXPIRY = 1e2"));
        assertEquals("SQL compilation error:\ninvalid value [-1.5] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER cd_values SET DAYS_TO_EXPIRY = -1.5"));
        assertEquals("SQL compilation error:\ninvalid value [2.5] for parameter 'MINS_TO_UNLOCK'",
            refusalOf("ALTER USER cd_values SET MINS_TO_UNLOCK = 2.5"));
        assertEquals("SQL compilation error:\ninvalid value ['x'] for parameter 'MINS_TO_BYPASS_MFA'",
            refusalOf("ALTER USER cd_values SET MINS_TO_BYPASS_MFA = 'x'"));
        // Past 32 bits the value no longer reads as a number at all.
        assertEquals("SQL compilation error:\ninvalid value [2147483648] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER cd_values SET DAYS_TO_EXPIRY = 2147483648"));
        engine.execute("ALTER USER cd_values SET DAYS_TO_EXPIRY = -2147483648");
    }

    @Test
    public void eachCountdownHasItsCeiling() {
        engine.execute("CREATE USER cd_ranges");
        engine.execute("ALTER USER cd_ranges SET DAYS_TO_EXPIRY = 365000");
        assertEquals("SQL compilation error:\ninvalid value '365,001' for property 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER cd_ranges SET DAYS_TO_EXPIRY = 365001"));
        engine.execute("ALTER USER cd_ranges SET MINS_TO_UNLOCK = 525600000");
        assertEquals("SQL compilation error:\ninvalid value '525,600,001' for property 'MINS_TO_UNLOCK'",
            refusalOf("ALTER USER cd_ranges SET MINS_TO_UNLOCK = 525600001"));
        engine.execute("ALTER USER cd_ranges SET MINS_TO_BYPASS_MFA = 1440");
        assertEquals("SQL compilation error:\ninvalid value '1,441' for property 'MINS_TO_BYPASS_MFA'",
            refusalOf("ALTER USER cd_ranges SET MINS_TO_BYPASS_MFA = 1441"));
        assertEquals("SQL compilation error:\ninvalid value '2,147,483,647' for property 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER cd_ranges SET DAYS_TO_EXPIRY = 2147483647"));
        // A value that does not read is refused before one out of range, wherever each stands.
        assertEquals("SQL compilation error:\ninvalid value [1.5] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER cd_ranges SET MINS_TO_UNLOCK = 999999999 DAYS_TO_EXPIRY = 1.5"));
    }

    /** A property named twice is refused, whichever property it is, before its value is read. */
    @Test
    public void aPropertyNamedTwiceIsRefused() {
        assertEquals("SQL compilation error:\nduplicate property 'DAYS_TO_EXPIRY';",
            refusalOf("CREATE USER cd_twice DAYS_TO_EXPIRY = 5 DAYS_TO_EXPIRY = 6"));
        assertEquals("SQL compilation error:\nduplicate property 'EMAIL';",
            refusalOf("CREATE USER cd_twice EMAIL = 'a@b.c' EMAIL = 'd@e.f'"));
        engine.execute("CREATE USER cd_twice");
        assertEquals("SQL compilation error:\nduplicate property 'DAYS_TO_EXPIRY';",
            refusalOf("ALTER USER cd_twice SET DAYS_TO_EXPIRY = 1.5 DAYS_TO_EXPIRY = 2"));
        assertEquals("SQL compilation error:\nduplicate property 'MINS_TO_UNLOCK';",
            refusalOf("ALTER USER cd_twice SET MINS_TO_UNLOCK = 5 MINS_TO_UNLOCK = 6"));
        assertEquals("SQL compilation error:\nduplicate property 'DAYS_TO_EXPIRY';",
            refusalOf("ALTER USER cd_twice UNSET DAYS_TO_EXPIRY, DAYS_TO_EXPIRY"));
    }

    @Test
    public void aServiceUserMayNotBypassMfa() {
        assertEquals("SQL execution error: Cannot set MINS_TO_BYPASS_MFA on users with TYPE=SERVICE.",
            refusalOf("CREATE USER cd_svc TYPE = SERVICE MINS_TO_BYPASS_MFA = 5"));
        assertEquals("SQL execution error: Cannot set MINS_TO_BYPASS_MFA on users with TYPE=LEGACY_SERVICE.",
            refusalOf("CREATE USER cd_svc TYPE = LEGACY_SERVICE MINS_TO_BYPASS_MFA = 5 DAYS_TO_EXPIRY = 5"));
        // Zero is refused as well; the type is checked before the range, the value's form before both.
        assertEquals("SQL execution error: Cannot set MINS_TO_BYPASS_MFA on users with TYPE=SERVICE.",
            refusalOf("CREATE USER cd_svc TYPE = SERVICE MINS_TO_BYPASS_MFA = 0"));
        assertEquals("SQL execution error: Cannot set MINS_TO_BYPASS_MFA on users with TYPE=SERVICE.",
            refusalOf("CREATE USER cd_svc TYPE = SERVICE MINS_TO_BYPASS_MFA = 5000"));
        assertEquals("SQL compilation error:\ninvalid value [1.5] for parameter 'MINS_TO_BYPASS_MFA'",
            refusalOf("CREATE USER cd_svc TYPE = SERVICE MINS_TO_BYPASS_MFA = 1.5"));
        engine.execute("CREATE USER cd_svc TYPE = SERVICE MINS_TO_BYPASS_MFA = NULL DAYS_TO_EXPIRY = 5 "
            + "MINS_TO_UNLOCK = 5");
        engine.execute("ALTER USER cd_svc UNSET MINS_TO_BYPASS_MFA");
        assertEquals("SQL execution error: Cannot set MINS_TO_BYPASS_MFA on users with TYPE=SERVICE.",
            refusalOf("ALTER USER cd_svc SET MINS_TO_BYPASS_MFA = -3"));
        assertEquals("SERVICE", shownCell("CD_SVC", "type"));
        assertBetween(4.99, 5.0, shownCell("CD_SVC", "days_to_expiry"));
    }

    // ── the public keys ───────────────────────────────────────────────────────────────────────────

    @Test
    public void aKeyIsKeptAsWrittenWithItsFingerprint() throws GeneralSecurityException {
        final PublicKey first = key("RSA", 2048);
        final PublicKey second = key("RSA", 2048);
        engine.execute("CREATE USER key_user RSA_PUBLIC_KEY = '" + body(first) + "'");
        Map<String, String> described = described("key_user");
        assertEquals(body(first), described.get("RSA_PUBLIC_KEY"));
        assertEquals(fingerprint(first), described.get("RSA_PUBLIC_KEY_FP"));
        assertTrue(described.get("RSA_PUBLIC_KEY_LAST_SET_TIME").matches(KEY_TIME),
            described.get("RSA_PUBLIC_KEY_LAST_SET_TIME"));
        assertEquals("true", described.get("HAS_KEYPAIR"));
        assertEquals("null", described.get("RSA_PUBLIC_KEY_2"));
        assertEquals("null", described.get("RSA_PUBLIC_KEY_2_FP"));
        assertEquals("null", described.get("RSA_PUBLIC_KEY_2_LAST_SET_TIME"));
        assertEquals("true", shownCell("KEY_USER", "has_rsa_public_key"));

        // PEM armour and line breaks are kept as written, and the fingerprint is the key's.
        final String armoured = "-----BEGIN PUBLIC KEY-----\n" + body(second).substring(0, 64) + "\n"
            + body(second).substring(64) + "\n-----END PUBLIC KEY-----";
        engine.execute("ALTER USER key_user SET RSA_PUBLIC_KEY_2 = '" + armoured + "'");
        described = described("key_user");
        assertEquals(armoured, described.get("RSA_PUBLIC_KEY_2"));
        assertEquals(fingerprint(second), described.get("RSA_PUBLIC_KEY_2_FP"));
        // Both slots may hold the same key.
        engine.execute("ALTER USER key_user SET RSA_PUBLIC_KEY_2 = '" + body(first) + "'");
        assertEquals(fingerprint(first), described("key_user").get("RSA_PUBLIC_KEY_2_FP"));
        // An EC key is taken too.
        final PublicKey ec = key("EC", 0);
        engine.execute("ALTER USER key_user SET RSA_PUBLIC_KEY = '" + body(ec) + "'");
        assertEquals(fingerprint(ec), described("key_user").get("RSA_PUBLIC_KEY_FP"));
    }

    /** Clearing a slot empties it and keeps the time a key was last put there. */
    @Test
    public void clearingAKeyKeepsItsSetTime() throws GeneralSecurityException {
        final PublicKey first = key("RSA", 2048);
        engine.execute("CREATE USER key_clear RSA_PUBLIC_KEY = '" + body(first) + "' RSA_PUBLIC_KEY_2 = '"
            + body(first) + "'");
        final String setTime = described("key_clear").get("RSA_PUBLIC_KEY_LAST_SET_TIME");
        engine.execute("ALTER USER key_clear UNSET RSA_PUBLIC_KEY");
        Map<String, String> described = described("key_clear");
        assertEquals("null", described.get("RSA_PUBLIC_KEY"));
        assertEquals("null", described.get("RSA_PUBLIC_KEY_FP"));
        assertEquals(setTime, described.get("RSA_PUBLIC_KEY_LAST_SET_TIME"));
        assertEquals("true", described.get("HAS_KEYPAIR"), "the second key is still there");
        assertEquals("true", shownCell("KEY_CLEAR", "has_rsa_public_key"));
        // The empty text empties the slot too, and is what DESCRIBE then shows.
        engine.execute("ALTER USER key_clear SET RSA_PUBLIC_KEY_2 = ''");
        described = described("key_clear");
        assertEquals("", described.get("RSA_PUBLIC_KEY_2"));
        assertEquals("null", described.get("RSA_PUBLIC_KEY_2_FP"));
        assertEquals("false", described.get("HAS_KEYPAIR"));
        assertEquals("false", shownCell("KEY_CLEAR", "has_rsa_public_key"));
        engine.execute("ALTER USER key_clear SET RSA_PUBLIC_KEY = '" + body(first) + "'");
        engine.execute("ALTER USER key_clear SET RSA_PUBLIC_KEY = NULL");
        assertEquals("null", described("key_clear").get("RSA_PUBLIC_KEY"));
        // A slot never given a key has no set time.
        engine.execute("CREATE USER key_empty RSA_PUBLIC_KEY = ''");
        described = described("key_empty");
        assertEquals("", described.get("RSA_PUBLIC_KEY"));
        assertEquals("null", described.get("RSA_PUBLIC_KEY_LAST_SET_TIME"));
        assertEquals("false", described.get("HAS_KEYPAIR"));
    }

    @Test
    public void theKeyPolicyRefusesWhatIsNoUsableKey() throws GeneralSecurityException {
        engine.execute("CREATE USER key_refused");
        assertEquals("SQL execution error:\nNew public key rejected by current policy. Reason: 'Invalid Public key'",
            refusalOf("ALTER USER key_refused SET RSA_PUBLIC_KEY = 'garbage'"));
        assertEquals("SQL execution error:\nNew public key rejected by current policy. Reason: 'Invalid Public key'",
            refusalOf("ALTER USER key_refused SET RSA_PUBLIC_KEY_2 = abc"));
        assertEquals("SQL execution error:\nNew public key rejected by current policy. Reason: "
                + "'Key length 1024 is smaller than minimal requirement of 2048.'",
            refusalOf("ALTER USER key_refused SET RSA_PUBLIC_KEY = '" + body(key("RSA", 1024)) + "'"));
        assertEquals("SQL execution error: The provided public key uses an unsupported key algorithm.",
            refusalOf("ALTER USER key_refused SET RSA_PUBLIC_KEY = '" + body(key("Ed25519", 0)) + "'"));
        assertEquals("SQL compilation error:\ninvalid value [5] for parameter 'RSA_PUBLIC_KEY'",
            refusalOf("ALTER USER key_refused SET RSA_PUBLIC_KEY = 5"));
        assertEquals("SQL compilation error:\ninvalid property 'RSA_PUBLIC_KEY_FP' for 'USER'",
            refusalOf("ALTER USER key_refused UNSET RSA_PUBLIC_KEY_FP"));
        // A refused statement changes nothing, the properties before the key included.
        assertTrue(refusalOf("ALTER USER key_refused SET DAYS_TO_EXPIRY = 1 RSA_PUBLIC_KEY = 'x'")
            .contains("Invalid Public key"));
        assertNull(shownCell("KEY_REFUSED", "days_to_expiry"));
    }

    /** A CREATE whose properties are refused leaves no user behind, and a refused CREATE OR REPLACE keeps the old. */
    @Test
    public void aRefusedCreateChangesNothing() {
        assertTrue(refusalOf("CREATE USER key_none RSA_PUBLIC_KEY = 'garbage'").contains("Invalid Public key"));
        assertEquals(0, engine.executeQuery("SHOW USERS LIKE 'KEY_NONE'").getRowCount());
        assertEquals("SQL compilation error:\ninvalid value 'ROBOT' for property 'TYPE'",
            refusalOf("CREATE USER key_none TYPE = ROBOT"));
        assertEquals(0, engine.executeQuery("SHOW USERS LIKE 'KEY_NONE'").getRowCount());
        engine.execute("CREATE USER key_kept COMMENT = 'keep'");
        refusalOf("CREATE OR REPLACE USER key_kept DAYS_TO_EXPIRY = 1.5");
        assertEquals("keep", shownCell("KEY_KEPT", "comment"));
    }

    /** A key may be written dollar-quoted, while a boolean is no key at all and a countdown takes no string. */
    @Test
    public void aKeyMayBeDollarQuotedButNotABoolean() throws GeneralSecurityException {
        final PublicKey first = key("RSA", 2048);
        engine.execute("CREATE USER key_dollar");
        engine.execute("ALTER USER key_dollar SET RSA_PUBLIC_KEY_2 = $$" + body(first) + "$$");
        Map<String, String> described = described("key_dollar");
        assertEquals(body(first), described.get("RSA_PUBLIC_KEY_2"));
        assertEquals(fingerprint(first), described.get("RSA_PUBLIC_KEY_2_FP"));
        engine.execute("ALTER USER key_dollar SET RSA_PUBLIC_KEY_2 = $$$$");
        described = described("key_dollar");
        assertEquals("", described.get("RSA_PUBLIC_KEY_2"));
        assertEquals("false", described.get("HAS_KEYPAIR"));
        assertEquals("SQL execution error:\nNew public key rejected by current policy. Reason: 'Invalid Public key'",
            refusalOf("ALTER USER key_dollar SET RSA_PUBLIC_KEY = $$garbage$$"));
        assertEquals("SQL compilation error:\ninvalid value [TRUE] for parameter 'RSA_PUBLIC_KEY_2'",
            refusalOf("ALTER USER key_dollar SET RSA_PUBLIC_KEY_2 = TRUE"));
        assertEquals("SQL compilation error:\ninvalid value [FALSE] for parameter 'RSA_PUBLIC_KEY'",
            refusalOf("ALTER USER key_dollar SET RSA_PUBLIC_KEY = FALSE"));
        assertEquals("SQL compilation error:\ninvalid value [$$5$$] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER key_dollar SET DAYS_TO_EXPIRY = $$5$$"));
        assertEquals("SQL compilation error:\ninvalid value [$$abc$$] for parameter 'MINS_TO_UNLOCK'",
            refusalOf("ALTER USER key_dollar SET MINS_TO_UNLOCK = $$abc$$"));
    }

    // ── what IF [NOT] EXISTS leaves to the statement ──────────────────────────────────────────────────

    /**
     * CREATE USER IF NOT EXISTS that finds its user checks only the property list's form — a property named twice, a
     * value a property cannot take — and changes nothing; without IF NOT EXISTS the user's existence comes first.
     */
    @Test
    public void ifNotExistsFindsTheUserBeforeItsProperties() {
        engine.execute("CREATE USER cd_exists COMMENT = 'orig'");
        engine.execute("CREATE USER IF NOT EXISTS cd_exists TYPE = SERVICE FIRST_NAME = 'a'");
        engine.execute("CREATE USER IF NOT EXISTS cd_exists TYPE = ROBOT");
        engine.execute("CREATE USER IF NOT EXISTS cd_exists RSA_PUBLIC_KEY = 'garbage'");
        engine.execute("CREATE USER IF NOT EXISTS cd_exists TYPE = SERVICE MINS_TO_BYPASS_MFA = 5");
        engine.execute("CREATE USER IF NOT EXISTS cd_exists MINS_TO_UNLOCK = 999999999 DAYS_TO_EXPIRY = 5");
        assertEquals("SQL compilation error:\ninvalid value [1.5] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("CREATE USER IF NOT EXISTS cd_exists DAYS_TO_EXPIRY = 1.5"));
        assertEquals("SQL compilation error:\nduplicate property 'EMAIL';",
            refusalOf("CREATE USER IF NOT EXISTS cd_exists EMAIL = 'a@example.com' EMAIL = 'b@example.com'"));
        assertEquals("SQL compilation error:\ninvalid value [TRUE] for parameter 'RSA_PUBLIC_KEY'",
            refusalOf("CREATE USER IF NOT EXISTS cd_exists RSA_PUBLIC_KEY = TRUE"));
        assertEquals("orig", shownCell("CD_EXISTS", "comment"));
        assertEquals("PERSON", shownCell("CD_EXISTS", "type"));
        assertNull(shownCell("CD_EXISTS", "days_to_expiry"));
        final String exists = "SQL compilation error:\nObject 'CD_EXISTS' already exists.";
        assertEquals(exists, refusalOf("CREATE USER cd_exists RSA_PUBLIC_KEY = 'garbage'"));
        assertEquals(exists, refusalOf("CREATE USER cd_exists TYPE = SERVICE FIRST_NAME = 'a'"));
        assertEquals(exists, refusalOf("CREATE USER cd_exists"));
        assertEquals("SQL compilation error:\ninvalid value [1.5] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("CREATE USER cd_exists DAYS_TO_EXPIRY = 1.5"));
    }

    /**
     * ALTER USER IF EXISTS answers a missing user and nothing else: on a user that exists every refusal stands, and
     * on a missing one the property list's form is still checked.
     */
    @Test
    public void alterIfExistsSkipsOnlyAMissingUser() {
        engine.execute("CREATE USER cd_alter");
        engine.execute("CREATE USER cd_alter_other");
        assertEquals("SQL compilation error:\ninvalid value [1.5] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER IF EXISTS cd_alter SET DAYS_TO_EXPIRY = 1.5"));
        assertEquals("SQL execution error:\nNew public key rejected by current policy. Reason: 'Invalid Public key'",
            refusalOf("ALTER USER IF EXISTS cd_alter SET RSA_PUBLIC_KEY = 'garbage'"));
        assertEquals("SQL compilation error:\ninvalid value '1,441' for property 'MINS_TO_BYPASS_MFA'",
            refusalOf("ALTER USER IF EXISTS cd_alter SET MINS_TO_BYPASS_MFA = 1441"));
        assertEquals("SQL execution error: Cannot set MINS_TO_BYPASS_MFA on users with TYPE=SERVICE.",
            refusalOf("ALTER USER IF EXISTS cd_alter SET TYPE = SERVICE MINS_TO_BYPASS_MFA = 5"));
        assertEquals("SQL compilation error:\nduplicate property 'EMAIL';",
            refusalOf("ALTER USER IF EXISTS cd_alter SET EMAIL = 'a@example.com' EMAIL = 'b@example.com'"));
        assertEquals("SQL compilation error:\ninvalid value 'ROBOT' for property 'TYPE'",
            refusalOf("ALTER USER IF EXISTS cd_alter SET TYPE = ROBOT"));
        assertEquals("SQL execution error: Cannot set FIRST_NAME on users with TYPE=SERVICE.",
            refusalOf("ALTER USER IF EXISTS cd_alter SET FIRST_NAME = 'x' TYPE = SERVICE"));
        assertEquals("SQL compilation error:\ninvalid property 'RSA_PUBLIC_KEY_FP' for 'USER'",
            refusalOf("ALTER USER IF EXISTS cd_alter UNSET RSA_PUBLIC_KEY_FP"));
        assertEquals("SQL compilation error:\nObject 'CD_ALTER_OTHER' already exists.",
            refusalOf("ALTER USER IF EXISTS cd_alter RENAME TO cd_alter_other"));
        engine.execute("ALTER USER IF EXISTS cd_alter SET MINS_TO_UNLOCK = 3");
        assertBetween(2, 3, shownCell("CD_ALTER", "mins_to_unlock"));
        assertEquals("PERSON", shownCell("CD_ALTER", "type"));

        // A missing user: the form of the list is checked, and nothing else.
        assertEquals("SQL compilation error:\ninvalid value [1.5] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER IF EXISTS cd_missing_user SET DAYS_TO_EXPIRY = 1.5"));
        assertEquals("SQL compilation error:\nduplicate property 'DAYS_TO_EXPIRY';",
            refusalOf("ALTER USER IF EXISTS cd_missing_user SET DAYS_TO_EXPIRY = 5 DAYS_TO_EXPIRY = 6"));
        assertEquals("SQL compilation error:\ninvalid value [5] for parameter 'RSA_PUBLIC_KEY'",
            refusalOf("ALTER USER IF EXISTS cd_missing_user SET RSA_PUBLIC_KEY = 5"));
        assertEquals("SQL compilation error:\ninvalid property 'RSA_PUBLIC_KEY_FP' for 'USER'",
            refusalOf("ALTER USER IF EXISTS cd_missing_user UNSET RSA_PUBLIC_KEY_FP"));
        assertEquals("SQL compilation error:\nduplicate property 'DAYS_TO_EXPIRY';",
            refusalOf("ALTER USER IF EXISTS cd_missing_user UNSET DAYS_TO_EXPIRY, DAYS_TO_EXPIRY"));
        engine.execute("ALTER USER IF EXISTS cd_missing_user SET MINS_TO_UNLOCK = 999999999");
        engine.execute("ALTER USER IF EXISTS cd_missing_user SET RSA_PUBLIC_KEY = 'garbage'");
        engine.execute("ALTER USER IF EXISTS cd_missing_user SET TYPE = SERVICE MINS_TO_BYPASS_MFA = 5");
        engine.execute("ALTER USER IF EXISTS cd_missing_user SET TYPE = ROBOT");
        engine.execute("ALTER USER IF EXISTS cd_missing_user RENAME TO cd_alter_other");
        assertEquals(0, engine.executeQuery("SHOW USERS LIKE 'CD_MISSING_USER'").getRowCount());
        // Without IF EXISTS the form still comes first, and a list that reads well meets the missing user.
        assertEquals("SQL compilation error:\ninvalid value [1.5] for parameter 'DAYS_TO_EXPIRY'",
            refusalOf("ALTER USER cd_missing_user SET DAYS_TO_EXPIRY = 1.5"));
        assertTrue(refusalOf("ALTER USER cd_missing_user SET MINS_TO_UNLOCK = 999999999")
            .contains("User 'CD_MISSING_USER' does not exist or not authorized."));
    }

    // ── what a service user reads ─────────────────────────────────────────────────────────────────────

    /**
     * A SERVICE or LEGACY_SERVICE user reads no first, middle or last name and no MFA bypass; a SERVICE user reads no
     * password either. The details come back, the bypass still counting, once the type is PERSON again.
     */
    @Test
    public void aServiceTypeHidesThePersonsDetails() {
        engine.execute("CREATE USER cd_hidden FIRST_NAME = 'f' MIDDLE_NAME = 'm' LAST_NAME = 'l' "
            + "EMAIL = 'p@example.com' MINS_TO_BYPASS_MFA = 5 MUST_CHANGE_PASSWORD = TRUE PASSWORD = 'Qx7-pLm2-vR9z-T4wK'");
        engine.execute("ALTER USER cd_hidden SET TYPE = SERVICE");
        assertNull(shownCell("CD_HIDDEN", "first_name"));
        assertNull(shownCell("CD_HIDDEN", "last_name"));
        assertNull(shownCell("CD_HIDDEN", "mins_to_bypass_mfa"));
        assertEquals("false", shownCell("CD_HIDDEN", "has_password"));
        assertEquals("false", shownCell("CD_HIDDEN", "must_change_password"));
        assertEquals("p@example.com", shownCell("CD_HIDDEN", "email"));
        Map<String, String> described = described("cd_hidden");
        assertEquals("null", described.get("FIRST_NAME"));
        assertEquals("null", described.get("MIDDLE_NAME"));
        assertEquals("null", described.get("LAST_NAME"));
        assertEquals("null", described.get("MINS_TO_BYPASS_MFA"));
        assertEquals("false", described.get("MUST_CHANGE_PASSWORD"));

        engine.execute("ALTER USER cd_hidden SET TYPE = LEGACY_SERVICE");
        assertNull(shownCell("CD_HIDDEN", "first_name"));
        assertNull(shownCell("CD_HIDDEN", "mins_to_bypass_mfa"));
        assertEquals("true", shownCell("CD_HIDDEN", "has_password"));
        assertEquals("true", shownCell("CD_HIDDEN", "must_change_password"));
        described = described("cd_hidden");
        assertEquals("null", described.get("MIDDLE_NAME"));
        assertEquals("true", described.get("MUST_CHANGE_PASSWORD"));

        engine.execute("ALTER USER cd_hidden SET TYPE = PERSON");
        assertEquals("f", shownCell("CD_HIDDEN", "first_name"));
        assertEquals("l", shownCell("CD_HIDDEN", "last_name"));
        assertBetween(3, 5, shownCell("CD_HIDDEN", "mins_to_bypass_mfa"));
        assertEquals("m", described("cd_hidden").get("MIDDLE_NAME"));

        // Hidden details may still be cleared while the type hides them.
        engine.execute("ALTER USER cd_hidden SET TYPE = SERVICE");
        engine.execute("ALTER USER cd_hidden UNSET MINS_TO_BYPASS_MFA");
        engine.execute("ALTER USER cd_hidden UNSET FIRST_NAME");
        engine.execute("ALTER USER cd_hidden SET TYPE = PERSON");
        assertNull(shownCell("CD_HIDDEN", "first_name"));
        assertNull(shownCell("CD_HIDDEN", "mins_to_bypass_mfa"));
        assertEquals("l", shownCell("CD_HIDDEN", "last_name"));
    }

    // ── syntax ────────────────────────────────────────────────────────────────────────────────────────

    /** A fault anywhere in an ALTER USER's SET or UNSET list is refused at the first word of the list. */
    @Test
    public void aFaultInAPropertyListIsRefusedAtItsFirstWord() {
        engine.execute("CREATE USER cd_syntax");
        for (final String sql : new String[] {
                "ALTER USER cd_syntax SET DAYS_TO_EXPIRY",
                "ALTER USER cd_syntax SET DAYS_TO_EXPIRY =",
                "ALTER USER cd_syntax SET MINS_TO_UNLOCK 5",
                "ALTER USER cd_syntax SET RSA_PUBLIC_KEY = 'x' 'y'",
                "ALTER USER cd_syntax SET EMAIL = 'x' DAYS_TO_EXPIRY",
                "ALTER USER cd_syntax UNSET DAYS_TO_EXPIRY MINS_TO_UNLOCK"}) {
            final int at = sql.indexOf("SET ") + 4;
            final String word = sql.substring(at).split(" ")[0];
            assertEquals("SQL compilation error:\nsyntax error line 1 at position " + at + " unexpected '" + word
                + "'.", refusalOf(sql), sql);
        }
    }
}
