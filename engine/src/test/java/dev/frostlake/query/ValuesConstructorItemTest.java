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
 * A VALUES list takes no item that holds a semi-structured part anywhere, in a FROM as in an INSERT: an array or an
 * object constructor, a value cast to VARIANT or ARRAY, an element read off one, a call that answers one and a call
 * that reads one, its argument a NULL included. The statement is refused while it compiles, the item echoed as the
 * plan spells it; in a row after the first, an item that is a semi-structured value is refused by its type. A plain
 * call, a NULL of any type and a constant through a VARIANT back to a scalar are taken (all live-verified).
 */
public class ValuesConstructorItemTest extends MeasuredCellsTestSupport {

    private static final String REFUSED = "REFUSED SQL compilation error: | Invalid expression [";

    @Test
    public void aConstructorItemIsRefusedWhileCompiling() {
        assertCells(new String[][] {
            {"SELECT TO_VARCHAR(a[0]::DATE, 'YYYYMMDD') FROM (SELECT column1 AS a FROM VALUES ([1]))",
                REFUSED + "ARRAY_CONSTRUCT(1)] in VALUES clause"},
            {"SELECT * FROM VALUES ([1, 2])", REFUSED + "ARRAY_CONSTRUCT(1, 2)] in VALUES clause"},
            {"SELECT * FROM VALUES (ARRAY_CONSTRUCT(1, 2))", REFUSED + "ARRAY_CONSTRUCT(1, 2)] in VALUES clause"},
            {"SELECT * FROM VALUES ({'a': 1})", REFUSED + "OBJECT_CONSTRUCT('a', 1)] in VALUES clause"},
            {"SELECT * FROM (VALUES ([1]))", REFUSED + "ARRAY_CONSTRUCT(1)] in VALUES clause"},
        });
    }

    @Test
    public void aSemiStructuredPartAnywhereRefusesTheItem() {
        assertCells(new String[][] {
            {"SELECT * FROM VALUES (ARRAY_SIZE([1]))", REFUSED + "ARRAY_SIZE(ARRAY_CONSTRUCT(1))] in VALUES clause"},
            {"SELECT * FROM VALUES (TO_ARRAY(1))", REFUSED + "CAST(1 AS ARRAY)] in VALUES clause"},
            {"SELECT * FROM VALUES (PARSE_JSON('[1]'))", REFUSED + "PARSE_JSON('[1]')] in VALUES clause"},
            {"SELECT * FROM VALUES ([1][0])", REFUSED + "GET(ARRAY_CONSTRUCT(1), 0)] in VALUES clause"},
            {"SELECT * FROM VALUES (ARRAY_SIZE(TO_ARRAY(1)))",
                REFUSED + "ARRAY_SIZE(CAST(1 AS ARRAY))] in VALUES clause"},
            {"SELECT * FROM VALUES ([1]::VARCHAR)",
                REFUSED + "CAST(ARRAY_CONSTRUCT(1) AS VARCHAR(134217728))] in VALUES clause"},
            {"SELECT * FROM VALUES (ARRAY_SIZE(SPLIT('a,b', ',')))",
                REFUSED + "ARRAY_SIZE(SPLIT('a,b', ','))] in VALUES clause"},
            {"SELECT * FROM VALUES (1), (ARRAY_SIZE([1, 2]))",
                REFUSED + "ARRAY_SIZE(ARRAY_CONSTRUCT(1, 2))] in VALUES clause"},
            {"SELECT * FROM VALUES (TO_VARIANT(1))", REFUSED + "CAST(1 AS VARIANT)] in VALUES clause"},
            {"SELECT * FROM VALUES (1::VARIANT)", REFUSED + "CAST(1 AS VARIANT)] in VALUES clause"},
            {"SELECT * FROM VALUES (ARRAY_CONSTRUCT_COMPACT(1))",
                REFUSED + "ARRAY_CONSTRUCT_COMPACT(1)] in VALUES clause"},
            {"SELECT * FROM VALUES (GET([1], 0))", REFUSED + "GET(ARRAY_CONSTRUCT(1), 0)] in VALUES clause"},
            {"SELECT * FROM VALUES (TYPEOF([1]))",
                REFUSED + "TYPEOF(CAST(ARRAY_CONSTRUCT(1) AS VARIANT))] in VALUES clause"},
            {"SELECT * FROM VALUES (ARRAY_SIZE(NULL))", REFUSED + "ARRAY_SIZE(null)] in VALUES clause"},
            {"SELECT * FROM VALUES (TO_VARCHAR(PARSE_JSON('1')))",
                REFUSED + "CAST(PARSE_JSON('1') AS VARCHAR(134217728))] in VALUES clause"},
            {"SELECT * FROM VALUES (TO_JSON(1))", REFUSED + "TO_JSON(CAST(1 AS VARIANT))] in VALUES clause"},
            {"SELECT * FROM VALUES (1, [1])", REFUSED + "ARRAY_CONSTRUCT(1)] in VALUES clause"},
            {"SELECT * FROM VALUES (CHECK_JSON('1'))", REFUSED + "CHECK_JSON('1')] in VALUES clause"},
            {"SELECT * FROM VALUES (STRTOK_TO_ARRAY('a b'))", REFUSED + "STRTOK_TO_ARRAY('a b')] in VALUES clause"},
            {"SELECT * FROM VALUES (ARRAY_TO_STRING([1, 2], ','))",
                REFUSED + "ARRAY_TO_STRING(ARRAY_CONSTRUCT(1, 2), ',')] in VALUES clause"},
            {"SELECT * FROM VALUES (IS_ARRAY(PARSE_JSON('[1]')))",
                REFUSED + "IS_ARRAY(PARSE_JSON('[1]'))] in VALUES clause"},
            {"SELECT * FROM (VALUES (ARRAY_SIZE([1])))", REFUSED + "ARRAY_SIZE(ARRAY_CONSTRUCT(1))] in VALUES clause"},
            {"SELECT * FROM VALUES (1, 2), ([1], 3)", "REFUSED SQL compilation error: | Invalid data type [ARRAY] in "
                + "VALUES clause"},
        });
    }

    @Test
    public void anInsertsItemIsJudgedAlike() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tv (n INT)", "Table TV successfully created."},
            {"INSERT INTO tv VALUES (ARRAY_SIZE([1]))", REFUSED + "ARRAY_SIZE(ARRAY_CONSTRUCT(1))] in VALUES clause"},
            {"INSERT INTO tv VALUES (ABS(-1))", "1"},
        });
    }

    @Test
    public void aPlainCallANullAndAVariantRoundTripAreTaken() {
        assertCells(new String[][] {
            {"SELECT * FROM VALUES (1), (2) ORDER BY 1", "1 ;; 2"},
            {"SELECT * FROM VALUES (ABS(-1))", "1"},
            {"SELECT * FROM VALUES (UPPER('a'))", "A"},
            {"SELECT * FROM VALUES (1 + 1)", "2"},
            {"SELECT * FROM VALUES (CONCAT('a', 'b'))", "ab"},
            {"SELECT * FROM VALUES (IFF(TRUE, 1, 2))", "1"},
            {"SELECT * FROM VALUES ((SELECT 1))", "1"},
            {"SELECT * FROM VALUES (NULL::ARRAY)", "null"},
            {"SELECT * FROM VALUES ('a'::VARIANT::VARCHAR)", "a"},
            {"SELECT * FROM VALUES (NULLIF(1, 1))", "null"},
            {"SELECT * FROM VALUES (COALESCE(NULL, 1))", "1"},
            {"SELECT * FROM VALUES (CAST('1' AS NUMBER))", "1"},
            {"SELECT * FROM VALUES (SYSTEM$TYPEOF(1))", "NUMBER(1,0)[SB1]"},
        });
    }
}
