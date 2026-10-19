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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A SQL function called once for every row of a table answers for each row what the same expression written in
 * the query answers - with its body an expression, a Snowflake Scripting block and a query in turn under one
 * name, with two parameters, a default, named arguments, a function that calls another, a constant argument,
 * texts that hold quotes and backslashes, and in a WHERE and a join's ON. The body is read once and the calls
 * are many; what each call answers is the same either way. Every answer is live-verified.
 */
public class SqlFunctionRepeatedCallTest extends MeasuredCellsTestSupport {

    @Test
    public void aCallForEveryRow() {
        assertAnswer(
            "Table ROWS200 successfully created.",
            "CREATE OR REPLACE TABLE rows200 AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 200))");
        assertAnswer(
            "Function RC successfully created.",
            "CREATE OR REPLACE FUNCTION rc(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        assertAnswer(
            "20100|200",
            "SELECT SUM(rc(i)), COUNT(DISTINCT rc(i)) FROM rows200");
        assertAnswer(
            "Function RC successfully created.",
            "CREATE OR REPLACE FUNCTION rc(x NUMBER) RETURNS NUMBER AS $$ BEGIN RETURN x + 2; END $$");
        assertAnswer(
            "20300",
            "SELECT SUM(rc(i)) FROM rows200");
        assertAnswer(
            "Function RC successfully created.",
            "CREATE OR REPLACE FUNCTION rc(x NUMBER) RETURNS NUMBER AS 'SELECT x + 3'");
        assertAnswer(
            "20500",
            "SELECT SUM(rc(i)) FROM rows200");
        assertAnswer(
            "Function RC2 successfully created.",
            "CREATE OR REPLACE FUNCTION rc2(a NUMBER, b VARCHAR) RETURNS VARCHAR AS 'a || ''-'' || UPPER(b)'");
        assertAnswer(
            "0-V0|99-V99",
            "SELECT MIN(rc2(i, 'v' || i)), MAX(rc2(i, 'v' || i)) FROM rows200");
        assertAnswer(
            "Function RC3 successfully created.",
            "CREATE OR REPLACE FUNCTION rc3(x NUMBER) RETURNS NUMBER AS 'rc(x) * 2'");
        assertAnswer(
            "41000",
            "SELECT SUM(rc3(i)) FROM rows200");
        assertAnswer(
            "1600|3200",
            "SELECT SUM(rc(5)), SUM(rc3(5)) FROM rows200");
        assertAnswer(
            "Function RC4 successfully created.",
            "CREATE OR REPLACE FUNCTION rc4(x NUMBER) RETURNS VARCHAR AS 'CASE WHEN x % 2 = 0 THEN ''even'' ELSE "
                + "''odd'' END'");
        assertAnswer(
            "even|100 ;; odd|100",
            "SELECT rc4(i), COUNT(*) FROM rows200 GROUP BY 1 ORDER BY 1");
        assertAnswer(
            "Function RC5 successfully created.",
            "CREATE OR REPLACE FUNCTION rc5(x NUMBER) RETURNS NUMBER AS 'x + DATEDIFF(day, CURRENT_TIMESTAMP((3)),"
                + " CURRENT_TIMESTAMP((3)))'");
        assertAnswer(
            "19900",
            "SELECT SUM(rc5(i)) FROM rows200");
        assertAnswer(
            "Function RC6 successfully created.",
            "CREATE OR REPLACE FUNCTION rc6(x NUMBER, y NUMBER DEFAULT 10) RETURNS NUMBER AS 'x * y'");
        assertAnswer(
            "199000|39800|59700",
            "SELECT SUM(rc6(i)), SUM(rc6(i, 2)), SUM(rc6(y => 3, x => i)) FROM rows200");
        assertAnswer(
            "Function RC7 successfully created.",
            "CREATE OR REPLACE FUNCTION rc7(s VARCHAR) RETURNS VARCHAR AS 's || ''!'''");
        assertAnswer(
            "200|a\\b0!",
            "SELECT COUNT(DISTINCT rc7('it''s ' || i)), MIN(rc7('a\\\\b' || i)) FROM rows200");
        assertAnswer(
            "15453",
            "SELECT SUM(rc(i)) FROM rows200 WHERE rc(i) > 100");
        assertAnswer(
            "197",
            "SELECT COUNT(*) FROM rows200 a JOIN rows200 b ON rc(a.i) = b.i");
    }
}
