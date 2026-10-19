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
 * A view created with CHANGE_TRACKING = TRUE is refused the first construct its query cannot track, in the order
 * the query is written (live-verified): a query block without FROM at its SELECT, a VALUES list and a table function
 * at no place, a scalar subquery at its SELECT, EXISTS at EXISTS, IN at no place, and a view it reads by that view's
 * own query, named.
 */
public class ChangeTrackedViewQueryTest extends MeasuredCellsTestSupport {

    @Test
    public void eachConstructIsRefusedWhereItStands() {
        engine.execute("CREATE OR REPLACE TABLE kt (a INT)");
        engine.execute("INSERT INTO kt VALUES (1)");
        engine.execute("CREATE OR REPLACE VIEW kv AS SELECT 1 AS a");
        assertCells(new String[][] {
            {"CREATE OR REPLACE VIEW kvc CHANGE_TRACKING = TRUE AS SELECT a FROM (SELECT 1 AS a)",
                "REFUSED SQL compilation error: line 1 at position 68: | Change tracking is not supported on "
                + "queries with 'VALUES'."},
            {"CREATE OR REPLACE VIEW kvc2 CHANGE_TRACKING = TRUE AS WITH c AS (SELECT 1 AS a) SELECT a FROM c",
                "REFUSED SQL compilation error: line 1 at position 65: | Change tracking is not supported on "
                + "queries with 'VALUES'."},
            {"CREATE OR REPLACE VIEW kvc3 CHANGE_TRACKING = TRUE AS SELECT 1 AS a WHERE EXISTS (SELECT 1 FROM kt)",
                "REFUSED SQL compilation error: line 1 at position 54: | Change tracking is not supported on "
                + "queries with 'VALUES'."},
            {"CREATE OR REPLACE VIEW kvc4 CHANGE_TRACKING = TRUE AS SELECT * FROM VALUES (1)",
                "REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with 'VALUES'."},
            {"CREATE OR REPLACE VIEW kvc5 CHANGE_TRACKING = TRUE AS SELECT a FROM kv",
                "REFUSED SQL compilation error: line 1 at position 29: | Change tracking is not supported on "
                + "queries with 'VALUES'. (inside view 'TEST_DB.TEST_SCHEMA.KV')."},
        });
    }

    @Test
    public void subqueriesTableFunctionsAndUnionsAreRefusedToo() {
        engine.execute("CREATE OR REPLACE TABLE kt (a INT)");
        engine.execute("INSERT INTO kt VALUES (1)");
        engine.execute("CREATE OR REPLACE VIEW kv AS SELECT 1 AS a");
        assertCells(new String[][] {
            {"CREATE OR REPLACE VIEW kvc6 CHANGE_TRACKING = TRUE AS SELECT a FROM kt WHERE a IN (SELECT 1)",
                "REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with subquery expressions."},
            {"CREATE OR REPLACE VIEW kvc7 CHANGE_TRACKING = TRUE AS SELECT a, (SELECT 1) AS b FROM kt",
                "REFUSED SQL compilation error: line 1 at position 65: | Change tracking is not supported on "
                + "queries with subquery expressions."},
            {"CREATE OR REPLACE VIEW kvc8 CHANGE_TRACKING = TRUE AS SELECT a FROM kt UNION ALL SELECT 1",
                "REFUSED SQL compilation error: line 1 at position 81: | Change tracking is not supported on "
                + "queries with 'VALUES'."},
            {"CREATE OR REPLACE VIEW kvc9 CHANGE_TRACKING = TRUE AS SELECT a FROM kt WHERE EXISTS (SELECT 1 FROM kt)",
                "REFUSED SQL compilation error: line 1 at position 77: | Change tracking is not supported on "
                + "queries with subquery expressions."},
            {"CREATE OR REPLACE VIEW kvc11 CHANGE_TRACKING = TRUE AS SELECT 1 AS a UNION ALL SELECT * FROM VALUES (1)",
                "REFUSED SQL compilation error: line 1 at position 55: | Change tracking is not supported on "
                + "queries with 'VALUES'."},
            {"CREATE OR REPLACE VIEW kvc13 CHANGE_TRACKING = TRUE AS WITH c AS (SELECT a FROM kt) SELECT a FROM c",
                "View KVC13 successfully created."},
            {"CREATE OR REPLACE VIEW kvc14 CHANGE_TRACKING = TRUE AS SELECT SEQ4() AS s FROM "
                + "TABLE(GENERATOR(ROWCOUNT => 1))",
                "REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with 'TABLE_FUNCTION' inside views, saw 'GENERATOR'."},
            {"CREATE OR REPLACE VIEW kvc15 CHANGE_TRACKING = TRUE AS\nSELECT 1 AS a",
                "REFUSED SQL compilation error: line 2 at position 0: | Change tracking is not supported on "
                + "queries with 'VALUES'."},
            {"CREATE OR REPLACE VIEW kvc16 CHANGE_TRACKING = TRUE AS SELECT a FROM (VALUES (1)) v(a)",
                "REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with 'VALUES'."},
            {"CREATE OR REPLACE VIEW kvc17 CHANGE_TRACKING = TRUE AS SELECT a FROM kt, (SELECT 1 AS b)",
                "REFUSED SQL compilation error: line 1 at position 74: | Change tracking is not supported on "
                + "queries with 'VALUES'."},
            {"CREATE OR REPLACE VIEW kvc18 CHANGE_TRACKING = TRUE COMMENT = 'c' AS SELECT 1 AS a",
                "REFUSED SQL compilation error: line 1 at position 69: | Change tracking is not supported on "
                + "queries with 'VALUES'."},
            {"CREATE OR REPLACE SECURE VIEW kvc19 CHANGE_TRACKING = TRUE AS SELECT 1 AS a",
                "REFUSED SQL compilation error: line 1 at position 62: | Change tracking is not supported on "
                + "queries with 'VALUES'."},
        });
    }
}
