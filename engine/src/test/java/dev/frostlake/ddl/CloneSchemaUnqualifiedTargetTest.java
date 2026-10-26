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
 * CREATE SCHEMA with an unqualified name clones a source named in full, however its database is spelled, into the
 * current database, and a missing source database or schema is refused in the account's words (all
 * live-verified).
 */
public class CloneSchemaUnqualifiedTargetTest extends MeasuredCellsTestSupport {

    @Test
    public void unqualifiedTargetQualifiedSource() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.ct (a INT)");
        assertAnswer("Schema S769A successfully created.", "CREATE OR REPLACE SCHEMA s769a CLONE test_db.test_schema");
        assertAnswer("CT", "SHOW TABLES IN SCHEMA test_db.s769a ->> SELECT \"name\" FROM $1");
        answer("USE SCHEMA test_db.test_schema");
        assertAnswer("Schema S769B successfully created.", "CREATE OR REPLACE SCHEMA s769b CLONE TEST_DB.TEST_SCHEMA");
        answer("USE SCHEMA test_db.test_schema");
        assertAnswer("Schema S769C successfully created.",
            "CREATE OR REPLACE SCHEMA s769c CLONE \"TEST_DB\".TEST_SCHEMA");
        answer("USE SCHEMA test_db.test_schema");
        assertAnswer("Schema S769D successfully created.",
            "CREATE OR REPLACE SCHEMA test_db.s769d CLONE test_db.test_schema");
        answer("USE SCHEMA test_db.test_schema");
        assertAnswer("REFUSED SQL compilation error: | Database 'NODB769' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NODB769.",
            "CREATE SCHEMA s769e CLONE nodb769.test_schema");
        assertAnswer("REFUSED SQL compilation error: | Schema 'TEST_DB.NOSCHEMA769' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSCHEMA769.",
            "CREATE SCHEMA s769f CLONE test_db.noschema769");
        assertAnswer("TEST_SCHEMA", "SELECT CURRENT_SCHEMA()");
    }
}
