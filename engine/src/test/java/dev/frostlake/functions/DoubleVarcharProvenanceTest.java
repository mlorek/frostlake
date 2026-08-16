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
 * A VARIANT member's DOUBLE through the string conversions, by provenance (live-verified cell by
 * cell). A freshly parsed member, a re-stored EXTRACTED member and a rewritten row all render in the
 * ten-significant-digit FLOAT text; the DECIMAL-family member and the exactly-short doubles agree
 * from every provenance; and TO_JSON always writes the fifteen-decimal scientific form.
 *
 * <p>★ THE ONE PROVENANCE LEFT UNMATCHED ON PURPOSE: a member read back from its ORIGINAL
 * {@code INSERT .. SELECT PARSE_JSON} write renders all seventeen digits on a real account — and
 * flips back to the ten-digit text the moment the row is rewritten ({@code UPDATE t SET v = v}) or
 * the extracted value is stored again. That long form is an artifact of the un-rewritten storage
 * generation, which this engine does not model; it renders the ten-digit text from every provenance.
 */
public class DoubleVarcharProvenanceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE dvp (v VARIANT)");
        engine.execute("INSERT INTO dvp SELECT PARSE_JSON('[1.234567890123456, 2.500000000000000e+00,"
            + " 1.000000000000000e+05]')");
    }

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aFreshMemberRendersTheFloatText() {
        assertEquals("0.8999999762",
            cell("SELECT PARSE_JSON('[8.999999761581421e-01]')[0]::VARCHAR"));
        assertEquals("0.8999999762",
            cell("SELECT TO_VARCHAR(PARSE_JSON('[8.999999761581421e-01]')[0])"));
    }

    @Test
    public void toJsonAlwaysWritesTheScientificForm() {
        assertEquals("8.999999761581421e-01",
            cell("SELECT TO_JSON(PARSE_JSON('[8.999999761581421e-01]')[0])"));
    }

    @Test
    public void theDecimalFamilyMemberKeepsItsDigitsFromStorage() {
        assertEquals("1.234567890123456", cell("SELECT v[0]::VARCHAR FROM dvp"));
    }

    @Test
    public void exactlyShortDoublesAgreeFromStorage() {
        // Only a value whose float text and shortest round-trip COINCIDE is stable from storage —
        // 1e5 is not one (its two spellings are 100000 and 100000.0, and which one a stored member
        // gives depends on the storage generation, live-measured both ways).
        assertEquals("2.5", cell("SELECT v[1]::VARCHAR FROM dvp"));
    }

    @Test
    public void aReStoredExtractedMemberRendersTheFloatText() {
        engine.execute("CREATE OR REPLACE TABLE dvp2 (w VARIANT)");
        engine.execute("INSERT INTO dvp2 SELECT PARSE_JSON('[8.999999761581421e-01]')[0]");
        assertEquals("0.8999999762", cell("SELECT w::VARCHAR FROM dvp2"));
    }
}
