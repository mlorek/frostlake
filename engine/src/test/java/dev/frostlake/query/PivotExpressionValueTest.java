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
 * A PIVOT value written as a constant expression — a cast, a call, an operator over constants — pivots on the value
 * it evaluates to and names its column as the plan prints it: TRUE::BOOLEAN is CAST(TRUE AS BOOLEAN), NOT FALSE is
 * NOT(FALSE), +2 UNARY PLUS(2), a scalar subquery its query, a cast NULL NULL; a text meets a timestamp column as the
 * timestamp it converts to (live-verified).
 */
public class PivotExpressionValueTest extends MeasuredCellsTestSupport {

    @Test
    public void aConstantExpressionIsAPivotValue() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, d DATE, s VARCHAR, ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE, '2024-01-01', 'X', '2024-01-01 10:00:00'), (2, FALSE, "
            + "'2024-01-02', 'y', '2024-01-02 00:00:00'), (3, NULL, NULL, NULL, NULL)");
        assertShape("D:DATE, S:VARCHAR(16777216), TS:TIMESTAMP_NTZ, CAST(TRUE AS BOOLEAN):NUMBER(38,0)",
            "SELECT * FROM fz PIVOT(SUM(id) FOR b IN (TRUE::BOOLEAN))");
        assertCells(new String[][] {
            {"SELECT * FROM fz PIVOT(SUM(id) FOR b IN (TRUE::BOOLEAN)) ORDER BY 1",
                "2024-01-01|X|2024-01-01T10:00|1 ;; 2024-01-02|y|2024-01-02T00:00|null ;; null|null|null|null"},
        });
        assertShape("B:BOOLEAN, S:VARCHAR(16777216), TS:TIMESTAMP_NTZ, CAST('2024-01-01' AS DATE):NUMBER(38,0)",
            "SELECT * FROM fz PIVOT(SUM(id) FOR d IN ('2024-01-01'::DATE))");
        assertCells(new String[][] {
            {"SELECT * FROM fz PIVOT(SUM(id) FOR d IN ('2024-01-01'::DATE)) ORDER BY 1",
                "false|y|2024-01-02T00:00|null ;; true|X|2024-01-01T10:00|1 ;; null|null|null|null"},
        });
        assertShape("B:BOOLEAN, S:VARCHAR(16777216), TS:TIMESTAMP_NTZ, TO_DATE('2024-01-01'):NUMBER(38,0)",
            "SELECT * FROM fz PIVOT(SUM(id) FOR d IN (TO_DATE('2024-01-01')))");
        assertCells(new String[][] {
            {"SELECT * FROM fz PIVOT(SUM(id) FOR d IN (TO_DATE('2024-01-01'))) ORDER BY 1",
                "false|y|2024-01-02T00:00|null ;; true|X|2024-01-01T10:00|1 ;; null|null|null|null"},
        });
        assertShape("B:BOOLEAN, D:DATE, S:VARCHAR(16777216), TS:TIMESTAMP_NTZ, 1 + 1:NUMBER(38,0), 3:NUMBER(38,0)",
            "SELECT * FROM fz PIVOT(SUM(id) FOR id IN (1 + 1, 3))");
        assertCells(new String[][] {
            {"SELECT * FROM fz PIVOT(SUM(id) FOR id IN (1 + 1, 3)) ORDER BY 1",
                "false|2024-01-02|y|2024-01-02T00:00|2|null ;; true|2024-01-01|X|2024-01-01T10:00|null|null ;; "
                + "null|null|null|null|null|3"},
        });
        assertShape("D:DATE, S:VARCHAR(16777216), TS:TIMESTAMP_NTZ, NOT(FALSE):NUMBER(38,0)",
            "SELECT * FROM fz PIVOT(SUM(id) FOR b IN (NOT FALSE))");
        assertCells(new String[][] {
            {"SELECT * FROM fz PIVOT(SUM(id) FOR b IN (NOT FALSE)) ORDER BY 1",
                "2024-01-01|X|2024-01-01T10:00|1 ;; 2024-01-02|y|2024-01-02T00:00|null ;; null|null|null|null"},
        });
        assertShape("B:BOOLEAN, D:DATE, TS:TIMESTAMP_NTZ, UPPER('x'):NUMBER(38,0), 'y':NUMBER(38,0)",
            "SELECT * FROM fz PIVOT(SUM(id) FOR s IN (UPPER('x'), 'y'))");
        assertCells(new String[][] {
            {"SELECT * FROM fz PIVOT(SUM(id) FOR s IN (UPPER('x'), 'y')) ORDER BY 1",
                "false|2024-01-02|2024-01-02T00:00|null|2 ;; true|2024-01-01|2024-01-01T10:00|1|null ;; "
                + "null|null|null|null|null"},
        });
        assertShape("B:BOOLEAN, D:DATE, S:VARCHAR(16777216), TS:TIMESTAMP_NTZ, -1:NUMBER(38,0), UNARY "
                + "PLUS(2):NUMBER(38,0)",
            "SELECT * FROM fz PIVOT(SUM(id) FOR id IN (-1, +2))");
        assertCells(new String[][] {
            {"SELECT * FROM fz PIVOT(SUM(id) FOR id IN (-1, +2)) ORDER BY 1",
                "false|2024-01-02|y|2024-01-02T00:00|null|2 ;; true|2024-01-01|X|2024-01-01T10:00|null|null ;; "
                + "null|null|null|null|null|null"},
        });
        assertShape("B:BOOLEAN, S:VARCHAR(16777216), TS:TIMESTAMP_NTZ, CAST('2024-01-01' AS DATE):NUMBER(38,0)",
            "SELECT * FROM fz PIVOT(SUM(id) FOR d IN (CAST('2024-01-01' AS DATE)))");
        assertCells(new String[][] {
            {"SELECT * FROM fz PIVOT(SUM(id) FOR d IN (CAST('2024-01-01' AS DATE))) ORDER BY 1",
                "false|y|2024-01-02T00:00|null ;; true|X|2024-01-01T10:00|1 ;; null|null|null|null"},
        });
        assertShape("B:BOOLEAN, D:DATE, S:VARCHAR(16777216), TS:TIMESTAMP_NTZ, (SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL):NUMBER(38,0)",
            "SELECT * FROM fz PIVOT(SUM(id) FOR id IN ((SELECT 1)))");
        assertCells(new String[][] {
            {"SELECT * FROM fz PIVOT(SUM(id) FOR id IN ((SELECT 1))) ORDER BY 1",
                "false|2024-01-02|y|2024-01-02T00:00|null ;; true|2024-01-01|X|2024-01-01T10:00|1 ;; "
                + "null|null|null|null|null"},
        });
        assertShape("B:BOOLEAN, D:DATE, S:VARCHAR(16777216), TS:TIMESTAMP_NTZ, NULL:NUMBER(38,0)",
            "SELECT * FROM fz PIVOT(SUM(id) FOR id IN (NULL::INT))");
        assertCells(new String[][] {
            {"SELECT * FROM fz PIVOT(SUM(id) FOR id IN (NULL::INT)) ORDER BY 1",
                "false|2024-01-02|y|2024-01-02T00:00|null ;; true|2024-01-01|X|2024-01-01T10:00|null ;; "
                + "null|null|null|null|null"},
        });
        assertShape("B:BOOLEAN, D:DATE, S:VARCHAR(16777216), '2024-01-01 10:00:00':NUMBER(38,0)",
            "SELECT * FROM fz PIVOT(SUM(id) FOR ts IN ('2024-01-01 10:00:00'))");
        assertCells(new String[][] {
            {"SELECT * FROM fz PIVOT(SUM(id) FOR ts IN ('2024-01-01 10:00:00')) ORDER BY 1",
                "false|2024-01-02|y|null ;; true|2024-01-01|X|1 ;; null|null|null|null"},
        });
    }
}
