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
 * A USING or NATURAL join matches its keys by equality: a NULL key joins nothing, another NULL key included, so
 * an inner join drops such rows and an outer join keeps each side's unmatched; two numbers are equal by value
 * whatever scale each is held at, and a text that spells the number joins it (live-verified).
 */
public class UsingJoinKeyEqualityTest extends MeasuredCellsTestSupport {

    @Test
    public void nullKeysJoinNothing() {
        answer(
            "CREATE OR REPLACE TABLE ja (k INT, a INT)");
        answer(
            "INSERT INTO ja VALUES (1, 10), (NULL, 20)");
        answer(
            "CREATE OR REPLACE TABLE jb (k INT, b INT)");
        answer(
            "INSERT INTO jb VALUES (1, 100), (NULL, 200)");
        answer(
            "CREATE OR REPLACE TABLE jc (k VARCHAR, c INT)");
        answer(
            "INSERT INTO jc VALUES ('1', 5), (NULL, 6)");
        answer(
            "CREATE OR REPLACE TABLE jd (k NUMBER(5,2), d INT)");
        answer(
            "INSERT INTO jd VALUES (1.00, 7), (NULL, 8)");
        answer(
            "CREATE OR REPLACE TABLE tn (s VARCHAR(10), n NUMBER(10,2))");
        answer(
            "INSERT INTO tn VALUES (NULL, 1.5)");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM ja JOIN jb USING (k)");
        assertAnswer(
            "1|10|100",
            "SELECT * FROM ja JOIN jb USING (k) ORDER BY 1");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM ja NATURAL JOIN jb");
        assertAnswer(
            "1|10|100 ;; null|20|null",
            "SELECT k, a, b FROM ja LEFT JOIN jb USING (k) ORDER BY a");
        assertAnswer(
            "1|10|100 ;; null|null|200",
            "SELECT k, a, b FROM ja RIGHT JOIN jb USING (k) ORDER BY b");
        assertAnswer(
            "1|10|100 ;; null|20|null ;; null|null|200",
            "SELECT k, a, b FROM ja FULL JOIN jb USING (k) ORDER BY a NULLS LAST, b");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM ja JOIN jb ON ja.k = jb.k");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM ja a1 JOIN ja a2 USING (k, a)");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM tn a JOIN tn b USING (s)");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM ja JOIN jb USING (k) WHERE k IS NULL");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM ja NATURAL LEFT JOIN jb");
        assertAnswer(
            "10|100 ;; 20|null ;; null|200",
            "SELECT a, b FROM ja NATURAL FULL JOIN jb ORDER BY a NULLS LAST, b");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM ja, jb WHERE ja.k = jb.k");
    }

    @Test
    public void numbersJoinByValue() {
        answer(
            "CREATE OR REPLACE TABLE ja (k INT, a INT)");
        answer(
            "INSERT INTO ja VALUES (1, 10), (NULL, 20)");
        answer(
            "CREATE OR REPLACE TABLE jb (k INT, b INT)");
        answer(
            "INSERT INTO jb VALUES (1, 100), (NULL, 200)");
        answer(
            "CREATE OR REPLACE TABLE jc (k VARCHAR, c INT)");
        answer(
            "INSERT INTO jc VALUES ('1', 5), (NULL, 6)");
        answer(
            "CREATE OR REPLACE TABLE jd (k NUMBER(5,2), d INT)");
        answer(
            "INSERT INTO jd VALUES (1.00, 7), (NULL, 8)");
        answer(
            "CREATE OR REPLACE TABLE tn (s VARCHAR(10), n NUMBER(10,2))");
        answer(
            "INSERT INTO tn VALUES (NULL, 1.5)");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM ja JOIN jc USING (k)");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM ja JOIN jd USING (k)");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM ja JOIN jb USING (k) JOIN jd USING (k)");
    }
}
