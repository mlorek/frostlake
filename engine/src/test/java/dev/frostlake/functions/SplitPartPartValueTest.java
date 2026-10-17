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
 * SPLIT_PART's part: a VARIANT holding a BOOLEAN reads as 1 or 0, as {@code TRUE::VARIANT} does, an ARRAY in one is
 * {@code Failed to cast variant value [1] to FIXED}, and a fraction a conversion of a text or a FLOAT constant made is
 * echoed by its value, {@code found: '1.6'} for {@code TO_DECIMAL('1.6', 4, 1)} (live-verified).
 */
public class SplitPartPartValueTest extends MeasuredCellsTestSupport {

    @Test
    public void aBooleanAndAFoldedFractionAreReadAsLiveReadsThem() {
        engine.execute("CREATE OR REPLACE TABLE tp (n NUMBER(4,2), f FLOAT, i INT, s VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO tp SELECT 1.6, 1.6, 2, '1.6', TO_VARIANT(TRUE)");
        assertCells(new String[][] {
            {"SELECT SPLIT_PART('a,b,c', ',', TO_DECIMAL('1.6', 4, 1))",
                "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'"},
            {"SELECT SPLIT_PART('a,b,c', ',', 1.6::FLOAT::NUMBER(4,2))",
                "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'"},
            {"SELECT SPLIT_PART('a,b,c', ',', TRUE::VARIANT)", "a"},
            {"SELECT SPLIT_PART('a,b,c', ',', FALSE::VARIANT)", "a"},
            {"SELECT SPLIT_PART('a,b,c', ',', v) FROM tp", "a"},
            {"SELECT SPLIT_PART('a,b,c', ',', PARSE_JSON('true'))", "a"},
            {"SELECT SPLIT_PART('a,b,c', ',', PARSE_JSON('2'))", "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', PARSE_JSON('2.5'))", "c"},
            {"SELECT SPLIT_PART('a,b,c', ',', PARSE_JSON('\"2\"'))", "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', (SELECT 2.0))", "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', f::NUMBER(4,0)) FROM tp", "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', '1.6'::NUMBER(4,2))",
                "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'"},
            {"SELECT SPLIT_PART('a,b,c', ',', 1.6::FLOAT)", "b"},
            {"SELECT SPLIT_PART('a,b,c', ',', n + 0) FROM tp",
                "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'TP.N + (CAST(0 AS NUMBER(4,2)))'"},
            {"SELECT SPLIT_PART('a,b,c', ',', n * 2) FROM tp",
                "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'TP.N * 2'"},
            {"SELECT SPLIT_PART('a,b,c', ',', MOD(5, 2.5))", "a"},
            {"SELECT SPLIT_PART('a,b,c', ',', TO_NUMBER('1.6', 4, 2))",
                "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'"},
            {"SELECT SPLIT_PART('a,b,c', ',', TO_DOUBLE('1.6')::NUMBER(4,2))",
                "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'1.6'"},
        });
    }

    @Test
    public void aVariantPartIsReadPerRow() {
        engine.execute("CREATE OR REPLACE TABLE tq (f FLOAT, s VARCHAR, i INT)");
        engine.execute("INSERT INTO tq VALUES (1.6, '1.6', 2), (2.6, '2.6', 3)");
        engine.execute("CREATE OR REPLACE TABLE tp (n NUMBER(4,2), f FLOAT, i INT, s VARCHAR)");
        engine.execute("INSERT INTO tp SELECT 1.6, 1.6, 2, '1.6'");
        engine.execute("CREATE OR REPLACE TABLE te (f FLOAT, s VARCHAR, i INT)");
        assertCells(new String[][] {
            {"SELECT SPLIT_PART('a,b,c,d,e,f,g,h', ',', i * 0.5) FROM tp", "a"},
            {"SELECT SPLIT_PART('a,b,c,d,e,f,g,h', ',', ROUND(i / 3)) FROM tp", "a"},
            {"SELECT SPLIT_PART('a,b,c', ',', 0.666667)",
                "REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, found: "
                + "'0.666667'"},
            {"SELECT SPLIT_PART('a,b,c,d,e,f,g,h', ',', TO_VARIANT(TRUE))", "a"},
            {"SELECT SPLIT_PART('a,b,c,d,e,f,g,h', ',', TO_VARIANT(1.6))", "b"},
            {"SELECT SPLIT_PART('a,b,c,d,e,f,g,h', ',', PARSE_JSON('1.6'))", "b"},
            {"SELECT SPLIT_PART('a,b,c,d,e,f,g,h', ',', PARSE_JSON('null'))", "null"},
            {"SELECT SPLIT_PART('a,b,c,d,e,f,g,h', ',', PARSE_JSON('[1]'))",
                "REFUSED Failed to cast variant value [1] to FIXED"},
            {"SELECT SPLIT_PART('a,b,c,d,e,f,g,h', ',', PARSE_JSON('\"x\"'))",
                "REFUSED Failed to cast variant value \"x\" to FIXED"},
            {"SELECT SPLIT_PART('a,b,c,d,e,f,g,h', ',', TRUE::VARIANT) FROM te", ""},
        });
    }
}
