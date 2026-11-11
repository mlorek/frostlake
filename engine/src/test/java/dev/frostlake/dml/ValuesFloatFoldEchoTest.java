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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A refused VALUES item echoes every constant FLOAT it holds folded to the double it computes, written as the double
 * prints (live-verified): in a conversion to VARIANT, an ARRAY_CONSTRUCT or an OBJECT_CONSTRUCT alike, {@code 0.1},
 * {@code 1000.0}, {@code 1.0E30}, a negation as {@code -2.5} and arithmetic over a number as its result.
 */
public class ValuesFloatFoldEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void constantFloatsFoldWhereverTheyStand() {
        engine.execute("CREATE OR REPLACE TABLE vt931 (v VARIANT)");
        assertCells(new String[][] {
            {"INSERT INTO vt931 VALUES (TO_VARIANT(0.1::FLOAT))",
                "REFUSED SQL compilation error: | Invalid expression [CAST(0.1 AS VARIANT)] in VALUES clause"},
            {"INSERT INTO vt931 VALUES (ARRAY_CONSTRUCT(0.1::FLOAT))",
                "REFUSED SQL compilation error: | Invalid expression [ARRAY_CONSTRUCT(0.1)] in VALUES clause"},
            {"INSERT INTO vt931 VALUES (OBJECT_CONSTRUCT('a', 1e3::FLOAT))",
                "REFUSED SQL compilation error: | Invalid expression [OBJECT_CONSTRUCT('a', 1000.0)] in VALUES clause"},
            {"INSERT INTO vt931 VALUES (TO_VARIANT(1e30::FLOAT))",
                "REFUSED SQL compilation error: | Invalid expression [CAST(1.0E30 AS VARIANT)] in VALUES clause"},
            {"INSERT INTO vt931 VALUES (TO_VARIANT(0.1::FLOAT + 1))",
                "REFUSED SQL compilation error: | Invalid expression [CAST(1.1 AS VARIANT)] in VALUES clause"},
            {"INSERT INTO vt931 VALUES (TO_VARIANT(1.2345678901234567::FLOAT))",
                "REFUSED SQL compilation error: | Invalid expression [CAST(1.2345678901234567 AS VARIANT)] in "
                + "VALUES clause"},
            {"INSERT INTO vt931 VALUES (ARRAY_CONSTRUCT(1e30::FLOAT))",
                "REFUSED SQL compilation error: | Invalid expression [ARRAY_CONSTRUCT(1.0E30)] in VALUES clause"},
            {"INSERT INTO vt931 VALUES (ARRAY_CONSTRUCT(CAST(0.25 AS FLOAT)))",
                "REFUSED SQL compilation error: | Invalid expression [ARRAY_CONSTRUCT(0.25)] in VALUES clause"},
            {"INSERT INTO vt931 VALUES (TO_VARIANT(-2.5::FLOAT))",
                "REFUSED SQL compilation error: | Invalid expression [CAST(-2.5 AS VARIANT)] in VALUES clause"},
        });
    }
}
