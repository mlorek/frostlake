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
package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code (+)} marker outer-joins its relation over any number of comma-separated relations: each marked relation
 * is null-supplied to the relation its conjunct names beside it, a chain of markers is a chain of left outer joins,
 * and {@code SELECT *} lists the relations as the FROM clause wrote them, whichever is null-supplied (live-verified,
 * the documentation's departments, projects and employees example among them).
 */
public class LegacyOuterJoinChainTest extends BaseDatabaseTest {

    private static final String NAMES = "SELECT d.name AS department, p.name AS project, e.name AS employee FROM ";
    private static final String ORDER = " ORDER BY d.department_id, p.project_id, e.employee_id";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE departments (department_id INTEGER, name VARCHAR, location VARCHAR)");
        engine.execute("CREATE TABLE projects (project_id INTEGER, name VARCHAR, department_id INTEGER)");
        engine.execute("CREATE TABLE employees (employee_id INTEGER, name VARCHAR, department_id INTEGER,"
            + " project_id INTEGER)");
        engine.execute("INSERT INTO departments (department_id, name, location) VALUES"
            + " (10, 'CUSTOMER SUPPORT', 'CHICAGO'), (40, 'RESEARCH', 'BOSTON'),"
            + " (80, 'Department with no employees yet', 'CHICAGO'),"
            + " (90, 'Department with no projects or employees yet', 'EREHWON')");
        engine.execute("INSERT INTO projects (project_id, name, department_id) VALUES"
            + " (4000, 'Detect fake product reviews', 40), (4001, 'Detect false insurance claims', 10),"
            + " (9000, 'Project with no employees yet', 80), (9099, 'Project with no department or employees', NULL)");
        engine.execute("INSERT INTO employees (employee_id, name, department_id, project_id) VALUES"
            + " (1012, 'May Aidez', 10, NULL), (1040, 'Devi Nobel', 40, 4000), (1041, 'Alfred Mendeleev', 40, 4001)");
    }

    /** The rows, a row's cells joined by '|', the rows by ' ;; '. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " ;; " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** The column names, joined by ','. */
    private String columns(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final ResultSetColumn column : result.getColumns()) {
            out.append(out.length() > 0 ? "," : "").append(column.getName());
        }
        return out.toString();
    }

    @Test
    public void aChainOfMarkersIsAChainOfLeftJoins() {
        final String expected = "CUSTOMER SUPPORT|Detect false insurance claims|Alfred Mendeleev"
            + " ;; RESEARCH|Detect fake product reviews|Devi Nobel"
            + " ;; Department with no employees yet|Project with no employees yet|null"
            + " ;; Department with no projects or employees yet|null|null";
        assertEquals(expected, rows(NAMES + "departments d, projects p, employees e"
            + " WHERE p.department_id(+) = d.department_id AND e.project_id(+) = p.project_id" + ORDER));
        assertEquals(expected, rows(NAMES + "departments d, projects p, employees e"
            + " WHERE d.department_id = p.department_id(+) AND p.project_id = e.project_id(+)" + ORDER));
        assertEquals(expected, rows(NAMES + "departments d LEFT OUTER JOIN projects p"
            + " ON p.department_id = d.department_id LEFT OUTER JOIN employees e ON e.project_id = p.project_id"
            + ORDER));
    }

    @Test
    public void oneMarkerJoinsOneRelation() {
        assertEquals("CUSTOMER SUPPORT|Detect false insurance claims|Alfred Mendeleev"
            + " ;; RESEARCH|Detect fake product reviews|Devi Nobel"
            + " ;; Department with no employees yet|Project with no employees yet|null",
            rows(NAMES + "departments d, projects p, employees e"
                + " WHERE p.department_id = d.department_id AND e.project_id(+) = p.project_id" + ORDER));
    }

    @Test
    public void twoRelationsJoinToOne() {
        assertEquals("CUSTOMER SUPPORT|Detect false insurance claims|May Aidez"
            + " ;; RESEARCH|Detect fake product reviews|Devi Nobel"
            + " ;; RESEARCH|Detect fake product reviews|Alfred Mendeleev"
            + " ;; Department with no employees yet|Project with no employees yet|null"
            + " ;; Department with no projects or employees yet|null|null",
            rows(NAMES + "departments d, projects p, employees e"
                + " WHERE p.department_id(+) = d.department_id AND e.department_id(+) = d.department_id" + ORDER));
    }

    @Test
    public void aMarkedFilterJoinsAndAnUnmarkedOneFilters() {
        assertEquals("CUSTOMER SUPPORT|Detect false insurance claims|Alfred Mendeleev"
            + " ;; RESEARCH|null|null"
            + " ;; Department with no employees yet|Project with no employees yet|null"
            + " ;; Department with no projects or employees yet|null|null",
            rows(NAMES + "departments d, projects p, employees e WHERE p.department_id(+) = d.department_id"
                + " AND p.project_id(+) > 4000 AND e.project_id(+) = p.project_id" + ORDER));
        assertEquals("Department with no employees yet|Project with no employees yet|null"
            + " ;; Department with no projects or employees yet|null|null",
            rows(NAMES + "departments d, projects p, employees e WHERE p.department_id(+) = d.department_id"
                + " AND e.project_id(+) = p.project_id AND e.employee_id IS NULL" + ORDER));
        assertEquals("4", rows("SELECT COUNT(*) FROM departments d, projects p, employees e, departments d2"
            + " WHERE p.department_id(+) = d.department_id AND e.project_id(+) = p.project_id"
            + " AND d2.department_id = d.department_id"));
    }

    @Test
    public void theRelationsReadInTheirWrittenOrder() {
        assertEquals("PROJECT_ID,NAME,DEPARTMENT_ID,DEPARTMENT_ID,NAME,LOCATION",
            columns("SELECT * FROM projects p, departments d WHERE p.department_id(+) = d.department_id"));
        assertEquals("null|null|null|90|Department with no projects or employees yet|EREHWON",
            rows("SELECT * FROM projects p, departments d WHERE p.department_id(+) = d.department_id"
                + " AND d.department_id = 90"));
        assertEquals("null|null|null|null|null|null|null|90|Department with no projects or employees yet|EREHWON",
            rows("SELECT * FROM employees e, projects p, departments d WHERE e.project_id(+) = p.project_id"
                + " AND p.department_id(+) = d.department_id AND d.department_id = 90"));
        assertEquals("Department with no projects or employees yet|null",
            rows("SELECT d.name, p.name FROM projects p, departments d WHERE p.department_id(+) = d.department_id"
                + " AND p.name IS NULL"));
    }

    @Test
    public void aLateralItemReadsTheNullSuppliedRows() {
        assertEquals("8", rows("SELECT COUNT(*) FROM departments d, projects p,"
            + " LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(1, 2)) f WHERE p.department_id(+) = d.department_id"));
        assertEquals("Department with no projects or employees yet|null|1"
            + " ;; Department with no projects or employees yet|null|2",
            rows("SELECT d.name, p.name, f.value FROM departments d, projects p,"
                + " LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(1, 2)) f WHERE p.department_id(+) = d.department_id"
                + " AND d.department_id = 90 ORDER BY 3"));
    }

    @Test
    public void aSelfJoinNullSuppliesItsOwnAlias() {
        engine.execute("CREATE TABLE emp (id INT, mgr INT, name VARCHAR)");
        engine.execute("INSERT INTO emp VALUES (1, NULL, 'boss'), (2, 1, 'a'), (3, 1, 'b'), (4, 2, 'c')");
        assertEquals("a|boss ;; b|boss ;; boss|null ;; c|a",
            rows("SELECT e.name, m.name FROM emp e, emp m WHERE e.mgr = m.id(+) ORDER BY 1"));
        assertEquals("a|boss ;; b|boss ;; boss|null ;; c|a",
            rows("SELECT e.name, m.name FROM emp m, emp e WHERE e.mgr = m.id(+) ORDER BY 1"));
    }

    @Test
    public void aGroupCountsTheNullSuppliedRows() {
        assertEquals("CUSTOMER SUPPORT|1 ;; Department with no employees yet|0"
            + " ;; Department with no projects or employees yet|0 ;; RESEARCH|1",
            rows("SELECT d.name, COUNT(e.employee_id) FROM departments d, projects p, employees e"
                + " WHERE p.department_id(+) = d.department_id AND e.project_id(+) = p.project_id"
                + " GROUP BY d.name ORDER BY 1"));
    }
}
