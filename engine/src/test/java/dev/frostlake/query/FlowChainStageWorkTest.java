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
 * Each stage of a ->> chain is a statement of its own: a later stage's failure leaves the work of the stages
 * before it, an INSERT, an UPDATE, a DELETE, a DDL statement and a stream a stage consumed alike, under
 * autocommit, inside an explicit transaction and with AUTOCOMMIT off, where the kept work commits or rolls back
 * with the transaction (all live-verified).
 */
public class FlowChainStageWorkTest extends MeasuredCellsTestSupport {

    @Test
    public void aLaterStagesFailureKeepsTheWorkBeforeIt() {
        assertAnswer("Table FZ737 successfully created.", "CREATE OR REPLACE TABLE fz737 (id INT)");
        assertAnswer("1", "INSERT INTO fz737 VALUES (1)");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : Division by zero",
            "INSERT INTO fz737 VALUES (2) ->> SELECT 1/0");
        assertAnswer("2", "SELECT COUNT(*) FROM fz737");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 66 : Division by zero",
            "INSERT INTO fz737 VALUES (3) ->> INSERT INTO fz737 VALUES (4) ->> SELECT 1/0");
        assertAnswer("4", "SELECT COUNT(*) FROM fz737");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : Division by zero",
            "SELECT 1/0 ->> INSERT INTO fz737 VALUES (5)");
        assertAnswer("4", "SELECT COUNT(*) FROM fz737");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Division by zero",
            "INSERT INTO fz737 SELECT 9 ->> SELECT * FROM $1 ->> SELECT 1/0");
        assertAnswer("1,2,3,4,9", "SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM fz737");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 48 : Division by zero",
            "UPDATE fz737 SET id = id + 100 WHERE id = 1 ->> SELECT 1/0");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : Division by zero",
            "DELETE FROM fz737 WHERE id = 9 ->> SELECT 1/0");
        assertAnswer("2,3,4,101", "SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM fz737");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : Division by zero",
            "CREATE OR REPLACE TABLE fz737b (a INT) ->> SELECT 1/0");
        assertAnswer("0", "SELECT COUNT(*) FROM fz737b");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : Division by zero",
            "INSERT INTO fz737 VALUES (6) ->> SELECT 1/0");
        assertAnswer("5", "SELECT COUNT(*) FROM fz737");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("4", "SELECT COUNT(*) FROM fz737");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 96 : Division by zero",
            "INSERT INTO fz737 VALUES (7) ->> INSERT INTO fz737 SELECT id + 1000 FROM fz737 WHERE id = 7 ->> SELECT "
                + "1/0");
        assertAnswer("2,3,4,7,101,1007", "SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM fz737");
    }

    @Test
    public void stagesUnderAutocommitOffAndAConsumedStream() {
        assertAnswer("Table FZ738 successfully created.", "CREATE OR REPLACE TABLE fz738 (id INT)");
        assertAnswer("Table SRC738 successfully created.", "CREATE OR REPLACE TABLE src738 (id INT)");
        assertAnswer("Table SINK738 successfully created.", "CREATE OR REPLACE TABLE sink738 (id INT)");
        assertAnswer("Stream ST738 successfully created.", "CREATE OR REPLACE STREAM st738 ON TABLE src738");
        assertAnswer("2", "INSERT INTO src738 VALUES (1), (2)");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 45 : Division by zero",
            "INSERT INTO sink738 SELECT id FROM st738 ->> SELECT 1/0");
        assertAnswer("2", "SELECT COUNT(*) FROM sink738");
        assertAnswer("0", "SELECT COUNT(*) FROM st738");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 109 : Division by "
                + "zero",
            "INSERT INTO fz738 VALUES (1) ->> CREATE OR REPLACE TABLE fz738b (a INT) ->> INSERT INTO fz738 VALUES "
                + "(2) ->> SELECT 1/0");
        assertAnswer("1,2", "SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM fz738");
        assertAnswer("Statement executed successfully.", "ALTER SESSION SET AUTOCOMMIT = FALSE");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : Division by zero",
            "INSERT INTO fz738 VALUES (3) ->> SELECT 1/0");
        assertAnswer("1,2,3", "SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM fz738");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("1,2", "SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM fz738");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 66 : Division by zero",
            "INSERT INTO fz738 VALUES (4) ->> INSERT INTO fz738 VALUES (5) ->> SELECT 1/0");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("Statement executed successfully.", "ALTER SESSION UNSET AUTOCOMMIT");
        assertAnswer("1,2,4,5", "SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM fz738");
    }
}
