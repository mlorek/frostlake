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
 * A {@code ->>} chain of several statements fails as a scripting block's statement does (live-verified): the failing
 * stage's refusal is {@code Uncaught exception of type 'STATEMENT_ERROR' on line L at position P : …}, placed at the
 * stage's first token counted past the request's opening comments, and a refusal raised as the stage compiles counts
 * its own places from that token. A reference to the stage before the chain's first is {@code Statement null not
 * found}. A chain of one statement keeps its refusal unwrapped, and so does a refusal raised before any stage runs.
 */
public class FlowChainStageFailureTest extends MeasuredCellsTestSupport {

    @Test
    public void aFailingStageIsTheChainsStatementError() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT)");
        engine.execute("INSERT INTO fz VALUES (1)");
        assertCells(new String[][] {
            {"SELECT 1/0 ->> SELECT * FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : Division by zero"},
            {"SELECT 1 ->> SELECT 1/0 FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : Division by zero"},
            {"SELECT nope ->> SELECT * FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'NOPE'"},
            {"SELECT 1 ->> SELECT nope FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'NOPE'"},
            {"SELECT 1\n->> SELECT nope FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 4 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'NOPE'"},
            {"SELECT 1 ->>\n  SELECT nope FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'NOPE'"},
            {"SHOW TABLES IN SCHEMA nope2 ->> SELECT * FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL compilation "
                + "error: | Object does not exist, or operation cannot be performed."},
            {"SELECT 1 ->> SHOW TABLES IN SCHEMA nope2",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Object does not exist, or operation cannot be performed."},
            {"SELECT 1 ->> SELECT * FROM $2",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : Statement null "
                + "not found"},
            {"SELECT 1 ->> SELECT 'a'::INT FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : Numeric value 'a' "
                + "is not recognized"},
            {"SELECT id FROM fz FOR UPDATE ->> SELECT * FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : Unsupported "
                + "feature 'FOR UPDATE clause'."},
            {"SELECT 1 ->> SELECT 2 ->> SELECT 1/0 FROM $2",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : Division by zero"},
            {"SELECT 1 ->> SELECT 2 ->> SELECT nope FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'NOPE'"},
            {"SELECT 1 ->> INSERT INTO nosuch_fc VALUES (1)",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Table 'NOSUCH_FC' does not exist or not authorized. Your primary role <ROLE> must have "
                + "at least one privilege granted on TABLE NOSUCH_FC."},
            {"/* c */ SELECT 1 ->> SELECT nope FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'NOPE'"},
            {"SELECT 1 ->> SELECT * FROM $1 WHERE nope = 1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 23 | invalid identifier 'NOPE'"},
            {"SELECT 1 ->> SELECT 1/0",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : Division by zero"},
            {"SELECT 1/0 ->> SELECT 1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : Division by zero"},
            {"SELECT 1/0", "REFUSED Division by zero"},
            {"SELECT 1 ->> SELECT 2/0 FROM $1;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : Division by zero"},
            {"SELECT 1 ->> SELECT $1 + 1 FROM $1", "2"},
            {"SELECT 1 ->> SELECT 'x' || nope2 FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 14 | invalid identifier 'NOPE2'"},
            {"SELECT 1 ->> SELECT 1 ->> SELECT $1 FROM $3",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : Statement null "
                + "not found"},
            {"SELECT 1 ->> CREATE TABLE fz (id INT)",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Object 'FZ' already exists."},
            {"SELECT 1 ->> SELECT COUNT(*) FROM fz ->> SELECT 1/0 FROM $2",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 41 : Division by zero"},
        });
    }

    @Test
    public void refusalsBeforeAnyStageRuns() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT)");
        engine.execute("INSERT INTO fz VALUES (1)");
        assertCells(new String[][] {
            {"SELECT 1 ->> SELECT * FROM $1 LIMIT 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ''x''."},
            {"SELECT 1 ->> CALL nosuch_proc_fp()",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown function NOSUCH_PROC_FP."},
            {"SELECT 1 ->> SELECT * FROM fz WHERE id = 1/0",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : Division by zero"},
            {"SELECT 1 ->> SELECT * FROM $1 ->> SELECT 1/0 FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : Division by zero"},
            {"SELECT 1 ->> SELECT DEFAULT FROM $1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'DEFAULT'"},
            {"INSERT INTO fz VALUES (2) ->> SELECT 1/0",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 30 : Division by zero"},
            {"SELECT 1 ->> SELECT * FROM $1 ORDER BY 2",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | [2] is not a valid order by expression"},
        });
    }
}
