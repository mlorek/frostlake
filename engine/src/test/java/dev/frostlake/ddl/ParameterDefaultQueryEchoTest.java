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
 * A routine parameter's default that reads a query is refused and echoed resolved: the operators as written and
 * a bare name its relation's; a table it names that is not there is refused first (all live-verified).
 */
public class ParameterDefaultQueryEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void queryDefaults() {
        engine.execute("CREATE OR REPLACE TABLE tc (i INT)");
        engine.execute("INSERT INTO tc VALUES (1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 50 | invalid default argument "
                + "expression (SELECT MAX(TC.I) AS \"MAX(I)\" FROM TC AS TC)",
            "CREATE OR REPLACE PROCEDURE p11(x NUMBER DEFAULT (SELECT MAX(i) FROM tc)) RETURNS NUMBER LANGUAGE SQL "
                + "AS 'BEGIN RETURN x; END'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 50 | invalid default argument "
                + "expression (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE PROCEDURE p12(x NUMBER DEFAULT (SELECT 1)) RETURNS NUMBER LANGUAGE SQL AS 'BEGIN "
                + "RETURN x; END'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 50 | invalid default argument "
                + "expression (SELECT MAX(TC.I) AS \"MAX(I)\" FROM TEST_DB.TEST_SCHEMA.TC AS TC)",
            "CREATE OR REPLACE PROCEDURE p13(x NUMBER DEFAULT (SELECT MAX(i) FROM test_db.test_schema.tc)) RETURNS "
                + "NUMBER LANGUAGE SQL AS 'BEGIN RETURN x; END'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 49 | invalid default argument "
                + "expression (SELECT MAX(TC.I) AS \"MAX(I)\" FROM TC AS TC)",
            "CREATE OR REPLACE FUNCTION f11(x NUMBER DEFAULT (SELECT MAX(i) FROM tc)) RETURNS NUMBER AS 'x'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 49 | invalid default argument "
                + "expression (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE FUNCTION f12(x NUMBER DEFAULT (SELECT 1)) RETURNS NUMBER AS 'x'");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_TC' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_TC.",
            "CREATE OR REPLACE PROCEDURE p14(x NUMBER DEFAULT (SELECT MAX(i) FROM nosuch_tc)) RETURNS NUMBER "
                + "LANGUAGE SQL AS 'BEGIN RETURN x; END'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 50 | invalid default argument "
                + "expression (SELECT COUNT(*) AS \"COUNT(*)\" FROM TC AS TC WHERE TC.I > 0)",
            "CREATE OR REPLACE PROCEDURE p15(x NUMBER DEFAULT (SELECT COUNT(*) FROM tc WHERE i > 0)) RETURNS NUMBER "
                + "LANGUAGE SQL AS 'BEGIN RETURN x; END'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 49 | invalid default argument "
                + "expression (SELECT MAX(TC.I) AS \"MAX(I)\" FROM TEST_DB.TEST_SCHEMA.TC AS TC)",
            "CREATE OR REPLACE FUNCTION f13(x NUMBER DEFAULT (SELECT MAX(i) FROM test_db.test_schema.tc)) RETURNS "
                + "NUMBER AS 'x'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 50 | invalid default argument "
                + "expression (SELECT 1 + 1 AS \"1 + 1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE PROCEDURE p16(x NUMBER DEFAULT (SELECT 1 + 1)) RETURNS NUMBER LANGUAGE SQL AS 'BEGIN "
                + "RETURN x; END'");
        assertAnswer("REFUSED SQL compilation error: | Object 'NOSUCH_TC' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_TC.",
            "CREATE OR REPLACE FUNCTION f14(x NUMBER DEFAULT (SELECT MAX(i) FROM nosuch_tc)) RETURNS NUMBER AS 'x'");
    }
}
