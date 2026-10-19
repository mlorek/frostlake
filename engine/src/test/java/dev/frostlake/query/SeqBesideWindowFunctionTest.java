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
 * The SEQ functions number the rows a query's projection is handed, with a window function in the select
 * list as without one: SEQ1, SEQ2, SEQ4 and SEQ8 read 0, 1, 2 beside SUM(...) OVER (), a SEQ4() in a
 * window's ORDER BY, its PARTITION BY and its argument is the same number as the one in the select list, and
 * a SEQ4() written beside a window call in one item is the row's own. Every answer is live-verified.
 */
public class SeqBesideWindowFunctionTest extends MeasuredCellsTestSupport {

    @Test
    public void aSeqCallReadsTheRowsNumberBesideAWindow() {
        assertAnswer(
            "Table N3 successfully created.",
            "CREATE OR REPLACE TABLE n3 (id INT)");
        assertAnswer(
            "3",
            "INSERT INTO n3 VALUES (10), (20), (30)");
        assertAnswer(
            "10|0 ;; 20|1 ;; 30|2",
            "SELECT id, SEQ4() s FROM n3 ORDER BY id");
        assertAnswer(
            "10|0|0|0|0|60 ;; 20|1|1|1|1|60 ;; 30|2|2|2|2|60",
            "SELECT id, SEQ1() s1, SEQ2() s2, SEQ4() s4, SEQ8() s8, SUM(id) OVER () t FROM n3 ORDER BY id");
        assertAnswer(
            "10|3 ;; 20|2 ;; 30|1",
            "SELECT id, ROW_NUMBER() OVER (ORDER BY SEQ4() DESC) rn FROM n3 ORDER BY id");
        assertAnswer(
            "0|3 ;; 1|3 ;; 2|3",
            "SELECT SEQ4() s, SUM(SEQ4()) OVER () t FROM TABLE(GENERATOR(ROWCOUNT => 3)) ORDER BY 1");
        assertAnswer(
            "0|1 ;; 1|2 ;; 2|3 ;; 3|4",
            "SELECT SEQ4() s, ROW_NUMBER() OVER (ORDER BY SEQ4()) rn FROM TABLE(GENERATOR(ROWCOUNT => 4)) ORDER "
                + "BY 1");
        assertAnswer(
            "0|3 ;; 1|2 ;; 2|3 ;; 3|2 ;; 4|3",
            "SELECT SEQ4() s, COUNT(*) OVER (PARTITION BY MOD(SEQ4(), 2)) c FROM TABLE(GENERATOR(ROWCOUNT => 5)) "
                + "ORDER BY 1");
        assertAnswer(
            "0|null ;; 1|0 ;; 2|1",
            "SELECT SEQ4() s, LAG(SEQ4()) OVER (ORDER BY SEQ4()) p FROM TABLE(GENERATOR(ROWCOUNT => 3)) ORDER BY 1");
        assertAnswer(
            "100|1 ;; 101|2 ;; 102|3",
            "SELECT SEQ4() + 100 s, SUM(1) OVER (ORDER BY SEQ4() ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT "
                + "ROW) r FROM TABLE(GENERATOR(ROWCOUNT => 3)) ORDER BY 1");
        assertAnswer(
            "0|3 ;; 1|2 ;; 2|1",
            "SELECT s, rn FROM (SELECT SEQ4() s, ROW_NUMBER() OVER (ORDER BY SEQ4() DESC) rn FROM "
                + "TABLE(GENERATOR(ROWCOUNT => 3))) ORDER BY 1");
        assertAnswer(
            "0 ;; 1",
            "SELECT SEQ4() s FROM TABLE(GENERATOR(ROWCOUNT => 4)) QUALIFY ROW_NUMBER() OVER (ORDER BY SEQ4() "
                + "DESC) <= 2 ORDER BY 1");
        assertAnswer(
            "0|0|1 ;; 1|1|1 ;; 2|2|1",
            "SELECT SEQ4() s, SEQ4() s2, RANK() OVER (ORDER BY 1) r FROM TABLE(GENERATOR(ROWCOUNT => 3)) ORDER BY "
                + "1");
        assertAnswer(
            "0|2 ;; 1|2 ;; 2|2",
            "SELECT SEQ8() s, FIRST_VALUE(SEQ8()) OVER (ORDER BY SEQ8() DESC) f FROM TABLE(GENERATOR(ROWCOUNT => "
                + "3)) ORDER BY 1");
        assertAnswer(
            "20|0|20 ;; 30|1|50",
            "SELECT id, SEQ4() s, SUM(id) OVER (ORDER BY id) r FROM n3 WHERE id > 10 ORDER BY id");
        assertAnswer(
            "1|0|2 ;; 1|1|2",
            "SELECT UNIFORM(1, 1, RANDOM()) u, SEQ4() s, COUNT(*) OVER () c FROM TABLE(GENERATOR(ROWCOUNT => 2)) "
                + "ORDER BY 2");
        assertAnswer(
            "0|0 ;; 1|1 ;; 2|3",
            "SELECT SEQ4() s, SUM(SEQ4()) OVER (ORDER BY SEQ4()) c FROM TABLE(GENERATOR(ROWCOUNT => 3)) ORDER BY 1");
        assertAnswer(
            "true ;; true ;; true",
            "SELECT ROW_NUMBER() OVER (ORDER BY SEQ4()) - 1 = SEQ4() same FROM TABLE(GENERATOR(ROWCOUNT => 3)) "
                + "ORDER BY 1");
    }
}
