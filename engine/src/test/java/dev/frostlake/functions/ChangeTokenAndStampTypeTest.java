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
 * SYSTEM$LAST_CHANGE_COMMIT_TIME's token is 0 for a table no statement has written — a view over one too — and a
 * CREATE TABLE AS or an INSERT ends the 0 whether or not it held a row; SYSTEM$TYPEOF tags the token by its value,
 * [SB1] for the 0 and [SB8] for a written table's. SYSTEM$STREAM_GET_TABLE_TIMESTAMP and SYSTEM$QUERY_REFERENCE are a
 * VARCHAR(1), to SYSTEM$TYPEOF, as a column and to what is built over them, whatever text they hold, and a table
 * created over one keeps the width and the whole text (all live-verified).
 */
public class ChangeTokenAndStampTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void theTokenIsZeroUntilAStatementWrites() {
        engine.execute("CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
        engine.execute("INSERT INTO tsrc VALUES ('a', 1)");
        engine.execute("CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
        engine.execute("CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
        engine.execute("INSERT INTO tcl VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE OR REPLACE TABLE tem (i INT)");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(SYSTEM$LAST_CHANGE_COMMIT_TIME('tsrc'))", "NUMBER(38,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$LAST_CHANGE_COMMIT_TIME('tem'))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tem')", "0"},
            {"SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tem') > 0", "false"},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$LAST_CHANGE_COMMIT_TIME('tsrc') + 1)", "NUMBER(38,0)[SB8]"},
        });
        assertShape("T:NUMBER(38,0)", "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tsrc') AS t");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tct AS SELECT 1 AS i", "Table TCT successfully created."},
            {"SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tct') > 0", "true"},
            {"CREATE OR REPLACE TABLE tce AS SELECT 1 AS i WHERE FALSE", "Table TCE successfully created."},
            {"SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tce') > 0", "true"},
            {"CREATE OR REPLACE TABLE tz (i INT)", "Table TZ successfully created."},
            {"INSERT INTO tz SELECT 1 WHERE FALSE", "0"},
            {"SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tz') > 0", "true"},
            {"DELETE FROM tz", "0"},
            {"SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tz') > 0", "true"},
            {"TRUNCATE TABLE tz", "Statement executed successfully."},
            {"SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tz') > 0", "true"},
            {"CREATE OR REPLACE VIEW vem AS SELECT i FROM tem", "View VEM successfully created."},
            {"SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('vem') > 0", "false"},
            {"SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tem') = 0", "true"},
            {"CREATE OR REPLACE TABLE tcl2 CLONE tcl", "Table TCL2 successfully created."},
            {"SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tcl2') = SYSTEM$LAST_CHANGE_COMMIT_TIME('tcl')", "false"},
            {"CREATE OR REPLACE TABLE tem2 CLONE tem", "Table TEM2 successfully created."},
            {"SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tem2')", "0"},
        });
    }

    @Test
    public void theStampAndTheReferenceAreOneCharacterTexts() {
        engine.execute("CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
        engine.execute("INSERT INTO tsrc VALUES ('a', 1)");
        engine.execute("CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
        engine.execute("CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
        engine.execute("INSERT INTO tcl VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE OR REPLACE TABLE tem (i INT)");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(SYSTEM$STREAM_GET_TABLE_TIMESTAMP('tsm'))", "VARCHAR(1)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$QUERY_REFERENCE('SELECT 1', FALSE))", "VARCHAR(1)[LOB]"},
        });
        assertShape("S:VARCHAR(1), Q:VARCHAR(1)",
            "SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('tsm') AS s, SYSTEM$QUERY_REFERENCE('SELECT 1', "
                + "FALSE) AS q");
        assertCells(new String[][] {
            {"SELECT LENGTH(SYSTEM$STREAM_GET_TABLE_TIMESTAMP('tsm')) > 1", "true"},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$STREAM_GET_TABLE_TIMESTAMP('tsm') || 'x')", "VARCHAR(2)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CONCAT(SYSTEM$QUERY_REFERENCE('SELECT 1', FALSE), 'xy'))", "VARCHAR(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, SYSTEM$STREAM_GET_TABLE_TIMESTAMP('tsm'), 'abc'))", "VARCHAR(3)[LOB]"},
            {"CREATE OR REPLACE TABLE tvs AS SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('tsm') AS s",
                "Table TVS successfully created."},
        });
        assertShape("S:VARCHAR(1)", "SELECT s FROM tvs");
        assertCells(new String[][] {
            {"SELECT LENGTH(s) > 1 FROM tvs", "true"},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$STREAM_GET_TABLE_TIMESTAMP(NULL))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_GET_TABLE_TIMESTAMP needs "
                + "to be constant, found 'null'"},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$CURRENT_USER_TASK_NAME())", "VARCHAR[LOB]"},
        });
        assertShape("S:VARCHAR(4)", "SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('tsm') AS s UNION ALL SELECT 'abcd'");
    }
}
