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
 * SHOW EXTERNAL VOLUMES reads a scope and lists only the account's (live-verified): IN ACCOUNT lists, IN SCHEMA, IN
 * DATABASE, IN TABLE and IN APPLICATION are {@code Unsupported statement type 'Cannot show objects of type EXTERNAL
 * VOLUME in SCHEMA'.} whatever they name, a bare name is {@code Object does not exist, or operation cannot be
 * performed.}, the LIKE comes before the scope, and TERSE, STARTS WITH, LIMIT and WITH PRIVILEGES are taken.
 */
public class ExternalVolumeShowScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void theScopesTheListingReads() {
        engine.execute("CREATE OR REPLACE TABLE t_ev (a INT)");
        assertCells(new String[][] {
            {"SHOW EXTERNAL VOLUMES IN SCHEMA",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type EXTERNAL "
                + "VOLUME in SCHEMA'."},
            {"SHOW EXTERNAL VOLUMES IN SCHEMA test_schema",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type EXTERNAL "
                + "VOLUME in SCHEMA'."},
            {"SHOW EXTERNAL VOLUMES IN SCHEMA test_db.test_schema",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type EXTERNAL "
                + "VOLUME in SCHEMA'."},
            {"SHOW EXTERNAL VOLUMES IN DATABASE",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type EXTERNAL "
                + "VOLUME in DATABASE'."},
            {"SHOW EXTERNAL VOLUMES IN DATABASE test_db",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type EXTERNAL "
                + "VOLUME in DATABASE'."},
            {"SHOW EXTERNAL VOLUMES IN DATABASE nosuch_db_ev",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type EXTERNAL "
                + "VOLUME in DATABASE'."},
            {"SHOW EXTERNAL VOLUMES LIKE 'nosuch%' IN ACCOUNT", ""},
            {"SHOW EXTERNAL VOLUMES IN ACCOUNT LIKE 'nosuch%'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'LIKE'."},
            {"SHOW EXTERNAL VOLUMES LIKE 'nosuch%' IN SCHEMA",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type EXTERNAL "
                + "VOLUME in SCHEMA'."},
            {"SHOW EXTERNAL VOLUMES IN test_schema",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW EXTERNAL VOLUMES IN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'."},
            {"SHOW EXTERNAL VOLUMES LIKE 'nosuch%' STARTS WITH 'N'", ""},
            {"SHOW EXTERNAL VOLUMES LIKE 'nosuch%' LIMIT 1", ""},
            {"SHOW EXTERNAL VOLUMES IN TABLE t_ev",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type EXTERNAL "
                + "VOLUME in TABLE'."},
            {"SHOW EXTERNAL VOLUMES LIKE 'nosuch%' IN APPLICATION a_ev",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type EXTERNAL "
                + "VOLUME in APPLICATION'."},
            {"SHOW EXTERNAL VOLUMES LIKE 'nosuch%'", ""},
            {"SHOW EXTERNAL VOLUMES IN ACCOUNT IN ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'IN'."},
            {"SHOW TERSE EXTERNAL VOLUMES LIKE 'nosuch%'", ""},
        });
    }

    @Test
    public void itsColumnsAndModifiers() {
        assertShape("name:VARCHAR(16777216), allow_writes:VARCHAR(16777216), comment:VARCHAR(16777216)",
            "SHOW EXTERNAL VOLUMES LIKE 'nosuch%'");
        assertShape("name:VARCHAR(16777216), allow_writes:VARCHAR(16777216), comment:VARCHAR(16777216)",
            "SHOW TERSE EXTERNAL VOLUMES LIKE 'nosuch%'");
        assertCells(new String[][] {
            {"SHOW EXTERNAL VOLUMES LIKE 'nosuch%' WITH PRIVILEGES USAGE",
                "REFUSED Unsupported feature 'SHOW EXTERNAL_VOLUMES ... WITH PRIVILEGES <>'."},
            {"SHOW EXTERNAL VOLUMES LIKE 'nosuch%' LIMIT 1 FROM 'x'", ""},
            {"SHOW EXTERNAL VOLUMES IN ACCOUNT LIMIT 0",
                "REFUSED page size \"0\" must be greater than 0 in limit clause"},
            {"SHOW EXTERNAL VOLUMES LIKE 'nosuch%' IN ACCOUNT STARTS WITH 'N' LIMIT 2", ""},
        });
    }
}
