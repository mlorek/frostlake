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
 * CREATE OR ALTER SCHEMA creates a schema that is not there, as CREATE does, current schema included, and makes
 * one that is there the schema it writes: what it names is set, a COMMENT, a retention, a parameter or MANAGED
 * ACCESS it leaves out is unset, and the session stays in its own schema. IF NOT EXISTS, a CLONE, a tag and a
 * change of transience are refused, a temporary schema as CREATE refuses one (all live-verified).
 */
public class CreateOrAlterSchemaTest extends MeasuredCellsTestSupport {

    @Test
    public void createsOrAltersInPlace() {
        assertAnswer("Schema S652A successfully created.", "CREATE OR ALTER SCHEMA s652a");
        assertAnswer("S652A", "SELECT CURRENT_SCHEMA()");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.test_schema");
        assertAnswer("Statement executed successfully.", "CREATE OR ALTER SCHEMA s652a COMMENT = 'x'");
        assertAnswer("TEST_SCHEMA", "SELECT CURRENT_SCHEMA()");
        assertAnswer("Statement executed successfully.",
            "CREATE OR ALTER SCHEMA s652a DATA_RETENTION_TIME_IN_DAYS = 0");
        assertAnswer("Statement executed successfully.", "CREATE OR ALTER SCHEMA s652a WITH MANAGED ACCESS");
        assertAnswer("Statement executed successfully.", "CREATE OR ALTER SCHEMA s652a");
        assertAnswer("REFUSED SQL compilation error: Property 'TRANSIENT_DATA' cannot be changed in CREATE OR ALTER.",
            "CREATE OR ALTER TRANSIENT SCHEMA s652a");
        assertAnswer("Statement executed successfully.",
            "CREATE OR ALTER SCHEMA s652a MAX_DATA_EXTENSION_TIME_IN_DAYS = 3 LOG_LEVEL = 'INFO'");
        assertAnswer("MAX_DATA_EXTENSION_TIME_IN_DAYS|3|14|SCHEMA|Maximum number of days to extend data retention "
                + "beyond the retention period to prevent a stream becoming stale.|NUMBER",
            "SHOW PARAMETERS LIKE 'MAX_DATA_EXTENSION_TIME_IN_DAYS' IN SCHEMA s652a");
        assertAnswer("LOG_LEVEL|INFO|OFF|SCHEMA|LOG_LEVEL to use when filtering events|STRING",
            "SHOW PARAMETERS LIKE 'LOG_LEVEL' IN SCHEMA s652a");
        assertAnswer("Statement executed successfully.", "CREATE OR ALTER SCHEMA s652a");
        assertAnswer("LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING",
            "SHOW PARAMETERS LIKE 'LOG_LEVEL' IN SCHEMA s652a");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'NOSUCHPARAM' for 'SCHEMA'",
            "CREATE OR ALTER SCHEMA s652a NOSUCHPARAM = 1");
        assertAnswer("REFUSED SQL compilation error: | options CREATE OR ALTER and TAG are incompatible.",
            "CREATE OR ALTER SCHEMA s652a WITH TAG (nosuchtag652 = 'v')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','.",
            "CREATE OR ALTER SCHEMA s652a, COMMENT = 'y'");
        assertAnswer("Statement executed successfully.",
            "CREATE OR ALTER SCHEMA s652a COMMENT = 'y', DATA_RETENTION_TIME_IN_DAYS = 1");
        assertAnswer("Schema S652B successfully created.", "CREATE OR ALTER SCHEMA test_db.s652b");
        assertAnswer("S652B", "SELECT CURRENT_SCHEMA()");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.test_schema");
        assertAnswer("REFUSED Unsupported feature 'TEMPORARY SCHEMA'.", "CREATE OR ALTER TEMPORARY SCHEMA s652c");
        assertAnswer("Schema S652D successfully created.", "CREATE OR ALTER TRANSIENT SCHEMA s652d");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.test_schema");
        assertAnswer("REFUSED SQL compilation error: Property 'TRANSIENT_DATA' cannot be changed in CREATE OR ALTER.",
            "CREATE OR ALTER SCHEMA s652d");
        assertAnswer("TEST_SCHEMA", "SELECT CURRENT_SCHEMA()");
        assertAnswer("Statement executed successfully.", "USE SCHEMA test_db.test_schema");
    }

    @Test
    public void refusalsAndTaskContactLines() {
        engine.execute("CREATE OR REPLACE TAG t650");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap650 AS (a INT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp650 AS (v INT) RETURNS INT -> v");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY ap650 AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "NO_AGGREGATION_CONSTRAINT()");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY pp650 AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        engine.execute("CREATE OR REPLACE JOIN POLICY jp650 AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        assertAnswer("REFUSED SQL compilation error: | options CREATE OR ALTER and TAG are incompatible.",
            "CREATE OR ALTER SCHEMA s650 WITH TAG (t650 = 'v')");
        assertAnswer("Schema S650B successfully created.", "CREATE OR ALTER SCHEMA s650b");
        assertAnswer("Statement executed successfully.", "CREATE OR ALTER SCHEMA s650b COMMENT = 'x'");
        assertAnswer("Statement executed successfully.",
            "CREATE OR ALTER SCHEMA s650b DATA_RETENTION_TIME_IN_DAYS = 1");
        assertAnswer("Statement executed successfully.", "CREATE OR ALTER SCHEMA s650b WITH MANAGED ACCESS");
        assertAnswer("Schema S650C successfully created.", "CREATE OR ALTER TRANSIENT SCHEMA s650c");
        assertAnswer("REFUSED SQL compilation error: | options IF NOT EXISTS and OR ALTER are incompatible.",
            "CREATE OR ALTER SCHEMA IF NOT EXISTS s650b");
        assertAnswer("REFUSED Unsupported feature 'CREATE OR ALTER SCHEMA with CLONE'.",
            "CREATE OR ALTER SCHEMA s650b CLONE test_schema");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'WITH'.",
            "CREATE OR ALTER TASK tk650 WITH TAG (t650 = 'v1') WITH CONTACT (SUPPORT = c) AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'CONTACT'. | "
                + "syntax error line 1 at position 40 unexpected '('. | syntax error line 1 at position 54 unexpected "
                + "'WITH'.",
            "CREATE OR ALTER TASK tk650 WITH CONTACT (SUPPORT = c) WITH TAG (t650 = 'v1') AS SELECT 1");
    }
}
