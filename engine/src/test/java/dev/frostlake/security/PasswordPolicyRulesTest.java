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
 * The password policy a password set by ALTER USER is held to (live-verified): the user's own, else the account's,
 * else the built-in one, of at least 14 characters and at most 256, with a digit, an upper and a lower case letter,
 * and none of the current or last five passwords again. The first unmet rule is named, in a fixed order: MIN_LENGTH,
 * MAX_LENGTH, MIN_DIGITS, MIN_UPPERCASE, MIN_LOWERCASE, MIN_SPECIAL, MIN_AGE, PRIOR_USE. Letters and digits count in
 * ASCII alone, and a special character is ASCII punctuation. A refused password refuses the whole statement. Only a
 * password that replaces one is judged: CREATE USER takes any, as does ALTER USER over a user without a password,
 * and an empty password takes the user's password off.
 *
 * <p>The statements change the account's users, so on the account they run only where the harness sends them
 * (see {@link dev.frostlake.LiveAccountStatements}).
 */
public class PasswordPolicyRulesTest extends MeasuredCellsTestSupport {

    /** What a statement that changes something and answers nothing of its own reads as. */
    private static final String DONE = "Statement executed successfully.";

    /** The properties of a user the third walk reads back after its DESCRIBE USER. */
    private static final String DESCRIBED = """
        SELECT LISTAGG("property" || '=' || "value", ',') WITHIN GROUP (ORDER BY "property") \
        FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "property" IN ('COMMENT', 'DISPLAY_NAME', 'HAS_PASSWORD')""";

    /** An ALTER USER's refusal of a password its policy does not take. */
    private static String rejected(final String reason) {
        return "REFUSED SQL execution error: | New password rejected by current password policy. Reason: '" + reason
            + "'";
    }

    /** An ALTER USER that sets a user's password. */
    private static String password(final String user, final String password) {
        return "ALTER USER " + user + " SET PASSWORD = '" + password + "'";
    }

    @Test
    public void theBuiltInPolicyJudgesAPasswordThatReplacesOne() {
        answer("USE ROLE ACCOUNTADMIN");
        try {
            assertAnswer("User PW_RULES_A successfully created.", "CREATE USER pw_rules_a PASSWORD = 'pass123'");
            assertAnswer(rejected("MIN_LENGTH"), "ALTER USER pw_rules_a SET PASSWORD = 'short'");
            assertAnswer(rejected("MIN_DIGITS"), "ALTER USER pw_rules_a SET PASSWORD = 'abcdefghijklmn'");
            assertAnswer(rejected("MIN_UPPERCASE"), "ALTER USER pw_rules_a SET PASSWORD = 'abcdefghijklm1'");
            assertAnswer(rejected("MIN_LOWERCASE"), "ALTER USER pw_rules_a SET PASSWORD = 'ABCDEFGHIJKLM1'");
            assertAnswer(DONE, "ALTER USER pw_rules_a SET PASSWORD = 'Abcdefghijklm1'");
            assertAnswer(DONE, "ALTER USER pw_rules_a SET PASSWORD = 'Abcdefghijkl1!'");
            assertAnswer(rejected("MIN_LENGTH"), "ALTER USER pw_rules_a SET PASSWORD = 'ab'");
            assertAnswer(rejected("MIN_LENGTH"), "ALTER USER pw_rules_a SET PASSWORD = 'a1'");
            assertAnswer(rejected("MIN_LENGTH"), "ALTER USER pw_rules_a SET PASSWORD = 'Abcd1234!xyz'");
            assertAnswer(rejected("MIN_DIGITS"), "ALTER USER pw_rules_a SET PASSWORD = 'Abcdefghijklmn'");
            assertAnswer(rejected("MIN_UPPERCASE"), "ALTER USER pw_rules_a SET PASSWORD = '12345678901234'");
            assertAnswer(rejected("MAX_LENGTH"), password("pw_rules_a", "A".repeat(255) + "b1"));
            assertAnswer(rejected("MAX_LENGTH"), password("pw_rules_a", "A".repeat(300) + "b1"));
            assertAnswer(DONE, "ALTER USER pw_rules_a SET PASSWORD = 'Abcdefghijklm1 '");
            assertAnswer(rejected("MIN_UPPERCASE"), "ALTER USER pw_rules_a SET PASSWORD = 'abcdefghijklm1!'");
            assertAnswer("User PW_RULES_B successfully created.",
                "CREATE OR REPLACE USER pw_rules_b PASSWORD = 'short'");
            assertAnswer("PW_RULES_B successfully dropped.", "DROP USER IF EXISTS pw_rules_b");
            assertAnswer("User PW_RULES_B successfully created.",
                "CREATE OR REPLACE USER pw_rules_b PASSWORD = 'abcdefghijklmn'");
            assertAnswer("PW_RULES_B successfully dropped.", "DROP USER IF EXISTS pw_rules_b");
        } finally {
            answer("DROP USER IF EXISTS pw_rules_a");
            answer("DROP USER IF EXISTS pw_rules_b");
        }
    }

    @Test
    public void aUsersOwnPolicyTakesOverInTheSameOrder() {
        answer("USE ROLE ACCOUNTADMIN");
        answer("CREATE USER pw_rules_c PASSWORD = 'Abcdefghijklm0'");
        try {
            assertAnswer(rejected("MIN_LENGTH"), "ALTER USER pw_rules_c SET PASSWORD = 'Abcdefghijkl1'");
            assertAnswer(DONE, password("pw_rules_c", "A".repeat(254) + "b1"));
            assertAnswer(rejected("MAX_LENGTH"), password("pw_rules_c", "a".repeat(300)));
            assertAnswer(rejected("MAX_LENGTH"), password("pw_rules_c", "A".repeat(300) + "b"));
            assertAnswer(rejected("MAX_LENGTH"), password("pw_rules_c", "A".repeat(300) + "1"));
            assertAnswer(rejected("MIN_UPPERCASE"), "ALTER USER pw_rules_c SET PASSWORD = '\u00c9bcdefghijklm1'");
            assertAnswer(rejected("MIN_DIGITS"), "ALTER USER pw_rules_c SET PASSWORD = 'Abcdefghijklm\uff11'");
            assertAnswer(rejected("MIN_LOWERCASE"), "ALTER USER pw_rules_c SET PASSWORD = '\u00e9BCDEFGHIJKLM1'");
            assertAnswer(DONE, "ALTER USER pw_rules_c SET PASSWORD = 'Abcdefghijklm1'");
            assertAnswer(DONE, "ALTER USER pw_rules_c SET PASSWORD = 'Abcdefghijklm2'");
            assertAnswer(rejected("PRIOR_USE"), "ALTER USER pw_rules_c SET PASSWORD = 'Abcdefghijklm1'");
            assertAnswer(rejected("PRIOR_USE"), "ALTER USER pw_rules_c SET PASSWORD = 'Abcdefghijklm0'");
            assertAnswer("Password policy 'PW_RULES_P37' created successfully", """
                CREATE OR REPLACE PASSWORD POLICY test_db.test_schema.pw_rules_p37 PASSWORD_MIN_LENGTH = 8 \
                PASSWORD_MIN_UPPER_CASE_CHARS = 0 PASSWORD_MIN_LOWER_CASE_CHARS = 2 PASSWORD_MIN_NUMERIC_CHARS = 2 \
                PASSWORD_MIN_SPECIAL_CHARS = 1 PASSWORD_HISTORY = 0 PASSWORD_MAX_LENGTH = 20""");
            assertAnswer(DONE, "ALTER USER pw_rules_c SET PASSWORD POLICY test_db.test_schema.pw_rules_p37");
            assertAnswer(rejected("MIN_LENGTH"), "ALTER USER pw_rules_c SET PASSWORD = 'abcdefg'");
            assertAnswer(rejected("MIN_DIGITS"), "ALTER USER pw_rules_c SET PASSWORD = 'abcdefgh'");
            assertAnswer(rejected("MIN_DIGITS"), "ALTER USER pw_rules_c SET PASSWORD = 'abcdefg1'");
            assertAnswer(rejected("MIN_SPECIAL"), "ALTER USER pw_rules_c SET PASSWORD = 'abcdef12'");
            assertAnswer(DONE, "ALTER USER pw_rules_c SET PASSWORD = 'abcdef12!'");
            assertAnswer(rejected("MIN_LOWERCASE"), "ALTER USER pw_rules_c SET PASSWORD = 'ABCDEF12!'");
            assertAnswer(rejected("MIN_LOWERCASE"), "ALTER USER pw_rules_c SET PASSWORD = 'aBCDEF12!'");
            assertAnswer(rejected("MIN_SPECIAL"), "ALTER USER pw_rules_c SET PASSWORD = 'abcdef12 '");
            assertAnswer(DONE, "ALTER USER pw_rules_c SET PASSWORD = 'abcdef12_'");
            assertAnswer(rejected("MIN_SPECIAL"), "ALTER USER pw_rules_c SET PASSWORD = 'abcdef12\u00e9'");
            assertAnswer(rejected("MAX_LENGTH"), "ALTER USER pw_rules_c SET PASSWORD = 'abcdef12!xxxxxxxxxxxx'");
            assertAnswer(DONE, "ALTER USER pw_rules_c SET PASSWORD = 'abcdef12!'");
            assertAnswer(rejected("MIN_LENGTH"), "ALTER USER pw_rules_c SET PASSWORD = 'ab!'");
            assertAnswer(rejected("MIN_DIGITS"), "ALTER USER pw_rules_c SET PASSWORD = 'ABCDEFGH'");
            assertAnswer(DONE, "ALTER PASSWORD POLICY test_db.test_schema.pw_rules_p37 SET PASSWORD_HISTORY = 3");
            assertAnswer(rejected("PRIOR_USE"), "ALTER USER pw_rules_c SET PASSWORD = 'abcdef12!'");
            assertAnswer(DONE, "ALTER USER pw_rules_c SET PASSWORD = 'abcdef13!'");
            assertAnswer(rejected("PRIOR_USE"), "ALTER USER pw_rules_c SET PASSWORD = 'abcdef12!'");
            assertAnswer(DONE, "ALTER USER pw_rules_c UNSET PASSWORD POLICY");
            assertAnswer(rejected("MIN_LENGTH"), "ALTER USER pw_rules_c SET PASSWORD = 'short'");
            assertAnswer("User PW_RULES_D successfully created.", "CREATE OR REPLACE USER pw_rules_d PASSWORD = 'x'");
            assertAnswer("PW_RULES_D successfully dropped.", "DROP USER IF EXISTS pw_rules_d");
        } finally {
            answer("ALTER USER IF EXISTS pw_rules_c UNSET PASSWORD POLICY");
            answer("DROP USER IF EXISTS pw_rules_c");
            answer("DROP USER IF EXISTS pw_rules_d");
            answer("DROP PASSWORD POLICY IF EXISTS test_db.test_schema.pw_rules_p37");
        }
    }

    @Test
    public void aRefusedPasswordRefusesTheStatementAndANewOneIsNotJudged() {
        answer("USE ROLE ACCOUNTADMIN");
        answer("CREATE USER pw_rules_e PASSWORD = 'Abcdefghijklm0' COMMENT = 'c0'");
        try {
            assertAnswer(rejected("MIN_LENGTH"), "ALTER USER pw_rules_e SET COMMENT = 'c1' PASSWORD = 'short'");
            engine.executeQuery("DESCRIBE USER pw_rules_e");
            assertAnswer("COMMENT=c0,DISPLAY_NAME=PW_RULES_E", DESCRIBED);
            assertAnswer(rejected("MIN_LENGTH"), "ALTER USER pw_rules_e SET PASSWORD = 'short' DISPLAY_NAME = 'd1'");
            engine.executeQuery("DESCRIBE USER pw_rules_e");
            assertAnswer("COMMENT=c0,DISPLAY_NAME=PW_RULES_E", DESCRIBED);
            assertAnswer(rejected("PRIOR_USE"), "ALTER USER pw_rules_e SET PASSWORD = 'Abcdefghijklm0'");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD = ''");
            engine.executeQuery("DESCRIBE USER pw_rules_e");
            assertAnswer("COMMENT=c0,DISPLAY_NAME=PW_RULES_E", DESCRIBED);
            assertAnswer("Password policy 'PW_RULES_P38' created successfully", """
                CREATE OR REPLACE PASSWORD POLICY test_db.test_schema.pw_rules_p38 PASSWORD_MIN_LENGTH = 8 \
                PASSWORD_MIN_UPPER_CASE_CHARS = 0 PASSWORD_MIN_LOWER_CASE_CHARS = 2 PASSWORD_MIN_NUMERIC_CHARS = 2 \
                PASSWORD_MIN_SPECIAL_CHARS = 1 PASSWORD_HISTORY = 0""");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD POLICY test_db.test_schema.pw_rules_p38");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD = 'ABCDEF12'");
            assertAnswer(rejected("MIN_DIGITS"), "ALTER USER pw_rules_e SET PASSWORD = 'ABCDEFGH!'");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD = 'abcdef12!'");
            assertAnswer(rejected("PRIOR_USE"), "ALTER USER pw_rules_e SET PASSWORD = 'abcdef12!'");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD = 'abcdef12~'");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD = 'abcdef12`'");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD = 'abcdef12\\\\'");
            assertAnswer(rejected("MIN_SPECIAL"), "ALTER USER pw_rules_e SET PASSWORD = 'abcdef12\\t'");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD = 'abcdef12'' '");
            assertAnswer(DONE, "ALTER PASSWORD POLICY test_db.test_schema.pw_rules_p38 SET PASSWORD_MIN_AGE_DAYS = 1");
            assertAnswer(rejected("MIN_AGE"), "ALTER USER pw_rules_e SET PASSWORD = 'abcdef34!'");
            assertAnswer(DONE, "ALTER PASSWORD POLICY test_db.test_schema.pw_rules_p38 SET PASSWORD_MIN_AGE_DAYS = 0 "
                + "PASSWORD_HISTORY = 2");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD = 'abcdef56!'");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD = 'abcdef78!'");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD = 'abcdef90!'");
            assertAnswer(rejected("PRIOR_USE"), "ALTER USER pw_rules_e SET PASSWORD = 'abcdef56!'");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD = 'abcdef12~'");
            assertAnswer(DONE, "ALTER USER pw_rules_e UNSET PASSWORD POLICY");
            assertAnswer(DONE, "ALTER USER pw_rules_e UNSET PASSWORD");
            assertAnswer(DONE, "ALTER USER pw_rules_e SET PASSWORD = 'abcdef90!'");
        } finally {
            answer("ALTER USER IF EXISTS pw_rules_e UNSET PASSWORD POLICY");
            answer("DROP USER IF EXISTS pw_rules_e");
            answer("DROP PASSWORD POLICY IF EXISTS test_db.test_schema.pw_rules_p38");
        }
    }

    @Test
    public void theMinimumAgeIsJudgedAfterTheTextAndBeforeItsHistory() {
        answer("USE ROLE ACCOUNTADMIN");
        answer("CREATE USER pw_rules_f PASSWORD = 'Abcdefghijklm0'");
        try {
            assertAnswer("Password policy 'PW_RULES_P39' created successfully", """
                CREATE OR REPLACE PASSWORD POLICY test_db.test_schema.pw_rules_p39 PASSWORD_MIN_LENGTH = 8 \
                PASSWORD_MIN_UPPER_CASE_CHARS = 0 PASSWORD_MIN_LOWER_CASE_CHARS = 2 PASSWORD_MIN_NUMERIC_CHARS = 2 \
                PASSWORD_MIN_SPECIAL_CHARS = 1 PASSWORD_HISTORY = 3""");
            assertAnswer(DONE, "ALTER USER pw_rules_f SET PASSWORD POLICY test_db.test_schema.pw_rules_p39");
            assertAnswer(rejected("MIN_LOWERCASE"), password("pw_rules_f", "ABCDEF12"));
            assertAnswer(rejected("MIN_LOWERCASE"), password("pw_rules_f", "ABCDEF12!"));
            assertAnswer(rejected("MIN_SPECIAL"), password("pw_rules_f", "abcdef12"));
            assertAnswer(DONE, password("pw_rules_f", "abcdef12!"));
            assertAnswer(DONE, "ALTER PASSWORD POLICY test_db.test_schema.pw_rules_p39 SET PASSWORD_MIN_AGE_DAYS = 1");
            assertAnswer(rejected("MIN_AGE"), password("pw_rules_f", "abcdef12!"));
            assertAnswer(rejected("MIN_SPECIAL"), password("pw_rules_f", "abcdef12"));
            assertAnswer(rejected("MIN_DIGITS"), password("pw_rules_f", "Abcdefghijklm0"));
            assertAnswer(rejected("MIN_AGE"), password("pw_rules_f", "abcdef34!"));
            assertAnswer(DONE, "ALTER PASSWORD POLICY test_db.test_schema.pw_rules_p39 SET PASSWORD_MIN_AGE_DAYS = 0");
            assertAnswer(DONE, "ALTER USER pw_rules_f UNSET PASSWORD POLICY");
        } finally {
            answer("ALTER USER IF EXISTS pw_rules_f UNSET PASSWORD POLICY");
            answer("DROP USER IF EXISTS pw_rules_f");
            answer("DROP PASSWORD POLICY IF EXISTS test_db.test_schema.pw_rules_p39");
        }
    }
}
