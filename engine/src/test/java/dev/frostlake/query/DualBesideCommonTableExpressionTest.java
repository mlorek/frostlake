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
 * The bare word dual in a FROM clause is the pseudo-table even beside a common table expression of that name, which
 * only "DUAL" reaches (live-verified): its one column holds NULL and no qualifier names it.
 */
public class DualBesideCommonTableExpressionTest extends MeasuredCellsTestSupport {

    @Test
    public void theBareWordDualIsThePseudoTable() {
        assertCells(new String[][] {
            {"WITH dual AS (SELECT 5 AS column1) SELECT dual.column1 FROM dual",
                "REFUSED SQL compilation error: error line 1 at position 42 | invalid identifier 'DUAL.COLUMN1'"},
            {"WITH dual AS (SELECT 5 AS column1) SELECT column1 FROM dual", "null"},
            {"WITH dual AS (SELECT 5 AS column1) SELECT COUNT(*) FROM dual", "1"},
            {"WITH dual AS (SELECT 5 AS c) SELECT c FROM dual",
                "REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'C'"},
            {"WITH \"dual\" AS (SELECT 5 AS c) SELECT c FROM \"dual\"", "5"},
            {"WITH \"DUAL\" AS (SELECT 5 AS c) SELECT c FROM \"DUAL\"", "5"},
            {"WITH dual AS (SELECT 5 AS c) SELECT c FROM \"DUAL\"", "5"},
            {"WITH dual AS (SELECT 5 AS c) SELECT d.c FROM dual d",
                "REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'D.C'"},
            {"WITH dual AS (SELECT 5 AS c) SELECT (SELECT c FROM dual)",
                "REFUSED SQL compilation error: error line 1 at position 44 | invalid identifier 'C'"},
            {"WITH dual AS (SELECT 5 AS c) SELECT c FROM test_db.test_schema.dual",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.DUAL' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.DUAL."},
        });
    }
}
