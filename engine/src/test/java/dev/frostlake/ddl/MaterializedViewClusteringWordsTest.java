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
 * A materialized view's clustering key, around its edges. In CREATE a word written among the key and the
 * COMMENT is read as a property's name, so what follows it is what is refused: {@code CLUSTER BY (x) COPY GRANTS
 * AS} is a syntax error at GRANTS. Under ALTER the actions on the key - CLUSTER BY, DROP CLUSTERING KEY, SUSPEND
 * and RESUME RECLUSTER - are a table's and name a view that is not there a Table, where every other action
 * names it a Materialized view. IF EXISTS forgives the view's absence, but not for SUSPEND, RESUME and SET or
 * UNSET SECURE, and a CLUSTER BY's key is compiled all the same. INFORMATION_SCHEMA and SHOW list a key as
 * {@code LINEAR(y, x)}. Every answer is live-verified.
 */
public class MaterializedViewClusteringWordsTest extends MeasuredCellsTestSupport {

    @Test
    public void theWordsAroundAMaterializedViewsKey() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        answer(
            "INSERT INTO t1 VALUES (1, 2)");
        answer(
            "CREATE OR REPLACE TABLE tc (x INT, y INT) CLUSTER BY (y, x)");
        answer(
            "CREATE OR REPLACE MATERIALIZED VIEW mv1 CLUSTER BY (y, x) AS SELECT x, y FROM t1");
        answer(
            "CREATE OR REPLACE MATERIALIZED VIEW mv0 AS SELECT x, y FROM t1");
        answer(
            "CREATE OR REPLACE VIEW v1 AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'GRANTS'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or CLUSTER BY (x) COPY GRANTS AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_OR successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or COPY GRANTS CLUSTER BY (x) AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'AS'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or CLUSTER BY (x) COPY AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_OR successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or CLUSTER BY (x) COMMENT = 'c' AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_OR successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or COMMENT = 'c' CLUSTER BY (x) AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_OR successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or CLUSTER BY (x) CLUSTER BY (y) AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'AS'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or CLUSTER BY (x) SECURE AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_OR successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or (a, b) CLUSTER BY (a) AS SELECT x, y FROM t1");
        answer(
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or CLUSTER BY (x) (a, b) AS SELECT x, y FROM t1");
        answer(
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or CLUSTER BY (x) COPY GRANTS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'AS'.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or CLUSTER BY (x) GRANTS AS SELECT x, y FROM t1");
        answer(
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or COPY GRANTS COPY GRANTS AS SELECT x, y FROM t1");
        assertAnswer(
            "Materialized view MV_OR successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW mv_or COPY GRANTS AS SELECT x, y FROM t1");
        assertAnswer(
            "View V_OR successfully created.",
            "CREATE OR REPLACE VIEW v_or COPY GRANTS COPY GRANTS AS SELECT x, y FROM t1");
        answer(
            "CREATE OR REPLACE VIEW v_or CLUSTER BY (x) AS SELECT x, y FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW nosuch_mv CLUSTER BY (x)");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW nosuch_mv DROP CLUSTERING KEY");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW nosuch_mv SUSPEND");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW nosuch_mv RESUME");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW nosuch_mv SUSPEND RECLUSTER");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW nosuch_mv RESUME RECLUSTER");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW nosuch_mv SET COMMENT = 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW nosuch_mv UNSET COMMENT");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW nosuch_mv SET SECURE");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW nosuch_mv UNSET SECURE");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW nosuch_mv RENAME TO nosuch_mv2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 56 | invalid identifier 'X'",
            "ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv CLUSTER BY (x)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 56 | invalid identifier 'X'",
            "ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv CLUSTER BY (x + 1, y)");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv DROP CLUSTERING KEY");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv SUSPEND");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv SUSPEND RECLUSTER");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv SET COMMENT = 'c'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv RENAME TO nosuch_mv2");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv SET SECURE");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t CLUSTER BY (x)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'X'",
            "ALTER TABLE IF EXISTS nosuch_t CLUSTER BY (x)");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE IF EXISTS nosuch_t DROP CLUSTERING KEY");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t DROP CLUSTERING KEY");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE IF EXISTS nosuch_t SUSPEND RECLUSTER");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE IF EXISTS nosuch_t ADD COLUMN z INT");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "ALTER TABLE IF EXISTS nosuch_t ADD COLUMN z NOSUCHTYPE");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE IF EXISTS nosuch_t DROP COLUMN z");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE IF EXISTS nosuch_t RENAME COLUMN z TO w");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE IF EXISTS nosuch_t SET COMMENT = 'c'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE IF EXISTS nosuch_t ADD CONSTRAINT pk PRIMARY KEY (z)");
        answer(
            "ALTER TABLE IF EXISTS nosuch_t ADD SEARCH OPTIMIZATION ON EQUALITY(z)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "ALTER TABLE IF EXISTS nosuch_t ALTER COLUMN z SET DATA TYPE NOSUCHTYPE");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE IF EXISTS nosuch_t SET DATA_RETENTION_TIME_IN_DAYS = 999");
        assertAnswer(
            "REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.NOSUCH_V' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_V.",
            "ALTER VIEW nosuch_v SET COMMENT = 'c'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER VIEW IF EXISTS nosuch_v SET COMMENT = 'c'");
        answer(
            "ALTER VIEW nosuch_v CLUSTER BY (x)");
        answer(
            "ALTER MATERIALIZED VIEW v1 CLUSTER BY (x)");
        answer(
            "ALTER MATERIALIZED VIEW t1 CLUSTER BY (x)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'NOSUCHCOL'",
            "ALTER MATERIALIZED VIEW mv0 CLUSTER BY (nosuchcol)");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW mv0 CLUSTER BY (x)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 50 | invalid identifier 'NOSUCHCOL'",
            "ALTER MATERIALIZED VIEW IF EXISTS mv0 CLUSTER BY (nosuchcol)");
        answer(
            "ALTER TABLE mv0 CLUSTER BY (y)");
        answer(
            "ALTER TABLE mv0 DROP CLUSTERING KEY");
        answer(
            "ALTER VIEW mv0 SET COMMENT = 'c'");
        answer(
            "ALTER TABLE v1 CLUSTER BY (x)");
        answer(
            "SELECT table_name, table_type, clustering_key FROM information_schema.tables WHERE table_schema = "
                + "'TEST_SCHEMA' AND table_name IN ('TC', 'MV1', 'MV0', 'T1', 'V1') ORDER BY table_name");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), comment:VARCHAR(16777216), "
                + "cluster_by:VARCHAR(16777216), rows:NUMBER(38,0), bytes:NUMBER(38,0), owner:VARCHAR(16777216), "
                + "retention_time:VARCHAR(16777216), automatic_clustering:VARCHAR(16777216), "
                + "change_tracking:VARCHAR(16777216), search_optimization:VARCHAR(16777216), "
                + "search_optimization_progress:NUMBER(38,0), search_optimization_bytes:NUMBER(38,0), "
                + "is_external:VARCHAR(16777216), enable_schema_evolution:VARCHAR(16777216), "
                + "owner_role_type:VARCHAR(16777216), is_event:VARCHAR(16777216), is_hybrid:VARCHAR(16777216), "
                + "is_iceberg:VARCHAR(16777216), is_dynamic:VARCHAR(16777216), is_immutable:VARCHAR(16777216), "
                + "is_interactive:VARCHAR(16777216), row_timestamp:VARCHAR(16777216), error_logging:VARCHAR(16777216)",
            "SHOW TABLES LIKE 'TC' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "TC|LINEAR(y, x)",
            "SELECT \"name\", \"cluster_by\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), reserved:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), cluster_by:VARCHAR(16777216), "
                + "rows:NUMBER(38,0), bytes:NUMBER(38,0), source_database_name:VARCHAR(16777216), "
                + "source_schema_name:VARCHAR(16777216), source_table_name:VARCHAR(16777216), "
                + "refreshed_on:TIMESTAMP_LTZ, compacted_on:TIMESTAMP_LTZ, owner:VARCHAR(16777216), "
                + "invalid:VARCHAR(16777216), invalid_reason:VARCHAR(16777216), behind_by:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), text:VARCHAR(16777216), is_secure:VARCHAR(16777216), "
                + "automatic_clustering:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW MATERIALIZED VIEWS LIKE 'MV%' IN SCHEMA test_db.test_schema");
        answer(
            "SELECT \"name\", \"cluster_by\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer(
            "create or replace TABLE TC cluster by (y, x)( | \tX NUMBER(38,0), | \tY NUMBER(38,0) | );",
            "SELECT GET_DDL('TABLE', 'tc')");
        assertAnswer(
            "create or replace materialized view MV1( | \tX, | \tY | ) cluster by (y, x) as SELECT x, y FROM t1;",
            "SELECT GET_DDL('VIEW', 'mv1')");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE tc CLUSTER BY (x)");
        assertAnswer(
            "LINEAR(x)",
            "SELECT clustering_key FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND "
                + "table_name = 'TC'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE tc CLUSTER BY (x + 1, UPPER(y::VARCHAR))");
        assertAnswer(
            "LINEAR(x + 1, UPPER(y::VARCHAR))",
            "SELECT clustering_key FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND "
                + "table_name = 'TC'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE tc DROP CLUSTERING KEY");
        assertAnswer(
            "null",
            "SELECT clustering_key FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND "
                + "table_name = 'TC'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW mv1 DROP CLUSTERING KEY");
        assertAnswer(
            "null",
            "SELECT clustering_key FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA' AND "
                + "table_name = 'MV1'");
    }

    @Test
    public void secureAndSuspend() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        answer(
            "INSERT INTO t1 VALUES (1, 2)");
        answer(
            "CREATE OR REPLACE MATERIALIZED VIEW mv0 AS SELECT x, y FROM t1");
        answer(
            "CREATE OR REPLACE FUNCTION f1(x OBJECT(a INT)) RETURNS VARCHAR AS $$ 's' $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv RESUME");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv UNSET COMMENT");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv RESUME RECLUSTER");
        assertAnswer(
            "REFUSED SQL compilation error: | Materialized view 'TEST_DB.TEST_SCHEMA.NOSUCH_MV' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MV.",
            "ALTER MATERIALIZED VIEW IF EXISTS nosuch_mv UNSET SECURE");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW mv0 SET SECURE");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), reserved:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), cluster_by:VARCHAR(16777216), "
                + "rows:NUMBER(38,0), bytes:NUMBER(38,0), source_database_name:VARCHAR(16777216), "
                + "source_schema_name:VARCHAR(16777216), source_table_name:VARCHAR(16777216), "
                + "refreshed_on:TIMESTAMP_LTZ, compacted_on:TIMESTAMP_LTZ, owner:VARCHAR(16777216), "
                + "invalid:VARCHAR(16777216), invalid_reason:VARCHAR(16777216), behind_by:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), text:VARCHAR(16777216), is_secure:VARCHAR(16777216), "
                + "automatic_clustering:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW MATERIALIZED VIEWS LIKE 'MV0' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "MV0|true",
            "SELECT \"name\", \"is_secure\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW mv0 UNSET SECURE");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), reserved:VARCHAR(16777216), "
                + "database_name:VARCHAR(16777216), schema_name:VARCHAR(16777216), cluster_by:VARCHAR(16777216), "
                + "rows:NUMBER(38,0), bytes:NUMBER(38,0), source_database_name:VARCHAR(16777216), "
                + "source_schema_name:VARCHAR(16777216), source_table_name:VARCHAR(16777216), "
                + "refreshed_on:TIMESTAMP_LTZ, compacted_on:TIMESTAMP_LTZ, owner:VARCHAR(16777216), "
                + "invalid:VARCHAR(16777216), invalid_reason:VARCHAR(16777216), behind_by:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), text:VARCHAR(16777216), is_secure:VARCHAR(16777216), "
                + "automatic_clustering:VARCHAR(16777216), owner_role_type:VARCHAR(16777216)",
            "SHOW MATERIALIZED VIEWS LIKE 'MV0' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "MV0|false",
            "SELECT \"name\", \"is_secure\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW IF EXISTS mv0 SET SECURE");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW IF EXISTS mv0 SUSPEND");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER MATERIALIZED VIEW IF EXISTS mv0 RESUME");
        answer(
            "ALTER MATERIALIZED VIEW mv0 SET SECURE = TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SECURE'.",
            "ALTER MATERIALIZED VIEW mv0 SECURE");
    }
}
