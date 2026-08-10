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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a CONDITIONAL declares is what its BRANCHES agree on — the same rule a set operation applies to
 * its arms, which is why both surfaces call one fold. Frostlake declared the 16MB VARCHAR placeholder
 * for every one of these, so two VARCHAR(4)s came back as TEXT(16777216).
 *
 * <pre>
 *   IFF over VARCHAR(4), VARCHAR(100)         TEXT(100)      the widest
 *   IFF over NUMBER(5,1), NUMBER(10,3)        NUMBER(10,3)
 *   IFF over NUMBER(5,4), NUMBER(10,0)        NUMBER(14,4)   NOT (10,4) — the integer part meets the scale
 *   COALESCE over TIMESTAMP_NTZ(3), (9)       scale 9
 * </pre>
 */
public class ConditionalDeclaredTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ct (s4 VARCHAR(4), s100 VARCHAR(100), n51 NUMBER(5,1),"
            + " n103 NUMBER(10,3), n54 NUMBER(5,4), n100 NUMBER(10,0),"
            + " t3 TIMESTAMP_NTZ(3), t9 TIMESTAMP_NTZ(9), d DATE)");
        engine.execute("INSERT INTO ct SELECT 'ab', 'cd', 1.5, 2.25, 0.5, 7,"
            + " '2026-01-01 00:00:00', '2026-01-01 00:00:00', '2026-01-01'");
    }

    private String declaredType(final String expression) {
        engine.execute("CREATE OR REPLACE VIEW ct_v AS SELECT " + expression + " AS c FROM ct");
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.ct_v");
        rs.next();
        return String.valueOf(rs.getValue("data_type"));
    }

    /** Two strings fold to the WIDEST, not to the placeholder. */
    @Test
    public void twoStringsFoldToTheWidest() {
        assertEquals("{\"type\":\"TEXT\",\"length\":100,\"byteLength\":400,\"nullable\":true,"
            + "\"fixed\":false}", declaredType("IFF(s4 = 'x', s4, s100)"));
        assertEquals("{\"type\":\"TEXT\",\"length\":100,\"byteLength\":400,\"nullable\":true,"
            + "\"fixed\":false}", declaredType("COALESCE(s4, s100)"));
    }

    /** Two numbers fold so the widest INTEGER PART meets the widest SCALE. */
    @Test
    public void twoNumbersFoldToTheSupertype() {
        assertEquals("{\"type\":\"FIXED\",\"precision\":10,\"scale\":3,\"nullable\":true}",
            declaredType("IFF(s4 = 'x', n51, n103)"));
        assertEquals("{\"type\":\"FIXED\",\"precision\":14,\"scale\":4,\"nullable\":true}",
            declaredType("IFF(s4 = 'x', n54, n100)"));
    }

    /** Temporals of one flavour fold to the WIDER precision. */
    @Test
    public void temporalsFoldToTheWiderPrecision() {
        assertEquals("{\"type\":\"TIMESTAMP_NTZ\",\"precision\":0,\"scale\":9,\"nullable\":true}",
            declaredType("COALESCE(t3, t9)"));
        assertEquals("{\"type\":\"DATE\",\"nullable\":true}", declaredType("IFF(s4 = 'x', d, d)"));
    }

    /** CASE, DECODE and NVL2 answer the same way — they are the same construct to the inferencer. */
    @Test
    public void everySpellingFoldsAlike() {
        assertEquals("{\"type\":\"TEXT\",\"length\":100,\"byteLength\":400,\"nullable\":true,"
            + "\"fixed\":false}", declaredType("CASE WHEN s4 = 'x' THEN s4 ELSE s100 END"));
        assertEquals("{\"type\":\"FIXED\",\"precision\":10,\"scale\":3,\"nullable\":true}",
            declaredType("NVL2(s4, n51, n103)"));
        assertEquals("{\"type\":\"FIXED\",\"precision\":10,\"scale\":3,\"nullable\":true}",
            declaredType("DECODE(s4, 'x', n51, n103)"));
    }
}
