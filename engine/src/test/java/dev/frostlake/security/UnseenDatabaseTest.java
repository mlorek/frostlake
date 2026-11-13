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
 * A database the role holds no privilege on, and does not see under MANAGE GRANTS, is to the statements naming it one
 * that does not exist (live-verified, as PUBLIC with no secondary roles in force): each SHOW listing scoped to it or to
 * one of its schemas answers as it answers a missing database, the generic sentence or the one naming the database by
 * kind; DESCRIBE of an object in it, a query of its table and GET_DDL are refused at the database, before the object;
 * USE of it, or of its schema, is the sentence of any failed USE; and a listing over the account leaves its objects
 * out. Live skips this class, which switches roles.
 */
public class UnseenDatabaseTest extends MeasuredCellsTestSupport {

    @Test
    public void aDatabaseTheRoleDoesNotSeeIsOneThatDoesNotExist() {
        final String opener = answer("SELECT CURRENT_ROLE()");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.t50 (x INT)");
        engine.execute("CREATE OR REPLACE VIEW test_db.test_schema.v50 AS SELECT x FROM test_db.test_schema.t50");
        engine.execute("CREATE OR REPLACE STAGE test_db.test_schema.st50");
        engine.execute("CREATE OR REPLACE SEQUENCE test_db.test_schema.sq50");
        engine.execute("CREATE OR REPLACE FILE FORMAT test_db.test_schema.ff50 TYPE = CSV");
        engine.execute("CREATE OR REPLACE FUNCTION test_db.test_schema.f50(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        engine.execute("CREATE OR REPLACE TASK test_db.test_schema.tk50 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE STREAM test_db.test_schema.sm50 ON TABLE test_db.test_schema.t50");
        try {
            engine.execute("USE ROLE PUBLIC");
            engine.execute("USE SECONDARY ROLES NONE");
            assertAnswer("PUBLIC", "SELECT CURRENT_ROLE()");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW TABLES IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW VIEWS IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW STAGES IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW SEQUENCES IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW FILE FORMATS IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW USER FUNCTIONS IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW USER PROCEDURES IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW TASKS IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW STREAMS IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW OBJECTS IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW SCHEMAS IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW PIPES IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW TERSE TABLES IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW MATERIALIZED VIEWS IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW DYNAMIC TABLES IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW TAGS IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW ALERTS IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW SECRETS IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW EXTERNAL TABLES IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW MASKING POLICIES IN DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW TABLES IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW VIEWS IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW STAGES IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW SEQUENCES IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW FILE FORMATS IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW USER FUNCTIONS IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW TASKS IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW STREAMS IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW OBJECTS IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW PIPES IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW TAGS IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW ALERTS IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SHOW MASKING POLICIES IN SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW TABLES IN DATABASE no_such_db_50");
            assertAnswer("REFUSED SQL compilation error: | Database 'NO_SUCH_DB_50' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "DATABASE NO_SUCH_DB_50.",
                "SHOW PIPES IN DATABASE no_such_db_50");
            assertAnswer("REFUSED SQL compilation error: | Database 'NO_SUCH_DB_50' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
                + "DATABASE NO_SUCH_DB_50.",
                "SHOW TASKS IN DATABASE no_such_db_50");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "SHOW TABLES IN SCHEMA test_db.no_such_schema_50");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "DESCRIBE TABLE test_db.test_schema.t50");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "DESCRIBE VIEW test_db.test_schema.v50");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "DESCRIBE STAGE test_db.test_schema.st50");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "DESCRIBE SEQUENCE test_db.test_schema.sq50");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "DESCRIBE FILE FORMAT test_db.test_schema.ff50");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "DESCRIBE FUNCTION test_db.test_schema.f50(NUMBER)");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "DESCRIBE TASK test_db.test_schema.tk50");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "DESCRIBE STREAM test_db.test_schema.sm50");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SELECT * FROM test_db.test_schema.t50");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "USE DATABASE test_db");
            assertAnswer("REFUSED SQL compilation error: | Object does not exist, or operation cannot be "
                + "performed.",
                "USE SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "SELECT GET_DDL('TABLE', 'test_db.test_schema.t50')");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "DESCRIBE SCHEMA test_db.test_schema");
            assertAnswer("REFUSED SQL compilation error: | Database 'TEST_DB' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE TEST_DB.",
                "DESCRIBE DATABASE test_db");
            assertAnswer("",
                "SHOW SCHEMAS LIKE 'TEST_SCHEMA' IN ACCOUNT");
        } finally {
            engine.execute("USE ROLE " + opener);
            engine.execute("USE SECONDARY ROLES ALL");
            engine.execute("DROP STREAM IF EXISTS test_db.test_schema.sm50");
            engine.execute("DROP TASK IF EXISTS test_db.test_schema.tk50");
            engine.execute("DROP FUNCTION IF EXISTS test_db.test_schema.f50(NUMBER)");
            engine.execute("DROP FILE FORMAT IF EXISTS test_db.test_schema.ff50");
            engine.execute("DROP SEQUENCE IF EXISTS test_db.test_schema.sq50");
            engine.execute("DROP STAGE IF EXISTS test_db.test_schema.st50");
            engine.execute("DROP VIEW IF EXISTS test_db.test_schema.v50");
            engine.execute("DROP TABLE IF EXISTS test_db.test_schema.t50");
        }
    }
}
