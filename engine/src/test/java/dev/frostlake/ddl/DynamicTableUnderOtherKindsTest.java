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
 * A dynamic table under another kind's statement (live-verified): ALTER TABLE sets and unsets its comment, gives
 * and drops its clustering key and suspends and resumes its reclustering, refuses to rename a column the key reads
 * and refuses every other action naming the dynamic table's kind; ALTER VIEW and ALTER MATERIALIZED VIEW refuse it
 * by its kind; ALTER DYNAMIC TABLE renames a view, and refuses it anything else. A dynamic table whose source is
 * renamed or dropped answers the rows of its last refresh, and one reading no stored relation is refused.
 */
public class DynamicTableUnderOtherKindsTest extends MeasuredCellsTestSupport {

    @Test
    public void anotherKindsAlterTakesWhatTheDynamicTableTakes() {
        engine.execute("CREATE OR REPLACE TABLE plain_t (a INT, b INT)");
        engine.execute("INSERT INTO plain_t VALUES (1, 2), (3, 4)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE DYNAMIC TABLE dt TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a, b "
                + "FROM plain_t",
                "Dynamic table DT successfully created."},
            {"CREATE OR REPLACE VIEW dv AS SELECT a FROM plain_t", "View DV successfully created."},
            {"ALTER TABLE dt SET COMMENT = 'x'", "Statement executed successfully."},
        });
        engine.execute("SHOW DYNAMIC TABLES LIKE 'DT'");
        assertCells(new String[][] {
            {"SELECT \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "x"},
            {"ALTER TABLE dt CLUSTER BY (a)", "Statement executed successfully."},
        });
        engine.execute("SHOW DYNAMIC TABLES LIKE 'DT'");
        assertCells(new String[][] {
            {"SELECT \"cluster_by\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "LINEAR(a)"},
            {"ALTER TABLE dt SUSPEND RECLUSTER", "Statement executed successfully."},
            {"ALTER TABLE dt RESUME RECLUSTER", "Statement executed successfully."},
            {"ALTER TABLE dt RENAME COLUMN a TO c",
                "REFUSED Cannot rename column 'A' which belongs to a clustering key | "},
            {"ALTER TABLE dt DROP CLUSTERING KEY", "Statement executed successfully."},
            {"ALTER TABLE dt UNSET COMMENT", "Statement executed successfully."},
            {"ALTER TABLE dt ADD COLUMN z INT",
                "REFUSED SQL compilation error: Object found is of type 'DYNAMIC_TABLE', not specified type 'TABLE'."},
            {"ALTER TABLE dt SET CHANGE_TRACKING = TRUE",
                "REFUSED SQL compilation error: Object found is of type 'DYNAMIC_TABLE', not specified type 'TABLE'."},
            {"ALTER TABLE dt SWAP WITH plain_t",
                "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'DYNAMIC_TABLE'."},
            {"ALTER TABLE dt DROP COLUMN b",
                "REFUSED SQL compilation error: Object found is of type 'DYNAMIC_TABLE', not specified type 'TABLE'."},
            {"ALTER VIEW dt RENAME TO dt8",
                "REFUSED SQL compilation error: Object found is of type 'DYNAMIC_TABLE', not specified type 'VIEW'."},
            {"ALTER VIEW dt SET COMMENT = 'v'",
                "REFUSED SQL compilation error: Object found is of type 'DYNAMIC_TABLE', not specified type 'VIEW'."},
            {"ALTER DYNAMIC TABLE dv RENAME TO dv2", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM dv2", "2"},
            {"ALTER DYNAMIC TABLE dv2 SET COMMENT = 'y'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'DYNAMIC_TABLE'."},
            {"ALTER DYNAMIC TABLE dv2 SUSPEND",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE IDENTIFIER('dt') SET COMMENT = 'z'", "Statement executed successfully."},
            {"CREATE OR REPLACE DYNAMIC TABLE dtd TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a FROM dt",
                "Dynamic table DTD successfully created."},
            {"ALTER DYNAMIC TABLE dt RENAME TO dtu", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM dtd", "2"},
            {"SELECT a FROM dtd ORDER BY 1", "1 ;; 3"},
            {"DROP DYNAMIC TABLE dtu", "DTU successfully dropped."},
            {"SELECT COUNT(*) FROM dtd", "2"},
            {"ALTER TABLE dtd SET COMMENT = 'after'", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW dtd RENAME TO dtd2",
                "REFUSED SQL compilation error: Object found is of type 'DYNAMIC_TABLE', not specified type "
                + "'MATERIALIZED_VIEW'."},
            {"ALTER TABLE nosuch_dt SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_DT' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_DT."},
        });
    }
}
