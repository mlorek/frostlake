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

/**
 * Demonstration of INSERT, UPDATE, and DELETE operations
 */
public class DMLOperationsDemo extends AbstractDemo {

    public static void main(final String[] args) {
        new DMLOperationsDemo().execute();
    }

    @Override
    protected String getDemoTitle() {
        return "DML Operations Demo: INSERT, UPDATE, DELETE";
    }

    @Override
    protected void runDemo() throws Exception {
        // Setup
        setupDatabase("company_db");

            // ==================== INSERT OPERATIONS ====================
            printSectionHeader("INSERT OPERATIONS");

            // Create employees table
            engine.execute(
                "CREATE TABLE employees (" +
                "id INTEGER, " +
                "name VARCHAR, " +
                "department VARCHAR, " +
                "salary INTEGER, " +
                "hire_date VARCHAR" +
                ")"
            );

            System.out.println("=== 1. Basic INSERT - Single Row ===");
            engine.execute("INSERT INTO employees VALUES (1, 'Alice Johnson', 'Engineering', 90000, '2023-01-15')");
            printSuccess("Inserted 1 employee\n");
            printTable("employees");

            System.out.println("\n=== 2. Bulk INSERT - Multiple Rows ===");
            engine.execute(
                "INSERT INTO employees VALUES " +
                "(2, 'Bob Smith', 'Sales', 70000, '2023-02-01'), " +
                "(3, 'Charlie Brown', 'Engineering', 95000, '2023-02-15'), " +
                "(4, 'Diana Prince', 'HR', 75000, '2023-03-01'), " +
                "(5, 'Eve Martinez', 'Engineering', 85000, '2023-03-10')"
            );
            printSuccess("Inserted 4 employees\n");
            printTable("employees");

            // ==================== UPDATE OPERATIONS ====================
            printSectionHeader("UPDATE OPERATIONS");

            System.out.println("=== 3. UPDATE Single Row - Give Alice a raise ===");
            engine.execute("UPDATE employees SET salary = 95000 WHERE id = 1");
            printSuccess("Updated Alice's salary\n");
            printQuery("SELECT * FROM employees WHERE id = 1");

            System.out.println("\n=== 4. UPDATE Multiple Columns ===");
            engine.execute("UPDATE employees SET department = 'Senior Engineering', salary = 105000 WHERE id = 3");
            printSuccess("Promoted Charlie\n");
            printQuery("SELECT * FROM employees WHERE id = 3");

            System.out.println("\n=== 5. UPDATE with Complex WHERE - Raise for all Engineering ===");
            engine.execute("UPDATE employees SET salary = 100000 WHERE department = 'Engineering' AND salary < 100000");
            printSuccess("Updated Engineering salaries\n");
            printQuery("SELECT * FROM employees WHERE department = 'Engineering'");

            System.out.println("\n=== 6. Conditional UPDATE - Salary adjustment ===");
            engine.execute("UPDATE employees SET salary = 80000 WHERE department = 'Sales' AND salary < 80000");
            printSuccess("Adjusted Sales salaries\n");
            printTable("employees");

            // ==================== DELETE OPERATIONS ====================
            printSectionHeader("DELETE OPERATIONS");

            System.out.println("=== 7. DELETE Single Row ===");
            engine.execute("INSERT INTO employees VALUES (6, 'Frank Test', 'Temp', 50000, '2024-01-01')");
            System.out.println("Added temporary employee:");
            printQuery("SELECT * FROM employees WHERE id = 6");

            engine.execute("DELETE FROM employees WHERE id = 6");
            printSuccess("\nDeleted temporary employee");
            System.out.println("Remaining employees: " + getRowCount("employees") + "\n");

            System.out.println("=== 8. DELETE with WHERE Clause ===");
            engine.execute("INSERT INTO employees VALUES (7, 'Grace Low', 'Sales', 45000, '2024-01-05')");
            System.out.println("Current employees:");
            printTable("employees");

            engine.execute("DELETE FROM employees WHERE salary < 60000");
            printSuccess("\nDeleted employees with salary < 60000");
            System.out.println("Remaining employees:");
            printTable("employees");

            System.out.println("\n=== 9. DELETE with Complex WHERE ===");
            engine.execute("INSERT INTO employees VALUES (8, 'Henry Temp', 'Engineering', 65000, '2024-02-01')");
            engine.execute("DELETE FROM employees WHERE department = 'Engineering' AND salary < 90000");
            printSuccess("Deleted junior Engineering employees");
            System.out.println("Remaining employees:");
            printTable("employees");

            // ==================== COMBINED OPERATIONS ====================
            printSectionHeader("COMBINED DML OPERATIONS");

            System.out.println("=== 10. Real-world Scenario: Department Reorganization ===");

            // Step 1: Add new employees
            System.out.println("Step 1: Hiring new employees...");
            engine.execute(
                "INSERT INTO employees VALUES " +
                "(9, 'Iris Chen', 'Engineering', 92000, '2024-03-01'), " +
                "(10, 'Jack Wilson', 'Sales', 78000, '2024-03-01')"
            );
            printSuccess("Hired 2 new employees\n");

            // Step 2: Update departments
            System.out.println("Step 2: Reorganizing departments...");
            engine.execute("UPDATE employees SET department = 'Engineering' WHERE department = 'Senior Engineering'");
            printSuccess("Merged Senior Engineering into Engineering\n");

            // Step 3: Give raises
            System.out.println("Step 3: Annual raises...");
            engine.execute("UPDATE employees SET salary = 110000 WHERE salary >= 100000");
            printSuccess("Applied raises for senior employees\n");

            // Step 4: View final state
            System.out.println("=== Final Employee List ===");
            printTable("employees");

            System.out.println("\n=== 11. Department Statistics ===");
            printQuery("SELECT department, COUNT(*), AVG(salary) " +
                "FROM employees " +
                "GROUP BY department " +
                "ORDER BY department");

            System.out.println("\n=== 12. High Earners ===");
            printQuery("SELECT name, department, salary " +
                "FROM employees " +
                "WHERE salary > 100000 " +
                "ORDER BY salary DESC");

            // ==================== TRANSACTION DEMO ====================
            printSectionHeader("TRANSACTIONAL DML OPERATIONS");

            System.out.println("=== 13. Transaction with COMMIT ===");
            engine.setAutoCommit(false);
            engine.beginTransaction();

            engine.execute("INSERT INTO employees VALUES (11, 'Katie New', 'HR', 72000, '2024-04-01')");
            engine.execute("UPDATE employees SET salary = 85000 WHERE id = 4");
            printSuccess("Made changes in transaction");

            engine.commit();
            printSuccess("Transaction committed\n");

            System.out.println("=== 14. Transaction with ROLLBACK ===");
            int beforeCount = getRowCount("employees");
            System.out.println("Employees before transaction: " + beforeCount);

            engine.beginTransaction();
            engine.execute("DELETE FROM employees WHERE department = 'HR'");
            printSuccess("Deleted HR employees (in transaction)");

            engine.rollback();
            printSuccess("Transaction rolled back");

            int afterCount = getRowCount("employees");
            System.out.println("Employees after rollback: " + afterCount);
            printSuccess("All employees restored!\n");

            System.out.println("=== Final Summary ===");
            System.out.println("Total Employees: " + getRowCount("employees"));
            printQuery("SELECT department, COUNT(*) as count, AVG(salary) as avg_salary " +
                "FROM employees " +
                "GROUP BY department");
    }
}
