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

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * EXEMPT_OTHER_POLICIES: a masking or a row access policy takes it beside its COMMENT, in either order, TRUE or FALSE
 * in any case; GET_DDL writes it when TRUE and SHOW lists it in the options; a row access policy and a conditional
 * masking policy read a column masked by an exempt policy, in whichever order the two are attached, while a column
 * masked by another policy stays out of their reach both ways (live-verified).
 */
public class MaskingPolicyExemptionTest extends BaseDatabaseTest {

    private static final String POLICY = "CREATE OR REPLACE MASKING POLICY %s AS (v VARCHAR) RETURNS VARCHAR -> '***' ";
    private static final String ERROR = "SQL compilation error:|";
    private static final String READ = "SQL compilation error: Column 'S' cannot be used as policy argument because "
        + "it is masked by another policy.";
    private static final String MASKED = "SQL compilation error: Column 'S' cannot be masked because it is used as "
        + "argument by one or more masking/row-access policies.";
    private static final String DONE = "Statement executed successfully.";

    private static String policy(final String name, final String properties) {
        return String.format(POLICY, name) + properties;
    }

    @Test
    public void thePropertyIsReadBesideTheComment() {
        assertCells(engine, new String[][] {
            {policy("mpx", "EXEMPT_OTHER_POLICIES = TRUE"), "Masking policy MPX successfully created."},
            {policy("mpf", "EXEMPT_OTHER_POLICIES = false"), "Masking policy MPF successfully created."},
            {policy("mpc", "COMMENT = 'c' EXEMPT_OTHER_POLICIES = TRUE"), "Masking policy MPC successfully created."},
            {policy("mpe", "EXEMPT_OTHER_POLICIES = true COMMENT = 'c'"), "Masking policy MPE successfully created."},
            {policy("m1", "EXEMPT_OTHER_POLICIES = YES"),
                ERROR + "invalid value [YES] for parameter 'EXEMPT_OTHER_POLICIES'"},
            {policy("m2", "EXEMPT_OTHER_POLICIES = 'TRUE'"),
                ERROR + "invalid value ['TRUE'] for parameter 'EXEMPT_OTHER_POLICIES'"},
            {policy("m3", "EXEMPT_OTHER_POLICIES = 1"),
                ERROR + "invalid value [1] for parameter 'EXEMPT_OTHER_POLICIES'"},
            {policy("m4", "EXEMPT_OTHER_POLICIES TRUE"),
                ERROR + "syntax error line 1 at position 98 unexpected 'TRUE'."},
            {policy("m6", "EXEMPT_OTHER_POLICIES = ON"),
                ERROR + "syntax error line 1 at position 100 unexpected 'ON'."},
            {policy("d1", "COMMENT = 'a' COMMENT = 'b'"), ERROR + "duplicate property 'COMMENT';"},
            {policy("d3", "EXEMPT_OTHER_POLICIES = TRUE EXEMPT_OTHER_POLICIES = TRUE"),
                ERROR + "duplicate property 'EXEMPT_OTHER_POLICIES';"},
            {"CREATE OR REPLACE ROW ACCESS POLICY rx AS (v VARCHAR) RETURNS BOOLEAN -> TRUE "
                + "EXEMPT_OTHER_POLICIES = TRUE", "Row access policy 'RX' is successfully created."},
            {"CREATE OR REPLACE PROJECTION POLICY pj AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => true) EXEMPT_OTHER_POLICIES = TRUE",
                ERROR + "invalid property 'EXEMPT_OTHER_POLICIES' for 'PROJECTION_POLICY'"},
            {"CREATE OR REPLACE AGGREGATION POLICY ag AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2) EXEMPT_OTHER_POLICIES = TRUE",
                ERROR + "invalid property 'EXEMPT_OTHER_POLICIES' for 'AGGREGATION_POLICY'"},
            {"SELECT GET_DDL('POLICY', 'mpx')",
                "create or replace masking policy MPX as (V VARCHAR) \nreturns VARCHAR ->\n'***'\n"
                    + "EXEMPT_OTHER_POLICIES=TRUE\n;"},
            {"SELECT GET_DDL('POLICY', 'mpf')", "create or replace masking policy MPF as (V VARCHAR) \nreturns VARCHAR "
                + "->\n'***'\n;"},
            {"SELECT GET_DDL('POLICY', 'mpc')", "create or replace masking policy MPC as (V VARCHAR) \nreturns VARCHAR "
                + "->\n'***'\nCOMMENT='c'\nEXEMPT_OTHER_POLICIES=TRUE\n;"},
            {"SELECT GET_DDL('POLICY', 'rx')", "create or replace row access policy RX as (V VARCHAR) \nreturns "
                + "BOOLEAN ->\nTRUE\nEXEMPT_OTHER_POLICIES=TRUE\n;"},
            {"SHOW MASKING POLICIES LIKE 'MPX' ->> SELECT \"name\", \"options\" FROM $1",
                "MPX, {\"EXEMPT_OTHER_POLICIES\":true}"},
            {"SHOW MASKING POLICIES LIKE 'MPF' ->> SELECT \"name\", \"options\" FROM $1", "MPF, "},
            {"SHOW ROW ACCESS POLICIES LIKE 'RX' ->> SELECT \"name\", \"options\" FROM $1",
                "RX, {\"EXEMPT_OTHER_POLICIES\":true}"},
            {"ALTER MASKING POLICY mpx SET EXEMPT_OTHER_POLICIES = FALSE",
                ERROR + "invalid property 'EXEMPT_OTHER_POLICIES' for 'MASKING_POLICY'"},
            {"DESCRIBE MASKING POLICY mpx", "MPX, (V VARCHAR), VARCHAR, '***'"},
        });
    }

    @Test
    public void anExemptPolicyLetsOtherPoliciesReadItsColumn() {
        engine.execute(policy("mp", ""));
        engine.execute(policy("mx", "EXEMPT_OTHER_POLICIES = TRUE"));
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rp AS (v VARCHAR) RETURNS BOOLEAN -> v <> 'hide'");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rx AS (v VARCHAR) RETURNS BOOLEAN -> TRUE "
            + "EXEMPT_OTHER_POLICIES = TRUE");
        engine.execute("CREATE OR REPLACE MASKING POLICY cmp AS (v VARCHAR, w VARCHAR) RETURNS VARCHAR -> "
            + "IFF(w = 'x', '***', v)");
        engine.execute("CREATE TABLE t1 (s VARCHAR WITH MASKING POLICY mp)");
        engine.execute("CREATE TABLE t2 (s VARCHAR)");
        engine.execute("CREATE TABLE t3 (s VARCHAR WITH MASKING POLICY mx)");
        engine.execute("CREATE TABLE t4 (s VARCHAR, w VARCHAR)");
        engine.execute("CREATE TABLE t5 (s VARCHAR)");
        engine.execute("CREATE TABLE t8 (s VARCHAR WITH MASKING POLICY mp, w VARCHAR)");
        engine.execute("CREATE TABLE t9 (s VARCHAR WITH MASKING POLICY mx, w VARCHAR)");
        assertCells(engine, new String[][] {
            {"ALTER TABLE t1 ADD ROW ACCESS POLICY rp ON (s)", READ},
            {"ALTER TABLE t1 ADD ROW ACCESS POLICY rx ON (s)", READ},
            {"ALTER TABLE t2 ADD ROW ACCESS POLICY rp ON (s)", DONE},
            {"ALTER TABLE t2 MODIFY COLUMN s SET MASKING POLICY mp", MASKED},
            {"ALTER TABLE t3 ADD ROW ACCESS POLICY rp ON (s)", DONE},
            {"ALTER TABLE t5 ADD ROW ACCESS POLICY rp ON (s)", DONE},
            {"ALTER TABLE t5 MODIFY COLUMN s SET MASKING POLICY mx", DONE},
            {"ALTER TABLE t4 MODIFY COLUMN w SET MASKING POLICY cmp USING (w, s)", DONE},
            {"ALTER TABLE t4 MODIFY COLUMN s SET MASKING POLICY mp", MASKED},
            {"ALTER TABLE t4 MODIFY COLUMN s SET MASKING POLICY mx", DONE},
            {"ALTER TABLE t8 MODIFY COLUMN w SET MASKING POLICY cmp USING (w, s)", READ},
            {"ALTER TABLE t9 MODIFY COLUMN w SET MASKING POLICY cmp USING (w, s)", DONE},
            {"CREATE TABLE t7 (s VARCHAR WITH MASKING POLICY mp, w VARCHAR) WITH ROW ACCESS POLICY rp ON (s)", MASKED},
            {"CREATE TABLE t11 (s VARCHAR WITH MASKING POLICY mp, w VARCHAR WITH MASKING POLICY cmp USING (w, s))",
                "SQL compilation error: Column 'S' cannot be masked and used as conditional argument at the same "
                    + "time."},
            {"CREATE TABLE ti (s VARCHAR WITH MASKING POLICY mx, w VARCHAR) WITH ROW ACCESS POLICY rp ON (s)",
                "Table TI successfully created."},
            {"CREATE TABLE t13 (s VARCHAR WITH MASKING POLICY mx, w VARCHAR WITH MASKING POLICY cmp USING (w, s))",
                "Table T13 successfully created."},
        });
    }
}
