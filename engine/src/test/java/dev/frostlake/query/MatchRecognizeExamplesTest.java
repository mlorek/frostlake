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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The examples the MATCH_RECOGNIZE reference gives, over its stock price history: one summary row for each V
 * shape, every row of each rising run, the runs above the average with empty matches left out and with the
 * unmatched rows kept, and a measure read through a symbol. Every answer is live-verified.
 */
public class MatchRecognizeExamplesTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        engine.execute("INSERT INTO s VALUES (1, 10), (2, 20), (3, 15), (4, 12), (5, 30), (6, 31), (7, 5), (8, 6)");
        engine.execute("CREATE OR REPLACE TABLE stock_price_history (company TEXT, price_date DATE, price INT)");
        engine.execute("""
            INSERT INTO stock_price_history VALUES
            ('ABCD', '2020-10-01', 50), ('XYZ' , '2020-10-01', 89), ('ABCD', '2020-10-02', 36),
            ('XYZ' , '2020-10-02', 24), ('ABCD', '2020-10-03', 39), ('XYZ' , '2020-10-03', 37),
            ('ABCD', '2020-10-04', 42), ('XYZ' , '2020-10-04', 63), ('ABCD', '2020-10-05', 30),
            ('XYZ' , '2020-10-05', 65), ('ABCD', '2020-10-06', 47), ('XYZ' , '2020-10-06', 56),
            ('ABCD', '2020-10-07', 71), ('XYZ' , '2020-10-07', 50), ('ABCD', '2020-10-08', 80),
            ('XYZ' , '2020-10-08', 54), ('ABCD', '2020-10-09', 75), ('XYZ' , '2020-10-09', 30),
            ('ABCD', '2020-10-10', 63), ('XYZ' , '2020-10-10', 32)""");
    }

    @Test
    public void aSummaryRowForEachVShape() {
        assertAnswer(
            "ABCD|1|2020-10-01|2020-10-04|4|1|2 ;; ABCD|2|2020-10-04|2020-10-08|5|1|3 ;; "
                + "XYZ|1|2020-10-01|2020-10-05|5|1|3 ;; XYZ|2|2020-10-05|2020-10-08|4|2|1 ;; "
                + "XYZ|3|2020-10-08|2020-10-10|3|1|1",
            """
                SELECT * FROM stock_price_history
                MATCH_RECOGNIZE(
                PARTITION BY company
                ORDER BY price_date
                MEASURES
                MATCH_NUMBER() AS match_number,
                FIRST(price_date) AS start_date,
                LAST(price_date) AS end_date,
                COUNT(*) AS rows_in_sequence,
                COUNT(row_with_price_decrease.*) AS num_decreases,
                COUNT(row_with_price_increase.*) AS num_increases
                ONE ROW PER MATCH
                AFTER MATCH SKIP TO LAST row_with_price_increase
                PATTERN(row_before_decrease row_with_price_decrease+ row_with_price_increase+)
                DEFINE
                row_with_price_decrease AS price < LAG(price),
                row_with_price_increase AS price > LAG(price)
                )
                ORDER BY company, match_number""");
    }

    @Test
    public void everyRowOfEachRisingRun() {
        assertAnswer(
            "2020-10-02|1|1|36|ANY_ROW ;; 2020-10-03|1|2|39|UP ;; 2020-10-04|1|3|42|UP ;; "
                + "2020-10-05|2|1|30|ANY_ROW ;; 2020-10-06|2|2|47|UP ;; 2020-10-07|2|3|71|UP ;; 2020-10-08|2|4|80|UP",
            """
                select price_date, match_number, msq, price, cl from
                (select * from stock_price_history where company='ABCD') match_recognize(
                order by price_date
                measures
                match_number() as "MATCH_NUMBER",
                match_sequence_number() as msq,
                classifier() as cl
                all rows per match
                pattern(ANY_ROW UP+)
                define
                ANY_ROW AS TRUE,
                UP as price > lag(price)
                )
                order by match_number, msq""");
    }

    @Test
    public void emptyMatchesLeftOutAndUnmatchedRowsKept() {
        assertAnswer(
            "ABCD|2020-10-07|71|7 ;; ABCD|2020-10-08|80|7 ;; ABCD|2020-10-09|75|7 ;; ABCD|2020-10-10|63|7 ;; "
                + "XYZ|2020-10-01|89|1 ;; XYZ|2020-10-04|63|4 ;; XYZ|2020-10-05|65|4 ;; XYZ|2020-10-06|56|4 ;; "
                + "XYZ|2020-10-08|54|6",
            """
                select * from stock_price_history match_recognize(
                partition by company
                order by price_date
                measures
                match_number() as "MATCH_NUMBER"
                all rows per match omit empty matches
                pattern(OVERAVG*)
                define
                OVERAVG as price > avg(price) over (rows between unbounded
                preceding and unbounded following)
                )
                order by company, price_date""");
        assertAnswer(
            "ABCD|2020-10-01|50|null|null ;; ABCD|2020-10-02|36|null|null ;; ABCD|2020-10-03|39|null|null ;; "
                + "ABCD|2020-10-04|42|null|null ;; ABCD|2020-10-05|30|null|null ;; ABCD|2020-10-06|47|null|null "
                + ";; ABCD|2020-10-07|71|1|OVERAVG ;; ABCD|2020-10-08|80|1|OVERAVG ;; "
                + "ABCD|2020-10-09|75|1|OVERAVG ;; ABCD|2020-10-10|63|1|OVERAVG ;; XYZ|2020-10-01|89|1|OVERAVG "
                + ";; XYZ|2020-10-02|24|null|null ;; XYZ|2020-10-03|37|null|null ;; XYZ|2020-10-04|63|2|OVERAVG "
                + ";; XYZ|2020-10-05|65|2|OVERAVG ;; XYZ|2020-10-06|56|2|OVERAVG ;; XYZ|2020-10-07|50|null|null "
                + ";; XYZ|2020-10-08|54|3|OVERAVG ;; XYZ|2020-10-09|30|null|null ;; XYZ|2020-10-10|32|null|null",
            """
                select * from stock_price_history match_recognize(
                partition by company
                order by price_date
                measures
                match_number() as "MATCH_NUMBER",
                classifier() as cl
                all rows per match with unmatched rows
                pattern(OVERAVG+)
                define
                OVERAVG as price > avg(price) over (rows between unbounded
                preceding and unbounded following)
                )
                order by company, price_date""");
    }

    @Test
    public void aMeasureReadThroughASymbol() {
        assertAnswer(
            "ABCD|2020-10-02|36|36|39 ;; ABCD|2020-10-03|39|36|39 ;; ABCD|2020-10-04|42|42|30 ;; "
                + "ABCD|2020-10-05|30|42|30",
            """
                SELECT company, price_date, price, "FINAL FIRST(LT45.price)", "FINAL LAST(LT45.price)"
                FROM stock_price_history
                MATCH_RECOGNIZE (
                PARTITION BY company
                ORDER BY price_date
                MEASURES
                FINAL FIRST(LT45.price) AS "FINAL FIRST(LT45.price)",
                FINAL LAST(LT45.price)  AS "FINAL LAST(LT45.price)"
                ALL ROWS PER MATCH
                AFTER MATCH SKIP PAST LAST ROW
                PATTERN (LT45 LT45)
                DEFINE
                LT45 AS price < 45.00
                )
                WHERE company = 'ABCD'
                ORDER BY price_date""");
    }

    @Test
    public void theSmallestClause() {
        assertAnswer(
            "1 ;; 1",
            "SELECT * FROM FZ MATCH_RECOGNIZE(MEASURES 1 AS m PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer(
            "1 ;; 1",
            "SELECT * FROM FZ MATCH_RECOGNIZE(ORDER BY id MEASURES 1 AS m PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer(
            "1|1|2|2 ;; 2|4|6|3 ;; 3|7|8|2",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, FIRST(id) AS f, "
                + "LAST(id) AS l, COUNT(*) AS c PATTERN (strt up+) DEFINE up AS v > LAG(v)) ORDER BY mn");
        assertAnswer(
            "1|10|1|1|STRT ;; 2|20|1|2|UP ;; 4|12|2|1|STRT ;; 5|30|2|2|UP ;; 6|31|2|3|UP ;; 7|5|3|1|STRT ;; "
                + "8|6|3|2|UP",
            "SELECT id, v, mn, msq, cl FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, "
                + "MATCH_SEQUENCE_NUMBER() AS msq, CLASSIFIER() AS cl ALL ROWS PER MATCH PATTERN (strt up+) "
                + "DEFINE up AS v > LAG(v)) ORDER BY id");
    }
}
