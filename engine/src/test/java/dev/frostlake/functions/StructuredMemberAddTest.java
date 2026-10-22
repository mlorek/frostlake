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
 * ARRAY_APPEND, ARRAY_PREPEND and ARRAY_INSERT hold an added element to its scale and to thirty-eight digits, not
 * to its declared precision or length, and a double past them is refused naming the element's declared width.
 * OBJECT_INSERT holds an updated field to its type, refused in the cast's words with the field's nullability and
 * outside any DML sentence, whether a SELECT, an INSERT or a CTAS computes it (all live-verified).
 */
public class StructuredMemberAddTest extends MeasuredCellsTestSupport {

    @Test
    public void anAddedMemberIsHeldAsTheAccountHoldsIt() {
        assertAnswer("[1,12345]", "SELECT ARRAY_APPEND([1]::ARRAY(NUMBER(3,0)), 12345)");
        assertAnswer("[12345,1]", "SELECT ARRAY_PREPEND([1]::ARRAY(NUMBER(3,0)), 12345)");
        assertAnswer("[12345,1]", "SELECT ARRAY_INSERT([1]::ARRAY(NUMBER(3,0)), 0, 12345)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){not null}, value 1e+40",
            "SELECT ARRAY_APPEND([1]::ARRAY(NUMBER(3,0)), 1e40)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 12345",
            "SELECT OBJECT_INSERT({'a':1}::OBJECT(a NUMBER(3,0)), 'a', 12345, TRUE)");
        assertAnswer("REFUSED String 'toolong' is too long and would be truncated",
            "SELECT OBJECT_INSERT({'a':'x'}::OBJECT(a VARCHAR(2)), 'a', 'toolong', TRUE)");
        assertAnswer("{\"a\":\"ab\"}", "SELECT OBJECT_INSERT({'a':'x'}::OBJECT(a VARCHAR(2)), 'a', 'ab', TRUE)");
        assertAnswer("REFUSED String 'toolong' is too long and would be truncated",
            "SELECT ARRAY_APPEND(['a']::ARRAY(VARCHAR(2)), 'toolong')[1]::VARCHAR, "
                + "SYSTEM$TYPEOF(ARRAY_APPEND(['a']::ARRAY(VARCHAR(2)), 'toolong')[1])");
        assertAnswer("[\"a\",\"123\"]", "SELECT ARRAY_APPEND(['a']::ARRAY(VARCHAR(2)), 123)");
        assertAnswer("REFUSED Numeric value 'x' is not recognized",
            "SELECT ARRAY_APPEND([1]::ARRAY(NUMBER(3,1)), 'x')");
        assertAnswer("Table SO successfully created.", "CREATE TABLE so (o OBJECT(a NUMBER(3,0)))");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO so SELECT OBJECT_INSERT({'a':1}::OBJECT(a NUMBER(3,0)), 'a', 12345, TRUE)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 12345",
            "CREATE TABLE so2 AS SELECT OBJECT_INSERT({'a':1}::OBJECT(a NUMBER(3,0)), 'a', 12345, TRUE) o");
        assertAnswer("Table SO3 successfully created.",
            "CREATE TABLE so3 AS SELECT ARRAY_APPEND([1]::ARRAY(NUMBER(3,0)), 12345) a");
    }
}
