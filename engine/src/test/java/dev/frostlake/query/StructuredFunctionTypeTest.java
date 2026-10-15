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
 * The array and object functions over a structured value answer a structured type (live-verified): ARRAY_CAT the
 * elements' common type, ARRAY_APPEND, ARRAY_SLICE and ARRAY_EXCEPT the first array's, OBJECT_DELETE the object
 * without the field, OBJECT_INSERT the object and the new field, and OBJECT_PICK the picked fields in the order picked
 * — its value in that order too.
 */
public class StructuredFunctionTypeTest extends MeasuredCellsTestSupport {

    private static final String CITY = "{'city':'San Mateo','state':'CA'}::OBJECT(city VARCHAR,state VARCHAR)";

    @Test
    public void theArrayFunctionsKeepTheStructuredType() {
        assertCells(new String[][] {
            {"SELECT ARRAY_CAT([1, 2, 3]::ARRAY(NUMBER), [5.5, NULL]::ARRAY(NUMBER(2, 1))) AS concatenated_array, "
                + "SYSTEM$TYPEOF(concatenated_array)", "[1,2,3,5.5,undefined]|ARRAY(NUMBER(38,1))[LOB]"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_CAT([1,2]::ARRAY(INT), [3]::ARRAY(INT)))", "ARRAY(NUMBER(38,0))[LOB]"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_APPEND([1,2]::ARRAY(INT), 3)) AS app, "
                + "SYSTEM$TYPEOF(ARRAY_SLICE([1,2]::ARRAY(INT), 0, 1)) AS sl, "
                + "SYSTEM$TYPEOF(ARRAY_EXCEPT([1,2]::ARRAY(INT), [1]::ARRAY(INT))) AS ex",
                "ARRAY(NUMBER(38,0))[LOB]|ARRAY(NUMBER(38,0))[LOB]|ARRAY(NUMBER(38,0))[LOB]"},
            {"SELECT OBJECT_KEYS({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR))", "[\"a\",\"b\"]"},
        });
    }

    @Test
    public void theObjectFunctionsKeepTheStructuredType() {
        assertCells(new String[][] {
            {"SELECT OBJECT_DELETE(" + CITY + ", 'city') AS new_object, SYSTEM$TYPEOF(new_object)",
                "{\"state\":\"CA\"}|OBJECT(state VARCHAR)[LOB]"},
            {"SELECT OBJECT_INSERT(" + CITY + ", 'zip_code', 94402::VARCHAR, false) AS new_object, "
                + "SYSTEM$TYPEOF(new_object)", "{\"city\":\"San Mateo\",\"state\":\"CA\",\"zip_code\":\"94402\"}|"
                + "OBJECT(city VARCHAR, state VARCHAR, zip_code VARCHAR NOT NULL)[LOB]"},
            {"SELECT OBJECT_PICK({'city':'San Mateo','state':'CA','zip_code':94402}::OBJECT(city VARCHAR,state VARCHAR,"
                + "zip_code DOUBLE), 'state', 'city') AS new_object, SYSTEM$TYPEOF(new_object)",
                "{\"state\":\"CA\",\"city\":\"San Mateo\"}|OBJECT(state VARCHAR, city VARCHAR)[LOB]"},
            {"SELECT OBJECT_PICK(" + CITY + ", 'state', 'city')", "{\"state\":\"CA\",\"city\":\"San Mateo\"}"},
        });
    }
}
