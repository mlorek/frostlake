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
 * A RECURSIVE view named by a quoted name recurses as the same name written plain does: "RVX" with a trailing
 * branch, and a name in lower or mixed case, which a WITH RECURSIVE refuses (all live-verified).
 */
public class QuotedRecursiveViewTest extends MeasuredCellsTestSupport {

    @Test
    public void quotedNames() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE t40 (a INT)");
        assertAnswer("View RV2 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv2(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rv2 WHERE n < 3 UNION "
                + "ALL SELECT 100");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rv2 ORDER BY 1");
        assertAnswer("View RVX successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW \"RVX\"(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM \"RVX\" WHERE n < 3 "
                + "UNION ALL SELECT 100");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM \"RVX\" ORDER BY 1");
        assertAnswer("View RVY successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW \"RVY\"(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM \"RVY\" WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rvy ORDER BY 1");
        assertAnswer("View \"rvq\" successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW \"rvq\"(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM \"rvq\" WHERE n < 3");
        assertAnswer("View RVZ successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rvz(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM \"RVZ\" WHERE n < 3 "
                + "UNION ALL SELECT 100");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rvz ORDER BY 1");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void namesInAnyCase() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("View \"rvq\" successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW \"rvq\"(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM \"rvq\" WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM \"rvq\" ORDER BY 1");
        assertAnswer("View \"Rv Q\" successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW \"Rv Q\"(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM \"Rv Q\" WHERE n < "
                + "3");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM \"Rv Q\" ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.RVW' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.RVW.",
            "CREATE OR REPLACE RECURSIVE VIEW \"rvw\"(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rvw WHERE n < 3");
        assertAnswer("REFUSED SQL compilation error: | 'rq' is an invalid Recursive CTE",
            "WITH RECURSIVE \"rq\"(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM \"rq\" WHERE n < 3) SELECT * FROM "
                + "\"rq\"");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
