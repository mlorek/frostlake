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
package dev.frostlake.session;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The list of an ALTER USER ... SET and of an ALTER ACCOUNT SET is judged whole, before the user is looked up and
 * before anything applies: a name written twice ({@code duplicate property 'X';}), then a name that is no
 * property's and no parameter's ({@code invalid property 'X' for 'USER'}, or {@code for 'ACCOUNT'}, the name as it
 * is written and a quoted one always), then a value of the wrong kind - the parameters before the properties, the
 * NUMBER ones first, then the STRING ones, then the BOOLEAN ones. A value of the right kind that its parameter
 * does not take is refused only once the user is found, so ALTER USER IF EXISTS on a user that is not there
 * answers without it. A user's text property takes a word or NULL; a session parameter stands among the
 * properties with or without commas. Written without a name the statement alters the session's own user, and
 * then takes no IF EXISTS. A list that does not read is refused where it begins: at the first token after the
 * SET for a named user and for the account, at the SET itself for the nameless user. Every statement here is
 * refused, names a user that does not exist, or only reads. Every answer is live-verified.
 */
public class UserAndAccountParameterListsTest extends MeasuredCellsTestSupport {

    @Test
    public void aUsersListIsJudgedBeforeTheUserIsLookedUp() {
        try {
            assertAnswer(
                "QUERY_TAG||||String (up to 2000 characters) used to tag statements executed by the session|STRING",
                "SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'USER'",
                "ALTER USER nosuch_user_x9 SET NOSUCH = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 UNSET QUERY_TAG");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'USER'",
                "ALTER USER nosuch_user_x9 UNSET NOSUCH");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER USER IF EXISTS nosuch_user_x9 SET QUERY_TAG = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
                "ALTER USER IF EXISTS nosuch_user_x9 SET QUERY_TAG = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'USER'",
                "ALTER USER IF EXISTS nosuch_user_x9 SET NOSUCH = 1");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER USER IF EXISTS nosuch_user_x9 UNSET QUERY_TAG");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'USER'",
                "ALTER USER IF EXISTS nosuch_user_x9 UNSET NOSUCH");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'USER'",
                "ALTER USER SET NOSUCH = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'USER'",
                "ALTER USER UNSET NOSUCH");
            assertAnswer(
                "REFUSED SQL compilation error: | duplicate property 'QUERY_TAG';",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = 'a', QUERY_TAG = 'b'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = 'a', query_tag = 'b'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
                "ALTER USER nosuch_user_x9 SET COMMENT = 'c' QUERY_TAG = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'COMMENT'",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = 't', COMMENT = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = 5 COMMENT = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';",
                "ALTER USER nosuch_user_x9 SET COMMENT = 'c', COMMENT = 'd'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET COMMENT = 'c' QUERY_TAG = 't'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = 't' COMMENT = 'c'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET DEFAULT_ROLE = nosuch_role_x9 QUERY_TAG = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET TIMEZONE = 'Nowhere/Land'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'AUTOCOMMIT'",
                "ALTER USER nosuch_user_x9 SET AUTOCOMMIT = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET STATEMENT_TIMEOUT_IN_SECONDS = -1");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property '\"QUERY_TAG\"' for 'USER'",
                "ALTER USER nosuch_user_x9 SET \"QUERY_TAG\" = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property '\"query_tag\"' for 'USER'",
                "ALTER USER nosuch_user_x9 SET \"query_tag\" = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET query_tag = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET ABORT_DETACHED_QUERY = TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET DATA_RETENTION_TIME_IN_DAYS = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET PREVENT_UNLOAD_TO_INLINE_URL = TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET ENABLE_UNREDACTED_QUERY_SYNTAX_ERROR = TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET LOCK_TIMEOUT = 5 QUERY_TAG = 't2'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET LOCK_TIMEOUT = 5, QUERY_TAG = 't2'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = abc");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET LOCK_TIMEOUT = -1");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET MULTI_STATEMENT_COUNT = 0");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET TAG nosuch_tag_x9 = 'v'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'USER'",
                "ALTER USER nosuch_user_x9 UNSET QUERY_TAG, NOSUCH");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'USER'",
                "ALTER USER nosuch_user_x9 UNSET NOSUCH, QUERY_TAG");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 UNSET COMMENT, QUERY_TAG");
            assertAnswer(
                "REFUSED SQL compilation error: | duplicate property 'QUERY_TAG';",
                "ALTER USER nosuch_user_x9 UNSET QUERY_TAG, QUERY_TAG");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'QUERY_TAG'.",
                "ALTER USER nosuch_user_x9 UNSET QUERY_TAG LOCK_TIMEOUT");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property '\"QUERY_TAG\"' for 'USER'",
                "ALTER USER nosuch_user_x9 UNSET \"QUERY_TAG\"");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 UNSET query_tag");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'.",
                "ALTER USER nosuch_user_x9 SET");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'.",
                "ALTER USER nosuch_user_x9 UNSET");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'SET'.",
                "ALTER USER SET");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'.",
                "ALTER USER UNSET");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'.",
                "ALTER USER");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
                "ALTER USER nosuch_user_x9 ");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'QUERY_TAG'.",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'QUERY_TAG'.",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG =");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '='.",
                "ALTER USER nosuch_user_x9 SET = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "SHOW PARAMETERS IN USER nosuch_user_x9");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER nosuch_user_x9");
            assertAnswer(
                "QUERY_TAG||||String (up to 2000 characters) used to tag statements executed by the session|STRING",
                "SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER");
            assertAnswer(
                "QUERY_TAG||||String (up to 2000 characters) used to tag statements executed by the session|STRING",
                "SHOW PARAMETERS LIKE 'QUERY_TAG' FOR USER");
            assertAnswer(
                "QUERY_TAG||||String (up to 2000 characters) used to tag statements executed by the session|STRING",
                "SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER");
        } finally {
            answer(
                "ALTER SESSION UNSET JSON_INDENT");
            answer(
                "ALTER SESSION UNSET WEEK_START");
            answer(
                "ALTER SESSION UNSET LOCK_TIMEOUT");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
        }
    }

    @Test
    public void theAccountsListIsRefusedAsAUsersIs() {
        try {
            assertAnswer(
                "QUERY_TAG||||String (up to 2000 characters) used to tag statements executed by the session|STRING",
                "SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'ACCOUNT'",
                "ALTER ACCOUNT SET NOSUCH = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'ACCOUNT'",
                "ALTER ACCOUNT UNSET NOSUCH");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
                "ALTER ACCOUNT SET");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
                "ALTER ACCOUNT UNSET");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
                "ALTER ACCOUNT");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH' for 'ACCOUNT'",
                "ALTER ACCOUNT UNSET NOSUCH, NOSUCH2");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property '\"nosuch\"' for 'ACCOUNT'",
                "ALTER ACCOUNT UNSET \"nosuch\"");
            assertAnswer(
                "QUERY_TAG||||String (up to 2000 characters) used to tag statements executed by the session|STRING",
                "SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER");
        } finally {
            answer(
                "ALTER SESSION UNSET JSON_INDENT");
            answer(
                "ALTER SESSION UNSET WEEK_START");
            answer(
                "ALTER SESSION UNSET LOCK_TIMEOUT");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
        }
    }

    @Test
    public void theOrderAUsersListIsJudgedIn() {
        try {
            assertAnswer(
                "QUERY_TAG||||String (up to 2000 characters) used to tag statements executed by the session|STRING",
                "SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
                "ALTER USER nosuch_user_x9 SET COMMENT = 5 QUERY_TAG = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = 5 LAST_NAME = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
                "ALTER USER nosuch_user_x9 SET LAST_NAME = 5 QUERY_TAG = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'EMAIL'",
                "ALTER USER nosuch_user_x9 SET EMAIL = 5, COMMENT = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'EMAIL'",
                "ALTER USER nosuch_user_x9 SET COMMENT = 5, EMAIL = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'LOCK_TIMEOUT'",
                "ALTER USER nosuch_user_x9 SET LOCK_TIMEOUT = 'x', COMMENT = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'LOCK_TIMEOUT'",
                "ALTER USER nosuch_user_x9 SET COMMENT = 5, LOCK_TIMEOUT = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'AUTOCOMMIT'",
                "ALTER USER nosuch_user_x9 SET AUTOCOMMIT = 1, COMMENT = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'AUTOCOMMIT'",
                "ALTER USER nosuch_user_x9 SET COMMENT = 5 AUTOCOMMIT = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET COMMENT = abc");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET COMMENT = NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [TRUE] for parameter 'COMMENT'",
                "ALTER USER nosuch_user_x9 SET COMMENT = TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'DISABLED'",
                "ALTER USER nosuch_user_x9 SET DISABLED = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'DISABLED'",
                "ALTER USER nosuch_user_x9 SET DISABLED = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'MUST_CHANGE_PASSWORD'",
                "ALTER USER nosuch_user_x9 SET MUST_CHANGE_PASSWORD = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'PASSWORD'",
                "ALTER USER nosuch_user_x9 SET PASSWORD = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'DEFAULT_ROLE'",
                "ALTER USER nosuch_user_x9 SET DEFAULT_ROLE = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'LOGIN_NAME'",
                "ALTER USER nosuch_user_x9 SET LOGIN_NAME = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'TYPE'",
                "ALTER USER nosuch_user_x9 SET TYPE = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET FIRST_NAME = abc");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET EMAIL = NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | duplicate property 'NOSUCH';",
                "ALTER USER nosuch_user_x9 UNSET NOSUCH, NOSUCH");
            assertAnswer(
                "REFUSED SQL compilation error: | duplicate property 'NOSUCH';",
                "ALTER USER nosuch_user_x9 SET NOSUCH = 1, NOSUCH = 2");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property '\"nosuch\"' for 'USER'",
                "ALTER USER nosuch_user_x9 UNSET \"nosuch\"");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property '\"nosuch\"' for 'USER'",
                "ALTER USER nosuch_user_x9 SET \"nosuch\" = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'nosuch' for 'USER'",
                "ALTER USER nosuch_user_x9 SET nosuch = 1");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'nosuch' for 'USER'",
                "ALTER USER nosuch_user_x9 UNSET nosuch");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'QUERY_TAG'.",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = 5 NOSUCH");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'QUERY_TAG'.",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = 'x',");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ','.",
                "ALTER USER nosuch_user_x9 SET , QUERY_TAG = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = 'x' NETWORK_POLICY = nosuch_policy_x9");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET NETWORK_POLICY = nosuch_policy_x9 QUERY_TAG = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5.5] for parameter 'LOCK_TIMEOUT'",
                "ALTER USER nosuch_user_x9 SET LOCK_TIMEOUT = 5.5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [99999999999] for parameter 'LOCK_TIMEOUT'",
                "ALTER USER nosuch_user_x9 SET LOCK_TIMEOUT = 99999999999");
            assertAnswer(
                "REFUSED SQL compilation error: error line -1 at position -1 | Session variable '$NOSUCH_VAR_X9' "
                    + "does not exist",
                "ALTER USER nosuch_user_x9 SET QUERY_TAG = $nosuch_var_x9");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'TIMEZONE'",
                "ALTER USER nosuch_user_x9 SET TIMEZONE = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET JSON_INDENT = 99");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET DATE_OUTPUT_FORMAT = 'nonsense'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 SET WEEK_START = 9");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'DATA_RETENTION_TIME_IN_DAYS'",
                "ALTER USER nosuch_user_x9 SET DATA_RETENTION_TIME_IN_DAYS = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'PREVENT_UNLOAD_TO_INLINE_URL'",
                "ALTER USER nosuch_user_x9 SET PREVENT_UNLOAD_TO_INLINE_URL = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter "
                    + "'ENABLE_UNREDACTED_QUERY_SYNTAX_ERROR'",
                "ALTER USER nosuch_user_x9 SET ENABLE_UNREDACTED_QUERY_SYNTAX_ERROR = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'NETWORK_POLICY'",
                "ALTER USER nosuch_user_x9 SET NETWORK_POLICY = 5 QUERY_TAG = 5");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER USER IF EXISTS nosuch_user_x9 SET TIMEZONE = 'Nowhere/Land'");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER USER IF EXISTS nosuch_user_x9 SET STATEMENT_TIMEOUT_IN_SECONDS = -1");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG_X9' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG_X9.",
                "ALTER USER IF EXISTS nosuch_user_x9 SET TAG nosuch_tag_x9 = 'v'");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG_X9' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG_X9.",
                "ALTER USER IF EXISTS nosuch_user_x9 UNSET TAG nosuch_tag_x9");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 UNSET TAG nosuch_tag_x9");
            assertAnswer(
                "REFUSED SQL compilation error: | Cannot specify IF EXISTS when no object name is specified",
                "ALTER USER IF EXISTS UNSET NOSUCH");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER USER IF EXISTS nosuch_user_x9 RENAME TO nosuch_user_x9");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_X9' does not exist or not authorized.",
                "ALTER USER nosuch_user_x9 RENAME TO nosuch_user_y9");
            assertAnswer(
                "QUERY_TAG||||String (up to 2000 characters) used to tag statements executed by the session|STRING",
                "SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER");
        } finally {
            answer(
                "ALTER SESSION UNSET JSON_INDENT");
            answer(
                "ALTER SESSION UNSET WEEK_START");
            answer(
                "ALTER SESSION UNSET LOCK_TIMEOUT");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
        }
    }

    @Test
    public void theAccountsEchoesAndASessionsList() {
        try {
            assertAnswer(
                "QUERY_TAG||||String (up to 2000 characters) used to tag statements executed by the session|STRING",
                "SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER");
            assertAnswer(
                "REFUSED SQL compilation error: | duplicate property 'NOSUCH';",
                "ALTER ACCOUNT SET NOSUCH = 5, NOSUCH = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | duplicate property 'NOSUCH';",
                "ALTER ACCOUNT UNSET NOSUCH, NOSUCH");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'nosuch' for 'ACCOUNT'",
                "ALTER ACCOUNT SET nosuch = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'nosuch' for 'ACCOUNT'",
                "ALTER ACCOUNT UNSET nosuch");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property '\"nosuch\"' for 'ACCOUNT'",
                "ALTER ACCOUNT SET \"nosuch\" = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
                "ALTER SESSION SET QUERY_TAG");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'.",
                "ALTER SESSION SET QUERY_TAG =");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '<EOF>'.",
                "ALTER SESSION SET QUERY_TAG = 5 NOSUCH");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
                "ALTER SESSION SET");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
                "ALTER SESSION UNSET");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid parameter 'NOSUCH'",
                "ALTER SESSION SET NOSUCH = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid parameter 'NOSUCH'",
                "ALTER SESSION UNSET NOSUCH");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid parameter '\"QUERY_TAG\"'",
                "ALTER SESSION SET \"QUERY_TAG\" = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | duplicate property 'QUERY_TAG';",
                "ALTER SESSION SET QUERY_TAG = 5, QUERY_TAG = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid parameter 'NOSUCH'",
                "ALTER SESSION SET NOSUCH = 5, QUERY_TAG = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'IF'.",
                "ALTER SESSION IF EXISTS SET QUERY_TAG = 5");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET JSON_INDENT = 99");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [9] for parameter 'WEEK_START'",
                "ALTER SESSION SET WEEK_START = 9");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value ['x'] for parameter 'LOCK_TIMEOUT'",
                "ALTER SESSION SET LOCK_TIMEOUT = 'x'");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET JSON_INDENT");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET WEEK_START");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET LOCK_TIMEOUT");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET QUERY_TAG");
            assertAnswer(
                "QUERY_TAG||||String (up to 2000 characters) used to tag statements executed by the session|STRING",
                "SHOW PARAMETERS LIKE 'QUERY_TAG' IN USER");
        } finally {
            answer(
                "ALTER SESSION UNSET JSON_INDENT");
            answer(
                "ALTER SESSION UNSET WEEK_START");
            answer(
                "ALTER SESSION UNSET LOCK_TIMEOUT");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
        }
    }
}
