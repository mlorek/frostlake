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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A DECIMAL literal's TRAILING ZEROS are not part of its value. Live normalises the literal before
 * anything reads it, so 1.00 types, refuses and renders exactly as 1 would:
 *
 * <pre>
 *   SYSTEM$TYPEOF(1.00)     NUMBER(1,0)[SB1]
 *   SYSTEM$TYPEOF(0.10)     NUMBER(2,1)[SB1]     only the meaningful decimal survives
 *   SYSTEM$TYPEOF(100.00)   NUMBER(3,0)[SB1]
 *   SELECT 1.00             1
 * </pre>
 *
 * <p>★ IT IS ONE NORMALISATION, NOT PER-CHANNEL: the argument-type refusal spells the same literal
 * NUMBER(1,0) in its bracketed signature, and TO_VARCHAR(1.00) is '1'. A literal whose zeros are
 * MEANINGFUL — 1.05 — keeps its full (3,2) everywhere.
 *
 * <p>★ THE ONE READER OF THE SPELLING IS UNIFORM, whose draw family follows how the bound was
 * WRITTEN — {@code UNIFORM(0.0, 1.0, …)} draws scaled values live even though 0.0 types as
 * NUMBER(1,0). That exemption lives at its dispatch and is pinned by RandomUniformTest.
 */
public class DecimalLiteralNormalisationTest extends BaseDatabaseTest {

    /** The first value of the first row. */
    private String value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** One statement's refusal, or its first value. */
    private String outcome(final String sql) {
        try {
            return "ACCEPTED " + value(sql);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    void trailingZerosDropFromTheType() {
        assertEquals("NUMBER(1,0)[SB1]", value("SELECT SYSTEM$TYPEOF(1.00)"));
        assertEquals("NUMBER(1,0)[SB1]", value("SELECT SYSTEM$TYPEOF(1.0)"));
        assertEquals("NUMBER(1,0)[SB1]", value("SELECT SYSTEM$TYPEOF(1.000)"));
        assertEquals("NUMBER(1,0)[SB1]", value("SELECT SYSTEM$TYPEOF(-1.00)"));
        assertEquals("NUMBER(1,0)[SB1]", value("SELECT SYSTEM$TYPEOF(0.00)"));
        assertEquals("NUMBER(3,0)[SB1]", value("SELECT SYSTEM$TYPEOF(100.00)"));
    }

    @Test
    void onlyTheMeaningfulDecimalsSurvive() {
        assertEquals("NUMBER(2,1)[SB1]", value("SELECT SYSTEM$TYPEOF(0.10)"));
        assertEquals("NUMBER(3,1)[SB1]", value("SELECT SYSTEM$TYPEOF(10.10)"));
        assertEquals("NUMBER(3,2)[SB1]", value("SELECT SYSTEM$TYPEOF(1.05)"));
    }

    @Test
    void theRefusalSignatureSpellsTheNormalisedType() {
        engine.execute("CREATE OR REPLACE TABLE tz (g VARCHAR(10))");
        engine.execute("INSERT INTO tz VALUES ('x')");
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for"
                + " function 'IFF': (VARCHAR(10), NUMBER(1,0), NUMBER(1,0))",
            outcome("SELECT IFF(g, 1.00, 2) FROM tz"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for"
                + " function 'IFF': (VARCHAR(10), NUMBER(3,2), NUMBER(1,0))",
            outcome("SELECT IFF(g, 1.05, 2) FROM tz"));
    }

    @Test
    void theValueRendersWithoutItsZeros() {
        assertEquals("1", value("SELECT TO_VARCHAR(1.00)"));
        assertEquals("0.1", value("SELECT TO_VARCHAR(0.10)"));
        assertEquals("1.05", value("SELECT TO_VARCHAR(1.05)"));
    }
}
