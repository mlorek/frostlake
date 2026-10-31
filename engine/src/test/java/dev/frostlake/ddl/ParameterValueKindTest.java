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
 * An ALTER TABLE or ALTER ALERT … SET judges each value against its parameter's type before the object's level or
 * the role's access: a NUMBER takes a whole number, a FLOAT a number written with a fraction, a BOOLEAN TRUE or
 * FALSE and a STRING a text, any other value refused as written, a parameter the object takes included (all
 * live-verified).
 */
public class ParameterValueKindTest extends MeasuredCellsTestSupport {

    @Test
    public void valuesBeforeAccess() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE t40 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['10.0'] for parameter "
                + "'INITIAL_REPLICATION_SIZE_LIMIT_IN_TB'",
            "ALTER TABLE t40 SET INITIAL_REPLICATION_SIZE_LIMIT_IN_TB = '10.0'");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['10.0'] for parameter "
                + "'LISTING_AUTO_FULFILLMENT_INITIAL_REFRESH_SIZE_LIMIT_IN_TB'",
            "ALTER TABLE t40 SET LISTING_AUTO_FULFILLMENT_INITIAL_REFRESH_SIZE_LIMIT_IN_TB = '10.0'");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['10.0'] for parameter "
                + "'REPLICATION_AUTO_RESOLVER_MAX_SIZE_LIMIT_IN_TB'",
            "ALTER TABLE t40 SET REPLICATION_AUTO_RESOLVER_MAX_SIZE_LIMIT_IN_TB = '10.0'");
        assertAnswer("Alert AL40 successfully created.",
            "CREATE OR REPLACE ALERT al40 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['10.0'] for parameter "
                + "'INITIAL_REPLICATION_SIZE_LIMIT_IN_TB'",
            "ALTER ALERT al40 SET INITIAL_REPLICATION_SIZE_LIMIT_IN_TB = '10.0'");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void valueKinds() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE t40 (a INT)");
        engine.execute("CREATE OR REPLACE ALERT al40 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on table 'T40'.",
            "ALTER TABLE t40 SET INITIAL_REPLICATION_SIZE_LIMIT_IN_TB = 10.0");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['abc'] for parameter "
                + "'INITIAL_REPLICATION_SIZE_LIMIT_IN_TB'",
            "ALTER TABLE t40 SET INITIAL_REPLICATION_SIZE_LIMIT_IN_TB = 'abc'");
        assertAnswer("REFUSED SQL compilation error: | invalid value [TRUE] for parameter "
                + "'INITIAL_REPLICATION_SIZE_LIMIT_IN_TB'",
            "ALTER TABLE t40 SET INITIAL_REPLICATION_SIZE_LIMIT_IN_TB = TRUE");
        assertAnswer("REFUSED SQL compilation error: | invalid value [7] for parameter "
                + "'INITIAL_REPLICATION_SIZE_LIMIT_IN_TB'",
            "ALTER TABLE t40 SET INITIAL_REPLICATION_SIZE_LIMIT_IN_TB = 7");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on table 'T40'.",
            "ALTER TABLE t40 SET AUTOCOMMIT_API_SUPPORTED = true");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter 'AUTOCOMMIT_API_SUPPORTED'",
            "ALTER TABLE t40 SET AUTOCOMMIT_API_SUPPORTED = 'x'");
        assertAnswer("REFUSED SQL compilation error: | invalid value [1] for parameter 'AUTOCOMMIT_API_SUPPORTED'",
            "ALTER TABLE t40 SET AUTOCOMMIT_API_SUPPORTED = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['true'] for parameter 'AUTOCOMMIT_API_SUPPORTED'",
            "ALTER ALERT al40 SET AUTOCOMMIT_API_SUPPORTED = 'true'");
        assertAnswer("REFUSED SQL compilation error: | invalid value [5] for parameter 'TIMEZONE'",
            "ALTER TABLE t40 SET TIMEZONE = 5");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on table 'T40'.",
            "ALTER TABLE t40 SET TIMEZONE = 'UTC'");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter "
                + "'ENABLE_UNREDACTED_QUERY_SYNTAX_ERROR'",
            "ALTER TABLE t40 SET ENABLE_UNREDACTED_QUERY_SYNTAX_ERROR = 'x'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on table 'T40'.",
            "ALTER TABLE t40 SET ENABLE_UNREDACTED_QUERY_SYNTAX_ERROR = TRUE");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter "
                + "'STATEMENT_TIMEOUT_IN_SECONDS'",
            "ALTER TABLE t40 SET STATEMENT_TIMEOUT_IN_SECONDS = 'x'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on table 'T40'.",
            "ALTER TABLE t40 SET STATEMENT_TIMEOUT_IN_SECONDS = 60");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter 'DATA_RETENTION_TIME_IN_DAYS'",
            "ALTER TABLE t40 SET DATA_RETENTION_TIME_IN_DAYS = 'x'");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter "
                + "'MAX_DATA_EXTENSION_TIME_IN_DAYS'",
            "ALTER TABLE t40 SET MAX_DATA_EXTENSION_TIME_IN_DAYS = 'x'");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter "
                + "'STATEMENT_TIMEOUT_IN_SECONDS'",
            "ALTER ALERT al40 SET STATEMENT_TIMEOUT_IN_SECONDS = 'x'");
        assertAnswer("REFUSED SQL compilation error: | invalid value [5] for parameter 'LOG_LEVEL'",
            "ALTER ALERT al40 SET LOG_LEVEL = 5");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on alert 'AL40'.",
            "ALTER ALERT al40 SET INITIAL_REPLICATION_SIZE_LIMIT_IN_TB = 10.0");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
