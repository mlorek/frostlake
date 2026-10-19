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
 * The columns a MATCH_RECOGNIZE relation answers. ONE ROW PER MATCH answers the PARTITION BY values, each
 * named as it is written, and the measures; ALL ROWS PER MATCH answers the source's columns and the measures.
 * MATCH_NUMBER, MATCH_SEQUENCE_NUMBER and COUNT are NUMBER(18,0), CLASSIFIER is a VARCHAR, and every other
 * measure has the type its expression has over a window. A clause that answers no column at all is refused, and
 * so is a column named twice under ONE ROW PER MATCH. Every answer is live-verified.
 */
public class MatchRecognizeColumnsTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE n (id INT, k VARCHAR, v INT)");
        engine.execute("INSERT INTO n VALUES (1, 'x', NULL), (2, 'x', 3), (3, 'y', 3), (NULL, 'y', 4), (5, 'x', NULL)");
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
        engine.execute("CREATE OR REPLACE TABLE t (g VARCHAR, id INT, v INT, w FLOAT, d DATE, x VARCHAR)");
        engine.execute("""
            INSERT INTO t VALUES ('a', 1, 1, 1.5, '2024-01-01', 'p'), ('a', 2, 2, 2.5, '2024-01-02', 'q'),
            ('a', 3, NULL, NULL, NULL, NULL), ('a', 4, 4, 0.5, '2024-01-04', 'r'), ('b', 1, 9, 9.0, '2024-02-01', 's'),
            ('b', 2, 8, 8.0, '2024-02-02', 't'), (NULL, 1, 7, 7.0, '2024-03-01', 'u')""");
    }

    @Test
    public void oneRowPerMatchAnswersThePartitionAndTheMeasures() {
        assertShape(
            "COMPANY:VARCHAR(16777216), MATCH_NUMBER:NUMBER(18,0), START_DATE:DATE, "
                + "ROWS_IN_SEQUENCE:NUMBER(18,0), CL:VARCHAR(134217728), TOTAL:NUMBER(38,0), MEAN:NUMBER(38,3)",
            """
                SELECT * FROM stock_price_history
                MATCH_RECOGNIZE(
                PARTITION BY company
                ORDER BY price_date
                MEASURES
                MATCH_NUMBER() AS match_number,
                FIRST(price_date) AS start_date,
                COUNT(*) AS rows_in_sequence,
                CLASSIFIER() AS cl,
                SUM(price) AS total,
                AVG(price) AS mean
                ONE ROW PER MATCH
                PATTERN(a b+)
                DEFINE b AS price > LAG(price)
                )""");
        assertShape(
            "M:NUMBER(1,0)",
            "SELECT * FROM FZ MATCH_RECOGNIZE(MEASURES 1 AS m PATTERN (a) DEFINE a AS TRUE)");
        assertShape(
            "ID % 2:NUMBER(38,0), UPPER(K):VARCHAR(50331648), K:VARCHAR(16777216), C:NUMBER(18,0)",
            "SELECT * FROM n MATCH_RECOGNIZE(PARTITION BY id % 2, UPPER(k), k ORDER BY id MEASURES COUNT(*) "
                + "AS c PATTERN (a+) DEFINE a AS TRUE)");
        assertShape(
            "MSQ:NUMBER(18,0), P:NUMBER(38,0), MI:NUMBER(38,0), C:NUMBER(18,0), R:NUMBER(38,6), "
                + "BARE:NUMBER(38,0), E:NUMBER(38,0), D:NUMBER(38,0)",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_SEQUENCE_NUMBER() AS msq, PREV(v) AS "
                + "p, MIN(v) AS mi, COUNT(v) AS c, SUM(v) / COUNT(*) AS r, v AS bare, id + 1 AS e, LAST(v) - "
                + "FIRST(v) AS d PATTERN (strt up+) DEFINE up AS v > LAG(v))");
        assertAnswer(
            "2|5|5|2|5.500000 ;; 2|10|10|2|15.000000 ;; 3|30|12|3|24.333333",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_SEQUENCE_NUMBER() AS msq, PREV(v) AS "
                + "p, MIN(v) AS mi, COUNT(v) AS c, SUM(v) / COUNT(*) AS r PATTERN (strt up+) DEFINE up AS v > "
                + "LAG(v)) ORDER BY 1, 2");
    }

    @Test
    public void allRowsPerMatchAnswersTheSourceAndTheMeasures() {
        assertShape(
            "ID:NUMBER(38,0), B:BOOLEAN, MN:NUMBER(18,0), CL:VARCHAR(134217728), C:NUMBER(18,0)",
            "SELECT * FROM fz MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, CLASSIFIER() AS cl, "
                + "COUNT(*) AS c ALL ROWS PER MATCH PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer(
            "5|true|1|A|1 ;; 7|false|2|A|1",
            "SELECT * FROM fz MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, CLASSIFIER() AS cl, "
                + "COUNT(*) AS c ALL ROWS PER MATCH PATTERN (a) DEFINE a AS TRUE) ORDER BY id");
        assertAnswer(
            "1|10 ;; 2|20 ;; 4|12 ;; 5|30 ;; 6|31 ;; 7|5 ;; 8|6",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id ALL ROWS PER MATCH PATTERN (strt up+) DEFINE up AS v "
                + "> LAG(v)) ORDER BY 1");
    }

    @Test
    public void aTableCreatedFromTheRelationKeepsTheTypes() {
        assertAnswer(
            "Table MRT successfully created.",
            "CREATE OR REPLACE TABLE mrt AS SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES "
                + "MATCH_NUMBER() AS mn, CLASSIFIER() AS cl, AVG(v) AS av, FIRST(v) AS fv, LAST(v) - FIRST(v) AS "
                + "d PATTERN (strt up+) DEFINE up AS v > LAG(v))");
        assertAnswer(
            "MN|NUMBER(18,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CL|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "AV|NUMBER(38,3)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "FV|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "D|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE mrt");
    }

    @Test
    public void aClauseAnsweringNoColumnOrOneTwiceIsRefused() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 17 | SELECT with no columns",
            "SELECT * FROM fz MATCH_RECOGNIZE(ORDER BY id PATTERN (a) DEFINE a AS TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'MN'",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS mn, COUNT(*) AS mn ONE "
                + "ROW PER MATCH PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "1|10|1 ;; 2|20|1 ;; 4|12|2 ;; 5|30|2 ;; 6|31|2 ;; 7|5|3 ;; 8|6|3",
            "SELECT * FROM s MATCH_RECOGNIZE(ORDER BY id MEASURES MATCH_NUMBER() AS id ALL ROWS PER MATCH "
                + "PATTERN (a b+) DEFINE b AS v > LAG(v)) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | ambiguous column name 'G'",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g ORDER BY id MEASURES COUNT(*) AS g PATTERN (a+) "
                + "DEFINE a AS TRUE) ORDER BY 1");
        assertAnswer(
            "false ;; true",
            "SELECT * FROM fz MATCH_RECOGNIZE(PARTITION BY b PATTERN (a) DEFINE a AS TRUE) ORDER BY 1");
        assertAnswer(
            "1|2",
            "SELECT * FROM fz MATCH_RECOGNIZE(PARTITION BY id % 2 ORDER BY id MEASURES COUNT(*) AS c PATTERN "
                + "(a+) DEFINE a AS TRUE) ORDER BY 1");
        assertAnswer(
            "a|0|2 ;; a|1|2 ;; b|0|1 ;; b|1|1 ;; null|1|1",
            "SELECT * FROM t MATCH_RECOGNIZE(PARTITION BY g, id % 2 ORDER BY id MEASURES COUNT(*) AS c "
                + "PATTERN (a+) DEFINE a AS TRUE) ORDER BY 1, 2");
    }
}
