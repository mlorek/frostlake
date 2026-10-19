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
package dev.frostlake.session;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A query carries each SYSTEM$ABORT_TRANSACTION call it writes out once, while it compiles: the transaction is
 * over whether or not a row reaches the call — behind a false IFF, over an empty table, in a WHERE no row passes,
 * in an ORDER BY, a HAVING or a subquery, and under EXPLAIN — and every row that reaches it reads the one answer.
 * An argument that fails as it is read names no transaction, a NULL the plan must type from what stands beside
 * it is no constant, and a CALL's refusal of the argument's type names no place (live-verified).
 */
public class AbortTransactionCompiledOnceTest extends MeasuredCellsTestSupport {

    @Test
    public void theCallRunsThoughNoRowReachesIt() {
        try {
            answer(
                "CREATE OR REPLACE TABLE ab (v INT)");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "no",
                "SELECT IFF(FALSE, SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 'no')");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER) FROM ab");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id: ;; Aborted transaction id:",
                "SELECT LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23) FROM (VALUES (1), (2)) t(v)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "no",
                "SELECT CASE WHEN 1 = 0 THEN SYSTEM$ABORT_TRANSACTION($txid::NUMBER) ELSE 'no' END");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER) WHERE 1 = 0");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT 1 FROM ab WHERE SYSTEM$ABORT_TRANSACTION($txid::NUMBER) IS NOT NULL");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab WHERE SYSTEM$ABORT_TRANSACTION($txid::NUMBER) = 'x'");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "no",
                "SELECT NVL2(NULL, SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 'no')");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "null",
                "SELECT (SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER) WHERE FALSE)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "1",
                "WITH w AS (SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER) AS c) SELECT 1");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER) LIMIT 0");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id:|Could not abort txn: 17",
                "SELECT LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23) AS a, "
                    + "LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23) AS b");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT 'no' FROM (VALUES (1), (2)) t(v) WHERE v > 5 OR SYSTEM$ABORT_TRANSACTION($txid::NUMBER) "
                    + "IS NULL");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "x",
                "SELECT COALESCE('x', SYSTEM$ABORT_TRANSACTION($txid::NUMBER))");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT 1 WHERE FALSE AND SYSTEM$ABORT_TRANSACTION($txid::NUMBER) IS NULL");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "true ;; true ;; true",
                "SELECT LENGTH(SYSTEM$ABORT_TRANSACTION($txid::NUMBER)) > 0 FROM (VALUES (1), (2), (3)) t(v) "
                    + "ORDER BY v");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "2|Aborted transaction id:",
                "SELECT v, LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23) FROM (VALUES (1), (2)) t(v) WHERE v "
                    + "= 2");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "null",
                "SELECT MAX(LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23)) FROM ab");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT * FROM (SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER) AS c) WHERE 1 = 0");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "1",
                "SELECT 1 UNION ALL SELECT LENGTH(SYSTEM$ABORT_TRANSACTION($txid::NUMBER)) FROM ab");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "status|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE RESULT LAST_QUERY_ID()");
            assertAnswer(
                "1",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
        } finally {
            answer(
                "ROLLBACK;");
            answer(
                "UNSET txid");
        }
    }

    @Test
    public void inOtherClausesOfAQuery() {
        try {
            answer(
                "CREATE OR REPLACE TABLE ab (v INT)");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id:",
                "SELECT LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT 1 FROM ab ORDER BY SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT COUNT(*) FROM ab GROUP BY SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab HAVING MAX(SYSTEM$ABORT_TRANSACTION($txid::NUMBER)) IS NULL");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT v FROM ab QUALIFY ROW_NUMBER() OVER (ORDER BY SYSTEM$ABORT_TRANSACTION($txid::NUMBER)) = 1");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT 1 WHERE EXISTS (SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER) FROM ab)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT * FROM (SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER) AS c FROM ab)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id:",
                "WITH w AS (SELECT LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23) AS c) SELECT c FROM w");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT 1 FROM ab a JOIN ab b ON SYSTEM$ABORT_TRANSACTION($txid::NUMBER) IS NULL");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT v FROM ab WHERE v IN (SELECT LENGTH(SYSTEM$ABORT_TRANSACTION($txid::NUMBER)))");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id:",
                "SELECT LEFT(c, 23) FROM (SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER) AS c)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id: ;; Could not abort txn: 17",
                "SELECT LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23) UNION ALL SELECT "
                    + "LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id: ;; Aborted transaction id:",
                "SELECT LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23) FROM (VALUES (1), (2), (3)) t(v) WHERE "
                    + "v > 1");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "2|Aborted transaction id: ;; 1|Aborted transaction id:",
                "SELECT v, LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23) FROM (VALUES (1), (2)) t(v) ORDER BY "
                    + "v DESC");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id:|2",
                "SELECT LEFT(MAX(SYSTEM$ABORT_TRANSACTION($txid::NUMBER)), 23), COUNT(*) FROM (VALUES (1), (2)) "
                    + "t(v)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id: ;; no",
                "SELECT IFF(v = 1, LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23), 'no') FROM (VALUES (1), "
                    + "(2)) t(v) ORDER BY v");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23) FROM ab LIMIT 0");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT 1 FROM ab WHERE 1 = 0 AND SYSTEM$ABORT_TRANSACTION($txid::NUMBER) IS NULL");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id: ;; Aborted transaction id:",
                "SELECT (SELECT LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23)) FROM (VALUES (1), (2)) t(v)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
        } finally {
            answer(
                "ROLLBACK;");
            answer(
                "UNSET txid");
        }
    }

    @Test
    public void argumentsThatFailAndNullsTypedBesideThem() {
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(TO_NUMBER('abc'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(1/0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'NVL(SYSTEM$NULL_TO_FIXED(null), 12)'",
            "SELECT SYSTEM$ABORT_TRANSACTION(NVL(NULL, 12))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 12 AS \"12\" FROM (VALUES (null)) DUAL)'",
            "SELECT SYSTEM$ABORT_TRANSACTION((SELECT 12))");
        assertAnswer(
            "Could not abort txn: 12",
            "SELECT SYSTEM$ABORT_TRANSACTION(TO_NUMBER('12'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(1/2)");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(ABS(-12))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(12 + 1)");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(NVL(12, 13))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'IFNULL(SYSTEM$NULL_TO_FIXED(null), 12)'",
            "SELECT SYSTEM$ABORT_TRANSACTION(COALESCE(NULL, 12))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(IFF(TRUE, 12, 13))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(NULLIF(12, 13))");
        assertAnswer(
            "null",
            "SELECT SYSTEM$ABORT_TRANSACTION(TRY_TO_NUMBER('abc'))");
        assertAnswer(
            "Could not abort txn: 12",
            "SELECT SYSTEM$ABORT_TRANSACTION(TRY_TO_NUMBER('12'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(SQRT(-1))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION('abc'::NUMBER)");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(12::NUMBER)");
        assertAnswer(
            "null",
            "SELECT SYSTEM$ABORT_TRANSACTION(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'SYSTEM$NULL_TO_FIXED(null)'",
            "SELECT SYSTEM$ABORT_TRANSACTION(NULL::NUMBER)");
        assertAnswer(
            "Could not abort txn: 12",
            "SELECT SYSTEM$ABORT_TRANSACTION('1' || '2')");
        assertAnswer(
            "null",
            "SELECT SYSTEM$ABORT_TRANSACTION(NVL(NULL, NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'IFNULL(SYSTEM$NULL_TO_FIXED(null), 12)'",
            "SELECT SYSTEM$ABORT_TRANSACTION(IFNULL(NULL, 12))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'IFNULL(SYSTEM$NULL_TO_FIXED(null), IFNULL(SYSTEM$NULL_TO_FIXED(null), 12))'",
            "SELECT SYSTEM$ABORT_TRANSACTION(COALESCE(NULL, NULL, 12))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'NVL(SYSTEM$NULL_TO_TEXT(null), '12')'",
            "SELECT SYSTEM$ABORT_TRANSACTION(NVL(NULL, '12'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(NVL2(NULL, 1, 12))");
        assertAnswer(
            "Could not abort txn: 13",
            "SELECT SYSTEM$ABORT_TRANSACTION(IFF(NULL, 12, 13))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SYSTEM$NULL_TO_FIXED(null)) + 1'",
            "SELECT SYSTEM$ABORT_TRANSACTION(NULL + 1)");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(TO_NUMBER('1e400'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(TO_DOUBLE('abc'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(LN(0))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(DATE_PART(year, 'x'::DATE))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SYSTEM$ABORT_TRANSACTION': (DATE)",
            "SELECT SYSTEM$ABORT_TRANSACTION('x'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 12 AS \"C\" FROM (VALUES (null)) DUAL)'",
            "SELECT SYSTEM$ABORT_TRANSACTION((SELECT 12 AS c))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT (CAST(1 AS NUMBER(7,6))) / 0 AS \"1/0\" FROM (VALUES (null)) DUAL)'",
            "SELECT SYSTEM$ABORT_TRANSACTION((SELECT 1/0))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT TO_NUMBER('abc') AS \"'ABC'::NUMBER\" FROM (VALUES (null)) DUAL)'",
            "SELECT SYSTEM$ABORT_TRANSACTION((SELECT 'abc'::NUMBER))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(1 / 0 + 1)");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(MOD(1, 0))");
    }

    @Test
    public void aCallsPlaceAndMoreArguments() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SYSTEM$ABORT_TRANSACTION': (BOOLEAN)",
            "CALL SYSTEM$ABORT_TRANSACTION(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SYSTEM$ABORT_TRANSACTION': (DATE)",
            "CALL SYSTEM$ABORT_TRANSACTION('2024-01-02'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[SYSTEM$ABORT_TRANSACTION()], expected 1, got 0",
            "CALL SYSTEM$ABORT_TRANSACTION()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | too many arguments for function "
                + "[SYSTEM$ABORT_TRANSACTION(1, 2)] expected 1, got 2",
            "CALL SYSTEM$ABORT_TRANSACTION(1, 2)");
        assertAnswer(
            "Bad transaction id",
            "CALL SYSTEM$ABORT_TRANSACTION(TO_NUMBER('abc'))");
        assertAnswer(
            "Bad transaction id",
            "CALL SYSTEM$ABORT_TRANSACTION(1/0)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'NVL(SYSTEM$NULL_TO_FIXED(null), 12)'",
            "CALL SYSTEM$ABORT_TRANSACTION(NVL(NULL, 12))");
        assertAnswer(
            "Could not abort txn: 12",
            "CALL SYSTEM$ABORT_TRANSACTION(12)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SYSTEM$WAIT': (BOOLEAN)",
            "CALL SYSTEM$WAIT(TRUE)");
        assertAnswer(
            "BOOLEAN[SB1]",
            "CALL SYSTEM$TYPEOF(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                + "identifierName=SYSTEM$WAIT} needs to be constant, found 'TO_NUMBER('x', 18, 0)'",
            "CALL SYSTEM$WAIT('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SYSTEM$WAIT': (ARRAY)",
            "CALL SYSTEM$WAIT(ARRAY_CONSTRUCT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SYSTEM$ABORT_TRANSACTION': (BOOLEAN)",
            "SELECT SYSTEM$ABORT_TRANSACTION(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '((CAST(1 AS NUMBER(7,6))) / 0) + (SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT SYSTEM$ABORT_TRANSACTION(1/0 + NULL)");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(NVL(1/0, 12))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SYSTEM$ABORT_TRANSACTION': (DATE)",
            "SELECT SYSTEM$ABORT_TRANSACTION(TO_DATE('x'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(SQRT(-1)::NUMBER)");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(TO_NUMBER('abc') + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'IFF(CAST(null AS BOOLEAN), 12, SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT SYSTEM$ABORT_TRANSACTION(IFF(NULL, 12, NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'IFF(CAST(null AS BOOLEAN), SYSTEM$NULL_TO_FIXED(null), 13)'",
            "SELECT SYSTEM$ABORT_TRANSACTION(IFF(NULL, NULL, 13))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(IFF(NULL, 12, 13 + 1))");
        assertAnswer(
            "Could not abort txn: 13",
            "SELECT SYSTEM$ABORT_TRANSACTION(CASE WHEN NULL THEN 12 ELSE 13 END)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SYSTEM$NULL_TO_FIXED(null)) * 2'",
            "SELECT SYSTEM$ABORT_TRANSACTION(NULL * 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'NEGATE(SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT SYSTEM$ABORT_TRANSACTION(-NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'IFF((SYSTEM$NULL_TO_FIXED(null)) = 12, CAST(null AS NULL), null)'",
            "SELECT SYSTEM$ABORT_TRANSACTION(NULLIF(NULL, 12))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'GREATEST(SYSTEM$NULL_TO_FIXED(null), 12)'",
            "SELECT SYSTEM$ABORT_TRANSACTION(GREATEST(NULL, 12))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'ABS(SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT SYSTEM$ABORT_TRANSACTION(ABS(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SYSTEM$NULL_TO_TEXT(null)) || '12''",
            "SELECT SYSTEM$ABORT_TRANSACTION(NULL || '12')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'NVL(SYSTEM$NULL_TO_FIXED(null), 12)'",
            "SELECT SYSTEM$ABORT_TRANSACTION(NVL(NULL::NUMBER, 12))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'ZEROIFNULL(SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT SYSTEM$ABORT_TRANSACTION(ZEROIFNULL(NULL))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(IFF(FALSE, 12, 13))");
        assertAnswer(
            "Could not abort txn: 13",
            "SELECT SYSTEM$ABORT_TRANSACTION(IFF(NULL, 12, '13'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(IFF(NULL, 12, 13.5))");
        assertAnswer(
            "Could not abort txn: 13",
            "SELECT SYSTEM$ABORT_TRANSACTION(IFF(NULL, 12, 13::FLOAT))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(IFF(1 = 2, 12, 13))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(CASE WHEN FALSE THEN 12 ELSE 13 END)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'IFNULL(12, SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT SYSTEM$ABORT_TRANSACTION(COALESCE(12, NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'NVL(12, SYSTEM$NULL_TO_FIXED(null))'",
            "SELECT SYSTEM$ABORT_TRANSACTION(NVL(12, NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'IFF(12 IS NOT NULL, SYSTEM$NULL_TO_FIXED(null), 13)'",
            "SELECT SYSTEM$ABORT_TRANSACTION(NVL2(12, NULL, 13))");
        assertAnswer(
            "Could not abort txn: 12",
            "SELECT SYSTEM$ABORT_TRANSACTION(DECODE(NULL, NULL, 12, 13))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(TRY_TO_NUMBER('1e400'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION('1e400'::FLOAT)");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(1e400)");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(POWER(10, 400))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(TO_NUMBER('12', 'XX'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(TO_NUMBER('ff', 'XX'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(12 / 1)");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(MOD(12, 5))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(LENGTH('abcdefghijkl'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(PARSE_JSON('12'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(PARSE_JSON('x'))");
        assertAnswer(
            "Bad transaction id",
            "SELECT SYSTEM$ABORT_TRANSACTION(TO_VARIANT(12))");
        assertAnswer(
            "null",
            "CALL SYSTEM$ABORT_TRANSACTION(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'SYSTEM$ABORT_TRANSACTION': (DATE)",
            "CALL SYSTEM$ABORT_TRANSACTION('x'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SYSTEM$NULL_TO_FIXED(null)) + 1'",
            "CALL SYSTEM$ABORT_TRANSACTION(NULL + 1)");
        assertAnswer(
            "Could not abort txn: 13",
            "CALL SYSTEM$ABORT_TRANSACTION(IFF(NULL, 12, 13))");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 12 AS \"12\" FROM (VALUES (null)) DUAL)'",
            "CALL SYSTEM$ABORT_TRANSACTION((SELECT 12))");
        assertAnswer(
            "Bad transaction id",
            "CALL SYSTEM$ABORT_TRANSACTION(SQRT(-1))");
    }
}
