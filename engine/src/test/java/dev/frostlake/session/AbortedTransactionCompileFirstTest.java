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
 * In a session whose transaction SYSTEM$ABORT_TRANSACTION ended, a query that does not compile is refused for
 * that, and only one that compiles meets the aborted transaction (live-verified); and two echoes of an argument
 * the call refuses as no constant: a query FROM DUAL prints over {@code (VALUES (null)) "VALUES"}, DATE_PART and
 * EXTRACT as {@code EXTRACT(year from …)}, and CURRENT_DATE() is no constant.
 */
public class AbortedTransactionCompileFirstTest extends MeasuredCellsTestSupport {

    @Test
    public void whatDoesNotCompileIsRefusedFirst() {
        engine.execute("CREATE OR REPLACE TABLE ab (a INT)");
        engine.execute("CREATE OR REPLACE TABLE ab2 (a INT, b INT)");
        assertCells(new String[][] {
            {"BEGIN", "Statement executed successfully."},
            {"SET txid = (SELECT CURRENT_TRANSACTION())", "Statement executed successfully."},
        });
        assertShapedAnswer(
            "Aborted transaction id: <ID19>",
            "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
        assertCells(new String[][] {
            {"SELECT * FROM no_such_table_zq7",
                "REFUSED SQL compilation error: | Object 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"SELECT nosuch_col FROM ab",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH_COL'"},
            {"SELECT NOSUCH_FN_ZQ7()", "REFUSED SQL compilation error: | Unknown function NOSUCH_FN_ZQ7."},
            {"SELECT UPPER()",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[UPPER()], expected 1, got 0"},
            {"SELECT 1 FROM ab x, ab x", "REFUSED SQL compilation error: | duplicate alias 'X'"},
            {"SELECT 1/0",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"SELECT 'a'::NUMBER",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"SELECT 1",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"INSERT INTO ab VALUES (1)",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"UPDATE ab SET a = 1",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"SELECT a FROM ab WHERE nosuch_col = 1",
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCH_COL'"},
            {"CREATE TABLE ab3 (x INT)",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"SELECT a FROM ab2",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"SELECT ABS(1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2"},
            {"SELECT a FROM ab GROUP BY 2", "REFUSED SQL compilation error: | [2] is not a valid group by expression"},
            {"SELECT SUM(a) FROM ab GROUP BY a HAVING b > 1",
                "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'B'"},
            {"SELECT * FROM ab JOIN no_such_table_zq7 ON TRUE",
                "REFUSED SQL compilation error: | Object 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"MERGE INTO ab USING ab2 ON ab.a = ab2.a WHEN MATCHED THEN UPDATE SET a = 1",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"ALTER TABLE ab ADD COLUMN c INT",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"TRUNCATE TABLE ab",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT SYSTEM$ABORT_TRANSACTION((SELECT 12 FROM DUAL))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 12 AS \"12\" FROM (VALUES (null)) \"VALUES\")'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION(DATE_PART(year, NULL::DATE))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'EXTRACT(year from SYSTEM$NULL_TO_DATE(null))'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION((SELECT 12))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 12 AS \"12\" FROM (VALUES (null)) DUAL)'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION((SELECT 12 FROM ab))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 12 AS \"12\" FROM AB AS AB)'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION(DATE_PART(month, NULL::DATE))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'EXTRACT(month from SYSTEM$NULL_TO_DATE(null))'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION(EXTRACT(year FROM NULL::DATE))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'EXTRACT(year from SYSTEM$NULL_TO_DATE(null))'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION(YEAR(NULL::DATE))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'EXTRACT(year from SYSTEM$NULL_TO_DATE(null))'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION(DATE_PART(year, CURRENT_DATE()))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'EXTRACT(year from CURRENT_DATE())'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION((SELECT 12 AS q FROM DUAL))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 12 AS \"Q\" FROM (VALUES (null)) \"VALUES\")'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION((SELECT 'a' FROM DUAL))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 'a' AS \"'A'\" FROM (VALUES (null)) \"VALUES\")'"},
        });
    }
}
