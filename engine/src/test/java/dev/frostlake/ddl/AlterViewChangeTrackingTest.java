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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * ALTER VIEW ... SET CHANGE_TRACKING = TRUE | FALSE and UNSET CHANGE_TRACKING turn a view's change tracking on and
 * off (live-verified). Turning it on turns it on for every table the view reads, through the views it reads, and
 * is refused for a query change tracking cannot follow, at the place in the view's own text; turning it off leaves
 * the tables' on, and a stream on the view, or a CHANGES clause over it, is then refused. The value is TRUE or
 * FALSE: a string, a number or a word is an invalid value, NULL is {@code invalid value 'null' for property
 * 'CHANGE_TRACKING'}, and a reserved word a syntax error at the list's first name. A CREATE VIEW ...
 * CHANGE_TRACKING = TRUE turns its tables' on too, and ALTER TABLE over a view is refused by the view's kind.
 */
public class AlterViewChangeTrackingTest extends MeasuredCellsTestSupport {

    @Test
    public void alterViewSetsAndUnsetsChangeTracking() {
        engine.execute("CREATE OR REPLACE TABLE mt (id INTEGER, s STRING)");
        engine.execute("INSERT INTO mt VALUES (1, 'a')");
        engine.execute("CREATE OR REPLACE VIEW cv2 AS SELECT id, s FROM mt");
        engine.execute("CREATE OR REPLACE VIEW kv AS SELECT 1 AS a");
        engine.execute("CREATE OR REPLACE SECURE VIEW sv AS SELECT id FROM mt");
        assertCells(new String[][] {
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = TRUE", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "ON|"},
            {"SHOW TABLES LIKE 'MT' ->> SELECT \"change_tracking\" FROM $1", "ON"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = FALSE", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "OFF|"},
            {"SHOW TABLES LIKE 'MT' ->> SELECT \"change_tracking\" FROM $1", "ON"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = 'TRUE'",
                "REFUSED SQL compilation error: | invalid value ['TRUE'] for parameter 'CHANGE_TRACKING'"},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "OFF|"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = 1",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'CHANGE_TRACKING'"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = ON",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'CHANGE_TRACKING'."},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = yes",
                "REFUSED SQL compilation error: | invalid value [yes] for parameter 'CHANGE_TRACKING'"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = NULL",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'CHANGE_TRACKING'"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'CHANGE_TRACKING'."},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'CHANGE_TRACKING'."},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = FALSE COMMENT = 'c'", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "OFF|c"},
            {"ALTER VIEW cv2 SET COMMENT = 'd' CHANGE_TRACKING = TRUE", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "ON|d"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = FALSE, COMMENT = 'e'", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "OFF|e"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = TRUE SECURE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'CHANGE_TRACKING'."},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "OFF|e"},
            {"ALTER VIEW cv2 UNSET CHANGE_TRACKING", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "OFF|e"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = TRUE", "Statement executed successfully."},
            {"ALTER VIEW cv2 UNSET CHANGE_TRACKING, COMMENT", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "OFF|"},
            {"ALTER VIEW IF EXISTS nosuch_cv SET CHANGE_TRACKING = TRUE", "Statement executed successfully."},
            {"ALTER VIEW nosuch_cv SET CHANGE_TRACKING = TRUE",
                "REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.NOSUCH_CV' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_CV."},
            {"ALTER VIEW kv SET CHANGE_TRACKING = TRUE",
                "REFUSED SQL compilation error: line 1 at position 29: | Change tracking is not supported on queries "
                + "with 'VALUES'. (inside view 'TEST_DB.TEST_SCHEMA.KV')."},
            {"SHOW VIEWS LIKE 'KV' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "OFF|"},
            {"ALTER VIEW mt SET CHANGE_TRACKING = TRUE",
                "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'VIEW'."},
            {"ALTER TABLE cv2 SET CHANGE_TRACKING = TRUE",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER VIEW sv SET CHANGE_TRACKING = TRUE", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'SV' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "ON|"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = TRUE", "Statement executed successfully."},
            {"CREATE OR REPLACE STREAM cst ON VIEW cv2", "Stream CST successfully created."},
            {"SELECT id, METADATA$ACTION FROM cst", ""},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = FALSE", "Statement executed successfully."},
            {"SELECT id FROM cv2 CHANGES(INFORMATION => DEFAULT) AT(STREAM => 'cst')",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.TEST_SCHEMA.CV2'."},
            {"SELECT id, METADATA$ACTION FROM cst",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.TEST_SCHEMA.CV2'."},
        });
    }

    @Test
    public void changeTrackingReachesTheTablesAndAlterTableOverAViewIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE mt (id INTEGER, s STRING)");
        engine.execute("CREATE OR REPLACE VIEW cv2 AS SELECT id, s FROM mt");
        engine.execute("CREATE OR REPLACE TABLE mt2 (id INT)");
        engine.execute("CREATE OR REPLACE TABLE mt3 (id INT)");
        engine.execute("CREATE OR REPLACE TABLE mt4 (id INT)");
        engine.execute("CREATE OR REPLACE TABLE mt5 (id INT)");
        assertCells(new String[][] {
            {"ALTER TABLE cv2 ADD COLUMN c INT",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE cv2 RENAME TO cv9", "Statement executed successfully."},
            {"ALTER TABLE cv2 SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.CV2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.CV2."},
            {"ALTER TABLE IF EXISTS cv2 SET COMMENT = 'x'", "Statement executed successfully."},
            {"ALTER TABLE cv2 UNSET COMMENT",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.CV2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.CV2."},
            {"ALTER TABLE cv2 SWAP WITH mt",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.CV2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.CV2."},
            {"ALTER TABLE cv2 CLUSTER BY (id)",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.CV2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.CV2."},
            {"ALTER TABLE cv2 SET DATA_RETENTION_TIME_IN_DAYS = 1",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.CV2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.CV2."},
            {"ALTER TABLE cv2 DROP COLUMN s",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.CV2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.CV2."},
            {"ALTER TABLE cv2 SET CHANGE_TRACKING = FALSE",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.CV2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.CV2."},
            {"ALTER TABLE IF EXISTS cv2 SET CHANGE_TRACKING = TRUE", "Statement executed successfully."},
            {"ALTER TABLE cv2 SUSPEND RECLUSTER",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.CV2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.CV2."},
            {"ALTER TABLE cv2 SET CHANGE_TRACKING = TRUE COMMENT = 'y'",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.CV2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.CV2."},
            {"CREATE OR REPLACE VIEW cv3 CHANGE_TRACKING = TRUE AS SELECT id FROM mt2",
                "View CV3 successfully created."},
            {"SHOW TABLES LIKE 'MT2' ->> SELECT \"change_tracking\" FROM $1", "ON"},
            {"SHOW VIEWS LIKE 'CV3' ->> SELECT \"change_tracking\" FROM $1", "ON"},
            {"CREATE OR REPLACE VIEW cv4 AS SELECT id FROM mt3", "View CV4 successfully created."},
            {"CREATE OR REPLACE STREAM s4 ON VIEW cv4", "Stream S4 successfully created."},
            {"SHOW TABLES LIKE 'MT3' ->> SELECT \"change_tracking\" FROM $1", "ON"},
            {"SHOW VIEWS LIKE 'CV4' ->> SELECT \"change_tracking\" FROM $1", "ON"},
            {"CREATE OR REPLACE VIEW cv5 AS SELECT id FROM cv3", "View CV5 successfully created."},
            {"ALTER VIEW cv5 SET CHANGE_TRACKING = TRUE", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV5' ->> SELECT \"change_tracking\" FROM $1", "ON"},
            {"SHOW VIEWS LIKE 'CV3' ->> SELECT \"change_tracking\" FROM $1", "ON"},
            {"CREATE OR REPLACE VIEW cv6 AS SELECT mt4.id FROM mt4 JOIN mt5 ON mt4.id = mt5.id",
                "View CV6 successfully created."},
            {"ALTER VIEW cv6 SET CHANGE_TRACKING = TRUE", "Statement executed successfully."},
            {"SHOW TABLES LIKE 'MT4' ->> SELECT \"change_tracking\" FROM $1", "ON"},
            {"SHOW TABLES LIKE 'MT5' ->> SELECT \"change_tracking\" FROM $1", "ON"},
            {"SHOW VIEWS LIKE 'CV6' ->> SELECT \"change_tracking\" FROM $1", "ON"},
            {"ALTER VIEW cv6 SET CHANGE_TRACKING = FALSE", "Statement executed successfully."},
            {"SHOW TABLES LIKE 'MT4' ->> SELECT \"change_tracking\" FROM $1", "ON"},
            {"SHOW VIEWS LIKE 'CV6' ->> SELECT \"change_tracking\" FROM $1", "OFF"},
            {"CREATE OR REPLACE VIEW cv7 AS SELECT id FROM mt2", "View CV7 successfully created."},
            {"SELECT id FROM cv7 CHANGES(INFORMATION => DEFAULT) AT(STREAM => 's4')",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.TEST_SCHEMA.CV7'."},
            {"ALTER VIEW cv2 SET COMMENT = ON",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'COMMENT'."},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = SELECT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'CHANGE_TRACKING'."},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = FROM",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'CHANGE_TRACKING'."},
            {"ALTER VIEW cv2 SET COMMENT = NULL",
                "REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.CV2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.CV2."},
            {"ALTER VIEW cv2 SET FOO = NULL", "REFUSED SQL compilation error: | invalid property 'FOO' for 'VIEW'"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = NULL COMMENT = 'x'",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'CHANGE_TRACKING'"},
            {"ALTER VIEW cv2 SET COMMENT = 'x' CHANGE_TRACKING = NULL",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'CHANGE_TRACKING'"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = null",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'CHANGE_TRACKING'"},
            {"ALTER VIEW IF EXISTS nosuch_v SET CHANGE_TRACKING = NULL",
                "REFUSED SQL compilation error: | invalid value 'null' for property 'CHANGE_TRACKING'"},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = TRUE, CHANGE_TRACKING = FALSE",
                "REFUSED SQL compilation error: | duplicate property 'CHANGE_TRACKING';"},
            {"ALTER VIEW cv2 UNSET CHANGE_TRACKING, CHANGE_TRACKING",
                "REFUSED SQL compilation error: | duplicate property 'CHANGE_TRACKING';"},
        });
    }
}
