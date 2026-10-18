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
 * A correlated scalar subquery over a derived table or a CTE whose set operation carries the outer row in an
 * operand's items is refused where the subquery reads that column — under MIN, MAX or COUNT of it, its operand first
 * or second, a column list naming it, inside a SQL function called over a column too — {@code Unsupported subquery
 * type cannot be evaluated}, and answered under COUNT(*), which reads no column (live-verified).
 */
public class SetOperationOuterRowTest extends MeasuredCellsTestSupport {

    @Test
    public void aColumnCarryingTheOuterRowIsRefusedWhereRead() {
        engine.execute("CREATE OR REPLACE TABLE ca (id VARCHAR)");
        engine.execute("INSERT INTO ca VALUES ('a1'), ('a2')");
        engine.execute("CREATE OR REPLACE TABLE g (id VARCHAR, v INT)");
        engine.execute("INSERT INTO g VALUES ('a1', 5), ('a2', 7)");
        engine.execute("CREATE OR REPLACE FUNCTION f1(p VARCHAR) RETURNS VARCHAR AS $$ WITH d AS (SELECT p AS v "
            + "UNION ALL SELECT 'z') SELECT MIN(v) FROM d $$");
        engine.execute("CREATE OR REPLACE FUNCTION f2(p VARCHAR) RETURNS VARCHAR AS $$ SELECT MIN(v) FROM (SELECT p "
            + "AS v UNION ALL SELECT 'z') $$");
        engine.execute("CREATE OR REPLACE FUNCTION f3(p VARCHAR) RETURNS INT AS $$ SELECT MAX(v) FROM (SELECT v "
            + "FROM g WHERE g.id = p UNION ALL SELECT 1) $$");
        engine.execute("CREATE OR REPLACE FUNCTION f4(p VARCHAR) RETURNS NUMBER AS $$ SELECT COUNT(*) FROM (SELECT "
            + "p AS v UNION ALL SELECT 'z') $$");
        engine.execute("CREATE OR REPLACE FUNCTION f5(p VARCHAR) RETURNS VARCHAR AS $$ SELECT MAX(v) FROM (SELECT "
            + "'z' AS v UNION ALL SELECT p) $$");
        assertCells(new String[][] {
            {"SELECT id, f1(id) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1"},
            {"SELECT id, f2(id) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F2"},
            {"SELECT f1('a1'), f2('a1')", "a1|a1"},
            {"SELECT id, f3(id) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F3"},
            {"SELECT id, f4(id) FROM ca ORDER BY 1", "a1|2 ;; a2|2"},
            {"SELECT id, f5(id) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F5"},
            {"SELECT id, (SELECT MIN(v) FROM (SELECT ca.id AS v UNION ALL SELECT 'z')) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MAX(v) FROM (SELECT ca.id AS v UNION ALL SELECT 'z')) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT ca.id AS v UNION ALL SELECT 'z')) FROM ca ORDER BY 1",
                "a1|2 ;; a2|2"},
            {"SELECT id, (SELECT COUNT(v) FROM (SELECT ca.id AS v UNION ALL SELECT 'z')) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (WITH d AS (SELECT ca.id AS v UNION ALL SELECT 'z') SELECT MIN(v) FROM d) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MAX(v) FROM (SELECT v FROM g WHERE g.id = ca.id UNION ALL SELECT 1)) FROM ca "
                + "ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MIN(v) FROM (SELECT 'z' AS v UNION ALL SELECT ca.id)) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MIN(v) FROM (SELECT ca.id AS v UNION SELECT 'z')) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MIN(v) FROM (SELECT ca.id AS v EXCEPT SELECT 'z')) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MIN(v) FROM (SELECT ca.id AS v)) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MIN(v) FROM (SELECT ca.id || 'x' AS v UNION ALL SELECT 'z')) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id FROM ca WHERE id IN (SELECT MIN(v) FROM (SELECT ca.id AS v UNION ALL SELECT 'z')) ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
        });
    }

    @Test
    public void aCteAndAColumnList() {
        engine.execute("CREATE OR REPLACE TABLE ca (id VARCHAR)");
        engine.execute("INSERT INTO ca VALUES ('a1'), ('a2')");
        assertCells(new String[][] {
            {"SELECT id, (WITH d AS (SELECT ca.id AS v) SELECT MIN(v) FROM d) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (WITH d AS (SELECT ca.id AS v UNION ALL SELECT 'z') SELECT COUNT(*) FROM d) FROM ca "
                + "ORDER BY 1",
                "a1|2 ;; a2|2"},
            {"SELECT id, (WITH d (w) AS (SELECT ca.id UNION ALL SELECT 'z') SELECT MIN(w) FROM d) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MIN(w) FROM (SELECT ca.id UNION ALL SELECT 'z') AS d (w)) FROM ca ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MIN(v) FROM (SELECT 'q' AS v UNION ALL SELECT 'z')) FROM ca ORDER BY 1",
                "a1|q ;; a2|q"},
        });
    }
}
