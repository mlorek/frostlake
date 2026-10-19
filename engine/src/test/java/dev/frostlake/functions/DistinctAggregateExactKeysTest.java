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
package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * COUNT, SUM, AVG and ARRAY_AGG with DISTINCT, and their window forms, read each exact value once by the value
 * itself: integers past 2^53 and decimals past a double's digits stay apart, 1.10 and 1.1 are one, and a
 * FLOAT's zeros are one. AVG(DISTINCT x) declares three more decimals than its argument, truncated, and its
 * window form reads the distinct values (live-verified).
 */
public class DistinctAggregateExactKeysTest extends MeasuredCellsTestSupport {

    @Test
    public void exactValuesStayApart() {
        try {
            assertAnswer(
                "Table IDS successfully created.",
                "CREATE OR REPLACE TABLE ids (g INT, id NUMBER(38,0))");
            assertAnswer(
                "4",
                "INSERT INTO ids VALUES (1, 9007199254740992), (1, 9007199254740993), (2, 9007199254740994), (2, "
                    + "9007199254740995)");
            assertAnswer(
                "4|4",
                "SELECT COUNT(DISTINCT id), COUNT(*) FROM ids");
            assertAnswer(
                "4",
                "SELECT COUNT(*) FROM (SELECT DISTINCT id FROM ids)");
            assertAnswer(
                "36028797018963974|36028797018963974",
                "SELECT SUM(DISTINCT id), SUM(id) FROM ids");
            assertAnswer(
                "9007199254740993.500",
                "SELECT AVG(DISTINCT id) FROM ids");
            assertAnswer(
                "4",
                "SELECT ARRAY_SIZE(ARRAY_AGG(DISTINCT id)) FROM ids");
            assertAnswer(
                "4",
                "SELECT ARRAY_SIZE(ARRAY_UNIQUE_AGG(id)) FROM ids");
            assertAnswer(
                "4",
                "SELECT DISTINCT COUNT(DISTINCT id) OVER () FROM ids");
            assertAnswer(
                "1|2 ;; 1|2 ;; 2|2 ;; 2|2",
                "SELECT g, COUNT(DISTINCT id) OVER (PARTITION BY g) AS n FROM ids ORDER BY g, n");
            assertAnswer(
                "1|2 ;; 2|2",
                "SELECT g, COUNT(DISTINCT id) FROM ids GROUP BY g ORDER BY g");
            assertAnswer(
                "4",
                "SELECT COUNT(DISTINCT g, id) FROM ids");
            assertAnswer(
                "9007199254740992,9007199254740993,9007199254740994,9007199254740995",
                "SELECT LISTAGG(DISTINCT id, ',') WITHIN GROUP (ORDER BY id) FROM ids");
            assertAnswer(
                "Table DECS successfully created.",
                "CREATE OR REPLACE TABLE decs (d NUMBER(38,20))");
            assertAnswer(
                "3",
                "INSERT INTO decs VALUES (0.10000000000000000001), (0.10000000000000000002), (0.1)");
            assertAnswer(
                "3|3",
                "SELECT COUNT(DISTINCT d), COUNT(*) FROM decs");
            assertAnswer(
                "0.30000000000000000003",
                "SELECT SUM(DISTINCT d) FROM decs");
            assertAnswer(
                "Table SC successfully created.",
                "CREATE OR REPLACE TABLE sc (a NUMBER(10,2), b NUMBER(10,1))");
            assertAnswer(
                "3",
                "INSERT INTO sc VALUES (1.10, 1.1), (1.00, 1.0), (2.50, 2.6)");
            assertAnswer(
                "3|3",
                "SELECT COUNT(DISTINCT a), COUNT(DISTINCT b) FROM sc");
            assertAnswer(
                "4",
                "SELECT COUNT(DISTINCT v) FROM (SELECT a AS v FROM sc UNION ALL SELECT b FROM sc)");
            assertAnswer(
                "Table FL successfully created.",
                "CREATE OR REPLACE TABLE fl (f FLOAT)");
            assertAnswer(
                "REFUSED DML operation to table FL failed on column F with error: Numeric value 'NaN' is not "
                    + "recognized",
                "INSERT INTO fl VALUES (0.0), (-0.0), (1e16), (1e16 + 1), ('NaN'), ('NaN')");
            assertAnswer(
                "0",
                "SELECT COUNT(DISTINCT f) FROM fl");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM (SELECT DISTINCT f FROM fl)");
            assertAnswer(
                "2",
                "SELECT COUNT(DISTINCT x) FROM (SELECT 9007199254740993::NUMBER(38,0) AS x UNION ALL SELECT "
                    + "9007199254740992)");
            assertAnswer(
                "1",
                "SELECT COUNT(DISTINCT x) FROM (SELECT 9007199254740993::FLOAT AS x UNION ALL SELECT "
                    + "9007199254740992::FLOAT)");
            assertAnswer(
                "true",
                "SELECT HASH_AGG(DISTINCT id) = HASH_AGG(id) FROM ids");
            assertAnswer(
                "3",
                "SELECT COUNT(DISTINCT id) FROM ids WHERE id > 9007199254740992");
        } finally {
            answer(
                "DROP TABLE IF EXISTS ids");
            answer(
                "DROP TABLE IF EXISTS decs");
            answer(
                "DROP TABLE IF EXISTS sc");
            answer(
                "DROP TABLE IF EXISTS fl");
        }
    }

    @Test
    public void distinctAverages() {
        try {
            assertAnswer(
                "Table AV successfully created.",
                "CREATE OR REPLACE TABLE av (v INT, n NUMBER(10,2), f FLOAT)");
            assertAnswer(
                "3",
                "INSERT INTO av VALUES (1, 1.25, 1.5), (2, 2.5, 2.0), (2, 2.5, 2.0)");
            assertShape(
                "A:NUMBER(38,3)",
                "SELECT AVG(DISTINCT v) AS a FROM av");
            assertShape(
                "A:NUMBER(25,5)",
                "SELECT AVG(DISTINCT n) AS a FROM av");
            assertShape(
                "A:NUMBER(38,6)",
                "SELECT AVG(v) AS a FROM av");
            assertAnswer(
                "1.500|1.666667|1.87500|2.08333333",
                "SELECT AVG(DISTINCT v), AVG(v), AVG(DISTINCT n), AVG(n) FROM av");
            assertShape(
                "V:NUMBER(38,0), A:NUMBER(38,3)",
                "SELECT v, AVG(DISTINCT v) OVER (PARTITION BY v) AS a FROM av");
            assertAnswer(
                "1.500",
                "SELECT AVG(DISTINCT v) OVER () AS a FROM av LIMIT 1");
            assertShape(
                "A:NUMBER(38,3)",
                "SELECT AVG(DISTINCT v) AS a FROM av GROUP BY n");
            assertAnswer(
                "Table AD successfully created.",
                "CREATE OR REPLACE TABLE ad (x INT, n NUMBER(10,2))");
            assertAnswer(
                "4",
                "INSERT INTO ad VALUES (1, 0.01), (2, 0.02), (5, 0.05), (5, 0.05)");
            assertAnswer(
                "2.666|0.02666|3.250000|0.03250000",
                "SELECT AVG(DISTINCT x), AVG(DISTINCT n), AVG(x), AVG(n) FROM ad");
            assertAnswer(
                "8|2.666",
                "SELECT DISTINCT SUM(DISTINCT x) OVER (), AVG(DISTINCT x) OVER () FROM ad");
            assertAnswer(
                "1|0.01 ;; 2|0.07 ;; 5|0.07 ;; 5|0.07",
                "SELECT x, SUM(DISTINCT n) OVER (PARTITION BY x > 1) AS s FROM ad ORDER BY x, s");
        } finally {
            answer(
                "DROP TABLE IF EXISTS av");
            answer(
                "DROP TABLE IF EXISTS ad");
        }
    }
}
