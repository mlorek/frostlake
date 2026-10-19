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
 * UNION [ALL] BY NAME types each result column by the arms' columns of its name wherever they stand - an arm
 * without the name adds only NULLs - so a VARCHAR column stays VARCHAR, a DATE and a NUMBER at swapped
 * positions combine, a table created from it keeps those types, and the arms' values take the folded type
 * (live-verified).
 */
public class UnionByNameTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void columnsAreTypedByName() {
        try {
            assertAnswer(
                "Table L successfully created.",
                "CREATE OR REPLACE TABLE l (a INT, b VARCHAR)");
            assertAnswer(
                "Table R successfully created.",
                "CREATE OR REPLACE TABLE r (a INT, b VARCHAR)");
            assertAnswer(
                "1",
                "INSERT INTO l VALUES (1, 'x')");
            assertAnswer(
                "1",
                "INSERT INTO r VALUES (2, 'y')");
            assertAnswer(
                "1|x ;; 2|y",
                "SELECT a, b FROM l UNION ALL BY NAME SELECT b, a FROM r ORDER BY a");
            assertShape(
                "A:NUMBER(38,0), B:VARCHAR(16777216)",
                "SELECT a, b FROM l UNION ALL BY NAME SELECT b, a FROM r");
            assertAnswer(
                "VARCHAR(16777216)[LOB] ;; VARCHAR(16777216)[LOB]",
                "SELECT SYSTEM$TYPEOF(b) FROM (SELECT a, b FROM l UNION ALL BY NAME SELECT b, a FROM r)");
            assertAnswer(
                "Table U successfully created.",
                "CREATE OR REPLACE TABLE u AS SELECT a, b FROM l UNION ALL BY NAME SELECT b, a FROM r");
            assertAnswer(
                "A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                    + "B|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE TABLE u");
            assertAnswer(
                "1|x ;; 2|y",
                "SELECT * FROM u ORDER BY a");
            assertAnswer(
                "1 ;; 2",
                "SELECT a + 0 FROM u ORDER BY 1");
            assertAnswer(
                "Table DT successfully created.",
                "CREATE OR REPLACE TABLE dt (d DATE, n NUMBER(5,2))");
            assertAnswer(
                "1",
                "INSERT INTO dt VALUES ('2024-01-02', 1.5)");
            assertAnswer(
                "2024-01-02|1.50 ;; 2024-01-02|1.50",
                "SELECT d, n FROM dt UNION ALL BY NAME SELECT n, d FROM dt");
            assertShape(
                "D:DATE, N:NUMBER(5,2)",
                "SELECT d, n FROM dt UNION ALL BY NAME SELECT n, d FROM dt");
            assertAnswer(
                "1|x ;; 2|y",
                "SELECT a, b FROM l UNION BY NAME SELECT b, a FROM r ORDER BY a");
            assertShape(
                "A:NUMBER(38,0), B:VARCHAR(16777216)",
                "SELECT a, b FROM l UNION BY NAME SELECT b, a FROM r");
            assertShape(
                "A:NUMBER(38,0), B:VARCHAR(16777216)",
                "SELECT a FROM l UNION ALL BY NAME SELECT b FROM r");
            assertAnswer(
                "1|x ;; null|y",
                "SELECT a, b FROM l UNION ALL BY NAME SELECT b FROM r ORDER BY a");
            assertShape(
                "X:NUMBER(2,1), Y:VARCHAR(1)",
                "SELECT 1 AS x, 's' AS y UNION ALL BY NAME SELECT 't' AS y, 2.5 AS x");
            assertAnswer(
                "1.0|s ;; 2.5|t",
                "SELECT 1 AS x, 's' AS y UNION ALL BY NAME SELECT 't' AS y, 2.5 AS x ORDER BY x");
            assertShape(
                "REFUSED Numeric value 'q' is not recognized",
                "SELECT 1 AS x UNION ALL BY NAME SELECT 'q' AS x");
            assertAnswer(
                "REFUSED Numeric value 'q' is not recognized",
                "SELECT 1 AS x UNION ALL BY NAME SELECT 'q' AS x");
        } finally {
            answer(
                "DROP TABLE IF EXISTS l");
            answer(
                "DROP TABLE IF EXISTS r");
            answer(
                "DROP TABLE IF EXISTS u");
            answer(
                "DROP TABLE IF EXISTS dt");
        }
    }
}
