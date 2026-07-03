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
import static org.junit.jupiter.api.Assertions.assertNull;

/** JAROWINKLER_SIMILARITY(s1, s2) — Jaro-Winkler similarity scaled to an integer 0–100 (case-insensitive). */
public class JarowinklerSimilarityTest extends BaseDatabaseTest {

    private long num(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void snowflakeVsOracle() {
        // Documented Snowflake example: 61.
        assertEquals(61L, num("SELECT JAROWINKLER_SIMILARITY('Snowflake', 'Oracle')"));
    }

    @Test
    public void marthaVsMarhta() {
        // Classic Jaro-Winkler example (Jaro 0.944 + 3-char prefix bonus) → 96.
        assertEquals(96L, num("SELECT JAROWINKLER_SIMILARITY('MARTHA', 'MARHTA')"));
    }

    @Test
    public void identicalStringsAreOneHundred() {
        assertEquals(100L, num("SELECT JAROWINKLER_SIMILARITY('Frostlake', 'Frostlake')"));
    }

    @Test
    public void comparisonIsCaseInsensitive() {
        assertEquals(100L, num("SELECT JAROWINKLER_SIMILARITY('SNOW', 'snow')"));
    }

    @Test
    public void nullArgumentYieldsNull() {
        assertNull(scalar("SELECT JAROWINKLER_SIMILARITY(NULL, 'x')"));
        assertNull(scalar("SELECT JAROWINKLER_SIMILARITY('x', NULL)"));
    }
}
