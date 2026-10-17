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
 * SHOW NETWORK POLICIES reads a name after IN ACCOUNT, refused as another account's ({@code Account 'X' does not
 * exist or not authorized.}), where the other security listings read it as a path that does not start from a
 * database. SHOW NETWORK RULES and SHOW SECRETS read ON: after ON ACCOUNT every token is refused, the statement's end
 * too, a rule is shown on no name ({@code Object does not exist, or operation cannot be performed.}) and a secret
 * listing looks its name up as a table (live-verified).
 */
public class SecurityListingHolderTest extends MeasuredCellsTestSupport {

    @Test
    public void aScopeAndAHolderAreReadAsLiveReadsThem() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertCells(new String[][] {
            {"SHOW NETWORK POLICIES LIMIT 1", ""},
            {"SHOW NETWORK POLICIES STARTS WITH 'x'", ""},
            {"SHOW NETWORK POLICIES LIMIT 1 FROM 'x'", ""},
            {"SHOW NETWORK POLICIES LIKE 'ZQ7%' LIMIT 1", ""},
            {"SHOW NETWORK POLICIES IN ACCOUNT LIMIT 1", ""},
            {"SHOW NETWORK POLICIES IN ACCOUNT STARTS WITH 'x'", ""},
            {"SHOW NETWORK POLICIES IN TABLE t LIMIT 1",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in TABLE'."},
            {"SHOW NETWORK POLICIES IN TABLE t",
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type NETWORK "
                + "POLICY in TABLE'."},
            {"SHOW NETWORK POLICIES IN ACCOUNT LIMIT 1 FROM 'x'", ""},
            {"SHOW NETWORK POLICIES STARTS WITH 'x' LIMIT 1", ""},
            {"SHOW NETWORK POLICIES LIKE 'ZQ7%' IN ACCOUNT x",
                "REFUSED SQL compilation error: | Account 'X' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>."},
            {"SHOW NETWORK POLICIES IN ACCOUNT x",
                "REFUSED SQL compilation error: | Account 'X' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>."},
            {"SHOW NETWORK POLICIES IN ACCOUNT x LIMIT 1",
                "REFUSED SQL compilation error: | Account 'X' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>."},
            {"SHOW NETWORK POLICIES IN ACCOUNT \"x\"",
                "REFUSED SQL compilation error: | Account '\"x\"' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>."},
            {"SHOW NETWORK POLICIES IN ACCOUNT x.y",
                "REFUSED SQL compilation error: | Account 'Y' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>."},
            {"SHOW NETWORK RULES ON ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'."},
            {"SHOW NETWORK RULES ON ACCOUNT x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'."},
            {"SHOW NETWORK RULES ON x",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW NETWORK RULES ON USER u",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'u'."},
            {"SHOW NETWORK RULES ON",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'."},
            {"SHOW SECRETS ON ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'."},
            {"SHOW SECRETS ON ACCOUNT x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'."},
            {"SHOW SECRETS ON x",
                "REFUSED SQL compilation error: | Table 'X' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE X."},
            {"SHOW PASSWORD POLICIES ON ACCOUNT", "null|BUILT-IN|null|null|PASSWORD_POLICY|null||null||SYSTEM"},
            {"SHOW NETWORK RULES IN ACCOUNT ON ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'ON'."},
            {"SHOW SECRETS LIKE 'x' ON ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'."},
        });
    }

    @Test
    public void aHolderNameIsLookedUp() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertCells(new String[][] {
            {"SHOW NETWORK RULES ON t",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW SECRETS ON TABLE t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'TABLE'."},
            {"SHOW NETWORK RULES ON TABLE t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'TABLE'."},
            {"SHOW SECRETS ON ACCOUNT;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ';'."},
            {"SHOW NETWORK RULES IN ACCOUNT x",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X"},
            {"SHOW SECRETS IN ACCOUNT x",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X"},
            {"SHOW PASSWORD POLICIES IN ACCOUNT x",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X"},
            {"SHOW NETWORK RULES ON nosuch_db.s.t",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW SECRETS ON nosuch_db.s.t",
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB."},
            {"SHOW SECRETS ON test_db.test_schema.nosuch",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH."},
            {"SHOW NETWORK RULES ON USER",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW SECRETS ON USER u",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'u'."},
            {"SHOW SECRETS ON \"x\"",
                "REFUSED SQL compilation error: | Table '\"x\"' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE \"x\"."},
            {"SHOW SECRETS ON ACCOUNT LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'LIMIT'."},
            {"SHOW NETWORK RULES ON ACCOUNT LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'LIMIT'."},
            {"SHOW NETWORK RULES ON t LIMIT 1",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW SECRETS ON t x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'."},
            {"SHOW NETWORK RULES ON t x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'."},
            {"SHOW NETWORK RULES LIKE 'x' ON nosuch",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW SECRETS ON DATABASE test_db",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'test_db'."},
            {"SHOW NETWORK RULES ON SCHEMA s",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 's'."},
            {"SHOW NETWORK POLICIES IN ACCOUNT x STARTS WITH 'a'",
                "REFUSED SQL compilation error: | Account 'X' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>."},
            {"SHOW NETWORK POLICIES IN ACCOUNT test_db",
                "REFUSED SQL compilation error: | Account 'TEST_DB' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>."},
        });
    }
}
