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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * Packages policies (live-verified): CREATE [OR REPLACE] [IF NOT EXISTS] … LANGUAGE PYTHON with the allow, the
 * block and the creation block lists and the comment; ALTER … SET, UNSET and RENAME TO; DROP, SHOW PACKAGES
 * POLICIES and DESCRIBE, whose one row shows each list in brackets. A language other than PYTHON is no language a
 * function is written in, and a quoted word is no language at all.
 */
public class PackagesPolicyTest extends MeasuredCellsTestSupport {

    @Test
    public void aPolicyIsCreatedAndDropped() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE PACKAGES POLICY pk1 LANGUAGE PYTHON", "Packages policy 'PK1' created successfully"},
            {"CREATE OR REPLACE PACKAGES POLICY pk2 LANGUAGE PYTHON ALLOWLIST = ('numpy') BLOCKLIST = ('pandas') "
                + "COMMENT = 'c'",
                "Packages policy 'PK2' created successfully"},
            {"DROP PACKAGES POLICY pk1", "PK1 successfully dropped."},
            {"DROP PACKAGES POLICY pk2", "PK2 successfully dropped."},
        });
    }

    @Test
    public void eachListIsJudgedAndShown() {
        engine.execute("CREATE OR REPLACE PACKAGES POLICY pk1 LANGUAGE PYTHON");
        engine.execute("CREATE OR REPLACE SESSION POLICY sp2");
        assertCells(new String[][] {
            {"SHOW PACKAGES POLICIES LIKE 'PK1' ->> SELECT \"name\", \"kind\", \"comment\", \"options\" FROM $1",
                "PK1|PACKAGES_POLICY||"},
            {"CREATE OR REPLACE PACKAGES POLICY pk2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '<EOF>'."},
            {"CREATE OR REPLACE PACKAGES POLICY pk2 LANGUAGE JAVA",
                "REFUSED SQL compilation error: | invalid property 'JAVA' for 'FUNCTION_LANGUAGE'"},
            {"CREATE OR REPLACE PACKAGES POLICY pk2 LANGUAGE PYTHON ALLOWLIST = ('numpy==1.0', 'pandas>=1')",
                "Packages policy 'PK2' created successfully"},
            {"CREATE OR REPLACE PACKAGES POLICY pk2 LANGUAGE PYTHON ALLOWLIST = 'numpy'",
                "REFUSED SQL compilation error: | invalid type of property ''numpy'' for 'ALLOWLIST'"},
            {"CREATE OR REPLACE PACKAGES POLICY pk2 LANGUAGE PYTHON ALLOWLIST = ()",
                "Packages policy 'PK2' created successfully"},
            {"CREATE OR REPLACE PACKAGES POLICY pk2 LANGUAGE PYTHON ADDITIONAL_CREATION_BLOCKLIST = ('x') COMMENT = "
                + "'q'",
                "Packages policy 'PK2' created successfully"},
            {"CREATE OR REPLACE PACKAGES POLICY pk2 LANGUAGE PYTHON NOPE = ('x')",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'PACKAGES_POLICY'"},
            {"CREATE OR REPLACE PACKAGES POLICY pk2 LANGUAGE PYTHON COMMENT = 1",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'COMMENT'"},
            {"CREATE OR REPLACE PACKAGES POLICY pk2 LANGUAGE PYTHON BLOCKLIST = ('a') BLOCKLIST = ('b')",
                "REFUSED SQL compilation error: | duplicate property 'BLOCKLIST';"},
            {"CREATE PACKAGES POLICY pk1 LANGUAGE PYTHON",
                "REFUSED SQL compilation error: | Object 'PK1' already exists."},
            {"CREATE PACKAGES POLICY IF NOT EXISTS pk1 LANGUAGE PYTHON", "PK1 already exists, statement succeeded."},
            {"CREATE PACKAGES POLICY sp2 LANGUAGE PYTHON",
                "REFUSED SQL compilation error: | Object 'SP2' already exists."},
            {"CREATE OR REPLACE PACKAGES POLICY sp2 LANGUAGE PYTHON",
                "REFUSED SQL compilation error: Object found is of type 'SESSION_POLICY', not specified type "
                + "'PACKAGES_POLICY'."},
            {"ALTER PACKAGES POLICY pk1 SET ALLOWLIST = ('numpy') COMMENT = 'x'", "Statement executed successfully."},
            {"ALTER PACKAGES POLICY pk1 UNSET ALLOWLIST", "Statement executed successfully."},
            {"ALTER PACKAGES POLICY pk1 UNSET COMMENT", "Statement executed successfully."},
            {"ALTER PACKAGES POLICY pk1 SET NOPE = ('x')",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'PACKAGES_POLICY'"},
            {"ALTER PACKAGES POLICY pk1 SET LANGUAGE = PYTHON",
                "REFUSED SQL compilation error: | invalid property 'LANGUAGE' for 'PACKAGES_POLICY'"},
            {"ALTER PACKAGES POLICY pk1 RENAME TO pk3", "Statement executed successfully."},
            {"ALTER PACKAGES POLICY nosuch_pk SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Packages policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PK' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PK."},
            {"ALTER PACKAGES POLICY IF EXISTS nosuch_pk SET COMMENT = 'x'", "Statement executed successfully."},
            {"DROP PACKAGES POLICY nosuch_pk",
                "REFUSED SQL compilation error: | Packages policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PK' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PK."},
            {"DROP PACKAGES POLICY IF EXISTS nosuch_pk",
                "Drop statement executed successfully (NOSUCH_PK already dropped)."},
            {"UNDROP PACKAGES POLICY pk1",
                "REFUSED Unsupported feature 'UNDROP not supported for objects of type PACKAGES_POLICY'."},
            {"SHOW PACKAGES POLICIES IN SCHEMA test_db.test_schema ->> SELECT \"name\" FROM $1 ORDER BY 1",
                "PK2 ;; PK3"},
            {"SELECT GET_DDL('POLICY', 'pk1')",
                "REFUSED SQL compilation error: | Policy 'TEST_DB.TEST_SCHEMA.PK1' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.PK1."},
            {"DROP PACKAGES POLICY pk1",
                "REFUSED SQL compilation error: | Packages policy 'TEST_DB.TEST_SCHEMA.PK1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.PK1."},
        });
    }

    @Test
    public void aPolicyIsDescribedWhole() {
        engine.execute("CREATE OR REPLACE PACKAGES POLICY pq1 LANGUAGE PYTHON");
        assertCells(new String[][] {
            {"DESCRIBE PACKAGES POLICY pq1 ->> SELECT \"name\", \"language\", \"allowlist\", \"blocklist\", "
                + "\"additional_creation_blocklist\", \"comment\" FROM $1",
                "PQ1|PYTHON|['*']|[]|null|null"},
            {"SHOW PACKAGES POLICIES LIKE 'PQ%' ->> SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", "
                + "\"comment\", \"owner_role_type\", \"options\" FROM $1 ORDER BY 1",
                "PQ1|TEST_DB|TEST_SCHEMA|PACKAGES_POLICY||ROLE|"},
            {"SHOW TERSE PACKAGES POLICIES LIKE 'PQ1' ->> SELECT \"name\", \"kind\" FROM $1", "PQ1|PACKAGES_POLICY"},
            {"CREATE OR REPLACE PACKAGES POLICY pq2 LANGUAGE python BLOCKLIST = ('a', 'b') COMMENT = 'it''s'",
                "Packages policy 'PQ2' created successfully"},
            {"DESCRIBE PACKAGES POLICY pq2 ->> SELECT \"name\", \"language\", \"allowlist\", \"blocklist\", "
                + "\"additional_creation_blocklist\", \"comment\" FROM $1",
                "PQ2|PYTHON|['*']|['a','b']|null|it's"},
            {"SHOW PACKAGES POLICIES LIKE 'PQ%' ->> SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", "
                + "\"comment\", \"owner_role_type\", \"options\" FROM $1 ORDER BY 1",
                "PQ1|TEST_DB|TEST_SCHEMA|PACKAGES_POLICY||ROLE| ;; PQ2|TEST_DB|TEST_SCHEMA|PACKAGES_POLICY|it's|ROLE|"},
            {"CREATE OR REPLACE PACKAGES POLICY pq3 LANGUAGE 'PYTHON'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ''PYTHON''."},
            {"CREATE OR REPLACE PACKAGES POLICY pq3 LANGUAGE \"PYTHON\"",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '\"PYTHON\"'. | "
                + "syntax error line 1 at position 55 unexpected '<EOF>'."},
            {"CREATE OR REPLACE PACKAGES POLICY pq3 LANGUAGE SQL",
                "REFUSED SQL compilation error: | invalid property 'SQL' for 'FUNCTION_LANGUAGE'"},
            {"CREATE OR REPLACE PACKAGES POLICY pq3 LANGUAGE PYTHON ALLOWLIST = ('a'), BLOCKLIST = ('b')",
                "Packages policy 'PQ3' created successfully"},
            {"ALTER PACKAGES POLICY pq2 SET ALLOWLIST = ('x'), COMMENT = 'y'", "Statement executed successfully."},
            {"DESCRIBE PACKAGES POLICY pq2 ->> SELECT \"name\", \"language\", \"allowlist\", \"blocklist\", "
                + "\"additional_creation_blocklist\", \"comment\" FROM $1",
                "PQ2|PYTHON|['x']|['a','b']|null|y"},
            {"ALTER PACKAGES POLICY pq2 UNSET BLOCKLIST, ADDITIONAL_CREATION_BLOCKLIST",
                "Statement executed successfully."},
            {"DESCRIBE PACKAGES POLICY pq2 ->> SELECT \"name\", \"language\", \"allowlist\", \"blocklist\", "
                + "\"additional_creation_blocklist\", \"comment\" FROM $1",
                "PQ2|PYTHON|['x']|[]|null|y"},
            {"ALTER PACKAGES POLICY pq2 SET ADDITIONAL_CREATION_BLOCKLIST = ()", "Statement executed successfully."},
            {"DESCRIBE PACKAGES POLICY pq2 ->> SELECT \"name\", \"language\", \"allowlist\", \"blocklist\", "
                + "\"additional_creation_blocklist\", \"comment\" FROM $1",
                "PQ2|PYTHON|['x']|[]|[]|y"},
            {"ALTER PACKAGES POLICY pq2 UNSET ADDITIONAL_CREATION_BLOCKLIST", "Statement executed successfully."},
            {"DESCRIBE PACKAGES POLICY pq2 ->> SELECT \"name\", \"language\", \"allowlist\", \"blocklist\", "
                + "\"additional_creation_blocklist\", \"comment\" FROM $1",
                "PQ2|PYTHON|['x']|[]|null|y"},
            {"ALTER PACKAGES POLICY pq2 UNSET NOPE",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'PACKAGES_POLICY'"},
            {"ALTER PACKAGES POLICY pq2 SET BLOCKLIST = 'x'",
                "REFUSED SQL compilation error: | invalid type of property ''x'' for 'BLOCKLIST'"},
        });
    }
}
