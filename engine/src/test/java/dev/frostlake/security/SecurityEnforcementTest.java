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

package dev.frostlake.security;

import dev.frostlake.DatabaseEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for security enforcement - verifying that GRANT/REVOKE actually enforce permissions
 */
public class SecurityEnforcementTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE security_test_db");
        engine.execute("USE DATABASE security_test_db");
        engine.execute("USE SCHEMA PUBLIC");

        // Create test tables
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, salary INTEGER)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 75000)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 85000)");

        engine.execute("CREATE TABLE departments (dept_id INTEGER, dept_name VARCHAR)");
        engine.execute("INSERT INTO departments VALUES (10, 'Engineering')");
        engine.execute("INSERT INTO departments VALUES (20, 'Sales')");

        // Create test roles and users
        engine.execute("CREATE ROLE readonly_role");
        engine.execute("CREATE ROLE data_writer_role");
        engine.execute("CREATE ROLE no_access_role");

        engine.execute("CREATE USER readonly_user");
        engine.execute("CREATE USER writer_user");
        engine.execute("CREATE USER blocked_user");

        // Grant roles to users
        engine.execute("GRANT ROLE readonly_role TO USER readonly_user");
        engine.execute("GRANT ROLE data_writer_role TO USER writer_user");
        engine.execute("GRANT ROLE no_access_role TO USER blocked_user");
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testSelectPermissionGranted() {
        // Grant SELECT permission
        engine.execute("GRANT SELECT ON TABLE employees TO ROLE readonly_role");

        // Switch to readonly user
        engine.setCurrentUser("readonly_user");
        engine.setCurrentRole("readonly_role");

        // Should be able to SELECT
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM employees");
                
            }
        }, "User with SELECT permission should be able to query");
    }

    @Test
    public void testSelectPermissionDenied() {
        // Do NOT grant SELECT permission

        // Switch to user without permission
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("blocked_user");
        engine.setCurrentRole("no_access_role");

        // Should be denied
        final SecurityException exception = assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM employees");
                
            }
        }, "User without SELECT permission should be denied");

        assertTrue(exception.getMessage().contains("Permission denied"));
        assertTrue(exception.getMessage().contains("SELECT"));
    }

    @Test
    public void testInsertPermissionGranted() {
        // Grant INSERT permission
        engine.execute("GRANT INSERT ON TABLE employees TO ROLE data_writer_role");

        // Switch to writer user
        engine.setCurrentUser("writer_user");
        engine.setCurrentRole("data_writer_role");

        // Should be able to INSERT
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 65000)");
                
            }
        }, "User with INSERT permission should be able to insert");
    }

    @Test
    public void testInsertPermissionDenied() {
        // Do NOT grant INSERT permission

        // Switch to user without permission
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("blocked_user");
        engine.setCurrentRole("no_access_role");

        // Should be denied
        final SecurityException exception = assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 65000)");
                
            }
        }, "User without INSERT permission should be denied");

        assertTrue(exception.getMessage().contains("Permission denied"));
        assertTrue(exception.getMessage().contains("INSERT"));
    }

    @Test
    public void testUpdatePermissionGranted() {
        // Grant UPDATE permission
        engine.execute("GRANT UPDATE ON TABLE employees TO ROLE data_writer_role");

        // Switch to writer user
        engine.setCurrentUser("writer_user");
        engine.setCurrentRole("data_writer_role");

        // Should be able to UPDATE
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("UPDATE employees SET salary = 80000 WHERE id = 1");
                
            }
        }, "User with UPDATE permission should be able to update");
    }

    @Test
    public void testUpdatePermissionDenied() {
        // Do NOT grant UPDATE permission

        // Switch to user without permission
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("blocked_user");
        engine.setCurrentRole("no_access_role");

        // Should be denied
        final SecurityException exception = assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("UPDATE employees SET salary = 80000 WHERE id = 1");
                
            }
        }, "User without UPDATE permission should be denied");

        assertTrue(exception.getMessage().contains("Permission denied"));
        assertTrue(exception.getMessage().contains("UPDATE"));
    }

    @Test
    public void testDeletePermissionGranted() {
        // Grant DELETE permission
        engine.execute("GRANT DELETE ON TABLE employees TO ROLE data_writer_role");

        // Switch to writer user
        engine.setCurrentUser("writer_user");
        engine.setCurrentRole("data_writer_role");

        // Should be able to DELETE
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("DELETE FROM employees WHERE id = 1");
                
            }
        }, "User with DELETE permission should be able to delete");
    }

    @Test
    public void testDeletePermissionDenied() {
        // Do NOT grant DELETE permission

        // Switch to user without permission
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("blocked_user");
        engine.setCurrentRole("no_access_role");

        // Should be denied
        final SecurityException exception = assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("DELETE FROM employees WHERE id = 1");
                
            }
        }, "User without DELETE permission should be denied");

        assertTrue(exception.getMessage().contains("Permission denied"));
        assertTrue(exception.getMessage().contains("DELETE"));
    }

    @Test
    public void testAllPrivilegesGranted() {
        // Grant ALL privileges
        engine.execute("GRANT ALL PRIVILEGES ON TABLE employees TO ROLE data_writer_role");

        // Switch to writer user
        engine.setCurrentUser("writer_user");
        engine.setCurrentRole("data_writer_role");

        // Should be able to do everything
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM employees");
                engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 65000)");
                engine.execute("UPDATE employees SET salary = 70000 WHERE id = 3");
                engine.execute("DELETE FROM employees WHERE id = 3");
                
            }
        }, "User with ALL privileges should be able to perform all operations");
    }

    @Test
    public void testRevokePermission() {
        // Grant then revoke SELECT permission
        engine.execute("GRANT SELECT ON TABLE employees TO ROLE readonly_role");
        engine.execute("REVOKE SELECT ON TABLE employees FROM ROLE readonly_role");

        // Switch to readonly user
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("readonly_user");
        engine.setCurrentRole("readonly_role");

        // Should be denied after revoke
        final SecurityException exception = assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM employees");
                
            }
        }, "User should lose access after REVOKE");

        assertTrue(exception.getMessage().contains("Permission denied"));
        assertTrue(exception.getMessage().contains("SELECT"));
    }

    @Test
    public void testOwnershipPermission() {
        // Grant OWNERSHIP (which grants all privileges)
        engine.execute("GRANT OWNERSHIP ON TABLE employees TO ROLE data_writer_role");

        // Switch to writer user
        engine.setCurrentUser("writer_user");
        engine.setCurrentRole("data_writer_role");

        // Should be able to do everything
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM employees");
                engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 65000)");
                engine.execute("UPDATE employees SET salary = 70000 WHERE id = 3");
                engine.execute("DELETE FROM employees WHERE id = 3");
                
            }
        }, "User with OWNERSHIP should have all privileges");
    }

    @Test
    public void testMultipleRolesAccumulated() {
        // Create another role with different permissions
        engine.execute("CREATE ROLE additional_role");
        engine.execute("GRANT SELECT ON TABLE employees TO ROLE readonly_role");
        engine.execute("GRANT INSERT ON TABLE employees TO ROLE additional_role");

        // Grant both roles to user
        engine.execute("GRANT ROLE additional_role TO USER readonly_user");

        // Switch to user with multiple roles
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("readonly_user");
        engine.setCurrentRole("readonly_role");
        engine.getSessionContext().addActiveRole("additional_role");

        // Should have both SELECT and INSERT permissions
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM employees");
                engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 65000)");
                
            }
        }, "User with multiple roles should have accumulated permissions");

        // But not UPDATE
        final SecurityException exception = assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("UPDATE employees SET salary = 70000 WHERE id = 3");
                
            }
        }, "User should not have permissions from ungranted operations");

        assertTrue(exception.getMessage().contains("Permission denied"));
        assertTrue(exception.getMessage().contains("UPDATE"));
    }

    @Test
    public void testRoleHierarchy() {
        // Create role hierarchy: parent_role -> child_role
        engine.execute("CREATE ROLE parent_role");
        engine.execute("CREATE ROLE child_role");

        engine.execute("GRANT SELECT ON TABLE employees TO ROLE child_role");
        engine.execute("GRANT ROLE child_role TO ROLE parent_role");

        // Create user with parent role
        engine.execute("CREATE USER hierarchical_user");
        engine.execute("GRANT ROLE parent_role TO USER hierarchical_user");

        // Switch to user
        engine.setCurrentUser("hierarchical_user");
        engine.setCurrentRole("parent_role");

        // Should inherit SELECT from child role
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM employees");
                
            }
        }, "User should inherit permissions from child roles");
    }

    @Test
    public void testSystemUserBypassesSecurity() {
        // SYSTEM user should always have access
        engine.setCurrentUser("SYSTEM");

        // Should be able to do everything without explicit grants
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM employees");
                engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 65000)");
                engine.execute("UPDATE employees SET salary = 70000 WHERE id = 3");
                engine.execute("DELETE FROM employees WHERE id = 3");
                
            }
        }, "SYSTEM user should bypass all security checks");
    }

    @Test
    public void testSecurityDisabled() {
        // Disable security
        engine.getSessionContext().setSecurityEnabled(false);

        // Switch to user without permissions
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("blocked_user");
        engine.setCurrentRole("no_access_role");

        // Should be able to access everything when security is disabled
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM employees");
                engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 65000)");
                
            }
        }, "All users should have access when security is disabled");

        // Re-enable security
        engine.getSessionContext().setSecurityEnabled(true);

        // Now should be denied
        final SecurityException exception = assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("UPDATE employees SET salary = 70000 WHERE id = 3");
                
            }
        }, "Users should be restricted again after re-enabling security");

        assertTrue(exception.getMessage().contains("Permission denied"));
        assertTrue(exception.getMessage().contains("UPDATE"));
    }

    @Test
    public void testPermissionsOnMultipleTables() {
        // Grant different permissions on different tables
        engine.execute("GRANT SELECT ON TABLE employees TO ROLE readonly_role");
        engine.execute("GRANT SELECT ON TABLE departments TO ROLE readonly_role");
        engine.execute("GRANT INSERT ON TABLE departments TO ROLE readonly_role");

        // Switch to readonly user
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("readonly_user");
        engine.setCurrentRole("readonly_role");

        // Should be able to SELECT from both tables
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM employees");
                engine.executeQuery("SELECT * FROM departments");
                
            }
        });

        // Should be able to INSERT into departments
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("INSERT INTO departments VALUES (30, 'Marketing')");
                
            }
        });

        // But not INSERT into employees
        final SecurityException exception = assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 65000)");
                
            }
        }, "User should not be able to INSERT into employees without permission");

        assertTrue(exception.getMessage().contains("Permission denied"));
        assertTrue(exception.getMessage().contains("INSERT"));
    }
}
