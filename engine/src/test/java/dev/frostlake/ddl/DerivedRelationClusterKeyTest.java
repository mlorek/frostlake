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
 * A materialized view's and a dynamic table's clustering key as written: SHOW and GET_DDL print the text after
 * CLUSTER BY as the statement wrote it, its spacing and casing kept, ALTER MATERIALIZED VIEW and ALTER DYNAMIC
 * TABLE set and drop it, and a column action ALTER DYNAMIC TABLE does not take is refused at its word (all
 * live-verified).
 */
public class DerivedRelationClusterKeyTest extends MeasuredCellsTestSupport {

    @Test
    public void keysAsWritten() {
        engine.execute("CREATE OR REPLACE TABLE ckt2050 (a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO ckt2050 VALUES (1, 'X', 3)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE ckt2050 CLUSTER BY (LOWER( b ),  a)");
        assertAnswer("LINEAR(LOWER( b ),  a)", "SHOW TABLES LIKE 'CKT2050' ->> SELECT \"cluster_by\" FROM $1");
        assertAnswer("Materialized view CKMV2050 successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW ckmv2050 CLUSTER BY (LOWER( b ),  a) AS SELECT a, b FROM ckt2050");
        assertAnswer("LINEAR(LOWER( b ),  a)",
            "SHOW MATERIALIZED VIEWS LIKE 'CKMV2050' ->> SELECT \"cluster_by\" FROM $1");
        assertAnswer("create or replace materialized view CKMV2050( | 	A, | 	B | ) cluster by (LOWER( b ),  a) as "
                + "SELECT a, b FROM ckt2050;",
            "SELECT GET_DDL('VIEW', 'ckmv2050')");
        assertAnswer("create or replace materialized view CKMV2050( | 	A, | 	B | ) cluster by (LOWER( b ),  a) as "
                + "SELECT a, b FROM ckt2050;",
            "SELECT GET_DDL('MATERIALIZED_VIEW', 'ckmv2050')");
        assertAnswer("Statement executed successfully.", "ALTER MATERIALIZED VIEW ckmv2050 CLUSTER BY (a,lower(B))");
        assertAnswer("LINEAR(a,lower(B))", "SHOW MATERIALIZED VIEWS LIKE 'CKMV2050' ->> SELECT \"cluster_by\" FROM $1");
        assertAnswer("Statement executed successfully.", "ALTER MATERIALIZED VIEW ckmv2050 DROP CLUSTERING KEY");
        assertAnswer("", "SHOW MATERIALIZED VIEWS LIKE 'CKMV2050' ->> SELECT \"cluster_by\" FROM $1");
        assertAnswer("Materialized view CKMV2050 successfully created.",
            "CREATE OR REPLACE MATERIALIZED VIEW ckmv2050 CLUSTER BY (b) AS SELECT a, b FROM ckt2050");
        assertAnswer("LINEAR(b)", "SHOW MATERIALIZED VIEWS LIKE 'CKMV2050' ->> SELECT \"cluster_by\" FROM $1");
        assertAnswer("Dynamic table CKDT2050 successfully created.",
            "CREATE OR REPLACE DYNAMIC TABLE ckdt2050 CLUSTER BY (LOWER( b ),  a) TARGET_LAG = '1 hour' WAREHOUSE = "
                + "compute_wh AS SELECT a, b FROM ckt2050");
        assertAnswer("LINEAR(LOWER( b ),  a)", "SHOW DYNAMIC TABLES LIKE 'CKDT2050' ->> SELECT \"cluster_by\" FROM $1");
        assertAnswer("create or replace dynamic table CKDT2050( | 	A, | 	B | ) target_lag = '1 hour' refresh_mode = "
                + "AUTO initialize = ON_CREATE warehouse = COMPUTE_WH |  cluster by (LOWER( b ),  a) as SELECT a, b "
                + "FROM ckt2050;",
            "SELECT GET_DDL('TABLE', 'ckdt2050')");
        assertAnswer("create or replace dynamic table CKDT2050( | 	A, | 	B | ) target_lag = '1 hour' refresh_mode = "
                + "AUTO initialize = ON_CREATE warehouse = COMPUTE_WH |  cluster by (LOWER( b ),  a) as SELECT a, b "
                + "FROM ckt2050;",
            "SELECT GET_DDL('DYNAMIC_TABLE', 'ckdt2050')");
        assertAnswer("Statement executed successfully.", "ALTER DYNAMIC TABLE ckdt2050 CLUSTER BY (a,lower(B))");
        assertAnswer("LINEAR(a,lower(B))", "SHOW DYNAMIC TABLES LIKE 'CKDT2050' ->> SELECT \"cluster_by\" FROM $1");
        assertAnswer("Statement executed successfully.", "ALTER DYNAMIC TABLE ckdt2050 DROP CLUSTERING KEY");
        assertAnswer("", "SHOW DYNAMIC TABLES LIKE 'CKDT2050' ->> SELECT \"cluster_by\" FROM $1");
        assertAnswer("Statement executed successfully.", "ALTER DYNAMIC TABLE ckdt2050 CLUSTER BY (b)");
        assertAnswer("LINEAR(b)", "SHOW DYNAMIC TABLES LIKE 'CKDT2050' ->> SELECT \"cluster_by\" FROM $1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'COLUMN'.",
            "ALTER DYNAMIC TABLE ckdt2050 RENAME COLUMN b TO b2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'COLUMN'.",
            "ALTER DYNAMIC TABLE ckdt2050 DROP COLUMN b");
    }
}
