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
 * A RECURSIVE view's body recurses as the account reads it: its distinct UNION keeps a repeated row, two
 * anchors joined by UNION ALL before the recursive operand, or a distinct UNION after it, make the body
 * non-recursive and the view is refused at CREATE as missing, and a trailing non-recursive operand is dropped
 * (all live-verified).
 */
public class RecursiveViewBodyRulesTest extends MeasuredCellsTestSupport {

    @Test
    public void recursionAndItsUnions() {
        assertAnswer("View RV6 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv6(n) AS SELECT 1 UNION ALL SELECT 1 UNION SELECT n + 1 FROM rv6 "
                + "WHERE n < 3");
        assertAnswer("1 ;; 1 ;; 2 ;; 2 ;; 3 ;; 3", "SELECT * FROM rv6 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.RV7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.RV7.",
            "CREATE OR REPLACE RECURSIVE VIEW rv7(n) AS SELECT 1 UNION ALL SELECT 10 UNION ALL SELECT n + 1 FROM rv7 "
                + "WHERE n < 3");
        assertAnswer("REFUSED SQL compilation error: | Object 'RV7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE RV7.",
            "SELECT * FROM rv7 ORDER BY 1");
        assertAnswer("View RV8 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv8(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rv8 WHERE n < 3 UNION "
                + "ALL SELECT 7");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rv8 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.RV10' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.RV10.",
            "CREATE OR REPLACE RECURSIVE VIEW rv10(n) AS SELECT 1 UNION SELECT 1 UNION SELECT n + 1 FROM rv10 WHERE "
                + "n < 3");
        assertAnswer("REFUSED SQL compilation error: | Object 'RV10' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE RV10.",
            "SELECT * FROM rv10 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.RV11' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.RV11.",
            "CREATE OR REPLACE RECURSIVE VIEW rv11(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rv11 WHERE n < 3 UNION "
                + "SELECT 2");
        assertAnswer("REFUSED SQL compilation error: | Object 'RV11' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE RV11.",
            "SELECT * FROM rv11 ORDER BY 1");
        assertAnswer("View RV12 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv12(n) AS SELECT 1 UNION SELECT n + 1 FROM rv12 WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rv12 ORDER BY 1");
        assertAnswer("View RV14 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv14(n) AS SELECT 1 UNION SELECT 10 UNION ALL SELECT n + 1 FROM rv14 "
                + "WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 3 ;; 10", "SELECT * FROM rv14 ORDER BY 1");
        assertAnswer("View RV15 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv15(n) AS (SELECT 1 UNION ALL SELECT 10) UNION ALL SELECT n + 1 FROM "
                + "rv15 WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 3 ;; 10", "SELECT * FROM rv15 ORDER BY 1");
        assertAnswer("View RV16 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv16(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rv16 WHERE n < 3 UNION "
                + "ALL SELECT n + 10 FROM rv16 WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rv16 ORDER BY 1");
    }
}
