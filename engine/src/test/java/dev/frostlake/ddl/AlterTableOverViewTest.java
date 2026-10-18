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
 * ALTER TABLE over a view (live-verified): RENAME TO renames the view, IF EXISTS or not; a clustering key, a
 * column's ALTER and an added constraint are {@code cannot use ALTER TABLE on a view; use ALTER VIEW instead}, a
 * RENAME COLUMN names a missing table, a parameter is refused as the view's to operate on, and every other action
 * is refused by the view's kind, IF EXISTS or not. ALTER VIEW ... SET COMMENT = NULL takes the comment away.
 */
public class AlterTableOverViewTest extends MeasuredCellsTestSupport {

    @Test
    public void alterTableOverAViewAnswersByAction() {
        engine.execute("CREATE OR REPLACE TABLE mt (id INTEGER, s STRING)");
        engine.execute("CREATE OR REPLACE VIEW cv2 COMMENT = 'c0' AS SELECT id, s FROM mt");
        assertCells(new String[][] {
            {"ALTER TABLE cv2 SET COMMENT = 'x'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "OFF|c0"},
            {"ALTER TABLE IF EXISTS cv2 SET COMMENT = 'x'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE cv2 UNSET COMMENT",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE cv2 SWAP WITH mt",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE cv2 CLUSTER BY (id)",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE cv2 SET DATA_RETENTION_TIME_IN_DAYS = 1",
                "REFUSED SQL access control error: | Insufficient privileges to operate on view 'CV2'."},
            {"ALTER TABLE cv2 DROP COLUMN s",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE cv2 SET CHANGE_TRACKING = TRUE",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE IF EXISTS cv2 SET CHANGE_TRACKING = TRUE",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE cv2 SUSPEND RECLUSTER",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE cv2 SET CHANGE_TRACKING = TRUE COMMENT = 'y'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE cv2 ALTER COLUMN s COMMENT 'z'",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE cv2 ADD CONSTRAINT pk PRIMARY KEY (id)",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE cv2 RENAME COLUMN s TO s2", "REFUSED SQL compilation error: | Table 'CV2' does not exist"},
            {"ALTER TABLE IF EXISTS cv2 ADD COLUMN c INT",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE cv2 ADD COLUMN c INT",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE cv2 DROP CLUSTERING KEY",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "OFF|c0"},
            {"ALTER VIEW cv2 SET COMMENT = NULL", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "OFF|"},
            {"ALTER VIEW cv2 SET COMMENT = 'k'", "Statement executed successfully."},
            {"ALTER VIEW cv2 SET CHANGE_TRACKING = TRUE COMMENT = NULL", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "ON|"},
            {"ALTER VIEW cv2 SET COMMENT = NULL, CHANGE_TRACKING = FALSE", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV2' ->> SELECT \"change_tracking\", \"comment\" FROM $1", "OFF|"},
            {"ALTER VIEW cv2 SET COMMENT = NULL COMMENT = 'n'",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"ALTER TABLE cv2 RENAME TO mt", "REFUSED SQL compilation error: | Object 'MT' already exists."},
            {"ALTER TABLE cv2 RENAME TO cv2", "REFUSED SQL compilation error: | Object 'CV2' already exists."},
            {"ALTER TABLE cv2 RENAME TO cv9", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV9' ->> SELECT \"name\", \"comment\" FROM $1", "CV9|"},
            {"SHOW TABLES LIKE 'CV9' ->> SELECT \"name\" FROM $1", ""},
            {"ALTER TABLE IF EXISTS cv9 RENAME TO cv8", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV8' ->> SELECT \"name\" FROM $1", "CV8"},
            {"ALTER TABLE IF EXISTS nosuch_tv RENAME TO cv7", "Statement executed successfully."},
            {"ALTER TABLE cv8 RENAME TO test_db.test_schema.cv6", "Statement executed successfully."},
            {"SHOW VIEWS LIKE 'CV6' ->> SELECT \"name\" FROM $1", "CV6"},
        });
    }
}
