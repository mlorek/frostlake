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
 * The array and map functions over a structured value answer a structured type, as the account types them
 * (live-verified): ARRAY_PREPEND and ARRAY_INSERT keep the array's type, ARRAY_INTERSECTION the elements' common one,
 * ARRAY_UNION_AGG the element marked NOT NULL, and MAP_INSERT, MAP_DELETE, MAP_CAT and MAP_PICK the map's type, so
 * an element read from them keeps its exact digits.
 */
public class StructuredFunctionResultTypeTest extends MeasuredCellsTestSupport {

    /** Each call and its answer or columns. */
    @Test
    public void theResultKeepsTheStructuredType() {
        assertCells(new String[][] {
            {"SELECT ARRAY_APPEND([9007199254740993]::ARRAY(NUMBER(38,0)), 0)[0] + 1 AS via_append, "
                + "ARRAY_PREPEND([9007199254740993]::ARRAY(NUMBER(38,0)), 0)[1] + 1 AS via_prepend, "
                + "ARRAY_INSERT([9007199254740993]::ARRAY(NUMBER(38,0)), 1, 0)[0] + 1 AS via_insert",
                "9007199254740994|9007199254740994|9007199254740994"},
        });
        assertShape("VIA_PREPEND:NUMBER(38,0), VIA_INSERT:NUMBER(38,0)",
            "SELECT ARRAY_PREPEND([9007199254740993]::ARRAY(NUMBER(38,0)), 0)[1] + 1 AS via_prepend, "
                + "ARRAY_INSERT([9007199254740993]::ARRAY(NUMBER(38,0)), 1, 0)[0] + 1 AS via_insert");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(ARRAY_PREPEND([1,2]::ARRAY(INT), 9)), SYSTEM$TYPEOF(ARRAY_INTERSECTION([1,2]::ARRAY"
                + "(INT), [2]::ARRAY(INT))), SYSTEM$TYPEOF(ARRAY_INSERT([1,2]::ARRAY(INT), 0, 7))",
                "ARRAY(NUMBER(38,0))[LOB]|ARRAY(NUMBER(38,0))[LOB]|ARRAY(NUMBER(38,0))[LOB]"},
            {"SELECT SYSTEM$TYPEOF(MAP_INSERT({'k':1}::MAP(VARCHAR,INT), 'j', 2)), SYSTEM$TYPEOF(MAP_DELETE({'k':1}::M"
                + "AP(VARCHAR,INT), 'k')), SYSTEM$TYPEOF(MAP_CAT({'k':1}::MAP(VARCHAR,INT), {'j':2}::MAP(VARCHAR,INT)"
                + ")), SYSTEM$TYPEOF(MAP_PICK({'k':1,'j':2}::MAP(VARCHAR,INT), 'k'))",
                "MAP(VARCHAR, NUMBER(38,0))[LOB]|MAP(VARCHAR, NUMBER(38,0))[LOB]|MAP(VARCHAR, NUMBER(38,0))[LOB]|MAP"
                + "(VARCHAR, NUMBER(38,0))[LOB]"},
            {"SELECT MAP_INSERT({'k':9007199254740993}::MAP(VARCHAR,NUMBER(38,0)), 'j', 1)['k'] + 1",
                "9007199254740994"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_PREPEND([1,2]::ARRAY(INT), 'x')), ARRAY_PREPEND([1,2]::ARRAY(INT), 'x')",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_INSERT([1,2]::ARRAY(INT), 0, 1.5)), ARRAY_INSERT([1,2]::ARRAY(INT), 0, 1.5)",
                "ARRAY(NUMBER(38,0))[LOB]|[2,1,2]"},
            {"SELECT SYSTEM$TYPEOF(MAP_INSERT({'k':1}::MAP(VARCHAR,INT), 'j', 'x'))",
                "MAP(VARCHAR, NUMBER(38,0))[LOB]"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_INTERSECTION([1,2]::ARRAY(INT), [2]::ARRAY(NUMBER(10,2))))",
                "ARRAY(NUMBER(38,2))[LOB]"},
            {"CREATE OR REPLACE TABLE sa_t (a ARRAY(INT))",
                "Table SA_T successfully created."},
            {"INSERT INTO sa_t SELECT [1,2]::ARRAY(INT) UNION ALL SELECT [2,3]::ARRAY(INT)",
                "2"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_UNION_AGG(a)) FROM sa_t",
                "ARRAY(NUMBER(38,0) NOT NULL)[LOB] ;; ARRAY(NUMBER(38,0) NOT NULL)[LOB]"},
        });
        assertShape("U:ARRAY",
            "SELECT ARRAY_UNION_AGG(a) AS u FROM sa_t");
    }
}
