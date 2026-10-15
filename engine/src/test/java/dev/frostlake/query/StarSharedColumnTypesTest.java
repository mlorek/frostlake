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
 * SELECT * over a join whose relations share a column name reports each of those columns with its own type, in
 * any join, over a derived relation and beside a qualified column of the same name (live-verified).
 */
public class StarSharedColumnTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void sharedNamesKeepTheirTypes() {
        try {
            assertAnswer(
                "Table T1 successfully created.",
                "CREATE OR REPLACE TABLE t1 (k NUMBER(5,0), a INT)");
            assertAnswer(
                "Table T2 successfully created.",
                "CREATE OR REPLACE TABLE t2 (k VARCHAR(3), b DATE)");
            assertAnswer(
                "1",
                "INSERT INTO t1 VALUES (1, 10)");
            assertAnswer(
                "1",
                "INSERT INTO t2 VALUES ('abc', '2024-01-02')");
            assertAnswer(
                "1|10|abc|2024-01-02",
                "SELECT * FROM t1, t2");
            assertShape(
                "K:NUMBER(5,0), A:NUMBER(38,0), K:VARCHAR(3), B:DATE",
                "SELECT * FROM t1, t2");
            assertShape(
                "K:NUMBER(5,0), A:NUMBER(38,0), K:VARCHAR(3), B:DATE",
                "SELECT t1.*, t2.* FROM t1, t2");
            assertShape(
                "K:NUMBER(5,0), A:NUMBER(38,0), K:VARCHAR(3), B:DATE",
                "SELECT * FROM t1 JOIN t2 ON TRUE");
            assertShape(
                "K:NUMBER(5,0), A:NUMBER(38,0), K:VARCHAR(3), B:DATE",
                "SELECT * FROM t1 LEFT JOIN t2 ON TRUE");
            assertShape(
                "K:NUMBER(5,0), A:NUMBER(38,0), K:VARCHAR(3), B:DATE, K:VARCHAR(3)",
                "SELECT *, t2.k FROM t1, t2");
            assertShape(
                "K:NUMBER(5,0), A:NUMBER(38,0), K:VARCHAR(3), B:DATE",
                "SELECT * FROM (SELECT * FROM t1, t2 LIMIT 1) x");
            assertAnswer(
                "Table T3 successfully created.",
                "CREATE OR REPLACE TABLE t3 (k BOOLEAN)");
            assertAnswer(
                "1",
                "INSERT INTO t3 VALUES (TRUE)");
            assertShape(
                "K:NUMBER(5,0), A:NUMBER(38,0), K:VARCHAR(3), B:DATE, K:BOOLEAN",
                "SELECT * FROM t1, t2, t3");
            assertAnswer(
                "1|10|abc|2024-01-02|true",
                "SELECT * FROM t1, t2, t3");
            assertShape(
                "K:VARCHAR(3), B:DATE, K:NUMBER(5,0), A:NUMBER(38,0)",
                "SELECT * FROM t2, t1");
        } finally {
            answer(
                "DROP TABLE IF EXISTS t1");
            answer(
                "DROP TABLE IF EXISTS t2");
            answer(
                "DROP TABLE IF EXISTS t3");
        }
    }
}
