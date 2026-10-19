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
 * Time travel to a statement of the session's own open transaction. AT reads the table as the transaction
 * saw it once the statement had run, its uncommitted changes included, and BEFORE as it saw it before; a
 * statement that read, or that wrote another table, names its own moment all the same, and so does a time.
 * A TRUNCATE is one of the transaction's statements and is rolled back with it. Once the transaction
 * commits, each of its statements reads as the commit; once it rolls back, as if it had never run. Every
 * answer is live-verified.
 */
public class StatementAnchorInOpenTransactionTest extends MeasuredCellsTestSupport {

    @Test
    public void aStatementOfTheOpenTransactionReadsItsOwnChanges() {
        try {
            engine.execute("CREATE OR REPLACE TABLE t9 (a INT)");
            engine.execute("INSERT INTO t9 VALUES (1), (4)");
            engine.execute("BEGIN");
            engine.execute("INSERT INTO t9 (a) VALUES (7)");
            engine.execute("SET q = LAST_QUERY_ID()");
            assertAnswer(
                "1 ;; 4 ;; 7",
                "SELECT a FROM t9 AT(STATEMENT => $q) ORDER BY a");
            assertAnswer(
                "1 ;; 4",
                "SELECT a FROM t9 BEFORE(STATEMENT => $q) ORDER BY a");
            engine.execute("INSERT INTO t9 (a) VALUES (9)");
            engine.execute("SET q2 = LAST_QUERY_ID()");
            assertAnswer(
                "1 ;; 4 ;; 7",
                "SELECT a FROM t9 AT(STATEMENT => $q) ORDER BY a");
            assertAnswer(
                "1 ;; 4 ;; 7 ;; 9",
                "SELECT a FROM t9 AT(STATEMENT => $q2) ORDER BY a");
            assertAnswer(
                "1 ;; 4 ;; 7",
                "SELECT a FROM t9 BEFORE(STATEMENT => $q2) ORDER BY a");
            engine.execute("UPDATE t9 SET a = a + 100 WHERE a = 7");
            engine.execute("SET q3 = LAST_QUERY_ID()");
            assertAnswer(
                "1 ;; 4 ;; 9 ;; 107",
                "SELECT a FROM t9 AT(STATEMENT => $q3) ORDER BY a");
            assertAnswer(
                "1 ;; 4 ;; 7 ;; 9",
                "SELECT a FROM t9 BEFORE(STATEMENT => $q3) ORDER BY a");
            engine.execute("DELETE FROM t9 WHERE a = 1");
            engine.execute("SET q4 = LAST_QUERY_ID()");
            assertAnswer(
                "4 ;; 9 ;; 107",
                "SELECT a FROM t9 AT(STATEMENT => $q4) ORDER BY a");
            assertAnswer(
                "1 ;; 4 ;; 7",
                "SELECT a FROM t9 AT(STATEMENT => $q) ORDER BY a");
            assertAnswer(
                "4 ;; 9 ;; 107",
                "SELECT a FROM t9 ORDER BY a");
            engine.execute("ROLLBACK");
            assertAnswer(
                "1 ;; 4",
                "SELECT a FROM t9 AT(STATEMENT => $q) ORDER BY a");
            assertAnswer(
                "1 ;; 4",
                "SELECT a FROM t9 ORDER BY a");
            engine.execute("BEGIN");
            engine.execute("INSERT INTO t9 (a) VALUES (20)");
            engine.execute("SET q5 = LAST_QUERY_ID()");
            engine.execute("COMMIT");
            assertAnswer(
                "1 ;; 4 ;; 20",
                "SELECT a FROM t9 AT(STATEMENT => $q5) ORDER BY a");
            assertAnswer(
                "1 ;; 4",
                "SELECT a FROM t9 BEFORE(STATEMENT => $q5) ORDER BY a");
        } finally {
            engine.execute("ROLLBACK");
        }
    }

    @Test
    public void aPointNamesTheTransactionAsItThenStood() {
        try {
            engine.execute("CREATE OR REPLACE TABLE t9 (a INT)");
            engine.execute("INSERT INTO t9 VALUES (1), (4)");
            engine.execute("CREATE OR REPLACE TABLE t8 (a INT)");
            engine.execute("INSERT INTO t8 VALUES (100)");
            engine.execute("BEGIN");
            engine.execute("INSERT INTO t9 (a) VALUES (7)");
            engine.execute("SET q1 = LAST_QUERY_ID()");
            engine.execute("INSERT INTO t8 (a) VALUES (200)");
            engine.execute("SET q2 = LAST_QUERY_ID()");
            engine.execute("INSERT INTO t9 (a) VALUES (9)");
            engine.execute("SET q3 = LAST_QUERY_ID()");
            assertAnswer(
                "1 ;; 4 ;; 7",
                "SELECT a FROM t9 AT(STATEMENT => $q2) ORDER BY a");
            assertAnswer(
                "1 ;; 4 ;; 7",
                "SELECT a FROM t9 BEFORE(STATEMENT => $q2) ORDER BY a");
            assertAnswer(
                "100",
                "SELECT a FROM t8 AT(STATEMENT => $q1) ORDER BY a");
            assertAnswer(
                "100",
                "SELECT a FROM t8 BEFORE(STATEMENT => $q2) ORDER BY a");
            engine.execute("SELECT COUNT(*) FROM t9");
            engine.execute("SET q4 = LAST_QUERY_ID()");
            assertAnswer(
                "1 ;; 4 ;; 7 ;; 9",
                "SELECT a FROM t9 AT(STATEMENT => $q4) ORDER BY a");
            assertAnswer(
                "1 ;; 4 ;; 7 ;; 9",
                "SELECT a FROM t9 BEFORE(STATEMENT => $q4) ORDER BY a");
            assertAnswer(
                "1 ;; 4 ;; 7 ;; 9",
                "SELECT a FROM t9 AT(OFFSET => 0) ORDER BY a");
            engine.execute("SET q5 = LAST_QUERY_ID()");
            engine.execute("TRUNCATE TABLE t9");
            engine.execute("SET q6 = LAST_QUERY_ID()");
            assertAnswer(
                "",
                "SELECT a FROM t9 AT(STATEMENT => $q6) ORDER BY a");
            assertAnswer(
                "1 ;; 4 ;; 7 ;; 9",
                "SELECT a FROM t9 BEFORE(STATEMENT => $q6) ORDER BY a");
            assertAnswer(
                "1 ;; 4 ;; 7",
                "SELECT a FROM t9 AT(STATEMENT => $q1) ORDER BY a");
            assertAnswer(
                "1|1 ;; 4|4 ;; 7|7",
                "SELECT x.a, y.a FROM t9 AT(STATEMENT => $q1) x JOIN t9 AT(STATEMENT => $q3) y ON x.a = y.a ORDER BY 1");
            assertAnswer(
                "4",
                "SELECT (SELECT COUNT(*) FROM t9 AT(STATEMENT => $q3))");
            engine.execute("COMMIT");
            assertAnswer(
                "",
                "SELECT a FROM t9 AT(STATEMENT => $q1) ORDER BY a");
            assertAnswer(
                "",
                "SELECT a FROM t9 AT(STATEMENT => $q3) ORDER BY a");
            assertAnswer(
                "1 ;; 4",
                "SELECT a FROM t9 BEFORE(STATEMENT => $q3) ORDER BY a");
            assertAnswer(
                "",
                "SELECT a FROM t9 AT(STATEMENT => $q6) ORDER BY a");
            assertAnswer(
                "",
                "SELECT a FROM t9 AT(STATEMENT => $q2) ORDER BY a");
        } finally {
            engine.execute("ROLLBACK");
        }
    }

    @Test
    public void aStatementThatFailedNamesItsMomentToo() {
        try {
            engine.execute("CREATE OR REPLACE TABLE t9 (a INT)");
            engine.execute("INSERT INTO t9 VALUES (1), (4)");
            engine.execute("BEGIN");
            engine.execute("INSERT INTO t9 (a) VALUES (7)");
            assertAnswer(
                "REFUSED DML operation to table T9 failed on column A with error: Numeric value 'nope_g' is not "
                + "recognized",
                "INSERT INTO t9 (a) VALUES ('nope_g')");
            engine.execute("SET q = LAST_QUERY_ID()");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE query_id = $q "
                + "AND query_text ILIKE '%nope_g%'");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            engine.execute("INSERT INTO t9 (a) VALUES (9)");
            assertAnswer(
                "1 ;; 4 ;; 7",
                "SELECT a FROM t9 AT(STATEMENT => $q) ORDER BY a");
            assertAnswer(
                "1 ;; 4",
                "SELECT a FROM t9 BEFORE(STATEMENT => $q) ORDER BY a");
            assertAnswer(
                "1 ;; 4 ;; 7 ;; 9",
                "SELECT a FROM t9 ORDER BY a");
        } finally {
            engine.execute("ROLLBACK");
        }
    }

    @Test
    public void aTruncateBelongsToItsTransaction() {
        try {
            engine.execute("CREATE OR REPLACE TABLE tt (a INT)");
            engine.execute("INSERT INTO tt VALUES (1), (4)");
            engine.execute("BEGIN");
            engine.execute("INSERT INTO tt VALUES (7)");
            engine.execute("TRUNCATE TABLE tt");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tt");
            engine.execute("INSERT INTO tt VALUES (9)");
            engine.execute("ROLLBACK");
            assertAnswer(
                "1 ;; 4",
                "SELECT a FROM tt ORDER BY a");
            engine.execute("BEGIN");
            engine.execute("DELETE FROM tt WHERE a = 1");
            engine.execute("TRUNCATE TABLE IF EXISTS tt");
            engine.execute("COMMIT");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tt");
        } finally {
            engine.execute("ROLLBACK");
        }
    }
}
