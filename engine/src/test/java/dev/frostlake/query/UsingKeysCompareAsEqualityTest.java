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
 * A join written with USING or NATURAL matches its keys as the ON form's = does: a NULL key joins nothing, in
 * the outer joins too, numbers are equal by value whatever their types, two texts compare under the collation
 * their columns carry, and a text key against a number is read as a number, a text that reads as none refusing
 * the statement (live-verified).
 */
public class UsingKeysCompareAsEqualityTest extends MeasuredCellsTestSupport {

    @Test
    public void keysCompareAsEqualityDoes() {
        try {
            assertAnswer(
                "Table A successfully created.",
                "CREATE OR REPLACE TABLE a (k INT, v VARCHAR)");
            assertAnswer(
                "Table B successfully created.",
                "CREATE OR REPLACE TABLE b (k INT, w VARCHAR)");
            assertAnswer(
                "2",
                "INSERT INTO a VALUES (1, 'a1'), (NULL, 'a-null')");
            assertAnswer(
                "2",
                "INSERT INTO b VALUES (1, 'b1'), (NULL, 'b-null')");
            assertAnswer(
                "1|a1|b1",
                "SELECT * FROM a JOIN b USING (k) ORDER BY v");
            assertAnswer(
                "1|a1|b1",
                "SELECT * FROM a NATURAL JOIN b ORDER BY v");
            assertAnswer(
                "1|a1|1|b1",
                "SELECT * FROM a JOIN b ON a.k = b.k ORDER BY v");
            assertAnswer(
                "null|a-null|null ;; 1|a1|b1",
                "SELECT * FROM a LEFT JOIN b USING (k) ORDER BY v");
            assertAnswer(
                "null|null|b-null ;; 1|a1|b1",
                "SELECT * FROM a RIGHT JOIN b USING (k) ORDER BY w");
            assertAnswer(
                "null|a-null|null ;; 1|a1|b1 ;; null|null|b-null",
                "SELECT * FROM a FULL JOIN b USING (k) ORDER BY v, w");
            assertAnswer(
                "null|a-null|null ;; 1|a1|b1",
                "SELECT * FROM a NATURAL LEFT JOIN b ORDER BY v");
            assertAnswer(
                "null|a-null|null ;; 1|a1|b1 ;; null|null|b-null",
                "SELECT * FROM a NATURAL FULL JOIN b ORDER BY v, w");
            assertAnswer(
                "Table D successfully created.",
                "CREATE OR REPLACE TABLE d (k NUMBER(10,2), x VARCHAR)");
            assertAnswer(
                "Table F successfully created.",
                "CREATE OR REPLACE TABLE f (k FLOAT, y VARCHAR)");
            assertAnswer(
                "1",
                "INSERT INTO d VALUES (1.00, 'd1')");
            assertAnswer(
                "1",
                "INSERT INTO f VALUES (1, 'f1')");
            assertAnswer(
                "1|a1|d1",
                "SELECT * FROM a JOIN d USING (k)");
            assertAnswer(
                "1|a1|f1",
                "SELECT * FROM a JOIN f USING (k)");
            assertAnswer(
                "1|a1|1.00|d1",
                "SELECT * FROM a JOIN d ON a.k = d.k");
            assertAnswer(
                "1.00|d1|f1",
                "SELECT * FROM d NATURAL JOIN f");
            assertAnswer(
                "Table CI1 successfully created.",
                "CREATE OR REPLACE TABLE ci1 (k VARCHAR COLLATE 'en-ci', c VARCHAR)");
            assertAnswer(
                "Table CI2 successfully created.",
                "CREATE OR REPLACE TABLE ci2 (k VARCHAR COLLATE 'en-ci', e VARCHAR)");
            assertAnswer(
                "1",
                "INSERT INTO ci1 VALUES ('Abc', 'c1')");
            assertAnswer(
                "1",
                "INSERT INTO ci2 VALUES ('abc', 'e1')");
            assertAnswer(
                "Abc|c1|e1",
                "SELECT * FROM ci1 JOIN ci2 USING (k)");
            assertAnswer(
                "Abc|c1|abc|e1",
                "SELECT * FROM ci1 JOIN ci2 ON ci1.k = ci2.k");
            assertAnswer(
                "Abc|c1|e1",
                "SELECT * FROM ci1 NATURAL JOIN ci2");
            assertAnswer(
                "Table SV successfully created.",
                "CREATE OR REPLACE TABLE sv (k VARCHAR, s VARCHAR)");
            assertAnswer(
                "3",
                "INSERT INTO sv VALUES ('1', 's1'), ('01', 's01'), ('a', 'sa')");
            assertAnswer(
                "REFUSED Numeric value 'a' is not recognized",
                "SELECT * FROM a JOIN sv USING (k) ORDER BY s");
            assertAnswer(
                "Table P1 successfully created.",
                "CREATE OR REPLACE TABLE p1 (k VARCHAR, m INT)");
            assertAnswer(
                "Table P2 successfully created.",
                "CREATE OR REPLACE TABLE p2 (k VARCHAR, n INT)");
            assertAnswer(
                "2",
                "INSERT INTO p1 VALUES ('a', 1), ('a ', 2)");
            assertAnswer(
                "1",
                "INSERT INTO p2 VALUES ('a', 3)");
            assertAnswer(
                "a|1|3",
                "SELECT * FROM p1 JOIN p2 USING (k) ORDER BY m");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM a JOIN b USING (k)");
        } finally {
            answer(
                "DROP TABLE IF EXISTS a");
            answer(
                "DROP TABLE IF EXISTS b");
            answer(
                "DROP TABLE IF EXISTS d");
            answer(
                "DROP TABLE IF EXISTS f");
            answer(
                "DROP TABLE IF EXISTS ci1");
            answer(
                "DROP TABLE IF EXISTS ci2");
            answer(
                "DROP TABLE IF EXISTS sv");
            answer(
                "DROP TABLE IF EXISTS p1");
            answer(
                "DROP TABLE IF EXISTS p2");
        }
    }
}
