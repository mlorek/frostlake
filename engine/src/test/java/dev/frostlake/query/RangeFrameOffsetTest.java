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
 * A RANGE frame with an offset holds the rows whose single ORDER BY key lies within the offset of the current
 * row's: a number's key by a number, a date's by days or an interval, a timestamp's by an interval, either
 * direction; a NULL key holds its peers. A key of the wrong kind, a second key, an offset of the other kind and a
 * negative interval are refused when the statement compiles, and an offset is a literal - a number or an
 * INTERVAL constant - and nothing else (live-verified).
 */
public class RangeFrameOffsetTest extends MeasuredCellsTestSupport {

    @Test
    public void datesAndTimestampsTakeIntervals() {
        try {
            assertAnswer(
                "Table EV successfully created.",
                "CREATE OR REPLACE TABLE ev (d DATE, t TIMESTAMP_NTZ, v INT)");
            assertAnswer(
                "3",
                "INSERT INTO ev VALUES ('2024-01-01', '2024-01-01 00:00', 1), ('2024-01-02', '2024-01-01 10:00', "
                    + "2), ('2024-01-04', '2024-01-01 23:00', 4)");
            assertAnswer(
                "2024-01-01|1 ;; 2024-01-02|3 ;; 2024-01-04|4",
                "SELECT d, SUM(v) OVER (ORDER BY d RANGE BETWEEN INTERVAL '1 day' PRECEDING AND CURRENT ROW) AS s "
                    + "FROM ev ORDER BY d");
            assertAnswer(
                "2024-01-01|1 ;; 2024-01-02|3 ;; 2024-01-04|4",
                "SELECT d, SUM(v) OVER (ORDER BY d RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS s FROM ev ORDER "
                    + "BY d");
            assertAnswer(
                "2024-01-01T00:00|1 ;; 2024-01-01T10:00|3 ;; 2024-01-01T23:00|4",
                "SELECT t, SUM(v) OVER (ORDER BY t RANGE BETWEEN INTERVAL '12 hours' PRECEDING AND CURRENT ROW) "
                    + "AS s FROM ev ORDER BY t");
            assertAnswer(
                "2024-01-01|3 ;; 2024-01-02|6 ;; 2024-01-04|4",
                "SELECT d, SUM(v) OVER (ORDER BY d RANGE BETWEEN CURRENT ROW AND INTERVAL '2 days' FOLLOWING) AS "
                    + "s FROM ev ORDER BY d");
            assertAnswer(
                "2024-01-01|3 ;; 2024-01-02|2 ;; 2024-01-04|4",
                "SELECT d, SUM(v) OVER (ORDER BY d DESC RANGE BETWEEN INTERVAL '1 day' PRECEDING AND CURRENT ROW) "
                    + "AS s FROM ev ORDER BY d");
            assertAnswer(
                "2024-01-01T00:00|1 ;; 2024-01-01T10:00|2 ;; 2024-01-01T23:00|1",
                "SELECT t, COUNT(*) OVER (ORDER BY t RANGE BETWEEN INTERVAL '10 hours' PRECEDING AND INTERVAL '1 "
                    + "hour' FOLLOWING) AS c FROM ev ORDER BY t");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid RANGE BETWEEN frame 'RANGE BETWEEN 1 PRECEDING AND "
                    + "CURRENT ROW': timestamp ORDER BY clause only allows interval window frame boundaries.",
                "SELECT t, SUM(v) OVER (ORDER BY t RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS s FROM ev ORDER "
                    + "BY t");
            assertAnswer(
                "2024-01-01|1 ;; 2024-01-02|2 ;; 2024-01-04|4",
                "SELECT d, SUM(v) OVER (ORDER BY d RANGE BETWEEN INTERVAL '1 hour' PRECEDING AND CURRENT ROW) AS "
                    + "s FROM ev ORDER BY d");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid RANGE BETWEEN frame 'RANGE BETWEEN INTERVAL '1 day' "
                    + "PRECEDING AND CURRENT ROW': numeric ORDER BY clause only allows numeric window frame "
                    + "boundaries.",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE BETWEEN INTERVAL '1 day' PRECEDING AND CURRENT ROW) AS s "
                    + "FROM ev ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 51 | Window frame 'RANGE BETWEEN "
                    + "INTERVAL '1 day' PRECEDING AND CURRENT ROW' allows only one ORDER BY expression.",
                "SELECT d, SUM(v) OVER (ORDER BY d, v RANGE BETWEEN INTERVAL '1 day' PRECEDING AND CURRENT ROW) "
                    + "AS s FROM ev ORDER BY d");
            assertAnswer(
                "2024-01-01|1 ;; 2024-01-02|3 ;; 2024-01-04|7",
                "SELECT d, SUM(v) OVER (ORDER BY d RANGE BETWEEN INTERVAL '1 month' PRECEDING AND CURRENT ROW) AS "
                    + "s FROM ev ORDER BY d");
            assertAnswer(
                "2024-01-01|null ;; 2024-01-02|1.000 ;; 2024-01-04|2.000",
                "SELECT d, AVG(v) OVER (ORDER BY d RANGE BETWEEN INTERVAL '2 days' PRECEDING AND INTERVAL '1 day' "
                    + "PRECEDING) AS s FROM ev ORDER BY d");
            assertAnswer(
                "Table EV successfully created.",
                "CREATE OR REPLACE TABLE ev (d DATE, t TIMESTAMP_NTZ, v INT, n NUMBER(5,1))");
            assertAnswer(
                "4",
                "INSERT INTO ev VALUES ('2024-01-01', '2024-01-01 00:00', 1, 1.0), ('2024-01-02', '2024-01-01 "
                    + "10:00', 2, 2.5), ('2024-01-04', '2024-01-01 23:00', 4, 3.0), (NULL, NULL, 8, NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 51 | Window frame 'RANGE BETWEEN 1 "
                    + "PRECEDING AND CURRENT ROW' allows only one ORDER BY expression.",
                "SELECT v, SUM(v) OVER (ORDER BY n, v RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "2024-01-01|3 ;; 2024-01-02|6 ;; 2024-01-04|4 ;; null|8",
                "SELECT d, SUM(v) OVER (ORDER BY d RANGE BETWEEN CURRENT ROW AND 2 FOLLOWING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "2024-01-01|1 ;; 2024-01-02|3 ;; 2024-01-04|4 ;; null|8",
                "SELECT d, SUM(v) OVER (ORDER BY d RANGE BETWEEN INTERVAL '1 day' PRECEDING AND CURRENT ROW) AS s "
                    + "FROM ev ORDER BY v");
            assertAnswer(
                "2024-01-01T00:00|1 ;; 2024-01-01T10:00|3 ;; 2024-01-01T23:00|4 ;; null|8",
                "SELECT t, SUM(v) OVER (ORDER BY t RANGE INTERVAL '12 hours' PRECEDING) AS s FROM ev ORDER BY v");
            assertAnswer(
                "2024-01-01|1 ;; 2024-01-02|3 ;; 2024-01-04|6 ;; null|8",
                "SELECT d, SUM(v) OVER (ORDER BY d RANGE BETWEEN 1.5 PRECEDING AND CURRENT ROW) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "2024-01-01|1 ;; 2024-01-02|3 ;; 2024-01-04|6 ;; null|12",
                "SELECT d, SUM(v) OVER (ORDER BY d ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "2024-01-01|null ;; 2024-01-02|1 ;; 2024-01-04|3 ;; null|15",
                "SELECT d, SUM(v) OVER (ORDER BY d RANGE BETWEEN UNBOUNDED PRECEDING AND INTERVAL '1 day' "
                    + "PRECEDING) AS s FROM ev ORDER BY v");
            assertAnswer(
                "2024-01-01|1 ;; 2024-01-02|3 ;; 2024-01-04|4 ;; null|8",
                "SELECT d, SUM(v) OVER (ORDER BY d::TIMESTAMP_LTZ RANGE BETWEEN INTERVAL '1 day' PRECEDING AND "
                    + "CURRENT ROW) AS s FROM ev ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 48 | Invalid interval frame: 'INTERVAL "
                    + "'-1 day' PRECEDING' has an invalid interval frame range.",
                "SELECT d, SUM(v) OVER (ORDER BY d RANGE BETWEEN INTERVAL '-1 day' PRECEDING AND CURRENT ROW) AS "
                    + "s FROM ev ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: | ORDER BY clause is invalid: The ORDER BY clause for window "
                    + "frame 'RANGE BETWEEN 1 PRECEDING AND CURRENT ROW' must have a numeric, date, or timestamp "
                    + "expression.",
                "SELECT g, SUM(v) OVER (ORDER BY g RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) FROM (SELECT 'a' AS "
                    + "g, 1 AS v)");
        } finally {
            answer(
                "DROP TABLE IF EXISTS ev");
        }
    }

    @Test
    public void anOffsetIsALiteral() {
        try {
            assertAnswer(
                "Table EV successfully created.",
                "CREATE OR REPLACE TABLE ev (p INT, n NUMBER(5,1), v INT, tz TIMESTAMP_TZ, f FLOAT)");
            assertAnswer(
                "5",
                "INSERT INTO ev VALUES (1, 1.0, 1, '2024-01-01 00:00:00 +00:00', 1.5), (1, 2.5, 2, '2024-01-01 "
                    + "05:00:00 +02:00', 2.0), (1, 3.0, 4, '2024-01-01 23:00:00 +00:00', 4.25), (2, NULL, 8, NULL, "
                    + "NULL), (2, 1.0, 16, '2024-01-02 00:00:00 -05:00', 0.5)");
            assertAnswer(
                "1|17 ;; 2|6 ;; 4|4 ;; 8|8 ;; 16|17",
                "SELECT v, SUM(v) OVER (ORDER BY n DESC RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "1|1 ;; 2|3 ;; 4|6 ;; 8|8 ;; 16|16",
                "SELECT v, SUM(v) OVER (PARTITION BY p ORDER BY n RANGE BETWEEN 1.5 PRECEDING AND CURRENT ROW) AS "
                    + "s FROM ev ORDER BY v");
            assertAnswer(
                "1|6 ;; 2|null ;; 4|null ;; 8|8 ;; 16|6",
                "SELECT v, SUM(v) OVER (ORDER BY n RANGE BETWEEN 1 FOLLOWING AND 2 FOLLOWING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "1|null ;; 2|17 ;; 4|17 ;; 8|8 ;; 16|null",
                "SELECT v, SUM(v) OVER (ORDER BY n RANGE BETWEEN 2 PRECEDING AND 1 PRECEDING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "1|17 ;; 2|23 ;; 4|23 ;; 8|31 ;; 16|17",
                "SELECT v, SUM(v) OVER (ORDER BY n RANGE BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING) AS s FROM "
                    + "ev ORDER BY v");
            assertAnswer(
                "1|1 ;; 2|3 ;; 4|4 ;; 8|8 ;; 16|20",
                "SELECT v, SUM(v) OVER (ORDER BY tz RANGE BETWEEN INTERVAL '6 hours' PRECEDING AND CURRENT ROW) "
                    + "AS s FROM ev ORDER BY v");
            assertAnswer(
                "1|3 ;; 2|3 ;; 4|4 ;; 8|8 ;; 16|16",
                "SELECT v, SUM(v) OVER (ORDER BY f RANGE BETWEEN 0.5 PRECEDING AND 0.5 FOLLOWING) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'NULL'.",
                "SELECT v, SUM(v) OVER (ORDER BY n RANGE BETWEEN NULL PRECEDING AND CURRENT ROW) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY n RANGE 1 FOLLOWING) AS s FROM ev ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected ''1''.",
                "SELECT v, SUM(v) OVER (ORDER BY n RANGE BETWEEN '1' PRECEDING AND CURRENT ROW) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "1|1 ;; 2|3 ;; 4|6 ;; 8|8 ;; 16|16",
                "SELECT v, SUM(v) OVER (ORDER BY v * 2 RANGE BETWEEN 4 PRECEDING AND CURRENT ROW) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "",
                "SELECT v, SUM(v) OVER (ORDER BY n RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS s FROM ev WHERE "
                    + "1 = 0");
            assertAnswer(
                "Table EV successfully created.",
                "CREATE OR REPLACE TABLE ev (v INT, n NUMBER(10,2), m NUMBER(5,1), d DATE, f FLOAT)");
            assertAnswer(
                "3",
                "INSERT INTO ev VALUES (1, 1.25, 1.0, '2024-01-01', 1.5), (2, 2.5, 2.5, '2024-01-02', 2.0), (4, "
                    + "4.75, 3.0, '2024-01-04', 4.25)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ''1''.",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN '1' PRECEDING AND CURRENT ROW) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'NULL'.",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN NULL PRECEDING AND CURRENT ROW) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1.5 PRECEDING AND CURRENT ROW) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS 1 FOLLOWING) AS s FROM ev ORDER BY v");
            assertAnswer(
                "1|1 ;; 2|3 ;; 4|6",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS 1 PRECEDING) AS s FROM ev ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN INTERVAL '1 day' PRECEDING AND CURRENT ROW) AS s "
                    + "FROM ev ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '('. | syntax "
                    + "error line 1 at position 62 unexpected 'AND'.",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE BETWEEN (1) PRECEDING AND CURRENT ROW) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "1|1 ;; 2|3 ;; 4|4",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE BETWEEN 1e0 PRECEDING AND CURRENT ROW) AS s FROM ev "
                    + "ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE 1 FOLLOWING) AS s FROM ev ORDER BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 2 FOLLOWING AND 1 FOLLOWING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 48 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE BETWEEN 1 FOLLOWING AND 1 PRECEDING) AS s FROM ev ORDER "
                    + "BY v");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 48 | Invalid window frame",
                "SELECT v, SUM(v) OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND 1 PRECEDING) AS s FROM ev ORDER "
                    + "BY v");
        } finally {
            answer(
                "DROP TABLE IF EXISTS ev");
        }
    }
}
