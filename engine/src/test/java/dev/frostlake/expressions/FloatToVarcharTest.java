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

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FLOAT-to-VARCHAR conversion renders with 10 significant digits (C {@code %.10g}, trailing zeros
 * stripped), matching Snowflake — not Java's shortest-round-trip form. The production shape is a
 * loader hashing {@code src:value:original_score::VARCHAR} into a SHA2 record id: Snowflake
 * feeds {@code 0.8999999762} to the hash where {@code Double.toString} yielded
 * {@code 0.8999999761581421}, so the ids diverged.
 */
public class FloatToVarcharTest extends BaseDatabaseTest {

    private String str(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void variantDoubleRendersWithTenSignificantDigits() {
        // float(0.9) widened to double — the classic score value a loader hashes.
        assertEquals("0.8999999762",
            str("SELECT PARSE_JSON('{\"s\":8.999999761581421e-01}'):s::VARCHAR"));
    }

    @Test
    public void shortDoublesKeepTheirShortForm() {
        assertEquals("0.8", str("SELECT PARSE_JSON('{\"s\":0.8}'):s::VARCHAR"));
        assertEquals("0.25", str("SELECT PARSE_JSON('{\"s\":0.25}'):s::VARCHAR"));
    }

    @Test
    public void integralDoubleRendersWithoutFraction() {
        assertEquals("3", str("SELECT PARSE_JSON('{\"s\":3.0e0}'):s::VARCHAR"));
    }

    @Test
    public void variantIntegerIsUntouched() {
        assertEquals("1", str("SELECT PARSE_JSON('{\"s\":1}'):s::VARCHAR"));
    }

    @Test
    public void hashOverStringifiedFloatMatchesSnowflake() {
        // SHA2 over the ::VARCHAR rendering — the exact record-id recipe.
        assertEquals(sha256("x:0.8999999762"),
            str("SELECT SHA2('x:' || PARSE_JSON('{\"s\":8.999999761581421e-01}'):s::VARCHAR, 256)"));
    }

    private static String sha256(final String input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
