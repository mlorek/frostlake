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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * INSERT, HEX_ENCODE and LISTAGG refuse a text past the 134,217,728 characters a text holds, before building it, as
 * REPEAT and REPLACE do; within the cap each still builds its text. Each says it the account's way (live-verified):
 * INSERT shows its result's first 200 characters in 'CONCAT', HEX_ENCODE its argument's in 'HEX_ENCODE', and LISTAGG
 * names its result.
 */
public class TextBuildersSizeTest extends MeasuredCellsTestSupport {

    private static String tooLongIn(final String beginning, final String function) {
        return "REFUSED String '" + beginning + "...' is too long and would be truncated in '" + function + "'";
    }

    @Test
    public void aTextPastTheCapIsRefused() {
        assertAnswer(tooLongIn("b".repeat(200), "CONCAT"),
            "SELECT LENGTH(INSERT(REPEAT('a', 70000000), 1, 0, REPEAT('b', 70000000)))");
        assertAnswer(tooLongIn("a".repeat(200), "HEX_ENCODE"), "SELECT LENGTH(HEX_ENCODE(REPEAT('a', 67108865)))");
        assertAnswer("REFUSED String '(LISTAGG result)' is too long and would be truncated",
            "SELECT LENGTH(LISTAGG(s)) FROM (SELECT REPEAT('x', 1000000) s FROM TABLE(GENERATOR(ROWCOUNT => 135)))");
    }

    @Test
    public void aTextWithinTheCapIsBuilt() {
        assertAnswer("aXYZc|6162|134000000", "SELECT INSERT('abc', 2, 1, 'XYZ'), HEX_ENCODE('ab'), "
            + "(SELECT LENGTH(LISTAGG(s)) FROM (SELECT REPEAT('x', 1000000) s "
            + "FROM TABLE(GENERATOR(ROWCOUNT => 134))))");
    }
}
