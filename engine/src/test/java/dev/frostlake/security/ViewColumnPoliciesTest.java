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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * ALTER VIEW sets and unsets a masking policy, with its USING columns, and a projection policy on a view's column,
 * several columns in one statement, and sets and unsets its tags; an ALTER TABLE naming the view sets its column's
 * projection policy too but is told to use ALTER VIEW for a masking policy. The view's readers see the column
 * masked or refused, and POLICY_REFERENCES, TAG_REFERENCES_ALL_COLUMNS, GET_DDL and DESCRIBE VIEW report the
 * policies (all live-verified).
 */
public class ViewColumnPoliciesTest extends MeasuredCellsTestSupport {

    @Test
    public void aViewColumnTakesPoliciesAndTagsThroughAlterView() {
        engine.execute("CREATE OR REPLACE TABLE pt (a INT, b VARCHAR)");
        engine.execute("INSERT INTO pt VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE OR REPLACE VIEW pv AS SELECT a, b FROM pt");
        engine.execute("CREATE OR REPLACE VIEW pv2 AS SELECT a, b FROM pv");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY pjp1 AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp1 AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE OR REPLACE MASKING POLICY mpi AS (v INT) RETURNS INT -> 0");
        engine.execute("CREATE OR REPLACE TAG tg1");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer("REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. "
                + "Please remove them from the list of projected columns: |  |  A",
            "SELECT a FROM pv");
        assertAnswer("x ;; y", "SELECT b FROM pv ORDER BY b");
        assertAnswer("2", "SELECT COUNT(*) FROM pv");
        assertAnswer("1 ;; 2", "SELECT a FROM pt ORDER BY a");
        assertAnswer("REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. "
                + "Please remove them from the list of projected columns: |  |  A",
            "SELECT a FROM pv2");
        assertAnswer("PJP1|PROJECTION_POLICY|PV|VIEW|A",
            "SELECT policy_name, policy_kind, ref_entity_name, ref_entity_domain, ref_column_name FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'pv', REF_ENTITY_DOMAIN => 'VIEW')) "
                + "ORDER BY 1");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN a UNSET PROJECTION POLICY");
        assertAnswer("1 ;; 2", "SELECT a FROM pv ORDER BY a");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN a UNSET PROJECTION POLICY");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv ALTER COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv ALTER COLUMN a UNSET PROJECTION POLICY");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY a SET PROJECTION POLICY pjp1");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY a UNSET PROJECTION POLICY");
        assertAnswer("Statement executed successfully.",
            "ALTER VIEW pv MODIFY COLUMN a SET PROJECTION POLICY pjp1 FORCE");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN a UNSET PROJECTION POLICY");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'NOSUCH_C'",
            "ALTER VIEW pv MODIFY COLUMN nosuch_c SET PROJECTION POLICY pjp1");
        assertAnswer("REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PJP' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_PJP.",
            "ALTER VIEW pv MODIFY COLUMN a SET PROJECTION POLICY nosuch_pjp");
        assertAnswer("REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.NOSUCH_V' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_V.",
            "ALTER VIEW nosuch_v MODIFY COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN b SET MASKING POLICY mp1");
        assertAnswer("*** ;; ***", "SELECT b FROM pv ORDER BY b");
        assertAnswer("x ;; y", "SELECT b FROM pt ORDER BY b");
        assertAnswer("*** ;; ***", "SELECT b FROM pv2 ORDER BY b");
        assertAnswer("MP1|MASKING_POLICY|PV|VIEW|B",
            "SELECT policy_name, policy_kind, ref_entity_name, ref_entity_domain, ref_column_name FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'pv', REF_ENTITY_DOMAIN => 'VIEW')) "
                + "ORDER BY 1");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN b SET MASKING POLICY mp1");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN b UNSET MASKING POLICY");
        assertAnswer("x ;; y", "SELECT b FROM pv ORDER BY b");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN b UNSET MASKING POLICY");
        assertAnswer("REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH_MP' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MP.",
            "ALTER VIEW pv MODIFY COLUMN b SET MASKING POLICY nosuch_mp");
        assertAnswer("REFUSED SQL compilation error: COLUMN data type NUMBER(38,0) does not match with masking policy "
                + "data type TEXT.",
            "ALTER VIEW pv MODIFY COLUMN a SET MASKING POLICY mp1");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN a SET MASKING POLICY mpi");
        assertAnswer("0 ;; 0", "SELECT a FROM pv ORDER BY a");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN a UNSET MASKING POLICY");
        assertAnswer("Statement executed successfully.", "ALTER TABLE pv MODIFY COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer("REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. "
                + "Please remove them from the list of projected columns: |  |  A",
            "SELECT a FROM pv");
        assertAnswer("Statement executed successfully.", "ALTER TABLE pv MODIFY COLUMN a UNSET PROJECTION POLICY");
        assertAnswer("REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead",
            "ALTER TABLE pv MODIFY COLUMN b SET MASKING POLICY mp1");
        assertAnswer("x ;; y", "SELECT b FROM pv ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead",
            "ALTER TABLE pv MODIFY COLUMN b UNSET MASKING POLICY");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN b SET TAG tg1 = 'w'");
        assertAnswer("B|TG1|w|COLUMN",
            "SELECT column_name, tag_name, tag_value, level FROM "
                + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('pv', 'TABLE'))");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN b UNSET TAG tg1");
        assertAnswer("",
            "SELECT column_name, tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS('pv', 'TABLE'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected 'PROJECTION'.",
            "ALTER VIEW pv MODIFY COLUMN b SET MASKING POLICY mp1, COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer("x ;; y", "SELECT b FROM pv ORDER BY b");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN b UNSET MASKING POLICY");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN a UNSET PROJECTION POLICY");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN b SET MASKING POLICY mp1");
        assertAnswer("create or replace view PV( | 	A, | 	B WITH MASKING POLICY TEST_DB.TEST_SCHEMA.MP1 | ) as SELECT "
                + "a, b FROM pt;",
            "SELECT GET_DDL('VIEW', 'pv')");
        assertAnswer("A|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; B|VARCHAR(16777216)|COLUMN|Y|"
                + "null|N|N|null|null|null|TEST_DB.TEST_SCHEMA.MP1|null|null",
            "DESCRIBE VIEW pv");
        assertAnswer("View PV successfully created.", "CREATE OR REPLACE VIEW pv AS SELECT a, b FROM pt");
        assertAnswer("x ;; y", "SELECT b FROM pv ORDER BY b");
        assertAnswer("",
            "SELECT policy_name, policy_kind, ref_entity_name, ref_entity_domain, ref_column_name FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'pv', REF_ENTITY_DOMAIN => 'VIEW')) "
                + "ORDER BY 1");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN b SET MASKING POLICY mp1");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv RENAME TO pv3");
        assertAnswer("*** ;; ***", "SELECT b FROM pv3 ORDER BY b");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv3 RENAME TO pv");
        assertAnswer("Statement executed successfully.", "ALTER VIEW pv MODIFY COLUMN b UNSET MASKING POLICY");
    }

    /**
     * A schema's clone takes its views with their columns' policies, each view's columns its own: a policy unset on a
     * column of the clone's view stays on the original's (live-verified).
     */
    @Test
    public void aClonedViewColumnKeepsItsOwnPolicy() {
        engine.execute("CREATE OR REPLACE TABLE ct (a INT, b VARCHAR)");
        engine.execute("INSERT INTO ct VALUES (1, 'x')");
        engine.execute("CREATE OR REPLACE MASKING POLICY cmp AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE OR REPLACE VIEW cv AS SELECT a, b FROM ct");
        engine.execute("ALTER VIEW cv MODIFY COLUMN b SET MASKING POLICY cmp");
        engine.execute("CREATE OR REPLACE SCHEMA test_db.cloned_s CLONE test_db.test_schema");
        assertAnswer("***", "SELECT b FROM test_db.cloned_s.cv");
        assertAnswer("Statement executed successfully.",
            "ALTER VIEW test_db.cloned_s.cv MODIFY COLUMN b UNSET MASKING POLICY");
        assertAnswer("x", "SELECT b FROM test_db.cloned_s.cv");
        assertAnswer("***", "SELECT b FROM test_db.test_schema.cv");
    }
}
