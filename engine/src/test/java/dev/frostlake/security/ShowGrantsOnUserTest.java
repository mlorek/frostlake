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
 * SHOW GRANTS ON USER lists the grants on a user, an object of the account, by its name - bare, quoted or through
 * IDENTIFIER() - with the listing's nine columns: the session's own user shows its OWNERSHIP, held by the role that
 * made it. A user that does not exist is refused as no user, a qualified name as no object, and the word USER alone
 * names a table. A signature after the name SHOW GRANTS ON or TO reads is refused inside its parentheses, as a DROP's
 * is. Every answer is live-verified; the user and the role are read back by their places, so that no account's names
 * are written here.
 */
public class ShowGrantsOnUserTest extends MeasuredCellsTestSupport {

    private static final String MASKED = "SELECT \"privilege\", \"granted_on\", IFF(\"name\" = CURRENT_USER(), 'ME', "
        + "\"name\"), \"granted_to\", IFF(\"grantee_name\" = CURRENT_ROLE(), 'MYROLE', \"grantee_name\"), "
        + "\"grant_option\", IFF(\"granted_by\" = CURRENT_ROLE(), 'MYROLE', \"granted_by\") "
        + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1, 4, 5";

    @Test
    public void theSessionsOwnUserIsListedByEachSpellingOfItsName() {
        engine.execute("SET q961 = 'SHOW GRANTS ON USER \"' || CURRENT_USER() || '\"'");
        engine.execute("SET q961b = 'SHOW GRANTS ON USER ' || CURRENT_USER()");
        engine.execute("SET q961c = 'SHOW GRANTS ON USER IDENTIFIER(''\"' || CURRENT_USER() || '\"'')'");
        engine.execute("EXECUTE IMMEDIATE $q961");
        assertAnswer("OWNERSHIP|USER|ME|ROLE|MYROLE|true|MYROLE", MASKED);
        engine.execute("EXECUTE IMMEDIATE $q961");
        assertAnswer("created_on,grant_option,granted_by,granted_by_role_type,granted_on,granted_to,grantee_name,"
            + "name,privilege", "SELECT ARRAY_TO_STRING(OBJECT_KEYS(OBJECT_CONSTRUCT_KEEP_NULL(*)), ',') "
            + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) LIMIT 1");
        engine.execute("EXECUTE IMMEDIATE $q961b");
        assertAnswer("OWNERSHIP|USER|ME|ROLE|MYROLE|true|MYROLE", MASKED);
        engine.execute("EXECUTE IMMEDIATE $q961c");
        assertAnswer("OWNERSHIP|USER|ME|ROLE|MYROLE|true|MYROLE", MASKED);
    }

    @Test
    public void aUserThatDoesNotExistIsNoUser() {
        assertCells(new String[][] {
            {"SHOW GRANTS ON USER nosuch_user_w9",
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_W9' does not exist or not authorized."},
            {"SHOW GRANTS ON USER \"nosuch_user_w9\"",
                "REFUSED SQL compilation error: | User '\"nosuch_user_w9\"' does not exist or not authorized."},
            {"SHOW GRANTS ON USER IDENTIFIER('nosuch_user_w9')",
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_W9' does not exist or not authorized."},
            {"SHOW GRANTS ON USER nosuch_user_w9 LIMIT 1",
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_W9' does not exist or not authorized."},
            {"SHOW GRANTS ON USER nosuch_db.nosuch_user_w9",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW GRANTS ON USER nosuch_user_w9 x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'x'."},
            {"SHOW GRANTS ON USER",
                "REFUSED SQL compilation error: | Object 'USER' does not exist or not authorized. Your primary role "
                    + "<ROLE> must have at least one privilege granted on TABLE USER."},
        });
    }

    @Test
    public void aSignatureIsRefusedInsideItsParentheses() {
        assertCells(new String[][] {
            {"SHOW GRANTS ON DATABASE nosuch_db_w9(1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'."},
            {"SHOW GRANTS ON SCHEMA nosuch_s_w9(1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'."},
            {"SHOW GRANTS ON TABLE nosuch_t_w9(1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'."},
            {"SHOW GRANTS TO ROLE nosuch_role_w9(1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'."},
            {"SHOW GRANTS ON DATABASE nosuch_db_w9()",
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB_W9' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_W9."},
            {"SHOW GRANTS ON TABLE nosuch_t_w9(INT)",
                "REFUSED SQL compilation error: | Table 'NOSUCH_T_W9' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T_W9."},
        });
    }
}
