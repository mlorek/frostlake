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
 * The clustering actions belong to tables and materialized views alike, whichever ALTER names them (live-verified):
 * ALTER TABLE clusters, reclusters, unclusters and renames a materialized view, and ALTER MATERIALIZED VIEW clusters,
 * reclusters and unclusters a table, its SUSPEND, RESUME and SECURE leaving the table as it is. A plain view is
 * refused a clustering key under either statement for the statement's kind, and SUSPEND or RESUME leave it be, while
 * RENAME TO renames it. Anything else is refused by the kind the name finds ({@code Object found is of type
 * 'MATERIALIZED_VIEW', not specified type 'TABLE'.}), a search optimization on a materialized view is an invalid
 * object type and a parameter one the role may not set; ALTER VIEW refuses a table's or a materialized view's comment
 * by its kind and renames a materialized view. A search optimization added to a missing table names an object, IF
 * EXISTS or not, and a materialized view's column list may follow its options.
 */
public class ClusteringAcrossKindsTest extends MeasuredCellsTestSupport {

    @Test
    public void clusteringActionsCrossTablesAndMaterializedViews() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        engine.execute("CREATE OR REPLACE TABLE tc (x INT, y INT) CLUSTER BY (y, x)");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv0 CLUSTER BY (x) AS SELECT x, y FROM t1");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv1 CLUSTER BY (y, x) AS SELECT x, y FROM tc");
        engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT x, y FROM t1");
        assertCells(new String[][] {
            {"ALTER TABLE mv0 CLUSTER BY (y)", "Statement executed successfully."},
        });
        engine.execute("SHOW MATERIALIZED VIEWS LIKE 'MV0'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"cluster_by\", \"automatic_clustering\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "MV0|LINEAR(y)|ON"},
            {"ALTER TABLE mv0 SUSPEND RECLUSTER", "Statement executed successfully."},
        });
        engine.execute("SHOW MATERIALIZED VIEWS LIKE 'MV0'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"cluster_by\", \"automatic_clustering\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "MV0|LINEAR(y)|OFF"},
            {"ALTER TABLE mv0 RESUME RECLUSTER", "Statement executed successfully."},
            {"ALTER TABLE mv0 DROP CLUSTERING KEY", "Statement executed successfully."},
            {"ALTER TABLE mv0 SUSPEND RECLUSTER", "REFUSED Table 'MV0' is not clustered | "},
            {"ALTER TABLE mv0 CLUSTER BY (nosuch)",
                "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'NOSUCH'"},
            {"ALTER TABLE mv0 ADD COLUMN z INT",
                "REFUSED SQL compilation error: Object found is of type 'MATERIALIZED_VIEW', not specified type "
                + "'TABLE'."},
            {"ALTER TABLE mv0 SET COMMENT = 'c'",
                "REFUSED SQL compilation error: Object found is of type 'MATERIALIZED_VIEW', not specified type "
                + "'TABLE'."},
            {"ALTER TABLE mv0 RENAME TO mv0r", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW t1 CLUSTER BY (x)", "Statement executed successfully."},
        });
        engine.execute("SHOW TABLES LIKE 'T1'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"cluster_by\", \"automatic_clustering\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "T1|LINEAR(x)|ON"},
            {"ALTER MATERIALIZED VIEW t1 SUSPEND RECLUSTER", "Statement executed successfully."},
        });
        engine.execute("SHOW TABLES LIKE 'T1'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"cluster_by\", \"automatic_clustering\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "T1|LINEAR(x)|OFF"},
            {"ALTER MATERIALIZED VIEW t1 RESUME RECLUSTER", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW t1 CLUSTER BY (nosuch)",
                "REFUSED SQL compilation error: error line 1 at position 39 | invalid identifier 'NOSUCH'"},
            {"ALTER MATERIALIZED VIEW t1 SUSPEND", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW t1 SET COMMENT = 'c'",
                "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type "
                + "'MATERIALIZED_VIEW'."},
            {"ALTER MATERIALIZED VIEW tc DROP CLUSTERING KEY", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW v1 CLUSTER BY (x)",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER MATERIALIZED VIEW v1 DROP CLUSTERING KEY",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER MATERIALIZED VIEW v1 SUSPEND", "Statement executed successfully."},
            {"ALTER TABLE v1 CLUSTER BY (x)",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE v1 SUSPEND RECLUSTER",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE IF EXISTS nosuch_t ADD SEARCH OPTIMIZATION ON EQUALITY(z)",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T."},
            {"ALTER TABLE IF EXISTS nosuch_t ADD SEARCH OPTIMIZATION",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T."},
            {"ALTER TABLE IF EXISTS nosuch_t DROP SEARCH OPTIMIZATION", "Statement executed successfully."},
            {"ALTER VIEW mv0 SET COMMENT = 'c'",
                "REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.MV0' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.MV0."},
            {"ALTER VIEW mv0 UNSET COMMENT",
                "REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.MV0' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.MV0."},
            {"ALTER VIEW t1 SET COMMENT = 'c'",
                "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'VIEW'."},
            {"CREATE OR REPLACE MATERIALIZED VIEW mv_or CLUSTER BY (x) (a, b) AS SELECT x, y FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 54 | invalid identifier 'X'"},
            {"CREATE OR REPLACE MATERIALIZED VIEW mv_or CLUSTER BY (a) (a, b) AS SELECT x, y FROM t1",
                "Materialized view MV_OR successfully created."},
            {"CREATE OR REPLACE MATERIALIZED VIEW mv_or COPY GRANTS COPY GRANTS AS SELECT x, y FROM t1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'GRANTS'."},
            {"CREATE OR REPLACE MATERIALIZED VIEW mv_or (a, b) CLUSTER BY (a) AS SELECT x, y FROM t1",
                "Materialized view MV_OR successfully created."},
            {"SELECT table_name, table_type, clustering_key FROM information_schema.tables WHERE table_schema = "
                + "'TEST_SCHEMA' AND table_name IN ('TC', 'MV1', 'MV0', 'T1', 'V1') ORDER BY table_name",
                "MV1|MATERIALIZED VIEW|LINEAR(y, x) ;; T1|BASE TABLE|LINEAR(x) ;; TC|BASE TABLE|null ;; V1|VIEW|null"},
        });
        engine.execute("SHOW MATERIALIZED VIEWS");
        assertCells(new String[][] {
            {"SELECT \"name\", \"cluster_by\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "MV0R| ;; MV1|LINEAR(y, x) ;; MV_OR|LINEAR(a)"},
        });
    }

    @Test
    public void eachStatementMeetsTheKindItsNameFinds() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        engine.execute("CREATE OR REPLACE TABLE t2 (x INT, y INT)");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv0 CLUSTER BY (x) AS SELECT x, y FROM t1");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv2 AS SELECT x, y FROM t1");
        engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT x, y FROM t1");
        assertCells(new String[][] {
            {"ALTER MATERIALIZED VIEW t1 RESUME", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW t1 UNSET COMMENT",
                "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type "
                + "'MATERIALIZED_VIEW'."},
            {"ALTER MATERIALIZED VIEW t1 SET SECURE", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW t1 RENAME TO t1r", "REFUSED SQL compilation error: | Empty SQL statement."},
            {"ALTER MATERIALIZED VIEW t2 SUSPEND RECLUSTER", "REFUSED Table 'T2' is not clustered | "},
            {"ALTER MATERIALIZED VIEW v1 RESUME", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW v1 SET COMMENT = 'c'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type "
                + "'MATERIALIZED_VIEW'."},
            {"ALTER MATERIALIZED VIEW v1 RENAME TO v1r", "Statement executed successfully."},
            {"ALTER MATERIALIZED VIEW v1 SUSPEND RECLUSTER",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.V1' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.V1."},
            {"ALTER MATERIALIZED VIEW nosuch_x SUSPEND",
                "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_X' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_X."},
            {"ALTER TABLE v1 ADD COLUMN z INT",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.V1' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.V1."},
            {"ALTER TABLE v1 SET COMMENT = 'c'",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.V1' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.V1."},
            {"ALTER TABLE v1 DROP CLUSTERING KEY",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.V1' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.V1."},
            {"ALTER TABLE v1 RESUME RECLUSTER",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.V1' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.TEST_SCHEMA.V1."},
            {"ALTER TABLE mv0 UNSET COMMENT",
                "REFUSED SQL compilation error: Object found is of type 'MATERIALIZED_VIEW', not specified type "
                + "'TABLE'."},
            {"ALTER TABLE mv0 SWAP WITH t2",
                "REFUSED SQL compilation error: Object found is of type 'MATERIALIZED_VIEW', not specified type "
                + "'TABLE'."},
            {"ALTER TABLE mv0 ADD SEARCH OPTIMIZATION", "REFUSED SQL compilation error: | Invalid object type: 'MV0'"},
            {"ALTER TABLE mv0 SET DATA_RETENTION_TIME_IN_DAYS = 1",
                "REFUSED SQL access control error: | Insufficient privileges to operate on materialized_view 'MV0'."},
            {"ALTER TABLE mv2 SUSPEND RECLUSTER", "REFUSED Table 'MV2' is not clustered | "},
            {"ALTER TABLE mv2 RESUME RECLUSTER", "REFUSED Table 'MV2' is not clustered | "},
            {"ALTER TABLE t2 SUSPEND RECLUSTER", "REFUSED Table 'T2' is not clustered | "},
            {"ALTER TABLE IF EXISTS mv0 CLUSTER BY (y)", "Statement executed successfully."},
            {"ALTER TABLE IF EXISTS v1 CLUSTER BY (x)",
                "REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'X'"},
            {"ALTER TABLE IF EXISTS nosuch_t CLUSTER BY (x)",
                "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'X'"},
            {"ALTER TABLE IF EXISTS nosuch_t ADD SEARCH OPTIMIZATION ON SUBSTRING(z)",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T."},
            {"ALTER TABLE IF EXISTS nosuch_t DROP SEARCH OPTIMIZATION ON EQUALITY(z)",
                "REFUSED SQL compilation error: error line 1 at position 68 | invalid identifier 'Z'"},
            {"ALTER TABLE nosuch_t ADD SEARCH OPTIMIZATION",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T."},
            {"ALTER VIEW t1 UNSET COMMENT",
                "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'VIEW'."},
            {"ALTER VIEW t1 RENAME TO t1v", "REFUSED SQL compilation error: | Empty SQL statement."},
            {"ALTER VIEW mv0 SET COMMENT = 'c'",
                "REFUSED SQL compilation error: Object found is of type 'MATERIALIZED_VIEW', not specified type "
                + "'VIEW'."},
            {"ALTER VIEW mv0 UNSET SECURE", "Statement executed successfully."},
            {"ALTER VIEW mv0 RENAME TO mv0v", "Statement executed successfully."},
            {"ALTER VIEW IF EXISTS t1 SET COMMENT = 'c'",
                "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'VIEW'."},
        });
        engine.execute("SHOW TABLES LIKE 'T%'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"kind\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1", "T1|TABLE ;; T2|TABLE"},
        });
        engine.execute("SHOW VIEWS LIKE '%'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"is_materialized\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "MV0V|true ;; MV2|true ;; V1R|false"},
        });
    }
}
