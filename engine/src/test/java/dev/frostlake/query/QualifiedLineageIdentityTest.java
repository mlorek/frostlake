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
 * A dynamic table and a join view over tables named in full know a row by its identity as over bare names: an
 * UPDATE reads as one row's DELETE and INSERT, METADATA$ISUPDATE TRUE, in CHANGES and on a stream (all
 * live-verified).
 */
public class QualifiedLineageIdentityTest extends MeasuredCellsTestSupport {

    @Test
    public void qualifiedNames() {
        engine.execute("CREATE OR REPLACE TABLE dtq2531 (a INT)");
        engine.execute("INSERT INTO dtq2531 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dq2531 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS "
            + "SELECT a FROM test_db.test_schema.dtq2531");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dqb2531 TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS "
            + "SELECT a * 10 AS b FROM test_db.test_schema.dtq2531 WHERE a > 0");
        engine.execute("CREATE OR REPLACE STREAM sdq2531 ON DYNAMIC TABLE dq2531");
        engine.execute("CREATE OR REPLACE TABLE jx2531 (k INT, a INT) CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE jy2531 (k INT, b INT) CHANGE_TRACKING = TRUE");
        engine.execute("INSERT INTO jx2531 VALUES (1, 10), (2, 20)");
        engine.execute("INSERT INTO jy2531 VALUES (1, 100), (2, 200)");
        engine.execute("CREATE OR REPLACE VIEW vjq2531 AS SELECT jx2531.k, a, b FROM test_db.test_schema.jx2531 JOIN "
            + "test_db.test_schema.jy2531 ON jx2531.k = jy2531.k");
        engine.execute("CREATE OR REPLACE STREAM svj2531 ON VIEW vjq2531");
        assertAnswer("Statement executed successfully.", "SET ts2531a = CURRENT_TIMESTAMP()");
        assertAnswer("1|0", "UPDATE dtq2531 SET a = 10 WHERE a = 1");
        engine.execute("ALTER DYNAMIC TABLE dq2531 REFRESH");
        engine.execute("ALTER DYNAMIC TABLE dqb2531 REFRESH");
        assertAnswer("Statement executed successfully.", "SET ts2531b = CURRENT_TIMESTAMP()");
        assertAnswer("1", "DELETE FROM dtq2531 WHERE a = 2");
        engine.execute("ALTER DYNAMIC TABLE dq2531 REFRESH");
        engine.execute("ALTER DYNAMIC TABLE dqb2531 REFRESH");
        assertAnswer("1|DELETE|true ;; 10|INSERT|true",
            "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM dq2531 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP "
                + "=> $ts2531a) END (TIMESTAMP => $ts2531b) ORDER BY 1, 2");
        assertAnswer("1|DELETE|true ;; 2|DELETE|false ;; 10|INSERT|true",
            "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM dq2531 CHANGES (INFORMATION => DEFAULT) AT (TIMESTAMP "
                + "=> $ts2531a) ORDER BY 1, 2");
        assertAnswer("10|DELETE|true ;; 100|INSERT|true",
            "SELECT b, METADATA$ACTION, METADATA$ISUPDATE FROM dqb2531 CHANGES (INFORMATION => DEFAULT) AT "
                + "(TIMESTAMP => $ts2531a) END (TIMESTAMP => $ts2531b) ORDER BY 1, 2");
        assertAnswer("10|DELETE|true ;; 20|DELETE|false ;; 100|INSERT|true",
            "SELECT b, METADATA$ACTION, METADATA$ISUPDATE FROM dqb2531 CHANGES (INFORMATION => DEFAULT) AT "
                + "(TIMESTAMP => $ts2531a) ORDER BY 1, 2");
        assertAnswer("1|DELETE|true ;; 2|DELETE|false ;; 10|INSERT|true",
            "SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM sdq2531 ORDER BY 1, 2");
        assertAnswer("1|0", "UPDATE jx2531 SET a = 11 WHERE k = 1");
        assertAnswer("1|10|100|DELETE|true ;; 1|11|100|INSERT|true",
            "SELECT k, a, b, METADATA$ACTION, METADATA$ISUPDATE FROM svj2531 ORDER BY 1, 4");
    }
}
