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

import org.junit.jupiter.api.Test;

/**
 * A FROM VALUES list refuses a semi-structured item, each echoed as the plan spells it (live-verified): PARSE_JSON,
 * TO_VARIANT and a cast to VARIANT, TO_ARRAY and TO_OBJECT as casts, the compact and keep-null constructors, a cast to
 * a structured ARRAY; and in a later row PARSE_JSON is named as the expression it is, where a constructor is refused by
 * its type.
 */
public class FromValuesSemiStructuredTest extends MeasuredCellsTestSupport {

    @Test
    public void semiStructuredItemsOfAFromValuesList() {
        assertCells(new String[][] {
            {"SELECT * FROM VALUES (PARSE_JSON('[1]'))",
                "REFUSED SQL compilation error: | Invalid expression [PARSE_JSON('[1]')] in VALUES clause"},
            {"SELECT * FROM VALUES (TO_VARIANT(1))",
                "REFUSED SQL compilation error: | Invalid expression [CAST(1 AS VARIANT)] in VALUES clause"},
            {"SELECT * FROM VALUES (1::VARIANT)",
                "REFUSED SQL compilation error: | Invalid expression [CAST(1 AS VARIANT)] in VALUES clause"},
            {"SELECT * FROM VALUES (TO_ARRAY(1))",
                "REFUSED SQL compilation error: | Invalid expression [CAST(1 AS ARRAY)] in VALUES clause"},
            {"SELECT * FROM VALUES (TO_OBJECT(PARSE_JSON('{}')))",
                "REFUSED SQL compilation error: | Invalid expression [CAST(PARSE_JSON('{}') AS OBJECT)] in VALUES "
                + "clause"},
            {"SELECT * FROM VALUES (ARRAY_CONSTRUCT_COMPACT(1))",
                "REFUSED SQL compilation error: | Invalid expression [ARRAY_CONSTRUCT_COMPACT(1)] in VALUES clause"},
            {"SELECT * FROM VALUES (OBJECT_CONSTRUCT_KEEP_NULL('a', NULL))",
                "REFUSED SQL compilation error: | Invalid expression [OBJECT_CONSTRUCT_KEEP_NULL('a', null)] in "
                + "VALUES clause"},
            {"SELECT * FROM VALUES (ARRAY_CONSTRUCT(1)::ARRAY(NUMBER))",
                "REFUSED SQL compilation error: | Invalid expression [CAST(ARRAY_CONSTRUCT(1) AS "
                + "ARRAY(NUMBER(38,0)))] in VALUES clause"},
            {"SELECT * FROM VALUES (1), (PARSE_JSON('2'))",
                "REFUSED SQL compilation error: | Invalid expression [PARSE_JSON('2')] in VALUES clause"},
            {"SELECT * FROM VALUES (CAST(0.1 AS VARIANT))",
                "REFUSED SQL compilation error: | Invalid expression [CAST(0.1 AS VARIANT)] in VALUES clause"},
        });
    }
}
