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
 * A (+) marker beside explicit joins (live-verified): each inner join's relation joins in the place it is written and
 * the marked relation left-joins to them, so FROM d JOIN p ON …, e WHERE e.k(+) = p.k keeps a project with no
 * employee, its columns in the written order; a marker beside an explicit outer join is refused.
 */
public class LegacyOuterJoinBesideJoinTest extends MeasuredCellsTestSupport {

    @Test
    public void aMarkedRelationJoinsAfterTheInnerJoins() {
        engine.execute("CREATE OR REPLACE TABLE departments (department_id INTEGER, name VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE projects (project_id INTEGER, name VARCHAR, department_id INTEGER)");
        engine.execute("CREATE OR REPLACE TABLE employees (employee_id INTEGER, name VARCHAR, project_id INTEGER)");
        engine.execute("INSERT INTO departments VALUES (1, 'CUSTOMER SUPPORT'), (2, 'RESEARCH'), (3, 'Department "
            + "with no projects yet'), (4, 'Department with no employees yet')");
        engine.execute("INSERT INTO projects VALUES (10000, 'Detect false insurance claims', 1), (10001, 'Detect "
            + "fake product reviews', 2), (10002, 'Project with no employees yet', 4)");
        engine.execute("INSERT INTO employees VALUES (100, 'Alfred Mendeleev', 10000), (101, 'Devi Nobel', 10001), "
            + "(102, 'Employee with no project yet', NULL)");
        assertCells(new String[][] {
            {"SELECT d.name AS department, p.name AS project, e.name AS employee FROM departments d JOIN "
                + "projects p ON p.department_id = d.department_id, employees e WHERE e.project_id(+) = p.project_id "
                + "ORDER BY d.department_id, p.project_id, e.employee_id",
                "CUSTOMER SUPPORT|Detect false insurance claims|Alfred Mendeleev ;; RESEARCH|Detect fake product "
                + "reviews|Devi Nobel ;; Department with no employees yet|Project with no employees yet|null"},
            {"SELECT d.name, p.name, e.name FROM departments d, projects p JOIN employees e ON e.project_id = "
                + "p.project_id WHERE p.department_id(+) = d.department_id ORDER BY d.department_id, p.project_id, "
                + "e.employee_id",
                "CUSTOMER SUPPORT|Detect false insurance claims|Alfred Mendeleev ;; RESEARCH|Detect fake product "
                + "reviews|Devi Nobel"},
            {"SELECT d.name, p.name FROM departments d LEFT JOIN projects p ON p.department_id = "
                + "d.department_id, employees e WHERE e.project_id(+) = p.project_id ORDER BY d.department_id, "
                + "p.project_id, e.employee_id",
                "REFUSED SQL compilation error: | (+)-style outer join not allowed with from-clause outer join: "
                + "'E.PROJECT_ID(+)'"},
            {"SELECT COUNT(*) FROM departments d JOIN projects p ON p.department_id = d.department_id, "
                + "employees e WHERE e.project_id(+) = p.project_id AND e.employee_id(+) > 100",
                "3"},
            {"SELECT * FROM departments d JOIN projects p ON p.department_id = d.department_id, employees e "
                + "WHERE e.project_id(+) = p.project_id ORDER BY d.department_id, p.project_id, e.employee_id",
                "1|CUSTOMER SUPPORT|10000|Detect false insurance claims|1|100|Alfred Mendeleev|10000 ;; "
                + "2|RESEARCH|10001|Detect fake product reviews|2|101|Devi Nobel|10001 ;; 4|Department with no "
                + "employees yet|10002|Project with no employees yet|4|null|null|null"},
        });
    }
}
