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
 * ERROR_ON_NONDETERMINISTIC_UPDATE. With it set, an UPDATE that joins one target row to several source rows
 * is refused before anything is written, naming the row as it is stored, and every UPDATE's result has the
 * one column; without it the row takes one of the values and is counted as multi-joined. A MERGE refuses the
 * same way by default. Every answer is live-verified.
 */
public class NondeterministicUpdateTest extends MeasuredCellsTestSupport {

    @Test
    public void theParameterRefusesARowJoinedTwice() {
        try {
            engine.execute("CREATE OR REPLACE TABLE ut (id INT, v VARCHAR)");
            engine.execute("INSERT INTO ut VALUES (1, 'a'), (2, 'b')");
            assertAnswer(
                "1|1",
                "UPDATE ut SET v = s.v FROM (SELECT 1 AS id, 'x' AS v UNION ALL SELECT 1, 'y') s WHERE ut.id = s.id");
            engine.execute("ALTER SESSION SET ERROR_ON_NONDETERMINISTIC_UPDATE = TRUE");
            assertAnswer(
                "REFUSED Duplicate row detected during DML action | Row Values: [1, \"x\"]",
                "UPDATE ut SET v = s.v FROM (SELECT 1 AS id, 'x' AS v UNION ALL SELECT 1, 'y') s WHERE ut.id = s.id");
            assertAnswer(
                "1",
                "UPDATE ut SET v = s.v FROM (SELECT 2 AS id, 'z' AS v) s WHERE ut.id = s.id");
            assertAnswer(
                "REFUSED Duplicate row detected during DML action | Row Values: [1, \"x\"]",
                "UPDATE ut SET v = s.v FROM (SELECT 1 AS id, 'x' AS v UNION ALL SELECT 1, 'x') s WHERE ut.id = s.id");
            assertAnswer(
                "REFUSED Duplicate row detected during DML action | Row Values: [1, \"x\"]",
                "UPDATE ut SET v = s.v FROM (SELECT 1 AS id, 'x' AS v UNION ALL SELECT 1, 'y' UNION ALL SELECT 2, "
                + "'p' UNION ALL SELECT 2, 'q') s WHERE ut.id = s.id");
            assertAnswer(
                "0",
                "UPDATE ut SET v = s.v FROM (SELECT 9 AS id, 'x' AS v UNION ALL SELECT 9, 'y') s WHERE ut.id = s.id");
            assertAnswer(
                "REFUSED Duplicate row detected during DML action | Row Values: [1, \"x\"]",
                "UPDATE ut SET v = s.v FROM (SELECT 1 AS id, NULL AS v UNION ALL SELECT 1, 'y') s WHERE ut.id = s.id");
            assertAnswer(
                "1|x ;; 2|z",
                "SELECT id, v FROM ut ORDER BY id");
            engine.execute("ALTER SESSION SET ERROR_ON_NONDETERMINISTIC_UPDATE = FALSE");
            assertAnswer(
                "1|1",
                "UPDATE ut SET v = s.v FROM (SELECT 1 AS id, 'x' AS v UNION ALL SELECT 1, 'y') s WHERE ut.id = s.id");
            engine.execute("ALTER SESSION UNSET ERROR_ON_NONDETERMINISTIC_UPDATE");
            assertAnswer(
                "REFUSED Duplicate row detected during DML action | Row Values: [1, \"x\"]",
                "MERGE INTO ut USING (SELECT 1 AS id, 'x' AS v UNION ALL SELECT 1, 'y') s ON ut.id = s.id WHEN "
                + "MATCHED THEN UPDATE SET v = s.v");
            engine.execute("ALTER SESSION SET ERROR_ON_NONDETERMINISTIC_MERGE = FALSE");
            assertAnswer(
                "1",
                "MERGE INTO ut USING (SELECT 1 AS id, 'x' AS v UNION ALL SELECT 1, 'x') s ON ut.id = s.id WHEN "
                + "MATCHED THEN UPDATE SET v = s.v");
            engine.execute("ALTER SESSION UNSET ERROR_ON_NONDETERMINISTIC_MERGE");
            assertAnswer(
                "ERROR_ON_NONDETERMINISTIC_UPDATE|false|false||raise an error when attempting to update a row "
                + "that joins many rows|BOOLEAN",
                "SHOW PARAMETERS LIKE 'ERROR_ON_NONDETERMINISTIC_UPDATE'");
        } finally {
            engine.execute("ALTER SESSION UNSET ERROR_ON_NONDETERMINISTIC_UPDATE");
            engine.execute("ALTER SESSION UNSET ERROR_ON_NONDETERMINISTIC_MERGE");
        }
    }

    @Test
    public void theResultAndTheRowNamed() {
        try {
            engine.execute("CREATE OR REPLACE TABLE ut (id INT, v VARCHAR)");
            engine.execute("INSERT INTO ut VALUES (1, 'a'), (2, 'b')");
            engine.execute("CREATE OR REPLACE TABLE us (id INT, v VARCHAR)");
            engine.execute("INSERT INTO us VALUES (1, 'x'), (1, 'y'), (2, 'z')");
            engine.execute("CREATE OR REPLACE TABLE uw (id INT, v VARCHAR, d DATE, b BOOLEAN, f FLOAT, n NUMBER(10,2), x "
            + "VARIANT, z VARCHAR, t TIMESTAMP_NTZ, a ARRAY)");
            engine.execute("INSERT INTO uw SELECT 1, 'te\"xt', '2024-01-02', TRUE, 1.5, 2.50, PARSE_JSON('{\"k\":1}'), NULL, "
            + "'2024-01-02 03:04:05', ARRAY_CONSTRUCT(1, 'q')");
            assertAnswer(
                "1|0",
                "UPDATE ut SET v = s.v FROM (SELECT 2 AS id, 'z' AS v) s WHERE ut.id = s.id");
            assertShape(
                "number of rows updated:NUMBER(19,0), number of multi-joined rows updated:NUMBER(19,0)",
                "UPDATE ut SET v = s.v FROM (SELECT 2 AS id, 'z' AS v) s WHERE ut.id = s.id");
            assertShape(
                "number of rows updated:NUMBER(19,0), number of multi-joined rows updated:NUMBER(19,0)",
                "UPDATE ut SET v = 'k' WHERE id = 2");
            engine.execute("ALTER SESSION SET ERROR_ON_NONDETERMINISTIC_UPDATE = TRUE");
            assertShape(
                "number of rows updated:NUMBER(19,0)",
                "UPDATE ut SET v = s.v FROM (SELECT 2 AS id, 'z' AS v) s WHERE ut.id = s.id");
            assertShape(
                "number of rows updated:NUMBER(19,0)",
                "UPDATE ut SET v = 'k' WHERE id = 2");
            assertAnswer(
                "1",
                "UPDATE ut SET v = 'm' WHERE id = 2");
            assertAnswer(
                "REFUSED Duplicate row detected during DML action | Row Values: [1, \"a\"]",
                "UPDATE ut SET v = s.v FROM us s WHERE ut.id = s.id");
            assertAnswer(
                "2",
                "UPDATE ut SET v = s.v FROM us s WHERE ut.id = s.id AND s.v <> 'y'");
            assertAnswer(
                "REFUSED Duplicate row detected during DML action | Row Values: [2, \"z\"]",
                "UPDATE ut SET v = s.v FROM (SELECT 2 AS id, 'p' AS v UNION ALL SELECT 2, 'q') s WHERE ut.id = s.id");
            assertAnswer(
                "REFUSED Duplicate row detected during DML action | Row Values: [1, \"x\"]",
                "UPDATE ut SET v = 'c' FROM us s");
            assertAnswer(
                "1|x ;; 2|z",
                "SELECT id, v FROM ut ORDER BY id");
            engine.execute("BEGIN");
            assertAnswer(
                "REFUSED Duplicate row detected during DML action | Row Values: [1, \"x\"]",
                "UPDATE ut SET v = s.v FROM us s WHERE ut.id = s.id");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            engine.execute("ROLLBACK");
            engine.execute("ALTER SESSION UNSET ERROR_ON_NONDETERMINISTIC_UPDATE");
            assertAnswer(
                "1|1",
                "UPDATE uw SET v = s.v FROM us s WHERE uw.id = s.id");
        } finally {
            engine.execute("ROLLBACK");
            engine.execute("ALTER SESSION UNSET ERROR_ON_NONDETERMINISTIC_UPDATE");
        }
    }

    @Test
    public void theRowIsPrintedAsItIsStored() {
        try {
            engine.execute("CREATE OR REPLACE TABLE us (id INT, v VARCHAR)");
            engine.execute("INSERT INTO us VALUES (1, 'x'), (1, 'y'), (2, 'z')");
            engine.execute("CREATE OR REPLACE TABLE uw (id INT, v VARCHAR, d DATE, b BOOLEAN, f FLOAT, n NUMBER(10,2), z "
            + "VARCHAR, t TIMESTAMP_NTZ, c NUMBER(5,0), e BOOLEAN)");
            engine.execute("INSERT INTO uw SELECT 1, 'te\"xt', '2024-01-02', TRUE, 1.5, 2.50, NULL, '2024-01-02 03:04:05', 7, FALSE");
            engine.execute("ALTER SESSION SET ERROR_ON_NONDETERMINISTIC_UPDATE = TRUE");
            assertAnswer(
                "REFUSED Duplicate row detected during DML action | Row Values: [1, \"te\"xt\", 19724, 1, 1.5, 250, "
                + "NULL, 1704164645000000000, 7, 0]",
                "UPDATE uw SET v = s.v FROM us s WHERE uw.id = s.id");
            assertAnswer(
                "te\"xt",
                "SELECT v FROM uw");
            engine.execute("ALTER SESSION UNSET ERROR_ON_NONDETERMINISTIC_UPDATE");
            assertAnswer(
                "REFUSED Duplicate row detected during DML action | Row Values: [1, \"te\"xt\", 19724, 1, 1.5, 250, "
                + "NULL, 1704164645000000000, 7, 0]",
                "MERGE INTO uw USING us s ON uw.id = s.id WHEN MATCHED THEN UPDATE SET v = s.v");
            assertAnswer(
                "te\"xt",
                "SELECT v FROM uw");
        } finally {
            engine.execute("ALTER SESSION UNSET ERROR_ON_NONDETERMINISTIC_UPDATE");
        }
    }
}
