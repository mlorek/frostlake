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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A numeric CAST that cannot convert its source names the value and the target the way Snowflake does,
 * and the two SOURCE SHAPES get DIFFERENT messages. Live-verified on a real account:
 * {@code PARSE_JSON('{}')::NUMBER} fails {@code Failed to cast variant value {} to FIXED} (SQLSTATE
 * 22000, error 100071) — {@code REAL} for the approximate targets — while {@code '{}'::NUMBER} fails
 * {@code Numeric value '{}' is not recognized} (SQLSTATE 22018, error 100038). Both used to escape as
 * the raw JDK parse text ("Character { is neither a decimal digit number, decimal point, nor \"e\"
 * notation exponential mark."), which named neither the offending value nor what it failed to become.
 */
public class NumericCastRejectionTest extends BaseDatabaseTest {

    private String failureOf(final String sql) {
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(failure.getMessage());
    }

    private String str(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aSemiStructuredVariantNamesItsValueAndTheNumericFamilyItFailedToBecome() {
        assertTrue(failureOf("SELECT PARSE_JSON('{}')::NUMBER")
            .endsWith("Failed to cast variant value {} to FIXED"));
        assertTrue(failureOf("SELECT PARSE_JSON('{\"a\":1}')::NUMBER(10,2)")
            .endsWith("Failed to cast variant value {\"a\":1} to FIXED"));
        assertTrue(failureOf("SELECT PARSE_JSON('[1,2]')::NUMBER")
            .endsWith("Failed to cast variant value [1,2] to FIXED"));
    }

    /** A padded text is echoed trimmed, a lone space as '' (live-verified). */
    @Test
    public void aPaddedTextIsEchoedTrimmed() {
        assertTrue(failureOf("SELECT '  abc  '::DOUBLE").endsWith("Numeric value 'abc' is not recognized"));
        assertTrue(failureOf("SELECT ' '::DOUBLE").endsWith("Numeric value '' is not recognized"));
        assertTrue(failureOf("SELECT ' '::NUMBER").endsWith("Numeric value '' is not recognized"));
        assertTrue(failureOf("SELECT '  abc  '::NUMBER").endsWith("Numeric value 'abc' is not recognized"));
        assertTrue(failureOf("SELECT CAST('  abc  ' AS INT)").endsWith("Numeric value 'abc' is not recognized"));
        assertTrue(failureOf("SELECT '  abc  '::NUMBER(10,2)").endsWith("Numeric value 'abc' is not recognized"));
    }

    @Test
    public void theIntegerAliasesReportTheSameFixedFamilyAsNumber() {
        assertTrue(failureOf("SELECT PARSE_JSON('{}')::INT")
            .endsWith("Failed to cast variant value {} to FIXED"));
        assertTrue(failureOf("SELECT PARSE_JSON('{}')::BIGINT")
            .endsWith("Failed to cast variant value {} to FIXED"));
    }

    @Test
    public void theApproximateTargetsReportRealRatherThanFixed() {
        assertTrue(failureOf("SELECT PARSE_JSON('{}')::FLOAT")
            .endsWith("Failed to cast variant value {} to REAL"));
        assertTrue(failureOf("SELECT PARSE_JSON('{}')::DOUBLE")
            .endsWith("Failed to cast variant value {} to REAL"));
        assertTrue(failureOf("SELECT PARSE_JSON('[1,2]')::FLOAT")
            .endsWith("Failed to cast variant value [1,2] to REAL"));
    }

    @Test
    public void aVariantHoldingAJsonStringIsReportedInItsQuotedJsonForm() {
        // Live prints the variant's own JSON text, so the quotes are part of the reported value.
        assertTrue(failureOf("SELECT PARSE_JSON('\"abc\"')::NUMBER")
            .endsWith("Failed to cast variant value \"abc\" to FIXED"));
    }

    @Test
    public void aPlainStringKeepsSnowflakesNumericValueMessageInstead() {
        assertTrue(failureOf("SELECT '{}'::NUMBER").endsWith("Numeric value '{}' is not recognized"));
        assertTrue(failureOf("SELECT '{}'::INT").endsWith("Numeric value '{}' is not recognized"));
        assertTrue(failureOf("SELECT '{}'::FLOAT").endsWith("Numeric value '{}' is not recognized"));
        assertTrue(failureOf("SELECT 'abc'::NUMBER").endsWith("Numeric value 'abc' is not recognized"));
    }

    @Test
    public void aVariantHoldingANumericJsonStringStillCasts() {
        // Live-verified: PARSE_JSON('"42"')::NUMBER is 42 — the JSON string's CONTENT is what is parsed,
        // so the quotes must not reach the number parser, and its padding is trimmed as usual.
        assertEquals("42", str("SELECT PARSE_JSON('42')::NUMBER"));
        assertEquals("42", str("SELECT PARSE_JSON('\"42\"')::NUMBER"));
        assertEquals("42", str("SELECT PARSE_JSON('\" 42 \"')::NUMBER"));
        assertEquals("42.5", str("SELECT PARSE_JSON('\"42.5\"')::NUMBER(10,1)"));
    }

    @Test
    public void aPlainStringWhoseTextIsQuotedIsItsOwnLiteralContent() {
        // Only a VARIANT is unwrapped: live rejects '"42"'::NUMBER, so a VARCHAR that happens to start
        // with a quote must never be read as a JSON string.
        assertTrue(failureOf("SELECT '\"42\"'::NUMBER").endsWith("Numeric value '\"42\"' is not recognized"));
    }
}
