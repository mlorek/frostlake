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
 * A set operation's later branch and a VALUES clause's later row give their values in the column's type, not
 * their own. Under a BOOLEAN column a number is FALSE for zero and TRUE for anything else in a set operation,
 * and in VALUES only 0 and 1 convert - any other number fails its row, where it is read. A VALUES column of
 * numbers holds every row at the folded scale, an exact number beside a FLOAT is a FLOAT, a text beside a
 * number is read as one or refused on its row, and a BOOLEAN after a number is no type the clause folds. The
 * duplicates a UNION, an INTERSECT and an EXCEPT judge are the converted values. Every answer is
 * live-verified.
 */
public class FoldedColumnValuesTest extends MeasuredCellsTestSupport {

    @Test
    public void laterBranchesAndRows() {
        engine.execute(
            "CREATE OR REPLACE TABLE tb (b BOOLEAN, n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tb VALUES (TRUE, 1.5, 0)");
        try {
            assertAnswer(
                "true|true ;; null|null",
                "SELECT a, TO_VARCHAR(a) FROM (SELECT NULL::BOOLEAN AS a UNION ALL SELECT 1) ORDER BY a");
            assertAnswer(
                "false ;; true",
                "SELECT a FROM (SELECT TRUE AS a UNION ALL SELECT 0) ORDER BY a");
            assertAnswer(
                "false ;; true ;; null",
                "SELECT a FROM (SELECT NULL::BOOLEAN AS a UNION SELECT 1 UNION SELECT 0) ORDER BY a");
            assertAnswer(
                "1.00 ;; 1.50",
                "SELECT $1 FROM VALUES (1.50::NUMBER(10,2)), (1) ORDER BY 1");
            assertAnswer(
                "1.00|1.00 ;; null|null",
                "SELECT $1, TO_VARCHAR($1) FROM VALUES (NULL::NUMBER(10,2)), (1) ORDER BY 1");
            assertAnswer(
                "true ;; true",
                "SELECT $1 FROM VALUES (TRUE), (1)");
            assertAnswer(
                "REFUSED Boolean value '2' is not recognized",
                "SELECT $1 FROM VALUES (TRUE), (2)");
            assertAnswer(
                "true ;; true",
                "SELECT a FROM (SELECT TRUE AS a UNION ALL SELECT 2)");
            assertAnswer(
                "true ;; true",
                "SELECT a FROM (SELECT TRUE AS a UNION ALL SELECT 1.5)");
            assertAnswer(
                "true ;; true",
                "SELECT a FROM (SELECT TRUE AS a UNION ALL SELECT 'yes')");
            assertAnswer(
                "REFUSED Boolean value 'x' is not recognized",
                "SELECT a FROM (SELECT TRUE AS a UNION ALL SELECT 'x')");
            assertAnswer(
                "REFUSED SQL compilation error: | inconsistent data type for result columns for set operator "
                    + "input branches, expected BOOLEAN, got NUMBER(1,0)",
                "SELECT a FROM (SELECT 1 AS a UNION ALL SELECT TRUE) ORDER BY a");
            assertAnswer(
                "REFUSED SQL compilation error: | inconsistent data type for result columns for set operator "
                    + "input branches, expected BOOLEAN, got NUMBER(1,0)",
                "SELECT a FROM (SELECT 0 AS a UNION ALL SELECT FALSE)");
            assertAnswer(
                "1.00 ;; 1.50",
                "SELECT a FROM (SELECT 1.50::NUMBER(10,2) AS a UNION ALL SELECT 1) ORDER BY a");
            assertAnswer(
                "1.00 ;; 1.50",
                "SELECT a FROM (SELECT 1 AS a UNION ALL SELECT 1.50::NUMBER(10,2)) ORDER BY a");
            assertAnswer(
                "1.000 ;; 2.555",
                "SELECT a FROM (SELECT 1::NUMBER(10,2) AS a UNION ALL SELECT 2.555) ORDER BY a");
            assertAnswer(
                "1.00 ;; 1.50",
                "SELECT $1 FROM VALUES (1), (1.50::NUMBER(10,2)) ORDER BY 1");
            assertAnswer(
                "1.50 ;; 2.00 ;; 3.25",
                "SELECT $1 FROM VALUES (1.5), (2), (3.25) ORDER BY 1");
            assertAnswer(
                "REFUSED Numeric value 'a' is not recognized",
                "SELECT $1 FROM VALUES ('a'), (1) ORDER BY 1");
            assertAnswer(
                "1 ;; 2",
                "SELECT $1 FROM VALUES (1), ('2') ORDER BY 1");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "SELECT $1 FROM VALUES (1), ('x')");
            assertAnswer(
                "1.5 ;; 2.0 ;; null",
                "SELECT $1 FROM VALUES (NULL), (1.5), (2) ORDER BY 1");
            assertAnswer(
                "1.0 ;; 2.0",
                "SELECT $1 FROM VALUES (1::FLOAT), (2) ORDER BY 1");
            assertAnswer(
                "1.0 ;; 2.0",
                "SELECT $1 FROM VALUES (1), (2::FLOAT) ORDER BY 1");
            assertAnswer(
                "2024-01-01 ;; 2024-02-01",
                "SELECT $1 FROM VALUES ('2024-01-01'::DATE), ('2024-02-01') ORDER BY 1");
            assertAnswer(
                "1.0|true ;; 2.5|false",
                "SELECT $1, $2 FROM VALUES (1, TRUE), (2.5, 0) ORDER BY 1");
            assertShape(
                "$1:NUMBER(10,2)",
                "SELECT $1 FROM VALUES (1.50::NUMBER(10,2)), (1)");
            assertShape(
                "$1:BOOLEAN",
                "SELECT $1 FROM VALUES (TRUE), (1)");
            assertShape(
                "A:BOOLEAN",
                "SELECT a FROM (SELECT NULL::BOOLEAN AS a UNION ALL SELECT 1)");
            assertShape(
                "$1:NUMBER(2,1), $2:BOOLEAN",
                "SELECT $1, $2 FROM VALUES (1, TRUE), (2.5, 0)");
            assertAnswer(
                "true",
                "SELECT a FROM (SELECT NULL::BOOLEAN AS a UNION ALL SELECT 1) WHERE a = TRUE");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM (SELECT NULL::BOOLEAN AS a UNION ALL SELECT 1 UNION ALL SELECT 0) WHERE a");
            assertAnswer(
                "true",
                "SELECT TRUE INTERSECT SELECT 1");
            assertAnswer(
                "",
                "SELECT TRUE EXCEPT SELECT 1");
            assertAnswer(
                "1.50",
                "SELECT 1.50::NUMBER(10,2) INTERSECT SELECT 1.5");
            assertAnswer(
                "true",
                "SELECT TRUE UNION SELECT 1");
            assertAnswer(
                "1.50",
                "SELECT 1.50::NUMBER(10,2) UNION SELECT 1.5");
            assertAnswer(
                "Table TU successfully created.",
                "CREATE OR REPLACE TABLE tu AS SELECT a FROM (SELECT NULL::BOOLEAN AS a UNION ALL SELECT 1)");
            assertAnswer(
                "true|BOOLEAN[SB1] ;; null|BOOLEAN[SB1]",
                "SELECT a, SYSTEM$TYPEOF(a) FROM tu ORDER BY a");
            assertAnswer(
                "true ;; true",
                "SELECT b FROM tb UNION ALL SELECT 1");
            assertAnswer(
                "1.00 ;; 1.50",
                "SELECT n FROM tb UNION ALL SELECT 1 ORDER BY 1");
            assertAnswer(
                "1.00 ;; 1.50",
                "SELECT 1 UNION ALL SELECT n FROM tb ORDER BY 1");
            assertAnswer(
                "true ;; true",
                "SELECT b FROM tb UNION ALL SELECT n FROM tb");
            assertAnswer(
                "false ;; true",
                "SELECT b FROM tb UNION ALL SELECT i FROM tb ORDER BY 1");
            assertAnswer(
                "REFUSED SQL compilation error: | inconsistent data type for result columns for set operator "
                    + "input branches, expected BOOLEAN, got NUMBER(38,0)",
                "SELECT i FROM tb UNION ALL SELECT b FROM tb ORDER BY 1");
            assertAnswer(
                "false|BOOLEAN[SB1] ;; true|BOOLEAN[SB1]",
                "SELECT a, SYSTEM$TYPEOF(a) FROM (SELECT TRUE AS a UNION ALL SELECT 0) ORDER BY a");
            assertAnswer(
                "2.00 ;; 2.50",
                "SELECT a + 1 FROM (SELECT 1.50::NUMBER(10,2) AS a UNION ALL SELECT 1) ORDER BY 1");
            assertAnswer(
                "false ;; true ;; null",
                "SELECT $1 FROM VALUES (TRUE), (NULL), (0) ORDER BY 1");
            assertAnswer(
                "1.500 ;; 2.555",
                "SELECT $1 FROM VALUES (1.5::NUMBER(10,2)), (2.555) ORDER BY 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid data type [BOOLEAN] in VALUES clause",
                "SELECT $1 FROM VALUES (0), (TRUE) ORDER BY 1");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void moreBranchesAndRows() {
        engine.execute(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        engine.execute(
            "INSERT INTO tp VALUES (1, 10), (2, 20), (3, 30)");
        engine.execute(
            "CREATE OR REPLACE TABLE tb (b BOOLEAN, n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tb VALUES (TRUE, 1.5, 0)");
        engine.execute(
            "CREATE OR REPLACE TABLE tv (n NUMBER(10,2))");
        try {
            assertShape(
                "A:NUMBER(2,1)",
                "SELECT NULL::VARCHAR(10) AS a UNION ALL SELECT 5 UNION ALL SELECT 6.5");
            assertShape(
                "REFUSED Numeric value 'a' is not recognized",
                "SELECT NULL::VARCHAR(10) AS a UNION ALL SELECT 5 UNION ALL SELECT 'a'");
            assertShape(
                "A:NUMBER(1,0)",
                "SELECT 5 AS a UNION ALL SELECT NULL::VARCHAR(10) UNION ALL SELECT NULL");
            assertShape(
                "$1:NUMBER(2,1)",
                "SELECT $1 FROM VALUES (NULL::VARCHAR(10)), (5), (6.5)");
            assertAnswer(
                "REFUSED Numeric value 'a' is not recognized",
                "SELECT $1 FROM VALUES (NULL::VARCHAR(10)), (5), ('a')");
            assertAnswer(
                "5.0 ;; 6.5 ;; null",
                "SELECT $1 FROM VALUES (NULL::VARCHAR(10)), (5), (6.5) ORDER BY 1");
            assertAnswer(
                "5.0 ;; 6.5 ;; null",
                "SELECT a FROM (SELECT NULL::VARCHAR(10) AS a UNION ALL SELECT 5 UNION ALL SELECT 6.5) ORDER BY a");
            assertAnswer(
                "REFUSED Boolean value 'x' is not recognized",
                "SELECT $1 FROM VALUES (TRUE), ('x')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid data type [NUMBER(1,0)] in VALUES clause",
                "SELECT $1 FROM VALUES ('2024-01-01'::DATE), (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid data type [BOOLEAN] in VALUES clause",
                "SELECT $1 FROM VALUES (1.5::FLOAT), (TRUE)");
            assertAnswer(
                "true ;; null",
                "SELECT a FROM (SELECT TRUE AS a UNION ALL SELECT NULL::INT) ORDER BY a");
            assertAnswer(
                "true ;; true",
                "SELECT a FROM (SELECT TRUE AS a UNION ALL SELECT -1)");
            assertAnswer(
                "REFUSED Boolean value '-1' is not recognized",
                "SELECT $1 FROM VALUES (TRUE), (-1)");
            assertAnswer(
                "true ;; true",
                "SELECT $1 FROM VALUES (TRUE), (1.0)");
            assertAnswer(
                "REFUSED Boolean value '1.5' is not recognized",
                "SELECT $1 FROM VALUES (TRUE), (1.5)");
            assertAnswer(
                "true",
                "SELECT b FROM tb UNION SELECT 1");
            assertAnswer(
                "false ;; true",
                "SELECT b FROM tb UNION SELECT 0 ORDER BY 1");
            assertAnswer(
                "true",
                "SELECT b FROM tb INTERSECT SELECT 5");
            assertAnswer(
                "",
                "SELECT b FROM tb EXCEPT SELECT 5");
            assertAnswer(
                "REFUSED SQL compilation error: | inconsistent data type for result columns for set operator "
                    + "input branches, expected BOOLEAN, got FLOAT",
                "SELECT a FROM (SELECT 1.5::FLOAT AS a UNION ALL SELECT TRUE)");
            assertAnswer(
                "false ;; true ;; true",
                "SELECT a FROM (SELECT TRUE AS a UNION ALL SELECT 1 UNION ALL SELECT 'no') ORDER BY a");
            assertAnswer(
                "1.00 ;; 1.50",
                "WITH c AS (SELECT * FROM VALUES (1.50::NUMBER(10,2)), (1)) SELECT $1 FROM c ORDER BY 1");
            assertAnswer(
                "1.00 ;; 1.50",
                "SELECT column1 FROM (VALUES (1.50::NUMBER(10,2)), (1)) ORDER BY 1");
            assertAnswer(
                "1.5 ;; 2.0",
                "SELECT x FROM (VALUES (1.5), (2)) AS t (x) ORDER BY 1");
            assertAnswer(
                "2",
                "INSERT INTO tv SELECT $1 FROM VALUES (1.5), (2)");
            assertAnswer(
                "1.50 ;; 2.00",
                "SELECT n FROM tv ORDER BY n");
            assertAnswer(
                "2.50|1.50|2",
                "SELECT SUM($1), MAX($1), COUNT($1) FROM VALUES (1.50::NUMBER(10,2)), (1)");
            assertAnswer(
                "2.5 ;; 3.0",
                "SELECT $1 + 1 FROM VALUES (1.5), (2) ORDER BY 1");
            assertAnswer(
                "1.000 ;; 2.500 ;; 3.125",
                "SELECT $1 FROM VALUES (1), (2.50), (3.125) ORDER BY 1");
            assertAnswer(
                "1.5 ;; 2.0",
                "SELECT $1 FROM VALUES ('1.5'), (2) ORDER BY 1");
            assertAnswer(
                "1.0|a ;; 2.5|bb",
                "SELECT $1, $2 FROM VALUES (1, 'a'), (2.5, 'bb') ORDER BY 1");
            assertAnswer(
                "true ;; null",
                "SELECT $1 FROM VALUES (TRUE), (NULL::INT) ORDER BY 1");
            assertAnswer(
                "true ;; null",
                "SELECT $1 FROM VALUES (NULL::BOOLEAN), (1) ORDER BY 1");
            assertAnswer(
                "REFUSED Boolean value '2' is not recognized",
                "SELECT $1 FROM VALUES (NULL::BOOLEAN), (2)");
            assertShape(
                "$1:NUMBER(2,1)",
                "SELECT $1 FROM VALUES ('1.5'), (2)");
            assertShape(
                "$1:NUMBER(4,3)",
                "SELECT $1 FROM VALUES (1), (2.50), (3.125)");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
