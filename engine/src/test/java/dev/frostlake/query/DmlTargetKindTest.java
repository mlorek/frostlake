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
 * What a DML statement may write to. Its target has to be a table: a view, a secure view and a stream are
 * refused with the statement's own word in front — INSERT, INSERT (MULTI-TABLE), UPDATE, DELETE, MERGE, and
 * INSERT for a COPY — a materialized view with "operations are not allowed on materialized views", and a
 * TRUNCATE names the kind it found. An event table takes no INSERT, UPDATE or COPY and no MERGE that inserts or
 * updates, while a DELETE, a TRUNCATE and a MERGE that only deletes run; its columns are the account's own, so
 * adding, dropping, renaming or altering one and swapping the table are refused, it cannot be cloned as a table,
 * and CREATE OR REPLACE TABLE does not replace it. Every answer is live-verified.
 */
public class DmlTargetKindTest extends MeasuredCellsTestSupport {

    @Test
    public void anEventTableTakesNoRowsFromAStatement() {
        engine.execute("CREATE OR REPLACE EVENT TABLE et");
        engine.execute("CREATE OR REPLACE TABLE plain (v VARIANT)");
        engine.execute("CREATE OR REPLACE STAGE est");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO et (value) VALUES (NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO et SELECT * FROM et");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT OVERWRITE INTO et SELECT * FROM et");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT (MULTI-TABLE) statement's target must be a table",
            "INSERT ALL INTO et (value) VALUES (NULL) SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT (MULTI-TABLE) statement's target must be a table",
            "INSERT FIRST WHEN 1 = 1 THEN INTO et (value) VALUES (NULL) SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO et (nope) VALUES (NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | UPDATE statement's target must be a table",
            "UPDATE et SET value = NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | UPDATE statement's target must be a table",
            "UPDATE et SET value = NULL WHERE 1 = 0");
        assertAnswer(
            "0",
            "DELETE FROM et");
        assertAnswer(
            "0",
            "DELETE FROM et WHERE 1 = 0");
        assertAnswer(
            "Statement executed successfully.",
            "TRUNCATE TABLE et");
        assertAnswer(
            "Statement executed successfully.",
            "TRUNCATE TABLE IF EXISTS et");
        assertAnswer(
            "REFUSED SQL compilation error: | MERGE statement's target must be a table",
            "MERGE INTO et USING (SELECT 1 x) s ON FALSE WHEN NOT MATCHED THEN INSERT (value) VALUES (NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | MERGE statement's target must be a table",
            "MERGE INTO et USING (SELECT 1 x) s ON TRUE WHEN MATCHED THEN UPDATE SET value = NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "COPY INTO et FROM @est");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM et");
        assertAnswer(
            "0",
            "INSERT INTO plain SELECT value FROM et");
    }

    @Test
    public void whatElseAnEventTableTakes() {
        engine.execute("CREATE OR REPLACE EVENT TABLE et");
        engine.execute("CREATE OR REPLACE TABLE plain (v VARIANT)");
        engine.execute("CREATE OR REPLACE STAGE est");
        assertAnswer(
            "Table FROM_ET successfully created.",
            "CREATE OR REPLACE TABLE from_et AS SELECT * FROM et");
        assertAnswer(
            "View ET_V successfully created.",
            "CREATE OR REPLACE VIEW et_v AS SELECT value FROM et");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO et_v VALUES (NULL)");
        assertAnswer(
            "Stream ET_S successfully created.",
            "CREATE OR REPLACE STREAM et_s ON TABLE et");
        assertAnswer(
            "REFUSED Unsupported feature 'INVALID OPERATION FOR EVENT TABLES'.",
            "ALTER TABLE et ADD COLUMN x INT");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE et SET COMMENT = 'c'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE et RENAME TO et_renamed");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE et_renamed RENAME TO et");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid operation Cannot clone from an event table.",
            "CREATE OR REPLACE TABLE et_c CLONE et");
        assertAnswer(
            "Table ET_L successfully created.",
            "CREATE OR REPLACE TABLE et_l LIKE et");
        assertAnswer(
            "ET successfully dropped.",
            "DROP TABLE et");
        assertAnswer(
            "Table ET successfully restored.",
            "UNDROP TABLE et");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO test_db.test_schema.et (value) VALUES (NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'ET_NOPE' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE ET_NOPE.",
            "INSERT INTO et_nope (value) VALUES (NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO et_v (value) VALUES (NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | UPDATE statement's target must be a table",
            "UPDATE et_v SET value = NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | DELETE statement's target must be a table",
            "DELETE FROM et_v");
    }

    @Test
    public void aViewAStreamOrAMaterializedViewIsNoTarget() {
        engine.execute("CREATE OR REPLACE TABLE base_t (a INT)");
        engine.execute("INSERT INTO base_t VALUES (1)");
        engine.execute("CREATE OR REPLACE VIEW base_v AS SELECT a FROM base_t");
        engine.execute("CREATE OR REPLACE SECURE VIEW base_sv AS SELECT a FROM base_t");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW base_mv AS SELECT a FROM base_t");
        engine.execute("CREATE OR REPLACE SEQUENCE base_q");
        engine.execute("CREATE OR REPLACE STREAM base_s ON TABLE base_t");
        engine.execute("CREATE OR REPLACE EVENT TABLE et");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO base_v VALUES (2)");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO base_v SELECT 2");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO base_v (nope) VALUES (2)");
        assertAnswer(
            "REFUSED SQL compilation error: | UPDATE statement's target must be a table",
            "UPDATE base_v SET a = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | DELETE statement's target must be a table",
            "DELETE FROM base_v");
        assertAnswer(
            "REFUSED SQL compilation error: | MERGE statement's target must be a table",
            "MERGE INTO base_v USING (SELECT 1 x) s ON FALSE WHEN NOT MATCHED THEN INSERT (a) VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.",
            "TRUNCATE TABLE base_v");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT (MULTI-TABLE) statement's target must be a table",
            "INSERT ALL INTO base_v VALUES (2) SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "COPY INTO base_v FROM @~/nothing");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO base_sv VALUES (2)");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT operations are not allowed on materialized views",
            "INSERT INTO base_mv VALUES (2)");
        assertAnswer(
            "REFUSED SQL compilation error: | UPDATE operations are not allowed on materialized views",
            "UPDATE base_mv SET a = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | DELETE operations are not allowed on materialized views",
            "DELETE FROM base_mv");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'MATERIALIZED_VIEW', not specified type "
                + "'TABLE'.",
            "TRUNCATE TABLE base_mv");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'BASE_Q' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE BASE_Q.",
            "INSERT INTO base_q VALUES (2)");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO base_s VALUES (2)");
        assertAnswer(
            "REFUSED SQL compilation error: | DELETE statement's target must be a table",
            "DELETE FROM base_s");
        assertAnswer(
            "REFUSED SQL compilation error: | UPDATE statement's target must be a table",
            "UPDATE base_s SET a = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'BASE_NOPE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE BASE_NOPE.",
            "UPDATE base_nope SET a = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'BASE_NOPE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE BASE_NOPE.",
            "DELETE FROM base_nope");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'BASE_NOPE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE BASE_NOPE.",
            "MERGE INTO base_nope USING (SELECT 1 x) s ON FALSE WHEN NOT MATCHED THEN INSERT (a) VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.BASE_NOPE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.BASE_NOPE.",
            "TRUNCATE TABLE base_nope");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT INTO\n  base_v VALUES (2)");
    }

    @Test
    public void anEventTablesColumnsAreTheAccountsOwn() {
        engine.execute("CREATE OR REPLACE TABLE base_t (a INT)");
        engine.execute("INSERT INTO base_t VALUES (1)");
        engine.execute("CREATE OR REPLACE VIEW base_v AS SELECT a FROM base_t");
        engine.execute("CREATE OR REPLACE SECURE VIEW base_sv AS SELECT a FROM base_t");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW base_mv AS SELECT a FROM base_t");
        engine.execute("CREATE OR REPLACE SEQUENCE base_q");
        engine.execute("CREATE OR REPLACE STREAM base_s ON TABLE base_t");
        engine.execute("CREATE OR REPLACE EVENT TABLE et");
        assertAnswer(
            "REFUSED Unsupported feature 'INVALID OPERATION FOR EVENT TABLES'.",
            "ALTER TABLE et DROP COLUMN value");
        assertAnswer(
            "REFUSED Unsupported feature 'INVALID OPERATION FOR EVENT TABLES'.",
            "ALTER TABLE et RENAME COLUMN value TO v2");
        assertAnswer(
            "REFUSED Unsupported feature 'INVALID OPERATION FOR EVENT TABLES'.",
            "ALTER TABLE et ALTER COLUMN value COMMENT 'c'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE et CLUSTER BY (timestamp)");
        assertAnswer(
            "REFUSED Unsupported feature 'INVALID OPERATION FOR EVENT TABLES'.",
            "ALTER TABLE et SWAP WITH base_t");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE et SET DATA_RETENTION_TIME_IN_DAYS = 0");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE et SET CHANGE_TRACKING = TRUE");
        assertAnswer(
            "0",
            "DELETE FROM et WHERE record_type = 'LOG'");
        assertAnswer(
            "0",
            "DELETE FROM et USING base_t WHERE base_t.a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT OVERWRITE INTO et (value) VALUES (NULL)");
        assertAnswer(
            "0",
            "MERGE INTO et USING (SELECT 1 x) s ON TRUE WHEN MATCHED THEN DELETE");
        assertAnswer(
            "REFUSED SQL compilation error: | UPDATE statement's target must be a table",
            "UPDATE et SET value = NULL FROM base_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'ET' already exists as EVENT_TABLE",
            "CREATE OR REPLACE TABLE et AS SELECT 1 a");
    }
}
