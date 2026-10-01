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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A masking policy and a row access policy apply to EVERY role. Frostlake let ACCOUNTADMIN and
 * SYSADMIN through both, and since SYSADMIN is its default session role, every embedded test and
 * every driver session read unmasked, unfiltered data from a table the same SQL protects on the
 * account — the bypass turned a security feature off by default.
 *
 * <p>A body that means to exempt a role says so itself. That is the only exemption there is, and it
 * exempts the role it names whether or not that role is an admin one.
 */
public class PolicyAppliesToEveryRoleTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE OR REPLACE TABLE t1 (s VARCHAR, n INT)");
        engine.execute("INSERT INTO t1 VALUES ('abc', 1), ('def', 2)");
    }

    /** Every row of the table, as "s, n | s, n". */
    private String visibleRows() {
        final ResultSet rs = engine.executeQuery("SELECT s, n FROM t1 ORDER BY n");
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(String.valueOf(rs.getValue("S"))).append(", ")
                .append(String.valueOf(rs.getValue("N")));
        }
        return out.toString();
    }

    /** Both policies apply under ACCOUNTADMIN and under SYSADMIN alike. */
    @Test
    public void bothPoliciesApplyToAnAdminRole() {
        engine.execute("CREATE OR REPLACE MASKING POLICY mp AS (val STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap AS (x INT) RETURNS BOOLEAN -> x > 1");
        engine.execute("ALTER TABLE t1 MODIFY COLUMN s SET MASKING POLICY mp");
        engine.execute("ALTER TABLE t1 ADD ROW ACCESS POLICY rap ON (n)");

        assertEquals("***, 2", visibleRows(), "masked and filtered for ACCOUNTADMIN");
        engine.execute("USE ROLE SYSADMIN");
        assertEquals("***, 2", visibleRows(), "and for SYSADMIN");
        engine.execute("USE ROLE ACCOUNTADMIN");

        engine.execute("ALTER TABLE t1 MODIFY COLUMN s UNSET MASKING POLICY");
        engine.execute("ALTER TABLE t1 DROP ROW ACCESS POLICY rap");
    }

    /** A body naming a role exempts that role, and only it. */
    @Test
    public void aBodyThatNamesARoleExemptsIt() {
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap AS (x INT) RETURNS BOOLEAN -> x > 1");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp2 AS (val STRING) RETURNS STRING ->"
            + " CASE WHEN CURRENT_ROLE() = 'ACCOUNTADMIN' THEN val ELSE '***' END");
        engine.execute("ALTER TABLE t1 MODIFY COLUMN s SET MASKING POLICY mp2");
        engine.execute("ALTER TABLE t1 ADD ROW ACCESS POLICY rap ON (n)");

        assertEquals("def, 2", visibleRows(),
            "the named role reads the value — and is still filtered by the row policy");
        engine.execute("USE ROLE SYSADMIN");
        assertEquals("***, 2", visibleRows(), "every other role is masked");
        engine.execute("USE ROLE ACCOUNTADMIN");

        engine.execute("ALTER TABLE t1 MODIFY COLUMN s UNSET MASKING POLICY");
        engine.execute("ALTER TABLE t1 DROP ROW ACCESS POLICY rap");
    }
}
