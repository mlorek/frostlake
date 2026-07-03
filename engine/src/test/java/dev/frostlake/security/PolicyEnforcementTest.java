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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

public class PolicyEnforcementTest {

    private static final Logger logger = LoggerFactory.getLogger(PolicyEnforcementTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, salary DOUBLE, department VARCHAR)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 80000, 'HR')");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob',   90000, 'IT')");
        engine.execute("INSERT INTO employees VALUES (3, 'Carol', 75000, 'HR')");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    // ── Column Masking Policies ────────────────────────────────────────────────

    @Test
    public void testCreateMaskingPolicy() {
        engine.execute("CREATE MASKING POLICY salary_mask AS (val DOUBLE) RETURNS DOUBLE -> IFF(CURRENT_ROLE() = 'HR_ADMIN', val, -1)");
        var policy = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC").getMaskingPolicy("SALARY_MASK");
        assertNotNull(policy);
        assertEquals("SALARY_MASK", policy.getName());
        assertEquals(1, policy.getParameters().size());
    }

    @Test
    public void testSetMaskingPolicyOnColumn() {
        engine.execute("CREATE MASKING POLICY salary_mask AS (val DOUBLE) RETURNS DOUBLE -> IFF(CURRENT_ROLE() = 'HR', val, -1)");
        engine.execute("ALTER TABLE employees ALTER COLUMN salary SET MASKING POLICY salary_mask");
        var col = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC").getTable("EMPLOYEES").getColumn("salary");
        assertTrue(col.hasMaskingPolicy());
        assertEquals("SALARY_MASK", col.getMaskingPolicyName());
    }

    @Test
    public void testMaskingPolicyEnforcedForNonPrivilegedRole() {
        engine.execute("CREATE OR REPLACE MASKING POLICY salary_mask AS (val DOUBLE) RETURNS DOUBLE -> IFF(CURRENT_ROLE() = 'HR_ADMIN', val, -1)");
        engine.execute("ALTER TABLE employees ALTER COLUMN salary SET MASKING POLICY salary_mask");

        // SYSADMIN bypasses masking
        engine.getSecurityManager().getSessionContext().setCurrentRole("SYSADMIN");
        ResultSet rsAdmin = engine.executeQuery("SELECT salary FROM employees WHERE id = 1");
        assertEquals(80000.0, ((Number) rsAdmin.getRows().get(0).getValue(0)).doubleValue(), 0.01);

        // PUBLIC role sees masked value
        engine.getSecurityManager().getSessionContext().setCurrentRole("PUBLIC");
        ResultSet rsMasked = engine.executeQuery("SELECT salary FROM employees WHERE id = 1");
        assertEquals(-1.0, ((Number) rsMasked.getRows().get(0).getValue(0)).doubleValue(), 0.01);
        logger.info("Masking enforced: PUBLIC sees -1");
    }

    @Test
    public void testMaskingPolicyBypassed_ForSysadmin() {
        engine.execute("CREATE OR REPLACE MASKING POLICY salary_mask AS (val DOUBLE) RETURNS DOUBLE -> IFF(CURRENT_ROLE() = 'HR_ADMIN', val, -1)");
        engine.execute("ALTER TABLE employees ALTER COLUMN salary SET MASKING POLICY salary_mask");
        engine.getSecurityManager().getSessionContext().setCurrentRole("SYSADMIN");
        ResultSet rs = engine.executeQuery("SELECT salary FROM employees WHERE id = 1");
        assertEquals(80000.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.01);
    }

    @Test
    public void testMaskingPolicyBypassed_ForAccountadmin() {
        engine.execute("CREATE OR REPLACE MASKING POLICY salary_mask AS (val DOUBLE) RETURNS DOUBLE -> IFF(CURRENT_ROLE() = 'HR_ADMIN', val, -1)");
        engine.execute("ALTER TABLE employees ALTER COLUMN salary SET MASKING POLICY salary_mask");
        engine.getSecurityManager().getSessionContext().setCurrentRole("ACCOUNTADMIN");
        ResultSet rs = engine.executeQuery("SELECT salary FROM employees WHERE id = 1");
        assertEquals(80000.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.01);
    }

    @Test
    public void testUnsetMaskingPolicy() {
        engine.execute("CREATE MASKING POLICY name_mask AS (val VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("ALTER TABLE employees ALTER COLUMN name SET MASKING POLICY name_mask");
        engine.execute("ALTER TABLE employees ALTER COLUMN name UNSET MASKING POLICY");
        var col = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC").getTable("EMPLOYEES").getColumn("name");
        assertFalse(col.hasMaskingPolicy());
    }

    // ── Masking inside expressions (regression: any expression around the column leaked raw data) ──

    private void maskSalaryAndName() {
        engine.execute("CREATE OR REPLACE MASKING POLICY salary_mask AS (val DOUBLE) RETURNS DOUBLE -> IFF(CURRENT_ROLE() = 'HR_ADMIN', val, -1)");
        engine.execute("ALTER TABLE employees ALTER COLUMN salary SET MASKING POLICY salary_mask");
        engine.execute("CREATE OR REPLACE MASKING POLICY name_mask AS (val VARCHAR) RETURNS VARCHAR -> IFF(CURRENT_ROLE() = 'HR_ADMIN', val, '***')");
        engine.execute("ALTER TABLE employees ALTER COLUMN name SET MASKING POLICY name_mask");
        engine.getSecurityManager().getSessionContext().setCurrentRole("PUBLIC");
    }

    @Test
    public void testMaskingAppliesInsideFunctionCall() {
        maskSalaryAndName();
        final ResultSet rs = engine.executeQuery("SELECT UPPER(name) FROM employees WHERE id = 1");
        assertEquals("***", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testMaskingAppliesInConcatenation() {
        maskSalaryAndName();
        final ResultSet rs = engine.executeQuery("SELECT name || '!' FROM employees WHERE id = 1");
        assertEquals("***!", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testMaskingAppliesInArithmetic() {
        maskSalaryAndName();
        final ResultSet rs = engine.executeQuery("SELECT salary + 0 FROM employees WHERE id = 1");
        assertEquals(-1.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.01);
    }

    @Test
    public void testMaskingAppliesToQualifiedReference() {
        maskSalaryAndName();
        final ResultSet rs = engine.executeQuery("SELECT e.salary FROM employees e WHERE e.id = 1");
        assertEquals(-1.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.01);
    }

    // A string literal that happens to contain a masked column's name is not rewritten.
    @Test
    public void testStringLiteralNotRewritten() {
        maskSalaryAndName();
        final ResultSet rs = engine.executeQuery("SELECT 'salary' FROM employees WHERE id = 1");
        assertEquals("salary", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testUnmaskedColumnUnaffectedInSameSelect() {
        maskSalaryAndName();
        final ResultSet rs = engine.executeQuery("SELECT id, salary FROM employees WHERE id = 1");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(-1.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.01);
    }

    @Test
    public void testAdminSeesRawInsideExpression() {
        maskSalaryAndName();
        engine.getSecurityManager().getSessionContext().setCurrentRole("SYSADMIN");
        final ResultSet rs = engine.executeQuery("SELECT salary + 0 FROM employees WHERE id = 1");
        assertEquals(80000.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.01);
    }

    @Test
    public void testDropMaskingPolicy() {
        engine.execute("CREATE MASKING POLICY to_drop AS (val VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("DROP MASKING POLICY to_drop");
        assertNull(engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC").getMaskingPolicy("TO_DROP"));
    }

    @Test
    public void testOrReplaceMaskingPolicy() {
        engine.execute("CREATE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> 'v1'");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> 'v2'");
        var policy = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC").getMaskingPolicy("MP");
        assertTrue(policy.getBody().contains("v2"));
    }

    @Test
    public void testRenameMaskingPolicy() {
        engine.execute("CREATE MASKING POLICY mp_old AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("ALTER MASKING POLICY mp_old RENAME TO mp_new");
        var schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        assertNull(schema.getMaskingPolicy("MP_OLD"), "Old name should be gone after rename");
        var renamed = schema.getMaskingPolicy("MP_NEW");
        assertNotNull(renamed, "New name should resolve after rename");
        assertEquals("MP_NEW", renamed.getName());
        assertTrue(renamed.getBody().contains("***"), "Body preserved across rename");
    }

    @Test
    public void testRenameMaskingPolicyBareSyntax() {
        // The engine also accepts the bare RENAME <name> form (no TO), matching its other ALTER ... RENAME actions.
        engine.execute("CREATE MASKING POLICY mp_bare AS (v VARCHAR) RETURNS VARCHAR -> 'x'");
        engine.execute("ALTER MASKING POLICY mp_bare RENAME mp_bare2");
        var schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        assertNull(schema.getMaskingPolicy("MP_BARE"));
        assertNotNull(schema.getMaskingPolicy("MP_BARE2"));
    }

    @Test
    public void testRenameMaskingPolicyIfExistsNoOp() {
        // IF EXISTS on a missing policy is a silent no-op (Snowflake semantics).
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER MASKING POLICY IF EXISTS no_such_mp RENAME TO whatever");
            }
        });
    }

    @Test
    public void testRenameMaskingPolicyNotFoundThrows() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER MASKING POLICY no_such_mp RENAME TO whatever");
            }
        });
    }

    @Test
    public void testTagDrivenMaskingViaGetTagOnCurrentColumn() {
        // A masking policy keyed off the column's own tag: SYSTEM$GET_TAG_ON_CURRENT_COLUMN.
        engine.execute("CREATE TAG classification ALLOWED_VALUES 'PII', 'PUBLIC'");
        engine.execute("ALTER TABLE employees ALTER COLUMN name SET TAG classification = 'PII'");
        engine.execute("ALTER TABLE employees ALTER COLUMN department SET TAG classification = 'PUBLIC'");
        engine.execute("CREATE MASKING POLICY tag_mask AS (v VARCHAR) RETURNS VARCHAR -> "
            + "IFF(SYSTEM$GET_TAG_ON_CURRENT_COLUMN('classification') = 'PII', '***', v)");
        engine.execute("ALTER TABLE employees ALTER COLUMN name SET MASKING POLICY tag_mask");
        engine.execute("ALTER TABLE employees ALTER COLUMN department SET MASKING POLICY tag_mask");

        engine.getSecurityManager().getSessionContext().setCurrentRole("PUBLIC");
        ResultSet rs = engine.executeQuery("SELECT name, department FROM employees WHERE id = 1");
        // 'name' is tagged classification=PII -> masked; 'department' is PUBLIC -> left unmasked.
        assertEquals("***", rs.getRows().get(0).getValue(0));
        assertEquals("HR", rs.getRows().get(0).getValue(1));
        logger.info("Tag-driven masking: name masked via GET_TAG_ON_CURRENT_COLUMN, department untouched");
    }

    @Test
    public void testTagDrivenMaskingViaGetTagOnCurrentTable() {
        // A masking policy keyed off the table's tag: SYSTEM$GET_TAG_ON_CURRENT_TABLE.
        engine.execute("CREATE TAG classification ALLOWED_VALUES 'PII', 'PUBLIC'");
        engine.execute("ALTER TABLE employees SET TAG classification = 'PII'");
        engine.execute("CREATE MASKING POLICY tbl_mask AS (v VARCHAR) RETURNS VARCHAR -> "
            + "IFF(SYSTEM$GET_TAG_ON_CURRENT_TABLE('classification') = 'PII', '###', v)");
        engine.execute("ALTER TABLE employees ALTER COLUMN name SET MASKING POLICY tbl_mask");

        engine.getSecurityManager().getSessionContext().setCurrentRole("PUBLIC");
        ResultSet rs = engine.executeQuery("SELECT name FROM employees WHERE id = 1");
        // The table is tagged classification=PII, so the column is masked.
        assertEquals("###", rs.getRows().get(0).getValue(0));
    }

    // ── Row Access Policies ────────────────────────────────────────────────────

    @Test
    public void testCreateRowAccessPolicy() {
        engine.execute("CREATE ROW ACCESS POLICY dept_filter AS (dept VARCHAR) RETURNS BOOLEAN -> CURRENT_ROLE() = 'ADMIN'");
        var policy = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC").getRowAccessPolicy("DEPT_FILTER");
        assertNotNull(policy);
        assertEquals("DEPT_FILTER", policy.getName());
    }

    @Test
    public void testAddRowAccessPolicyToTable() {
        engine.execute("CREATE ROW ACCESS POLICY dept_policy AS (dept VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("ALTER TABLE employees ADD ROW ACCESS POLICY dept_policy ON (department)");
        var table = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC").getTable("EMPLOYEES");
        assertTrue(table.hasRowAccessPolicy());
        assertEquals("DEPT_POLICY", table.getRowAccessPolicyName());
    }

    @Test
    public void testRowAccessPolicyFiltersRows() {
        engine.execute("CREATE ROW ACCESS POLICY it_only AS (dept VARCHAR) RETURNS BOOLEAN -> CURRENT_ROLE() = 'SYSADMIN' OR dept = 'IT'");
        engine.execute("ALTER TABLE employees ADD ROW ACCESS POLICY it_only ON (department)");

        // SYSADMIN sees all
        engine.getSecurityManager().getSessionContext().setCurrentRole("SYSADMIN");
        assertEquals(3, engine.executeQuery("SELECT * FROM employees").getRowCount());

        // PUBLIC sees only IT
        engine.getSecurityManager().getSessionContext().setCurrentRole("PUBLIC");
        ResultSet restricted = engine.executeQuery("SELECT * FROM employees");
        assertEquals(1, restricted.getRowCount());
        assertEquals("Bob", restricted.getRows().get(0).getValue(1).toString());
        logger.info("RLS: PUBLIC sees {} row(s)", restricted.getRowCount());
    }

    @Test
    public void testRowAccessPolicyBlocksAllRows() {
        engine.execute("CREATE ROW ACCESS POLICY block_all AS (dept VARCHAR) RETURNS BOOLEAN -> FALSE");
        engine.execute("ALTER TABLE employees ADD ROW ACCESS POLICY block_all ON (department)");
        engine.getSecurityManager().getSessionContext().setCurrentRole("PUBLIC");
        assertEquals(0, engine.executeQuery("SELECT * FROM employees").getRowCount());
    }

    @Test
    public void testDropRowAccessPolicy_FromTable() {
        engine.execute("CREATE ROW ACCESS POLICY p AS (d VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("ALTER TABLE employees ADD ROW ACCESS POLICY p ON (department)");
        engine.execute("ALTER TABLE employees DROP ROW ACCESS POLICY p");
        assertFalse(engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC").getTable("EMPLOYEES").hasRowAccessPolicy());
    }

    @Test
    public void testDropRowAccessPolicyObject() {
        engine.execute("CREATE ROW ACCESS POLICY rp_drop AS (d VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("DROP ROW ACCESS POLICY rp_drop");
        assertNull(engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC").getRowAccessPolicy("RP_DROP"));
    }

    @Test
    public void testRenameRowAccessPolicy() {
        engine.execute("CREATE ROW ACCESS POLICY rap_old AS (d VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("ALTER ROW ACCESS POLICY rap_old RENAME TO rap_new");
        var schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        assertNull(schema.getRowAccessPolicy("RAP_OLD"), "Old name should be gone after rename");
        var renamed = schema.getRowAccessPolicy("RAP_NEW");
        assertNotNull(renamed, "New name should resolve after rename");
        assertEquals("RAP_NEW", renamed.getName());
    }

    @Test
    public void testRenameRowAccessPolicyIfExistsNoOp() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER ROW ACCESS POLICY IF EXISTS no_such_rap RENAME TO whatever");
            }
        });
    }

    // ── Combined ──────────────────────────────────────────────────────────────

    @Test
    public void testMaskingAndRlsTogether() {
        engine.execute("CREATE MASKING POLICY sal_mask AS (v DOUBLE) RETURNS DOUBLE -> IFF(CURRENT_ROLE() = 'ADMIN', v, 0.0)");
        engine.execute("CREATE ROW ACCESS POLICY hr_only AS (d VARCHAR) RETURNS BOOLEAN -> CURRENT_ROLE() = 'ADMIN' OR d = 'HR'");
        engine.execute("ALTER TABLE employees ALTER COLUMN salary SET MASKING POLICY sal_mask");
        engine.execute("ALTER TABLE employees ADD ROW ACCESS POLICY hr_only ON (department)");

        engine.getSecurityManager().getSessionContext().setCurrentRole("PUBLIC");
        ResultSet rs = engine.executeQuery("SELECT name, salary FROM employees ORDER BY id");

        assertEquals(2, rs.getRowCount(), "Only HR rows visible");
        assertEquals(0.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.01);
        logger.info("Combined RLS+Masking: {} rows, salary={}", rs.getRowCount(), rs.getRows().get(0).getValue(1));
    }
}
