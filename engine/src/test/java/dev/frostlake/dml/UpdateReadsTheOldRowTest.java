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
package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * Every SET value of an UPDATE and of a MERGE's WHEN MATCHED THEN UPDATE reads the row as it was before the
 * statement - SET a = b, b = a swaps two columns, and a value reads the old value of a column the list sets
 * too - and a subquery in a plain UPDATE's SET clause over the table being updated sees it as the statement
 * began, inside an explicit transaction as well. Of two SET values that fail, a plain UPDATE reports the one of
 * the column the table holds first; UPDATE ... FROM and MERGE report the one written first (live-verified).
 */
public class UpdateReadsTheOldRowTest extends MeasuredCellsTestSupport {

    @Test
    public void setValuesReadTheOldRow() {
        try {
            assertAnswer(
                "Table T successfully created.",
                "CREATE OR REPLACE TABLE t (id INT, a INT, b INT)");
            assertAnswer(
                "2",
                "INSERT INTO t VALUES (1, 10, 20), (2, 30, 40)");
            assertAnswer(
                "2|0",
                "UPDATE t SET a = b, b = a");
            assertAnswer(
                "1|20|10 ;; 2|40|30",
                "SELECT * FROM t ORDER BY id");
            assertAnswer(
                "2|0",
                "UPDATE t SET b = a, a = b");
            assertAnswer(
                "1|10|20 ;; 2|30|40",
                "SELECT * FROM t ORDER BY id");
            assertAnswer(
                "Table N successfully created.",
                "CREATE OR REPLACE TABLE n (id INT, x INT, y INT)");
            assertAnswer(
                "1",
                "INSERT INTO n VALUES (1, 5, 0)");
            assertAnswer(
                "1|0",
                "UPDATE n SET y = x + 1, x = 0");
            assertAnswer(
                "1|0|6",
                "SELECT * FROM n");
            assertAnswer(
                "1|0",
                "UPDATE n SET x = 7, y = x");
            assertAnswer(
                "1|7|0",
                "SELECT * FROM n");
            assertAnswer(
                "Table M successfully created.",
                "CREATE OR REPLACE TABLE m (id INT, a INT, b INT)");
            assertAnswer(
                "1",
                "INSERT INTO m VALUES (1, 10, 20)");
            assertAnswer(
                "1",
                "MERGE INTO m USING (SELECT 1 AS id) x ON m.id = x.id WHEN MATCHED THEN UPDATE SET a = b, b = a");
            assertAnswer(
                "1|20|10",
                "SELECT * FROM m");
            assertAnswer(
                "1",
                "MERGE INTO m USING (SELECT 1 AS id) x ON m.id = x.id WHEN MATCHED THEN UPDATE SET b = a + 1, a = "
                    + "0");
            assertAnswer(
                "1|0|21",
                "SELECT * FROM m");
            assertAnswer(
                "Table F successfully created.",
                "CREATE OR REPLACE TABLE f (id INT, a INT, b INT)");
            assertAnswer(
                "1",
                "INSERT INTO f VALUES (1, 10, 20)");
            assertAnswer(
                "Table S successfully created.",
                "CREATE OR REPLACE TABLE s (id INT)");
            assertAnswer(
                "1",
                "INSERT INTO s VALUES (1)");
            assertAnswer(
                "1|0",
                "UPDATE f SET a = b, b = a FROM s WHERE f.id = s.id");
            assertAnswer(
                "1|20|10",
                "SELECT * FROM f");
            assertAnswer(
                "Table VS successfully created.",
                "CREATE OR REPLACE TABLE vs (id INT, a VARCHAR, b VARCHAR)");
            assertAnswer(
                "1",
                "INSERT INTO vs VALUES (1, 'A', 'B')");
            assertAnswer(
                "1|0",
                "UPDATE vs SET b = a, a = b");
            assertAnswer(
                "1|B|A",
                "SELECT * FROM vs");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "2|0",
                "UPDATE t SET a = b, b = a");
            assertAnswer(
                "Statement executed successfully.",
                "COMMIT");
            assertAnswer(
                "1|20|10 ;; 2|40|30",
                "SELECT * FROM t ORDER BY id");
            assertAnswer(
                "REFUSED SQL compilation error: | duplicate column name 'A'",
                "UPDATE t SET a = 1, a = 2");
            assertAnswer(
                "1|20|10 ;; 2|40|30",
                "SELECT * FROM t ORDER BY id");
            assertAnswer(
                "REFUSED DML operation to table T failed on column A with error: Division by zero",
                "UPDATE t SET a = 1/0, b = 'x'::INT");
            assertAnswer(
                "REFUSED DML operation to table T failed on column A with error: Division by zero",
                "UPDATE t SET b = 'x'::INT, a = 1/0");
            assertAnswer(
                "0|0",
                "UPDATE t SET b = 'x'::INT, a = 1/0 WHERE id = 3");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "MERGE INTO m USING (SELECT 1 AS id) x ON m.id = x.id WHEN MATCHED THEN UPDATE SET b = 'x'::INT, "
                    + "a = 1/0");
            assertAnswer(
                "1|20|10 ;; 2|40|30",
                "SELECT * FROM t ORDER BY id");
        } finally {
            answer(
                "ROLLBACK");
            answer(
                "DROP TABLE IF EXISTS t");
            answer(
                "DROP TABLE IF EXISTS n");
            answer(
                "DROP TABLE IF EXISTS m");
            answer(
                "DROP TABLE IF EXISTS f");
            answer(
                "DROP TABLE IF EXISTS s");
            answer(
                "DROP TABLE IF EXISTS vs");
        }
    }

    @Test
    public void aSetSubquerySeesTheTableAsTheStatementBegan() {
        try {
            assertAnswer(
                "Table T successfully created.",
                "CREATE OR REPLACE TABLE t (id INT, v INT)");
            assertAnswer(
                "3",
                "INSERT INTO t VALUES (1, 1), (2, 2), (3, 3)");
            assertAnswer(
                "3|0",
                "UPDATE t SET v = (SELECT SUM(v) FROM t)");
            assertAnswer(
                "1|6 ;; 2|6 ;; 3|6",
                "SELECT * FROM t ORDER BY id");
            assertAnswer(
                "Table U successfully created.",
                "CREATE OR REPLACE TABLE u (id INT, v INT)");
            assertAnswer(
                "4",
                "INSERT INTO u VALUES (1, 10), (2, 20), (3, 30), (4, 40)");
            assertAnswer(
                "4|0",
                "UPDATE u SET v = (SELECT MAX(v) FROM u) + 1");
            assertAnswer(
                "1|41 ;; 2|41 ;; 3|41 ;; 4|41",
                "SELECT * FROM u ORDER BY id");
            assertAnswer(
                "Table C successfully created.",
                "CREATE OR REPLACE TABLE c (id INT, v INT)");
            assertAnswer(
                "3",
                "INSERT INTO c VALUES (1, 1), (2, 2), (3, 3)");
            assertAnswer(
                "3|0",
                "UPDATE c SET v = (SELECT SUM(c2.v) FROM c c2 WHERE c2.id <> c.id)");
            assertAnswer(
                "1|5 ;; 2|4 ;; 3|3",
                "SELECT * FROM c ORDER BY id");
            assertAnswer(
                "Table R successfully created.",
                "CREATE OR REPLACE TABLE r (id INT, v INT)");
            assertAnswer(
                "3",
                "INSERT INTO r VALUES (1, 1), (2, 2), (3, 3)");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "3|0",
                "UPDATE r SET v = (SELECT SUM(v) FROM r)");
            assertAnswer(
                "1|6 ;; 2|6 ;; 3|6",
                "SELECT * FROM r ORDER BY id");
            assertAnswer(
                "Statement executed successfully.",
                "COMMIT");
            assertAnswer(
                "1|6 ;; 2|6 ;; 3|6",
                "SELECT * FROM r ORDER BY id");
            assertAnswer(
                "Table ONE successfully created.",
                "CREATE OR REPLACE TABLE one (k INT)");
            assertAnswer(
                "1",
                "INSERT INTO one VALUES (1)");
            assertAnswer(
                "Table W successfully created.",
                "CREATE OR REPLACE TABLE w (id INT, v INT)");
            assertAnswer(
                "3",
                "INSERT INTO w VALUES (1, 1), (2, 2), (3, 3)");
            assertAnswer(
                "3|0",
                "UPDATE w SET v = (SELECT SUM(v) FROM w) FROM one");
            assertAnswer(
                "1|6 ;; 2|6 ;; 3|6",
                "SELECT * FROM w ORDER BY id");
            assertAnswer(
                "Table MT successfully created.",
                "CREATE OR REPLACE TABLE mt (id INT, v INT)");
            assertAnswer(
                "3",
                "INSERT INTO mt VALUES (1, 1), (2, 2), (3, 3)");
            assertAnswer(
                "3",
                "MERGE INTO mt USING (SELECT id FROM mt) x ON mt.id = x.id WHEN MATCHED THEN UPDATE SET v = "
                    + "(SELECT SUM(v) FROM mt)");
            assertAnswer(
                "1|6 ;; 2|6 ;; 3|6",
                "SELECT * FROM mt ORDER BY id");
            assertAnswer(
                "2|0",
                "UPDATE t SET v = (SELECT COUNT(*) FROM t WHERE v > 6) WHERE id < 3");
            assertAnswer(
                "1|0 ;; 2|0 ;; 3|6",
                "SELECT * FROM t ORDER BY id");
            assertAnswer(
                "3|0",
                "UPDATE c SET v = v + (SELECT MIN(v) FROM c)");
            assertAnswer(
                "1|8 ;; 2|7 ;; 3|6",
                "SELECT * FROM c ORDER BY id");
        } finally {
            answer(
                "ROLLBACK");
            answer(
                "DROP TABLE IF EXISTS t");
            answer(
                "DROP TABLE IF EXISTS u");
            answer(
                "DROP TABLE IF EXISTS c");
            answer(
                "DROP TABLE IF EXISTS r");
            answer(
                "DROP TABLE IF EXISTS one");
            answer(
                "DROP TABLE IF EXISTS w");
            answer(
                "DROP TABLE IF EXISTS mt");
        }
    }

    @Test
    public void whichFailingValueIsReported() {
        try {
            assertAnswer(
                "Table EO successfully created.",
                "CREATE OR REPLACE TABLE eo (id INT, z INT, y INT)");
            assertAnswer(
                "1",
                "INSERT INTO eo VALUES (1, 1, 1)");
            assertAnswer(
                "REFUSED DML operation to table EO failed on column Z with error: Numeric value 'x' is not "
                    + "recognized",
                "UPDATE eo SET y = 1/0, z = 'x'::INT");
            assertAnswer(
                "REFUSED DML operation to table EO failed on column Z with error: Numeric value 'x' is not "
                    + "recognized",
                "UPDATE eo SET z = 'x'::INT, y = 1/0");
            assertAnswer(
                "REFUSED DML operation to table EO failed on column Z with error: Numeric value 'x1' is not "
                    + "recognized",
                "UPDATE eo SET y = 1/(id - 1), z = ('x' || id)::INT");
            assertAnswer(
                "REFUSED DML operation to table EO failed on column Z with error: Numeric value 'x1' is not "
                    + "recognized",
                "UPDATE eo SET z = ('x' || id)::INT, y = 1/(id - 1)");
            assertAnswer(
                "REFUSED Division by zero",
                "UPDATE eo SET y = 1/0, z = 'x'::INT FROM (SELECT 1 AS k) s WHERE eo.id = s.k");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "UPDATE eo SET z = 'x'::INT, y = 1/0 FROM (SELECT 1 AS k) s WHERE eo.id = s.k");
            assertAnswer(
                "REFUSED Division by zero",
                "MERGE INTO eo USING (SELECT 1 AS id) s ON eo.id = s.id WHEN MATCHED THEN UPDATE SET y = 1/0, z = "
                    + "'x'::INT");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "MERGE INTO eo USING (SELECT 1 AS id) s ON eo.id = s.id WHEN MATCHED THEN UPDATE SET z = 'x'::INT,"
                    + " y = 1/0");
            assertAnswer(
                "REFUSED DML operation to table EO failed on column Z with error: Numeric value 'x' is not "
                    + "recognized",
                "UPDATE eo SET y = 'q'::INT, z = 'x'::INT");
            assertAnswer(
                "REFUSED DML operation to table EO failed on column Z with error: Numeric value 'x' is not "
                    + "recognized",
                "UPDATE eo SET z = 'x'::INT, y = 'q'::INT");
            assertAnswer(
                "1|0",
                "UPDATE eo SET y = 1, z = y + 1, id = z + 1");
            assertAnswer(
                "2|2|1",
                "SELECT * FROM eo");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "REFUSED DML operation to table EO failed on column Z with error: Numeric value 'x' is not "
                    + "recognized",
                "UPDATE eo SET y = 1/0, z = 'x'::INT");
            assertAnswer(
                "REFUSED DML operation to table EO failed on column Z with error: Numeric value 'x' is not "
                    + "recognized",
                "UPDATE eo SET z = 'x'::INT, y = 1/0");
        } finally {
            answer(
                "ROLLBACK");
            answer(
                "DROP TABLE IF EXISTS eo");
        }
    }
}
