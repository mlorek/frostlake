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
 * A value IN a query compares with each member as value = member does: a number against text members reads
 * them as numbers - every one of them, so one that reads as none refuses the statement wherever the match
 * stands - a date against a text reads it as a date, and the members compare under the collation the value and
 * the query's column settle on; NOT IN inverts the same answer (live-verified).
 */
public class InSubqueryConversionTest extends MeasuredCellsTestSupport {

    @Test
    public void membersConvertAsEqualityDoes() {
        try {
            assertAnswer(
                "Table N successfully created.",
                "CREATE OR REPLACE TABLE n (k INT)");
            assertAnswer(
                "Table S successfully created.",
                "CREATE OR REPLACE TABLE s (t VARCHAR)");
            assertAnswer(
                "2",
                "INSERT INTO n VALUES (2), (10)");
            assertAnswer(
                "2",
                "INSERT INTO s VALUES ('02'), ('10.0')");
            assertAnswer(
                "2|true|true|true ;; 10|true|true|true",
                "SELECT k, k IN (SELECT t FROM s) AS in_subquery, k = ANY (SELECT t FROM s) AS eq_any, k IN ('02',"
                    + " '10.0') AS in_list FROM n ORDER BY k");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM n WHERE k IN (SELECT t FROM s)");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM n WHERE k NOT IN (SELECT t FROM s)");
            assertAnswer(
                "02|true ;; 10.0|true",
                "SELECT t, t IN (SELECT k FROM n) FROM s ORDER BY t");
            assertAnswer(
                "1",
                "INSERT INTO s VALUES ('x')");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT COUNT(*) FROM n WHERE k IN (SELECT t FROM s)");
            assertAnswer(
                "Table DD successfully created.",
                "CREATE OR REPLACE TABLE dd (d DATE)");
            assertAnswer(
                "1",
                "INSERT INTO dd VALUES ('2024-01-02')");
            assertAnswer(
                "true",
                "SELECT d IN (SELECT '2024-01-02') FROM dd");
            assertAnswer(
                "true",
                "SELECT d IN (SELECT '2024-1-2') FROM dd");
            assertAnswer(
                "true",
                "SELECT 'Abc' IN (SELECT 'abc' COLLATE 'en-ci')");
            assertAnswer(
                "true",
                "SELECT 'Abc' COLLATE 'en-ci' IN (SELECT 'abc')");
            assertAnswer(
                "true",
                "SELECT 1.0 IN (SELECT 1)");
            assertAnswer(
                "true",
                "SELECT 1 IN (SELECT 1.000::FLOAT)");
            assertAnswer(
                "true",
                "SELECT 2 IN (SELECT '2e0')");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM n WHERE (k, k) IN (SELECT t, t FROM s WHERE t <> 'x')");
            assertAnswer(
                "true",
                "SELECT TRUE IN (SELECT 'true')");
            assertAnswer(
                "false",
                "SELECT 'a ' IN (SELECT 'a')");
        } finally {
            answer(
                "DROP TABLE IF EXISTS n");
            answer(
                "DROP TABLE IF EXISTS s");
            answer(
                "DROP TABLE IF EXISTS dd");
        }
    }
}
