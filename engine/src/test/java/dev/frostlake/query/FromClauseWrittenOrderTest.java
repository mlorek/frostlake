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
 * A FROM clause's comma-separated items and explicit joins are joined in the order they are written (live-verified):
 * {@code SELECT *} over {@code FROM d JOIN p ON …, e} lists d, p and e, a positional reference reads them so, a USING
 * or NATURAL join merges the relations before it, and a join's ON reads only the relations written before it —
 * {@code FROM d JOIN p ON p.project_id = e.project_id, e} is "invalid identifier 'E.PROJECT_ID'".
 */
public class FromClauseWrittenOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void theItemsAndJoinsAreJoinedInWrittenOrder() {
        engine.execute("CREATE OR REPLACE TABLE d (department_id INT, name VARCHAR, location VARCHAR)");
        engine.execute("INSERT INTO d VALUES (1, 'D', 'L')");
        engine.execute("CREATE OR REPLACE TABLE p (project_id INT, name VARCHAR, department_id INT)");
        engine.execute("INSERT INTO p VALUES (10, 'P', 1)");
        engine.execute("CREATE OR REPLACE TABLE e (employee_id INT, name VARCHAR, department_id INT, project_id INT)");
        engine.execute("INSERT INTO e VALUES (100, 'E', 1, 10)");
        assertShape("DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), LOCATION:VARCHAR(16777216), "
                + "PROJECT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "EMPLOYEE_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "PROJECT_ID:NUMBER(38,0)",
            "SELECT * FROM d JOIN p ON p.department_id = d.department_id, e WHERE e.project_id = "
                + "p.project_id");
        assertCells(new String[][] {
            {"SELECT * FROM d JOIN p ON p.department_id = d.department_id, e WHERE e.project_id = p.project_id",
                "1|D|L|10|P|1|100|E|1|10"},
            {"SELECT $4, $7 FROM d JOIN p ON p.department_id = d.department_id, e WHERE e.project_id = p.project_id",
                "REFUSED SQL compilation error: error line 1 at position 11 | invalid identifier '$7'"},
            {"SELECT * FROM d JOIN p ON p.project_id = e.project_id, e",
                "REFUSED SQL compilation error: error line 1 at position 41 | invalid identifier 'E.PROJECT_ID'"},
            {"SELECT * FROM d, p JOIN e ON e.project_id = p.project_id", "1|D|L|10|P|1|100|E|1|10"},
        });
        assertShape("DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), LOCATION:VARCHAR(16777216), "
                + "PROJECT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "EMPLOYEE_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "PROJECT_ID:NUMBER(38,0), Z:NUMBER(1,0)",
            "SELECT * FROM d JOIN p ON TRUE, e JOIN (SELECT 1 AS z) q ON TRUE");
        assertCells(new String[][] {
            {"SELECT * FROM d JOIN p ON TRUE, e JOIN (SELECT 1 AS z) q ON TRUE", "1|D|L|10|P|1|100|E|1|10|1"},
        });
        assertShape("DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), LOCATION:VARCHAR(16777216), "
                + "PROJECT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "EMPLOYEE_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "PROJECT_ID:NUMBER(38,0)",
            "SELECT * FROM d LEFT JOIN p ON p.department_id = d.department_id, e");
        assertShape("DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), LOCATION:VARCHAR(16777216), "
                + "PROJECT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), EMPLOYEE_ID:NUMBER(38,0), "
                + "NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), PROJECT_ID:NUMBER(38,0)",
            "SELECT * FROM d JOIN p USING (department_id), e");
        assertCells(new String[][] {
            {"SELECT * FROM d JOIN p USING (department_id), e", "1|D|L|10|P|100|E|1|10"},
        });
        assertShape("DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), LOCATION:VARCHAR(16777216), "
                + "PROJECT_ID:NUMBER(38,0), EMPLOYEE_ID:NUMBER(38,0), NAME:VARCHAR(16777216), "
                + "DEPARTMENT_ID:NUMBER(38,0), PROJECT_ID:NUMBER(38,0)",
            "SELECT * FROM d NATURAL JOIN p, e");
        assertShape("DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), LOCATION:VARCHAR(16777216), "
                + "PROJECT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "EMPLOYEE_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "PROJECT_ID:NUMBER(38,0), Z:NUMBER(1,0)",
            "SELECT * FROM d, p, e JOIN (SELECT 1 AS z) q ON TRUE");
        assertShape("DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), LOCATION:VARCHAR(16777216), "
                + "PROJECT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "EMPLOYEE_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "PROJECT_ID:NUMBER(38,0), W:NUMBER(1,0)",
            "SELECT * FROM d JOIN p ON TRUE JOIN e ON TRUE, (SELECT 2 AS w) r");
        assertShape("DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), LOCATION:VARCHAR(16777216), "
                + "PROJECT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "EMPLOYEE_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "PROJECT_ID:NUMBER(38,0), V:NUMBER(1,0)",
            "SELECT * FROM d JOIN p ON TRUE, e, (SELECT 3 AS v) s");
        assertCells(new String[][] {
            {"SELECT * FROM d JOIN p ON TRUE, e, (SELECT 3 AS v) s", "1|D|L|10|P|1|100|E|1|10|3"},
        });
        assertShape("DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), LOCATION:VARCHAR(16777216), "
                + "PROJECT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "DN:VARCHAR(16777216)",
            "SELECT * FROM d JOIN p ON TRUE, LATERAL (SELECT d.name AS dn) l");
        assertShape("DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), LOCATION:VARCHAR(16777216), "
                + "PROJECT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "EMPLOYEE_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "PROJECT_ID:NUMBER(38,0)",
            "SELECT * FROM d CROSS JOIN p, e");
        assertShape("O:NUMBER(1,0), DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), LOCATION:VARCHAR(16777216), "
                + "PROJECT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "EMPLOYEE_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "PROJECT_ID:NUMBER(38,0)",
            "SELECT * FROM (SELECT 0 AS o) z, d JOIN p ON TRUE, e");
        assertCells(new String[][] {
            {"SELECT * FROM d JOIN p ON TRUE, e JOIN d d2 ON d2.department_id = e.department_id",
                "1|D|L|10|P|1|100|E|1|10|1|D|L"},
        });
        assertShape("DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), LOCATION:VARCHAR(16777216), "
                + "PROJECT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "EMPLOYEE_ID:NUMBER(38,0), NAME:VARCHAR(16777216), DEPARTMENT_ID:NUMBER(38,0), "
                + "PROJECT_ID:NUMBER(38,0), DEPARTMENT_ID:NUMBER(38,0), NAME:VARCHAR(16777216), "
                + "LOCATION:VARCHAR(16777216)",
            "SELECT * FROM d JOIN p ON TRUE, e JOIN d d2 ON d2.department_id = e.department_id");
    }
}
