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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A contact's USERS are kept (live-verified): SHOW CONTACTS counts them and lists them in brackets, each once and by
 * the name the account holds, an empty list or UNSET clearing them. A contact is reached by one of its users, its
 * address or its URL; a list given for the address or the URL is refused as an invalid value, and a statement's
 * faults are named in one order whatever order its properties are written in.
 */
public class ContactUsersTest extends MeasuredCellsTestSupport {

    @Test
    public void usersAreKeptAndListed() {
        assertCells(new String[][] {
            {"BEGIN LET s VARCHAR := 'CREATE OR REPLACE CONTACT ct936 USERS = (''' || CURRENT_USER() || ''')'; "
                + "EXECUTE IMMEDIATE :s; RETURN 'ok'; END;",
                "ok"},
        });
        engine.execute("SHOW CONTACTS LIKE 'CT936'");
        assertCells(new String[][] {
            {"SELECT \"entries_in_users\", REPLACE(\"users\", CURRENT_USER(), 'ME') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "1|[ME]"},
            {"BEGIN LET s VARCHAR := 'CREATE OR REPLACE CONTACT ct936b USERS = (''' || CURRENT_USER() || ''') "
                + "EMAIL_DISTRIBUTION_LIST = (''a@example.com'')'; EXECUTE IMMEDIATE :s; RETURN 'ok'; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 144 : Invalid value "
                + "specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"BEGIN LET s VARCHAR := 'CREATE OR REPLACE CONTACT ct936c USERS = (''' || CURRENT_USER() || ''', "
                + "''' || CURRENT_USER() || ''')'; EXECUTE IMMEDIATE :s; RETURN 'ok'; END;",
                "ok"},
        });
        engine.execute("SHOW CONTACTS LIKE 'CT936C'");
        assertCells(new String[][] {
            {"SELECT \"entries_in_users\", REPLACE(\"users\", CURRENT_USER(), 'ME') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "1|[ME]"},
            {"BEGIN LET s VARCHAR := 'ALTER CONTACT ct936 SET USERS = (''' || LOWER(CURRENT_USER()) || ''')'; "
                + "EXECUTE IMMEDIATE :s; RETURN 'ok'; END;",
                "ok"},
        });
        engine.execute("SHOW CONTACTS LIKE 'CT936'");
        assertCells(new String[][] {
            {"SELECT \"entries_in_users\", REPLACE(\"users\", CURRENT_USER(), 'ME') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "1|[ME]"},
            {"BEGIN LET s VARCHAR := 'ALTER CONTACT ct936 SET USERS = (''' || CURRENT_USER() || ''', "
                + "''NOSUCH_USER_936'')'; EXECUTE IMMEDIATE :s; RETURN 'ok'; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 110 : SQL compilation "
                + "error: | invalid value 'NOSUCH_USER_936' for property 'USER'"},
            {"BEGIN LET s VARCHAR := 'CREATE OR REPLACE CONTACT ct936d USERS = (''' || CURRENT_USER() || ''') "
                + "URL = ''https://example.com'''; EXECUTE IMMEDIATE :s; RETURN 'ok'; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 128 : Email, url or "
                + "users/email_list, only one could be set."},
            {"ALTER CONTACT ct936 SET USERS = ()", "Statement executed successfully."},
        });
        engine.execute("SHOW CONTACTS LIKE 'CT936'");
        assertCells(new String[][] {
            {"SELECT \"entries_in_users\", REPLACE(\"users\", CURRENT_USER(), 'ME') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "0|null"},
        });
        engine.execute("SHOW CONTACTS LIKE 'CT936%'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"entries_in_users\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "CT936|0 ;; CT936C|1"},
        });
    }

    @Test
    public void usersBesideTheOtherWays() {
        assertCells(new String[][] {
            {"BEGIN LET s VARCHAR := 'CREATE OR REPLACE CONTACT ct937 EMAIL_DISTRIBUTION_LIST = "
                + "(''a@example.com'') USERS = (''' || CURRENT_USER() || ''')'; EXECUTE IMMEDIATE :s; RETURN 'ok'; "
                + "END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 143 : Invalid value "
                + "specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"BEGIN LET s VARCHAR := 'CREATE OR REPLACE CONTACT ct937 EMAIL_DISTRIBUTION_LIST = "
                + "''a@example.com'' USERS = (''' || CURRENT_USER() || ''')'; EXECUTE IMMEDIATE :s; RETURN 'ok'; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 141 : Email, url or "
                + "users/email_list, only one could be set."},
            {"BEGIN LET s VARCHAR := 'CREATE OR REPLACE CONTACT ct937 USERS = (''' || CURRENT_USER() || ''') "
                + "EMAIL_DISTRIBUTION_LIST = ''a@example.com'''; EXECUTE IMMEDIATE :s; RETURN 'ok'; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 141 : Email, url or "
                + "users/email_list, only one could be set."},
            {"BEGIN LET s VARCHAR := 'CREATE OR REPLACE CONTACT ct937 USERS = (''' || CURRENT_USER() || ''') "
                + "COMMENT = ''c'''; EXECUTE IMMEDIATE :s; RETURN 'ok'; END;",
                "ok"},
        });
        engine.execute("SHOW CONTACTS LIKE 'CT937'");
        assertCells(new String[][] {
            {"SELECT \"entries_in_users\", REPLACE(\"users\", CURRENT_USER(), 'ME'), \"url\", "
                + "\"email_distribution_list\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "1|[ME]|null|null"},
            {"ALTER CONTACT ct937 SET EMAIL_DISTRIBUTION_LIST = ('a@example.com')",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"ALTER CONTACT ct937 SET EMAIL_DISTRIBUTION_LIST = 'a@example.com'",
                "REFUSED Email, url or users/email_list, only one could be set."},
            {"ALTER CONTACT ct937 SET URL = 'https://example.com'",
                "REFUSED Email, url or users/email_list, only one could be set."},
            {"ALTER CONTACT ct937 SET URL = ''", "Statement executed successfully."},
        });
        engine.execute("SHOW CONTACTS LIKE 'CT937'");
        assertCells(new String[][] {
            {"SELECT \"entries_in_users\", REPLACE(\"users\", CURRENT_USER(), 'ME'), \"url\", "
                + "\"email_distribution_list\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "1|[ME]||null"},
            {"ALTER CONTACT ct937 UNSET USERS", "Statement executed successfully."},
        });
        engine.execute("SHOW CONTACTS LIKE 'CT937'");
        assertCells(new String[][] {
            {"SELECT \"entries_in_users\", REPLACE(\"users\", CURRENT_USER(), 'ME'), \"url\", "
                + "\"email_distribution_list\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "0|null||null"},
            {"BEGIN LET s VARCHAR := 'ALTER CONTACT ct937 SET URL = ''https://example.com'' USERS = (''' || "
                + "CURRENT_USER() || ''')'; EXECUTE IMMEDIATE :s; RETURN 'ok'; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 119 : Email, url or "
                + "users/email_list, only one could be set."},
            {"BEGIN LET s VARCHAR := 'ALTER CONTACT ct937 SET USERS = (''' || CURRENT_USER() || ''') URL = "
                + "'''''; EXECUTE IMMEDIATE :s; RETURN 'ok'; END;",
                "ok"},
        });
        engine.execute("SHOW CONTACTS LIKE 'CT937'");
        assertCells(new String[][] {
            {"SELECT \"entries_in_users\", REPLACE(\"users\", CURRENT_USER(), 'ME'), \"url\", "
                + "\"email_distribution_list\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "1|[ME]||null"},
            {"CREATE OR REPLACE CONTACT ct937b EMAIL_DISTRIBUTION_LIST = ('a@example.com') URL = 'https://example.com'",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"CREATE OR REPLACE CONTACT ct937b USERS = () URL = 'https://example.com'",
                "Contact CT937B successfully created."},
        });
        engine.execute("SHOW CONTACTS LIKE 'CT937B'");
        assertCells(new String[][] {
            {"SELECT \"entries_in_users\", REPLACE(\"users\", CURRENT_USER(), 'ME'), \"url\", "
                + "\"email_distribution_list\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "0|null|https://example.com|null"},
        });
    }

    @Test
    public void aListForTheAddressOrTheUrl() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE CONTACT ct938 EMAIL_DISTRIBUTION_LIST = ('a@example.com')",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"CREATE OR REPLACE CONTACT ct938 EMAIL_DISTRIBUTION_LIST = ()",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"CREATE OR REPLACE CONTACT ct938 EMAIL_DISTRIBUTION_LIST = ('a@example.com') COMMENT = ('x')",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"CREATE OR REPLACE CONTACT ct938 EMAIL_DISTRIBUTION_LIST = ('a@example.com') USERS = ('NOSUCH_USER_938')",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"CREATE OR REPLACE CONTACT ct938 EMAIL_DISTRIBUTION_LIST = ('not an email')",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"CREATE OR REPLACE CONTACT ct938 URL = ('https://example.com')",
                "REFUSED Invalid value specified for property 'URL'"},
            {"CREATE OR REPLACE CONTACT ct938 EMAIL_DISTRIBUTION_LIST = ('a@example.com') URL = 'not a url'",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"CREATE OR REPLACE CONTACT ct938 EMAIL_DISTRIBUTION_LIST = 'bad' URL = ('x')",
                "REFUSED Invalid value specified for property 'URL'"},
            {"CREATE OR REPLACE CONTACT ct938 EMAIL_DISTRIBUTION_LIST = 'bad' COMMENT = ('x')",
                "REFUSED SQL compilation error: | invalid type of property '[x]' for 'COMMENT'"},
            {"CREATE OR REPLACE CONTACT ct938 EMAIL_DISTRIBUTION_LIST = ('a@example.com') FOO = 'x'",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'CONTACT'"},
            {"CREATE OR REPLACE CONTACT ct938 COMMENT = 'c'", "Contact CT938 successfully created."},
            {"ALTER CONTACT ct938 SET EMAIL_DISTRIBUTION_LIST = ('a@example.com', 'b@example.com')",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"ALTER CONTACT ct938 SET EMAIL_DISTRIBUTION_LIST = ('a@example.com') COMMENT = ('x')",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"ALTER CONTACT ct938 SET URL = ('https://example.com')",
                "REFUSED Invalid value specified for property 'URL'"},
        });
    }

    @Test
    public void theFaultNamedFirst() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE CONTACT ct939 URL = 'not a url' EMAIL_DISTRIBUTION_LIST = ('a@example.com')",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"CREATE OR REPLACE CONTACT ct939 USERS = ('NOSUCH_USER_939') EMAIL_DISTRIBUTION_LIST = ('a@example.com')",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"CREATE OR REPLACE CONTACT ct939 USERS = ('NOSUCH_USER_939') COMMENT = ('x')",
                "REFUSED SQL compilation error: | invalid value 'NOSUCH_USER_939' for property 'USER'"},
            {"CREATE OR REPLACE CONTACT ct939 URL = 'not a url' COMMENT = ('x')",
                "REFUSED SQL compilation error: | invalid type of property '[x]' for 'COMMENT'"},
            {"CREATE OR REPLACE CONTACT ct939 EMAIL_DISTRIBUTION_LIST = 'bad' URL = 'not a url'",
                "REFUSED Invalid email address(es): [bad]."},
            {"CREATE OR REPLACE CONTACT ct939 URL = 'not a url' EMAIL_DISTRIBUTION_LIST = 'bad'",
                "REFUSED Invalid email address(es): [bad]."},
            {"CREATE OR REPLACE CONTACT ct939 USERS = ('NOSUCH_USER_939') URL = 'not a url'",
                "REFUSED SQL compilation error: | invalid value 'NOSUCH_USER_939' for property 'USER'"},
            {"CREATE OR REPLACE CONTACT ct939 URL = 'not a url' USERS = ('NOSUCH_USER_939')",
                "REFUSED SQL compilation error: | invalid value 'NOSUCH_USER_939' for property 'USER'"},
            {"CREATE OR REPLACE CONTACT ct939 EMAIL_DISTRIBUTION_LIST = 'bad' USERS = ('NOSUCH_USER_939')",
                "REFUSED SQL compilation error: | invalid value 'NOSUCH_USER_939' for property 'USER'"},
            {"CREATE OR REPLACE CONTACT ct939 COMMENT = ('x') FOO = 'y'",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'CONTACT'"},
            {"CREATE OR REPLACE CONTACT ct939 USERS = 'x' FOO = 'y'",
                "REFUSED SQL compilation error: | invalid property 'FOO' for 'CONTACT'"},
            {"CREATE OR REPLACE CONTACT ct939 EMAIL_DISTRIBUTION_LIST = 'bad' URL = 'https://example.com'",
                "REFUSED Invalid email address(es): [bad]."},
            {"CREATE OR REPLACE CONTACT ct939 URL = 'https://example.com' EMAIL_DISTRIBUTION_LIST = 'bad'",
                "REFUSED Invalid email address(es): [bad]."},
            {"CREATE OR REPLACE CONTACT ct939 COMMENT = ('x') USERS = 'y'",
                "REFUSED SQL compilation error: | invalid type of property ''y'' for 'USERS'"},
            {"CREATE OR REPLACE CONTACT ct939 USERS = 'y' COMMENT = ('x')",
                "REFUSED SQL compilation error: | invalid type of property ''y'' for 'USERS'"},
            {"CREATE OR REPLACE CONTACT ct939 URL = ('x') COMMENT = ('y')",
                "REFUSED Invalid value specified for property 'URL'"},
            {"CREATE OR REPLACE CONTACT ct939 COMMENT = ('y') URL = ('x')",
                "REFUSED Invalid value specified for property 'URL'"},
        });
    }

    @Test
    public void typesBeforeValues() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE CONTACT ct940 USERS = 'y' URL = ('x')",
                "REFUSED SQL compilation error: | invalid type of property ''y'' for 'USERS'"},
            {"CREATE OR REPLACE CONTACT ct940 URL = ('x') USERS = 'y'",
                "REFUSED SQL compilation error: | invalid type of property ''y'' for 'USERS'"},
            {"CREATE OR REPLACE CONTACT ct940 URL = ('x') EMAIL_DISTRIBUTION_LIST = ('a@example.com')",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"CREATE OR REPLACE CONTACT ct940 EMAIL_DISTRIBUTION_LIST = ('a@example.com') URL = ('x')",
                "REFUSED Invalid value specified for property 'EMAIL_DISTRIBUTION_LIST'"},
            {"CREATE OR REPLACE CONTACT ct940 USERS = ('NOSUCH_USER_940') URL = ('x')",
                "REFUSED Invalid value specified for property 'URL'"},
            {"CREATE OR REPLACE CONTACT ct940 COMMENT = ('x') URL = 'not a url' EMAIL_DISTRIBUTION_LIST = 'bad'",
                "REFUSED SQL compilation error: | invalid type of property '[x]' for 'COMMENT'"},
        });
    }
}
