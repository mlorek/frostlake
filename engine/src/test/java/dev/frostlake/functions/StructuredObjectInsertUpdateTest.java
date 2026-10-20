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
 * OBJECT_INSERT and OBJECT_DELETE over a structured OBJECT, as the account takes them (live-verified): with the
 * update flag the value is cast to the field's declared type and the type is kept, a field the object does not
 * declare and a NULL value are refused, and without the flag a field it declares is refused; OBJECT_DELETE refuses
 * a field it does not declare, and a table built over a cast stores its unsized VARCHAR leaf at 16777216.
 */
public class StructuredObjectInsertUpdateTest extends MeasuredCellsTestSupport {

    /** Each call and its answer. */
    @Test
    public void theFieldsDeclaredTypeRules() {
        assertCells(new String[][] {
            {"SELECT OBJECT_INSERT({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'a', '5', TRUE) AS x, SYSTEM$TYPEOF(x)",
                "{\"a\":5,\"b\":\"x\"}|OBJECT(a NUMBER(38,0), b VARCHAR)[LOB]"},
            {"SELECT OBJECT_INSERT({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'a', 'zz', TRUE) AS x",
                "REFUSED Numeric value 'zz' is not recognized"},
            {"SELECT OBJECT_INSERT({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'c', 7, TRUE) AS x, SYSTEM$TYPEOF(x)",
                "REFUSED Function OBJECT_INSERT: expected structured object to contain field c but it did not."},
            {"SELECT OBJECT_INSERT({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'c', 7) AS x, SYSTEM$TYPEOF(x)",
                "{\"a\":1,\"b\":\"x\",\"c\":7}|OBJECT(a NUMBER(38,0), b VARCHAR, c NUMBER(1,0) NOT NULL)[LOB]"},
            {"SELECT OBJECT_INSERT({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'a', 7) AS x",
                "REFUSED Function OBJECT_INSERT: expected structured object to not contain field a but it did."},
            {"SELECT OBJECT_INSERT({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'a', 2.7, TRUE) AS x, SYSTEM$TYPEOF(x)",
                "{\"a\":3,\"b\":\"x\"}|OBJECT(a NUMBER(38,0), b VARCHAR)[LOB]"},
            {"SELECT OBJECT_INSERT({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'b', 5, TRUE) AS x, SYSTEM$TYPEOF(x)",
                "{\"a\":1,\"b\":\"5\"}|OBJECT(a NUMBER(38,0), b VARCHAR)[LOB]"},
            {"SELECT OBJECT_INSERT({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'a', NULL, TRUE) AS x, SYSTEM$TYPEOF(x)",
                "REFUSED Cannot set NULL value for structured object field"},
            {"SELECT OBJECT_DELETE({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'nope') AS x",
                "REFUSED Function OBJECT_DELETE: expected structured object to contain field nope but it did not."},
            {"SELECT OBJECT_DELETE({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'a') AS x, SYSTEM$TYPEOF(x)",
                "{\"b\":\"x\"}|OBJECT(b VARCHAR)[LOB]"},
            {"SELECT OBJECT_DELETE({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'a', 'nope') AS x",
                "REFUSED Function OBJECT_DELETE: expected structured object to contain field nope but it did not."},
            {"CREATE OR REPLACE TABLE so_t AS SELECT OBJECT_INSERT({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'a', "
                + "'5', TRUE) AS x",
                "Table SO_T successfully created."},
            {"DESCRIBE TABLE so_t",
                "X|OBJECT(a NUMBER(38,0), b VARCHAR(16777216))|COLUMN|Y|null|N|N|null|null|null|null|null|null"},
            {"CREATE OR REPLACE TABLE so_t2 AS SELECT {'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR) AS x",
                "Table SO_T2 successfully created."},
            {"DESCRIBE TABLE so_t2",
                "X|OBJECT(a NUMBER(38,0), b VARCHAR(16777216))|COLUMN|Y|null|N|N|null|null|null|null|null|null"},
            {"CREATE OR REPLACE TABLE so_t3 AS SELECT OBJECT_DELETE({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR), 'a') "
                + "AS x",
                "Table SO_T3 successfully created."},
            {"DESCRIBE TABLE so_t3",
                "X|OBJECT(b VARCHAR(16777216))|COLUMN|Y|null|N|N|null|null|null|null|null|null"},
        });
    }
}
