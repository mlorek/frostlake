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
 * A built-in function named under INFORMATION_SCHEMA, bare or under a database's, is the built-in itself: it runs,
 * a CALL of it too, and its refusals name it bare, {@code too many arguments for function [UPPER('a', 'b')]}; a
 * database written before the schema is looked up. A name no built-in has is refused as an unknown function by its
 * whole name, listed with the bare unknown names (live-verified).
 */
public class InformationSchemaBuiltInTest extends MeasuredCellsTestSupport {

    @Test
    public void aBuiltInUnderInformationSchemaIsTheBuiltIn() {
        assertCells(new String[][] {
            {"SELECT SNOWFLAKE.INFORMATION_SCHEMA.SYSTEM$TYPEOF(1)", "NUMBER(1,0)[SB1]"},
            {"SELECT SNOWFLAKE.INFORMATION_SCHEMA.SYSTEM$WAIT(0)", "waited 0 seconds"},
            {"SELECT INFORMATION_SCHEMA.SYSTEM$TYPEOF(1)", "NUMBER(1,0)[SB1]"},
            {"SELECT SNOWFLAKE.INFORMATION_SCHEMA.UPPER('a')", "A"},
            {"SELECT INFORMATION_SCHEMA.UPPER('a')", "A"},
            {"CALL SNOWFLAKE.INFORMATION_SCHEMA.SYSTEM$WAIT(0)", "waited 0 seconds"},
            {"SELECT SNOWFLAKE.INFORMATION_SCHEMA.SYSTEM$TYPEOF(a => 1)", "NUMBER(1,0)[SB1]"},
            {"SELECT SNOWFLAKE.INFORMATION_SCHEMA.SYSTEM$TYPEOF(1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SYSTEM$TYPEOF(1, 2)] expected 1, got 2"},
            {"SELECT SNOWFLAKE.INFORMATION_SCHEMA.SYSTEM$TYPEOF()",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$TYPEOF()], expected 1, got 0"},
            {"SELECT test_db.INFORMATION_SCHEMA.SYSTEM$TYPEOF(1)", "NUMBER(1,0)[SB1]"},
            {"SELECT SNOWFLAKE.INFORMATION_SCHEMA.SYSTEM$NOSUCH_ZQ7(1)",
                "REFUSED SQL compilation error: | Unknown function SNOWFLAKE.INFORMATION_SCHEMA.SYSTEM$NOSUCH_ZQ7."},
            {"SELECT information_schema.upper('a')", "A"},
            {"SELECT INFORMATION_SCHEMA.NOSUCH_FN_ZQ7(1)",
                "REFUSED SQL compilation error: | Unknown function INFORMATION_SCHEMA.NOSUCH_FN_ZQ7."},
            {"SELECT INFORMATION_SCHEMA.ABS(-2), INFORMATION_SCHEMA.LENGTH('abc')", "2|3"},
            {"SELECT SYSTEM$TYPEOF(INFORMATION_SCHEMA.ABS(-2))", "NUMBER(2,0)[SB1]"},
            {"SELECT INFORMATION_SCHEMA.UPPER('a', 'b')",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[UPPER('a', 'b')] expected 1, got 2"},
            {"SELECT INFORMATION_SCHEMA.COALESCE(NULL, 1)", "1"},
            {"SELECT INFORMATION_SCHEMA.IFF(TRUE, 1, 2)", "1"},
            {"SELECT INFORMATION_SCHEMA.CURRENT_DATABASE()", "TEST_DB"},
            {"SELECT INFORMATION_SCHEMA.TO_VARCHAR(12)", "12"},
            {"SELECT test_db.INFORMATION_SCHEMA.UPPER('x')", "X"},
            {"SELECT nosuch_db.INFORMATION_SCHEMA.UPPER('x')",
                "REFUSED SQL compilation error: | Object 'NOSUCH_DB' does not exist or not authorized."},
            {"SELECT a FROM (SELECT 1 a) WHERE INFORMATION_SCHEMA.ABS(a) = 1", "1"},
            {"CALL INFORMATION_SCHEMA.SYSTEM$WAIT(0)", "waited 0 seconds"},
            {"CALL INFORMATION_SCHEMA.UPPER('a')", "A"},
            {"SELECT INFORMATION_SCHEMA.NOSUCH_FN_ZQ7(1), NOSUCH_FN2_ZQ7(2)",
                "REFUSED SQL compilation error: | Unknown functions INFORMATION_SCHEMA.NOSUCH_FN_ZQ7, NOSUCH_FN2_ZQ7."},
        });
    }
}
