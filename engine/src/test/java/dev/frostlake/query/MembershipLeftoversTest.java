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
 * Two memberships as the account judges them (live-verified). A set operation an IN reads that holds an outer
 * aggregate is {@code Unsupported subquery type cannot be evaluated} at the subquery, ahead of the place rule. And
 * {@code x IN (SELECT 1 WHERE FALSE)} is NULL only for a left operand the plan holds as NULL — NULL, NULL::INT,
 * MAX(NULL), MIN(NULL) — where an aggregate of a type over NULLs alone, SUM(NULL), AVG(NULL) or MAX(NULL::INT), is
 * NULL only as its rows are aggregated, and the membership is FALSE.
 */
public class MembershipLeftoversTest extends MeasuredCellsTestSupport {

    @Test
    public void aSetOperationWithAnOuterAggregateIsUnsupported() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT)");
        engine.execute("INSERT INTO fz VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT)");
        engine.execute("INSERT INTO g VALUES (1)");
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE id IN (SELECT 1 UNION ALL SELECT MAX(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "31"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT MAX(NULL) AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT NULL AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT MIN(NULL) AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT SUM(NULL) AS x)", "false"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT MAX(NULL::INT) AS x)", "false"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT MAX(id) AS x FROM fz WHERE FALSE)", "false"},
            {"SELECT x IN (SELECT 1) FROM (SELECT MAX(NULL) AS x)", "null"},
            {"SELECT x NOT IN (SELECT 1 WHERE FALSE) FROM (SELECT MAX(NULL) AS x)", "null"},
            {"SELECT id FROM fz WHERE id IN (SELECT 1 UNION SELECT MAX(fz.id) FROM g)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "31"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(fz.id) FROM g UNION ALL SELECT 1)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "31"},
        });
    }

    @Test
    public void aNullTheAggregateComputesIsNoFoldedNull() {
        assertCells(new String[][] {
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT NULL AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT MAX(NULL) AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT MIN(NULL) AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT SUM(NULL) AS x)", "false"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT AVG(NULL) AS x)", "false"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT COUNT(NULL) AS x)", "false"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT MAX(NULL::INT) AS x)", "false"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT NULL::INT AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT CAST(NULL AS VARCHAR) AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT IFF(FALSE, 1, NULL) AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT COALESCE(NULL, NULL) AS x)", "null"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT MAX(CAST(NULL AS INT)) AS x)", "false"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT MIN(NULL::VARCHAR) AS x)", "false"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT NULL AS x)", "NULL[LOB]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT MAX(NULL) AS x)", "NULL[LOB]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT MIN(NULL) AS x)", "NULL[LOB]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT COUNT(NULL) AS x)", "NUMBER(18,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT MAX(NULL::INT) AS x)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT NULL::INT AS x)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT CAST(NULL AS VARCHAR) AS x)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT ANY_VALUE(NULL) AS x)", "NULL[LOB]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT NULLIF(1, 1) AS x)", "NUMBER(1,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT IFF(FALSE, 1, NULL) AS x)", "NUMBER(1,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT COALESCE(NULL, NULL) AS x)", "NULL[LOB]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT MAX(CAST(NULL AS INT)) AS x)", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT MIN(NULL::VARCHAR) AS x)", "VARCHAR[LOB]"},
            {"SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT MAX(NULL) AS x) LIMIT 1", "null"},
            {"SELECT x NOT IN (SELECT 1 WHERE FALSE) FROM (SELECT SUM(NULL) AS x)", "true"},
            {"SELECT x = ANY (SELECT 1 WHERE FALSE) FROM (SELECT MAX(NULL) AS x)", "null"},
            {"SELECT x = ANY (SELECT 1 WHERE FALSE) FROM (SELECT SUM(NULL) AS x)", "false"},
        });
    }
}
