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
 * A RECURSIVE view's body recurses as the account reads it (live-verified): the top of the body is its trailing run
 * of one set operator, INTERSECT binding tighter; that run recurses when it unions, the element after its first
 * operator reads the view and no element before it does - the anchor - and every element after that recursive term
 * takes no part, a second recursive term included. Any other body - an EXCEPT or a MINUS on top, a distinct UNION on
 * top whose second element reads nothing, a second anchor before the recursive term, an anchor that reads the view -
 * reads the view as any query would, and is refused at CREATE as the missing object the view still is. A body whose
 * recursive term holds an INTERSECT is created; reading it fails on the account itself, which is not mirrored.
 */
public class RecursiveViewShapeTest extends MeasuredCellsTestSupport {

    @Test
    public void aTrailingOperandTakesNoPartAndOtherShapesAreRefused() {
        assertAnswer("View RV2 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv2(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rv2 WHERE n < 3 UNION "
                + "ALL SELECT 100");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rv2 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.RV6' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.RV6.",
            "CREATE OR REPLACE RECURSIVE VIEW rv6(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rv6 WHERE n < 3 EXCEPT "
                + "SELECT 2");
        assertAnswer("REFUSED SQL compilation error: | Object 'RV6' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE RV6.",
            "SELECT * FROM rv6 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | 'R' is an invalid Recursive CTE",
            "WITH r(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM r WHERE n<3 UNION ALL SELECT 100) SELECT * FROM r "
                + "ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.RV7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.RV7.",
            "CREATE OR REPLACE RECURSIVE VIEW rv7(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rv7 WHERE n < 3 UNION "
                + "SELECT 100");
        assertAnswer("REFUSED SQL compilation error: | Object 'RV7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE RV7.",
            "SELECT * FROM rv7 ORDER BY 1");
        assertAnswer("View RV8 successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rv8(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rv8 WHERE n < 3 "
                + "INTERSECT SELECT 2");
        answer("SELECT * FROM rv8 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.RV9' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.RV9.",
            "CREATE OR REPLACE RECURSIVE VIEW rv9(n) AS SELECT 100 UNION ALL SELECT 1 UNION ALL SELECT n + 1 FROM "
                + "rv9 WHERE n < 3");
        assertAnswer("REFUSED SQL compilation error: | Object 'RV9' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE RV9.",
            "SELECT * FROM rv9 ORDER BY 1");
    }

    @Test
    public void theRecursionIsTheTopRunsSecondElementAlone() {
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.RVA' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.RVA.",
            "CREATE OR REPLACE RECURSIVE VIEW rva(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rva WHERE n < 3 UNION "
                + "SELECT n + 10 FROM rva WHERE n < 3");
        assertAnswer("REFUSED SQL compilation error: | Object 'RVA' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE RVA.",
            "SELECT * FROM rva ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.RVB' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.RVB.",
            "CREATE OR REPLACE RECURSIVE VIEW rvb(n) AS SELECT n FROM rvb UNION ALL SELECT n + 1 FROM rvb WHERE n < 3");
        assertAnswer("REFUSED SQL compilation error: | Object 'RVB' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE RVB.",
            "SELECT * FROM rvb ORDER BY 1");
        assertAnswer("View RVC successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rvc(n) AS SELECT 5 EXCEPT SELECT 6 UNION ALL SELECT n + 1 FROM rvc "
                + "WHERE n < 7");
        assertAnswer("5 ;; 6 ;; 7", "SELECT * FROM rvc ORDER BY 1");
        assertAnswer("View RVD successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rvd(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rvd WHERE n < 3 UNION "
                + "ALL SELECT n + 10 FROM rvd WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rvd ORDER BY 1");
        assertAnswer("View RVE successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rve(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rve WHERE n < 3 UNION "
                + "ALL SELECT 100 UNION ALL SELECT n + 10 FROM rve WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rve ORDER BY 1");
        assertAnswer("View RVF successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rvf(n) AS SELECT 1 UNION SELECT n + 1 FROM rvf WHERE n < 3 UNION "
                + "SELECT 100");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rvf ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.RVG' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.RVG.",
            "CREATE OR REPLACE RECURSIVE VIEW rvg(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rvg WHERE n < 3 MINUS "
                + "SELECT 2");
        assertAnswer("View RVH successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rvh(n) AS (SELECT 1 UNION ALL SELECT 2) UNION ALL SELECT n + 1 FROM "
                + "rvh WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 2 ;; 3 ;; 3", "SELECT * FROM rvh ORDER BY 1");
        assertAnswer("View RVI successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rvi(n) AS SELECT 1 UNION ALL (SELECT n + 1 FROM rvi WHERE n < 3 "
                + "INTERSECT SELECT 2)");
        answer("SELECT COUNT(*) FROM rvi");
        assertAnswer("View RVJ successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rvj(n) AS SELECT 1 INTERSECT SELECT 1 UNION ALL SELECT n + 1 FROM rvj "
                + "WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rvj ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.RV_NOSUCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.RV_NOSUCH.",
            "CREATE OR REPLACE VIEW rvmiss AS SELECT * FROM rv_nosuch");
        assertAnswer("View RVK successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rvk(n) AS SELECT 1 UNION SELECT 2 UNION ALL SELECT n + 1 FROM rvk "
                + "WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 2 ;; 3 ;; 3", "SELECT * FROM rvk ORDER BY 1");
        assertAnswer("View RVL successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW RVL(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rvl WHERE n < 3");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rvl ORDER BY 1");
        assertAnswer("View RVM successfully created.",
            "CREATE OR REPLACE RECURSIVE VIEW rvm(n) AS SELECT 1 UNION ALL SELECT n + 1 FROM rvm WHERE n < 3 "
                + "INTERSECT SELECT n + 1 FROM rvm WHERE n < 2");
    }
}
