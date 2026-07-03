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

package dev.frostlake.demo;

import dev.frostlake.storage.ResultSet;

/**
 * Demonstration of LATERAL JOIN functionality in Frostlake SQL Engine
 *
 * LATERAL allows subqueries to reference columns from the outer query,
 * enabling correlated subqueries in the FROM clause.
 */
public class LateralJoinDemo extends AbstractDemo {

    public static void main(final String[] args) {
        new LateralJoinDemo().execute();
    }

    @Override
    protected String getDemoTitle() {
        return "LATERAL JOIN Demo";
    }

    @Override
    protected void runDemo() throws Exception {
        // Setup
        setupData();

        // Demo 1: LATERAL with comma syntax
        demo1LateralWithComma();

        // Demo 2: LATERAL with CROSS JOIN
        demo2LateralWithCrossJoin();

        // Demo 3: LATERAL with filtering
        demo3LateralWithFilter();

        // Demo 4: Multiple LATERAL joins
        demo4MultipleLateralJoins();
    }

    private void setupData() {
        setupDatabase("lateral_demo_db");

        // Drop tables if they exist (silently ignore if they don't)
        try { engine.execute("DROP TABLE employees"); } catch (final Exception e) {}
        try { engine.execute("DROP TABLE departments"); } catch (final Exception e) {}
        try { engine.execute("DROP TABLE projects"); } catch (final Exception e) {}

        // Create and populate employees
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, dept_id INTEGER, salary INTEGER)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 10, 75000)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 20, 85000)");
        engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 10, 65000)");
        engine.execute("INSERT INTO employees VALUES (4, 'Diana', 30, 95000)");

        // Create and populate departments
        engine.execute("CREATE TABLE departments (dept_id INTEGER, dept_name VARCHAR, budget INTEGER)");
        engine.execute("INSERT INTO departments VALUES (10, 'Engineering', 500000)");
        engine.execute("INSERT INTO departments VALUES (20, 'Sales', 300000)");
        engine.execute("INSERT INTO departments VALUES (30, 'Marketing', 250000)");

        // Create and populate projects
        engine.execute("CREATE TABLE projects (proj_id INTEGER, proj_name VARCHAR, dept_id INTEGER, status VARCHAR)");
        engine.execute("INSERT INTO projects VALUES (101, 'Project Alpha', 10, 'active')");
        engine.execute("INSERT INTO projects VALUES (102, 'Project Beta', 10, 'planning')");
        engine.execute("INSERT INTO projects VALUES (103, 'Project Gamma', 20, 'active')");
        engine.execute("INSERT INTO projects VALUES (104, 'Project Delta', 30, 'completed')");

        printSuccess("Test data created\n");
    }

    private void demo1LateralWithComma() {
        printSubsection("Demo 1: LATERAL with Comma Syntax");
        System.out.println("Query: SELECT * FROM employees e, LATERAL (SELECT dept_name FROM departments d WHERE d.dept_id = e.dept_id) dept\n");
        System.out.println("This correlates each employee with their department name.");
        System.out.println("The subquery references e.dept_id from the outer query.\n");

        ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees e, LATERAL (SELECT dept_name FROM departments d WHERE d.dept_id = e.dept_id) dept"
        );

        printResultSet(rs);
        System.out.println("Rows returned: " + rs.getRowCount() + "\n");
    }

    private void demo2LateralWithCrossJoin() {
        printSubsection("Demo 2: LATERAL with CROSS JOIN");
        System.out.println("Query: SELECT * FROM employees e CROSS JOIN LATERAL (SELECT dept_name FROM departments d WHERE d.dept_id = e.dept_id) dept\n");
        System.out.println("Same as Demo 1 but using explicit CROSS JOIN LATERAL syntax.\n");

        ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees e CROSS JOIN LATERAL (SELECT dept_name FROM departments d WHERE d.dept_id = e.dept_id) dept"
        );

        printResultSet(rs);
        System.out.println("Rows returned: " + rs.getRowCount() + "\n");
    }

    private void demo3LateralWithFilter() {
        printSubsection("Demo 3: LATERAL with Additional Filtering");
        System.out.println("Query: SELECT * FROM employees e, LATERAL (SELECT dept_name FROM departments d WHERE d.dept_id = e.dept_id AND d.dept_id = 10) dept\n");
        System.out.println("This only returns employees in department 10.\n");

        ResultSet rs = engine.executeQuery(
            "SELECT * FROM employees e, LATERAL (SELECT dept_name FROM departments d WHERE d.dept_id = e.dept_id AND d.dept_id = 10) dept"
        );

        printResultSet(rs);
        System.out.println("Rows returned: " + rs.getRowCount());
        System.out.println("Note: Only Engineering department employees are returned.\n");
    }

    private void demo4MultipleLateralJoins() {
        printSubsection("Demo 4: Multiple LATERAL Joins");
        System.out.println("Query: SELECT e.name, dept.dept_name, proj.proj_name");
        System.out.println("       FROM employees e,");
        System.out.println("            LATERAL (SELECT dept_name FROM departments d WHERE d.dept_id = e.dept_id) dept,");
        System.out.println("            LATERAL (SELECT proj_name FROM projects p WHERE p.dept_id = e.dept_id) proj\n");
        System.out.println("This correlates employees with their department and projects.\n");

        ResultSet rs = engine.executeQuery(
            "SELECT e.name, dept.dept_name, proj.proj_name " +
            "FROM employees e, " +
            "LATERAL (SELECT dept_name FROM departments d WHERE d.dept_id = e.dept_id) dept, " +
            "LATERAL (SELECT proj_name FROM projects p WHERE p.dept_id = e.dept_id) proj"
        );

        printResultSet(rs);
        System.out.println("Rows returned: " + rs.getRowCount());
        System.out.println("Note: Employees are repeated for each project in their department.\n");
    }

    @Override
    protected int getMaxDisplayRows() {
        return 10; // Show maximum 10 rows for clarity
    }
}
