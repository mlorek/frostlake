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
 * A database's PUBLIC schema is owned by the role that owns the database, which created both, with OWNERSHIP
 * granted by that role; and the schema's owner holding MANAGE GRANTS turns managed access on and off, again and
 * again, with no future grant open (all live-verified).
 */
public class PublicSchemaOwnerAndManagedAccessTest extends MeasuredCellsTestSupport {

    @Test
    public void publicSchemaOwner() {
        engine.execute("USE SCHEMA test_db.test_schema");
        assertAnswer("true",
            "SELECT (SELECT SCHEMA_OWNER FROM test_db.INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = 'PUBLIC') = "
                + "(SELECT DATABASE_OWNER FROM test_db.INFORMATION_SCHEMA.DATABASES WHERE DATABASE_NAME = 'TEST_DB')");
        assertAnswer("true",
            "SELECT (SELECT SCHEMA_OWNER FROM test_db.INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = 'PUBLIC') = "
                + "CURRENT_ROLE()");
        assertAnswer("OWNERSHIP|ROLE|true|true",
            "SHOW GRANTS ON SCHEMA test_db.public ->> SELECT \"privilege\", \"granted_to\", \"grantee_name\" = "
                + "CURRENT_ROLE(), \"granted_by\" = CURRENT_ROLE() FROM $1 WHERE \"privilege\" = 'OWNERSHIP'");
        assertAnswer("true",
            "SELECT SCHEMA_OWNER IS NULL FROM test_db.INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = "
                + "'INFORMATION_SCHEMA'");
        assertAnswer("PUBLIC|true",
            "SHOW SCHEMAS LIKE 'PUBLIC' IN DATABASE test_db ->> SELECT \"name\", \"owner\" = CURRENT_ROLE() FROM $1");
    }

    @Test
    public void managedAccessSwitch() {
        engine.execute("CREATE OR REPLACE SCHEMA test_db.pq2");
        engine.execute("USE SCHEMA test_db.test_schema");
        assertAnswer("Statement executed successfully.", "ALTER SCHEMA test_db.pq2 ENABLE MANAGED ACCESS");
        assertAnswer("PQ2|MANAGED ACCESS",
            "SHOW SCHEMAS LIKE 'PQ2' IN DATABASE test_db ->> SELECT \"name\", \"options\" FROM $1");
        assertAnswer("Statement executed successfully.", "ALTER SCHEMA test_db.pq2 ENABLE MANAGED ACCESS");
        assertAnswer("Statement executed successfully.", "ALTER SCHEMA test_db.pq2 DISABLE MANAGED ACCESS");
        assertAnswer("PQ2|", "SHOW SCHEMAS LIKE 'PQ2' IN DATABASE test_db ->> SELECT \"name\", \"options\" FROM $1");
        assertAnswer("Statement executed successfully.", "ALTER SCHEMA test_db.pq2 DISABLE MANAGED ACCESS");
        assertAnswer("Statement executed successfully.",
            "ALTER SCHEMA IF EXISTS test_db.pq_none ENABLE MANAGED ACCESS");
    }
}
