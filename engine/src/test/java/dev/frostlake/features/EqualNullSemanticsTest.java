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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * EQUAL_NULL and IS [NOT] DISTINCT FROM treat two NULLs as equal and a NULL beside a value as distinct, and for two
 * values answer exactly what {@code =} answers: a VARIANT number and a VARIANT string with the same digits are
 * distinct, two TIMESTAMP_TZ values at one instant are not (live-verified).
 */
public class EqualNullSemanticsTest extends BaseDatabaseTest {

    /** Every cell of the one row, joined. */
    private String row(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < row.getValues().size(); i++) {
            out.append(i > 0 ? "," : "").append(row.getValue(i));
        }
        return out.toString();
    }

    @Test
    public void aVariantNumberIsNotItsText() {
        assertEquals("false,false,false", row("SELECT PARSE_JSON('1.5') = PARSE_JSON('\"1.5\"'),"
            + " PARSE_JSON('1.5') IS NOT DISTINCT FROM PARSE_JSON('\"1.5\"'),"
            + " EQUAL_NULL(PARSE_JSON('1.5'), PARSE_JSON('\"1.5\"'))"));
        assertEquals("false,false", row("SELECT PARSE_JSON('true') = PARSE_JSON('\"true\"'),"
            + " PARSE_JSON('true') IS NOT DISTINCT FROM PARSE_JSON('\"true\"')"));
    }

    @Test
    public void oneInstantIsOneValue() {
        final String plusOne = "'2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ";
        final String utc = "'2023-12-31 23:00:00 +00:00'::TIMESTAMP_TZ";
        assertEquals("true,true,true,false", row("SELECT " + plusOne + " = " + utc + ", " + plusOne
            + " IS NOT DISTINCT FROM " + utc + ", EQUAL_NULL(" + plusOne + ", " + utc + "), " + plusOne
            + " IS DISTINCT FROM " + utc));
    }

    @Test
    public void nullsAreKnownValues() {
        assertEquals("true,false,true,true,false", row("SELECT NULL IS NOT DISTINCT FROM NULL,"
            + " 1 IS NOT DISTINCT FROM NULL, NULL IS DISTINCT FROM 1, EQUAL_NULL(NULL, NULL), EQUAL_NULL(1, NULL)"));
        assertEquals("true,true,false", row("SELECT 1 IS NOT DISTINCT FROM 1.0, EQUAL_NULL(1, 1.0),"
            + " 1 IS DISTINCT FROM 1.0"));
    }

    @Test
    public void aNullSafeJoinPairsWhatEqualsPairs() {
        engine.execute("CREATE TABLE v (x VARIANT)");
        engine.execute("INSERT INTO v SELECT PARSE_JSON('1.5')");
        engine.execute("INSERT INTO v SELECT PARSE_JSON('\"1.5\"')");
        assertEquals("2", row("SELECT COUNT(*) FROM v a JOIN v b ON a.x = b.x"));
        assertEquals("2", row("SELECT COUNT(*) FROM v a JOIN v b ON a.x IS NOT DISTINCT FROM b.x"));
        assertEquals("2", row("SELECT COUNT(*) FROM v a JOIN v b ON EQUAL_NULL(a.x, b.x)"));
    }
}
