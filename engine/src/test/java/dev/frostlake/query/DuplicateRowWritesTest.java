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
 * Rows of the same values are rows of their own: an UPDATE writes each of them and a DELETE removes each of
 * them - outside a transaction and inside one, among the rows the transaction itself inserted, after a ROLLBACK
 * put them back, when the table holds its own rows a second time, when every column is NULL, through a MERGE,
 * an UPDATE ... FROM, a DELETE ... USING and a subquery. Every answer is live-verified.
 */
public class DuplicateRowWritesTest extends MeasuredCellsTestSupport {

    @Test
    public void everyRowOfTheSameValuesIsWritten() {
        engine.execute(
            "CREATE OR REPLACE TABLE dup (c INT, d VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE TABLE src (c INT, d VARCHAR)");
        try {
            assertAnswer(
                "4",
                "INSERT INTO dup VALUES (1, 'x'), (1, 'x'), (1, 'x'), (2, 'y')");
            assertAnswer(
                "3|0",
                "UPDATE dup SET c = c + 10 WHERE d = 'x'");
            assertAnswer(
                "2|1 ;; 11|3",
                "SELECT c, COUNT(*) FROM dup GROUP BY c ORDER BY c");
            assertAnswer(
                "3",
                "DELETE FROM dup WHERE c = 11");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM dup");
            assertAnswer(
                "2",
                "INSERT INTO dup VALUES (5, 'z'), (5, 'z')");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "2|0",
                "UPDATE dup SET d = 'w' WHERE c = 5");
            assertAnswer(
                "w|2",
                "SELECT d, COUNT(*) FROM dup WHERE c = 5 GROUP BY d ORDER BY d");
            assertAnswer(
                "2",
                "INSERT INTO dup VALUES (6, 'p'), (6, 'p')");
            assertAnswer(
                "2|0",
                "UPDATE dup SET c = 7 WHERE c = 6");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM dup WHERE c = 7");
            assertAnswer(
                "2",
                "DELETE FROM dup WHERE c = 7");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM dup WHERE c = 7");
            assertAnswer(
                "2",
                "DELETE FROM dup WHERE c = 5");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM dup");
            assertAnswer(
                "Statement executed successfully.",
                "COMMIT");
            assertAnswer(
                "2|y",
                "SELECT c, d FROM dup ORDER BY c");
            assertAnswer(
                "1",
                "INSERT INTO dup VALUES (2, 'y')");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "2",
                "DELETE FROM dup");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM dup");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM dup");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "2|0",
                "UPDATE dup SET d = 'r'");
            assertAnswer(
                "r|2",
                "SELECT d, COUNT(*) FROM dup GROUP BY d ORDER BY d");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "y|2",
                "SELECT d, COUNT(*) FROM dup GROUP BY d ORDER BY d");
            assertAnswer(
                "2",
                "INSERT INTO dup SELECT * FROM dup");
            assertAnswer(
                "4|0",
                "UPDATE dup SET d = 'q'");
            assertAnswer(
                "q|4",
                "SELECT d, COUNT(*) FROM dup GROUP BY d ORDER BY d");
            assertAnswer(
                "1",
                "INSERT INTO src VALUES (2, 'm')");
            assertAnswer(
                "4",
                "MERGE INTO dup USING src ON dup.c = src.c WHEN MATCHED THEN UPDATE SET d = src.d");
            assertAnswer(
                "m|4",
                "SELECT d, COUNT(*) FROM dup GROUP BY d ORDER BY d");
            assertAnswer(
                "4",
                "MERGE INTO dup USING src ON dup.c = src.c WHEN MATCHED THEN DELETE");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM dup");
            assertAnswer(
                "2",
                "INSERT INTO dup VALUES (3, 'u'), (3, 'u')");
            assertAnswer(
                "1",
                "INSERT INTO src VALUES (3, 'n')");
            assertAnswer(
                "2|0",
                "UPDATE dup SET d = src.d FROM src WHERE dup.c = src.c");
            assertAnswer(
                "n|2",
                "SELECT d, COUNT(*) FROM dup GROUP BY d ORDER BY d");
            assertAnswer(
                "2",
                "DELETE FROM dup USING src WHERE dup.c = src.c");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM dup");
            assertAnswer(
                "3",
                "INSERT INTO dup VALUES (4, 'a'), (4, 'a'), (4, 'a')");
            assertAnswer(
                "3",
                "DELETE FROM dup WHERE c IN (SELECT c FROM dup WHERE c = 4)");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM dup");
            assertAnswer(
                "2",
                "INSERT INTO dup VALUES (NULL, NULL), (NULL, NULL)");
            assertAnswer(
                "2|0",
                "UPDATE dup SET c = 9 WHERE c IS NULL");
            assertAnswer(
                "9|2",
                "SELECT c, COUNT(*) FROM dup GROUP BY c ORDER BY c");
            assertAnswer(
                "2",
                "DELETE FROM dup WHERE d IS NULL");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM dup");
            assertAnswer(
                "2",
                "INSERT INTO dup VALUES (8, 'h'), (8, 'h')");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE dup");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM dup");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
        } finally {
            answer(
                "ROLLBACK");
        }
    }
}
