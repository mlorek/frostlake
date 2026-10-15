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
 * Two TIMESTAMP_TZ or TIMESTAMP_LTZ values for one moment are one value wherever values meet: a join, an IN
 * subquery, UNION, INTERSECT, EXCEPT, DISTINCT, COUNT(DISTINCT), GROUP BY, a window's PARTITION BY, RANK and an
 * ORDER BY tie. A DATE meets its midnight TIMESTAMP_NTZ, and a TIMESTAMP_NTZ meets a TIMESTAMP_TZ as = compares
 * them, in the session's zone (live-verified).
 */
public class TimestampInstantKeysTest extends MeasuredCellsTestSupport {

    @Test
    public void equalMomentsAreOneValue() {
        try {
            assertAnswer(
                "Table A1 successfully created.",
                "CREATE OR REPLACE TABLE a1 (id INT, tz TIMESTAMP_TZ)");
            assertAnswer(
                "Table B1 successfully created.",
                "CREATE OR REPLACE TABLE b1 (id INT, tz TIMESTAMP_TZ)");
            assertAnswer(
                "2",
                "INSERT INTO a1 VALUES (1, '2024-01-01 10:00:00 +02:00'), (3, '2024-01-01 09:00:00 +01:00')");
            assertAnswer(
                "2",
                "INSERT INTO b1 VALUES (2, '2024-01-01 08:00:00 +00:00'), (4, '2024-01-01 08:00:01 +00:00')");
            assertAnswer(
                "1|2|true ;; 1|4|false ;; 3|2|true ;; 3|4|false",
                "SELECT a1.id, b1.id, a1.tz = b1.tz FROM a1, b1 ORDER BY 1, 2");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM a1 JOIN b1 ON a1.tz = b1.tz");
            assertAnswer(
                "1|2 ;; 3|2",
                "SELECT a1.id, b1.id FROM a1 JOIN b1 ON a1.tz = b1.tz ORDER BY 1, 2");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM a1 LEFT JOIN b1 ON a1.tz = b1.tz WHERE b1.id IS NULL");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM a1 JOIN b1 USING (tz)");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM a1 WHERE tz IN (SELECT tz FROM b1)");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM a1 WHERE tz NOT IN (SELECT tz FROM b1)");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM b1 WHERE tz IN (SELECT tz FROM a1)");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM (SELECT tz FROM a1 UNION SELECT tz FROM b1)");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM (SELECT tz FROM a1 INTERSECT SELECT tz FROM b1)");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM (SELECT tz FROM b1 EXCEPT SELECT tz FROM a1)");
            assertAnswer(
                "2",
                "SELECT COUNT(DISTINCT tz) FROM (SELECT tz FROM a1 UNION ALL SELECT tz FROM b1)");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM (SELECT DISTINCT tz FROM (SELECT tz FROM a1 UNION ALL SELECT tz FROM b1))");
            assertAnswer(
                "1 ;; 3",
                "SELECT COUNT(*) AS n FROM (SELECT tz FROM a1 UNION ALL SELECT tz FROM b1) GROUP BY tz ORDER BY n");
            assertAnswer(
                "1|1 ;; 2|1 ;; 3|1 ;; 4|4",
                "SELECT id, RANK() OVER (ORDER BY tz) AS r FROM (SELECT * FROM a1 UNION ALL SELECT * FROM b1) "
                    + "ORDER BY id");
            assertAnswer(
                "1|1 ;; 2|1 ;; 3|1 ;; 4|2",
                "SELECT id, DENSE_RANK() OVER (ORDER BY tz) AS r FROM (SELECT * FROM a1 UNION ALL SELECT * FROM "
                    + "b1) ORDER BY id");
            assertAnswer(
                "1 ;; 2 ;; 3 ;; 4",
                "SELECT id FROM (SELECT * FROM a1 UNION ALL SELECT * FROM b1) ORDER BY tz, id");
            assertAnswer(
                "4 ;; 1 ;; 2 ;; 3",
                "SELECT id FROM (SELECT * FROM a1 UNION ALL SELECT * FROM b1) ORDER BY tz DESC, id");
            assertAnswer(
                "1|3 ;; 2|3 ;; 3|3 ;; 4|1",
                "SELECT id, COUNT(*) OVER (PARTITION BY tz) AS n FROM (SELECT * FROM a1 UNION ALL SELECT * FROM "
                    + "b1) ORDER BY id");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM a1 WHERE EXISTS (SELECT 1 FROM b1 WHERE b1.tz = a1.tz)");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM a1 WHERE tz IN ('2024-01-01 08:00:00 +00:00'::TIMESTAMP_TZ)");
            assertAnswer(
                "2",
                "SELECT ARRAY_SIZE(ARRAY_AGG(DISTINCT tz)) FROM (SELECT tz FROM a1 UNION ALL SELECT tz FROM b1)");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM (SELECT tz, COUNT(*) FROM (SELECT tz FROM a1 UNION ALL SELECT tz FROM b1) "
                    + "GROUP BY ALL)");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM a1 NATURAL JOIN (SELECT tz FROM b1)");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM (SELECT tz FROM a1 UNION SELECT tz FROM b1 UNION SELECT tz FROM a1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 84 | invalid identifier 'ID'",
                "SELECT MIN(tz) = MAX(tz) FROM (SELECT tz FROM a1 UNION ALL SELECT tz FROM b1) WHERE id < 4");
        } finally {
            answer(
                "DROP TABLE IF EXISTS a1");
            answer(
                "DROP TABLE IF EXISTS b1");
        }
    }

    @Test
    public void datesMeetTheirMidnight() {
        try {
            assertAnswer(
                "Table DA successfully created.",
                "CREATE OR REPLACE TABLE da (id INT, d DATE)");
            assertAnswer(
                "Table DT successfully created.",
                "CREATE OR REPLACE TABLE dt (id INT, t TIMESTAMP_NTZ)");
            assertAnswer(
                "2",
                "INSERT INTO da VALUES (1, '2024-01-01'), (3, '2024-01-02')");
            assertAnswer(
                "2",
                "INSERT INTO dt VALUES (2, '2024-01-01 00:00:00'), (4, '2024-01-02 00:00:01')");
            assertAnswer(
                "1|2|true ;; 1|4|false ;; 3|2|false ;; 3|4|false",
                "SELECT da.id, dt.id, da.d = dt.t FROM da, dt ORDER BY 1, 2");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM da JOIN dt ON da.d = dt.t");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM da WHERE d IN (SELECT t FROM dt)");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM dt WHERE t IN (SELECT d FROM da)");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM (SELECT d FROM da UNION SELECT t FROM dt)");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM (SELECT d FROM da INTERSECT SELECT t FROM dt)");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM (SELECT t FROM dt EXCEPT SELECT d FROM da)");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM da JOIN dt ON da.d = dt.t::DATE");
            assertAnswer(
                "Table LT successfully created.",
                "CREATE OR REPLACE TABLE lt (id INT, l TIMESTAMP_LTZ)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "1",
                "INSERT INTO lt VALUES (1, '2024-01-01 10:00:00')");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Europe/Warsaw'");
            assertAnswer(
                "1",
                "INSERT INTO lt VALUES (2, '2024-01-01 11:00:00')");
            assertAnswer(
                "1|true",
                "SELECT COUNT(DISTINCT l), MIN(l) = MAX(l) FROM lt");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM (SELECT DISTINCT l FROM lt)");
            assertAnswer(
                "2",
                "SELECT COUNT(*) AS n FROM lt GROUP BY l");
            assertAnswer(
                "Table TZZ successfully created.",
                "CREATE OR REPLACE TABLE tzz (id INT, tz TIMESTAMP_TZ)");
            assertAnswer(
                "1",
                "INSERT INTO tzz VALUES (9, '2024-01-01 12:00:00 +02:00')");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM lt JOIN tzz ON lt.l = tzz.tz");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM lt WHERE l IN (SELECT tz FROM tzz)");
            assertAnswer(
                "REFUSED SQL compilation error: | inconsistent data type for result columns for set operator "
                    + "input branches, expected TIMESTAMP_TZ(9), got TIMESTAMP_LTZ(9)",
                "SELECT COUNT(*) FROM (SELECT l FROM lt UNION SELECT tz FROM tzz)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET TIMEZONE");
        } finally {
            answer(
                "alter session unset timezone");
            answer(
                "DROP TABLE IF EXISTS da");
            answer(
                "DROP TABLE IF EXISTS dt");
            answer(
                "DROP TABLE IF EXISTS lt");
            answer(
                "DROP TABLE IF EXISTS tzz");
        }
    }

    @Test
    public void naiveMeetsZonedInTheSessionZone() {
        try {
            assertAnswer(
                "Table NT successfully created.",
                "CREATE OR REPLACE TABLE nt (id INT, t TIMESTAMP_NTZ)");
            assertAnswer(
                "1",
                "INSERT INTO nt VALUES (5, '2024-01-01 08:00:00')");
            assertAnswer(
                "Table TZ2 successfully created.",
                "CREATE OR REPLACE TABLE tz2 (id INT, tz TIMESTAMP_TZ)");
            assertAnswer(
                "3",
                "INSERT INTO tz2 VALUES (6, '2024-01-01 08:00:00 +00:00'), (7, '2024-01-01 10:00:00 +02:00'), (8, "
                    + "'2024-01-01 08:00:00 +01:00')");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'UTC'");
            assertAnswer(
                "5|6|true ;; 5|7|true ;; 5|8|false",
                "SELECT nt.id, tz2.id, nt.t = tz2.tz FROM nt, tz2 ORDER BY 2");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM nt JOIN tz2 ON nt.t = tz2.tz");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM nt WHERE t IN (SELECT tz FROM tz2)");
            assertAnswer(
                "REFUSED SQL compilation error: | inconsistent data type for result columns for set operator "
                    + "input branches, expected TIMESTAMP_TZ(9), got TIMESTAMP_NTZ(9)",
                "SELECT COUNT(*) FROM (SELECT t FROM nt UNION SELECT tz FROM tz2)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET TIMEZONE = 'Europe/Warsaw'");
            assertAnswer(
                "5|6|false ;; 5|7|false ;; 5|8|true",
                "SELECT nt.id, tz2.id, nt.t = tz2.tz FROM nt, tz2 ORDER BY 2");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM nt JOIN tz2 ON nt.t = tz2.tz");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM nt WHERE t IN (SELECT tz FROM tz2)");
            assertAnswer(
                "REFUSED SQL compilation error: | inconsistent data type for result columns for set operator "
                    + "input branches, expected TIMESTAMP_TZ(9), got TIMESTAMP_NTZ(9)",
                "SELECT COUNT(*) FROM (SELECT t FROM nt UNION SELECT tz FROM tz2)");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM (SELECT tz FROM tz2 UNION SELECT t FROM nt)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET TIMEZONE");
        } finally {
            answer(
                "alter session unset timezone");
            answer(
                "DROP TABLE IF EXISTS nt");
            answer(
                "DROP TABLE IF EXISTS tz2");
        }
    }
}
