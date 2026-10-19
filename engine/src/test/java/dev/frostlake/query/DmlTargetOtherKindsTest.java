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
 * What a DML statement may write, for the kinds that are no table: a materialized view under a MERGE and a
 * multi-table INSERT, a dynamic table under every statement that writes rows, and a TRUNCATE of a stream
 * (live-verified).
 */
public class DmlTargetOtherKindsTest extends MeasuredCellsTestSupport {

    @Test
    public void aMaterializedViewADynamicTableOrAStream() {
        answer(
            "CREATE OR REPLACE TABLE base_t (a INT)");
        answer(
            "INSERT INTO base_t VALUES (1)");
        answer(
            "CREATE OR REPLACE VIEW base_v AS SELECT a FROM base_t");
        answer(
            "CREATE OR REPLACE MATERIALIZED VIEW base_mv AS SELECT a FROM base_t");
        answer(
            "CREATE OR REPLACE STREAM base_s ON TABLE base_t");
        answer(
            "CREATE OR REPLACE DYNAMIC TABLE base_dt TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT a FROM "
                + "base_t");
        answer(
            "CREATE OR REPLACE EVENT TABLE base_et");
        answer(
            "CREATE OR REPLACE TABLE plain_t (a INT)");
        answer(
            "CREATE OR REPLACE TABLE plain_u (a INT)");
        answer(
            "CREATE OR REPLACE TABLE plain_w (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | MERGE operations are not allowed on materialized views",
            "MERGE INTO base_mv USING (SELECT 1 x) s ON FALSE WHEN NOT MATCHED THEN INSERT (a) VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | MERGE operations are not allowed on materialized views",
            "MERGE INTO base_mv m USING (SELECT 1 x) s ON m.a = s.x WHEN MATCHED THEN UPDATE SET a = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | MERGE operations are not allowed on materialized views",
            "MERGE INTO base_mv m USING (SELECT 1 x) s ON m.a = s.x WHEN MATCHED THEN DELETE");
        assertAnswer(
            "REFUSED SQL compilation error: | MERGE operations are not allowed on materialized views",
            "MERGE INTO base_mv m USING (SELECT 1 x) s ON m.a = s.x WHEN MATCHED THEN UPDATE SET a = 2 WHEN NOT "
                + "MATCHED THEN INSERT (a) VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | MERGE operations are not allowed on materialized views",
            "MERGE INTO base_mv m USING (SELECT 1 x) s ON m.nope = s.x WHEN MATCHED THEN DELETE");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT (MULTI-TABLE) operations are not allowed on materialized "
                + "views",
            "INSERT ALL INTO base_mv VALUES (2) SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT (MULTI-TABLE) operations are not allowed on materialized "
                + "views",
            "INSERT FIRST WHEN 1 = 1 THEN INTO base_mv VALUES (2) ELSE INTO plain_t VALUES (3) SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT (MULTI-TABLE) operations are not allowed on materialized "
                + "views",
            "INSERT ALL INTO plain_t VALUES (2) INTO base_mv VALUES (2) SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT operations are not allowed on materialized views",
            "INSERT OVERWRITE INTO base_mv SELECT 2");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT operations are not allowed on materialized views",
            "COPY INTO base_mv FROM @~/nothing");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT operations are not allowed on materialized views",
            "INSERT INTO base_mv (nope) VALUES (2)");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM plain_t");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation INSERT on Dynamic Table",
            "INSERT INTO base_dt VALUES (2)");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation INSERT on Dynamic Table",
            "INSERT INTO base_dt SELECT 2");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation INSERT on Dynamic Table",
            "INSERT INTO base_dt (nope) VALUES (2)");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation UPDATE on Dynamic Table",
            "UPDATE base_dt SET a = 2");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation DELETE on Dynamic Table",
            "DELETE FROM base_dt");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation MERGE on Dynamic Table",
            "MERGE INTO base_dt d USING (SELECT 1 x) s ON d.a = s.x WHEN MATCHED THEN UPDATE SET a = 2");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation MERGE on Dynamic Table",
            "MERGE INTO base_dt d USING (SELECT 1 x) s ON d.a = s.x WHEN MATCHED THEN DELETE");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation MERGE on Dynamic Table",
            "MERGE INTO base_dt USING (SELECT 1 x) s ON FALSE WHEN NOT MATCHED THEN INSERT (a) VALUES (1)");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation INSERT (MULTI-TABLE) on Dynamic Table",
            "INSERT ALL INTO base_dt VALUES (2) SELECT 1");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation INSERT on Dynamic Table",
            "INSERT OVERWRITE INTO base_dt SELECT 2");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation INSERT on Dynamic Table",
            "COPY INTO base_dt FROM @~/nothing");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation TRUNCATE on Dynamic Table",
            "TRUNCATE TABLE base_dt");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation TRUNCATE on Dynamic Table",
            "TRUNCATE base_dt");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation TRUNCATE on Dynamic Table",
            "TRUNCATE TABLE IF EXISTS base_dt");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'STREAM', not specified type 'TABLE'.",
            "TRUNCATE TABLE base_s");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'STREAM', not specified type 'TABLE'.",
            "TRUNCATE base_s");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'STREAM', not specified type 'TABLE'.",
            "TRUNCATE TABLE IF EXISTS base_s");
        assertAnswer(
            "REFUSED SQL compilation error: | MERGE statement's target must be a table",
            "MERGE INTO base_s USING (SELECT 1 x) s ON FALSE WHEN NOT MATCHED THEN INSERT (a) VALUES (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT (MULTI-TABLE) statement's target must be a table",
            "INSERT ALL INTO base_s VALUES (2) SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.",
            "TRUNCATE base_v");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.",
            "TRUNCATE TABLE IF EXISTS base_v");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'MATERIALIZED_VIEW', not specified type "
                + "'TABLE'.",
            "TRUNCATE TABLE IF EXISTS base_mv");
        assertAnswer(
            "Statement executed successfully.",
            "TRUNCATE TABLE IF EXISTS base_nope");
        assertAnswer(
            "Statement executed successfully.",
            "TRUNCATE TABLE base_et");
        assertAnswer(
            "Statement executed successfully.",
            "TRUNCATE TABLE IF EXISTS base_et");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT (MULTI-TABLE) statement's target must be a table",
            "INSERT ALL INTO base_et VALUES (2) SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | INSERT statement's target must be a table",
            "INSERT OVERWRITE INTO base_et SELECT 2");
        assertAnswer(
            "0",
            "DELETE FROM base_et");
    }

    @Test
    public void aQualifiedOrAJoinedDynamicTable() {
        answer(
            "CREATE OR REPLACE TABLE base_t (a INT)");
        answer(
            "INSERT INTO base_t VALUES (1)");
        answer(
            "CREATE OR REPLACE VIEW base_v AS SELECT a FROM base_t");
        answer(
            "CREATE OR REPLACE STREAM base_s ON TABLE base_t");
        answer(
            "CREATE OR REPLACE DYNAMIC TABLE base_dt TARGET_LAG = '1 day' WAREHOUSE = COMPUTE_WH AS SELECT a FROM "
                + "base_t");
        answer(
            "CREATE OR REPLACE EVENT TABLE base_et");
        answer(
            "CREATE OR REPLACE TABLE plain_w (a INT)");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation INSERT on Dynamic Table",
            "INSERT INTO test_db.test_schema.base_dt VALUES (2)");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'STREAM', not specified type 'TABLE'.",
            "TRUNCATE TABLE test_schema.base_s");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation DELETE on Dynamic Table",
            "DELETE FROM base_dt WHERE nope = 1");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation UPDATE on Dynamic Table",
            "UPDATE base_dt SET nope = 1");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation UPDATE on Dynamic Table",
            "UPDATE base_dt d SET a = 2 FROM base_t t WHERE d.a = t.a");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation DELETE on Dynamic Table",
            "DELETE FROM base_dt USING base_t t WHERE base_dt.a = t.a");
        assertAnswer(
            "REFUSED SQL Compilation error: Invalid Operation TRUNCATE on Dynamic Table",
            "TRUNCATE TABLE IF EXISTS test_schema.base_dt");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM base_dt");
    }
}
