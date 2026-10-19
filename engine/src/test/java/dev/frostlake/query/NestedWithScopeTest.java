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
 * A WITH nested in a derived table, a scalar, IN or EXISTS subquery or a parenthesized set-operation arm sees the
 * enclosing statement's CTEs (live-verified), and an enclosing CTE wins over a nested one of its name:
 * {@code WITH c AS (SELECT 99 AS a) SELECT * FROM (WITH c AS (SELECT 5 AS a) SELECT * FROM c)} is 99.
 */
public class NestedWithScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void aNestedWithSeesTheEnclosingCtes() {
        engine.execute("CREATE OR REPLACE TABLE c (a INT)");
        engine.execute("INSERT INTO c VALUES (1), (2)");
        assertCells(new String[][] {
            {"WITH c AS (SELECT 99 AS a) SELECT * FROM (SELECT * FROM c)", "99"},
            {"WITH c AS (SELECT 99 AS a) SELECT * FROM (WITH d AS (SELECT * FROM c) SELECT * FROM d)", "99"},
            {"WITH c AS (SELECT 99 AS a) SELECT * FROM c WHERE a IN (WITH d AS (SELECT a FROM c) SELECT a FROM d)",
                "99"},
            {"WITH x AS (SELECT 1 AS v) SELECT * FROM x UNION ALL (WITH d AS (SELECT v + 1 AS v FROM x) SELECT * FROM "
                + "d) ORDER BY 1",
                "1 ;; 2"},
            {"WITH c AS (SELECT 99 AS a) SELECT * FROM (WITH c AS (SELECT 5 AS a) SELECT * FROM c)", "99"},
            {"WITH c AS (SELECT 99 AS a) SELECT (WITH d AS (SELECT a FROM c) SELECT MAX(a) FROM d)", "99"},
            {"WITH c AS (SELECT 99 AS a) SELECT EXISTS (WITH d AS (SELECT a FROM c WHERE a = 99) SELECT * FROM d)",
                "true"},
            {"WITH c AS (SELECT 99 AS a) SELECT * FROM (WITH d AS (SELECT * FROM c) SELECT * FROM d UNION ALL SELECT "
                + "* FROM c) ORDER BY 1",
                "99 ;; 99"},
        });
    }
}
