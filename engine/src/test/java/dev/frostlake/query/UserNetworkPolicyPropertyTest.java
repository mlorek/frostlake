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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * NETWORK_POLICY is one of a user's properties: ALTER USER reads it in a SET list among the others, in any
 * place, the properties a comma or a blank apart, and in an UNSET list. A property named twice is a duplicate,
 * a number and a BOOLEAN name no policy, and NULL is read; each is judged while the statement compiles, before
 * the user is looked up, so IF EXISTS forgives a missing user and none of them. Every answer is live-verified
 * over a user that is not there.
 */
public class UserNetworkPolicyPropertyTest extends MeasuredCellsTestSupport {

    @Test
    public void thePropertyStandsAmongTheOthers() {
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 SET NETWORK_POLICY = 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 SET NETWORK_POLICY = x");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 SET NETWORK_POLICY = x COMMENT = 'c'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 SET COMMENT = 'c' NETWORK_POLICY = x");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 SET COMMENT = 'c', NETWORK_POLICY = x");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 UNSET NETWORK_POLICY");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 UNSET NETWORK_POLICY, COMMENT");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 UNSET COMMENT, NETWORK_POLICY");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER USER IF EXISTS nosuch_user_p1308 SET NETWORK_POLICY = x");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "SHOW PARAMETERS LIKE 'NETWORK_POLICY' IN USER nosuch_user_p1308");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'NETWORK_POLICY'",
                "ALTER USER nosuch_user_p1308 SET NETWORK_POLICY = 5");
            assertAnswer(
                "",
                "SHOW PARAMETERS LIKE 'NETWORK_POLICY'");
            assertAnswer(
                "REFUSED SQL compilation error: | Network policy 'NOSUCH_POLICY_P1308' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on NETWORK "
                    + "POLICY NOSUCH_POLICY_P1308.",
                "DESCRIBE NETWORK POLICY nosuch_policy_p1308");
            assertAnswer(
                "",
                "SHOW NETWORK POLICIES LIKE 'nosuch_policy_p1308'");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void listsCommasAndValues() {
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 SET COMMENT = 'c', FIRST_NAME = 'f'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 SET COMMENT = 'c' FIRST_NAME = 'f'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'COMMENT'.",
                "ALTER USER nosuch_user_p1308 SET COMMENT = 'c',");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'COMMENT'.",
                "ALTER USER nosuch_user_p1308 SET COMMENT = 'c', , FIRST_NAME = 'f'");
            assertAnswer(
                "REFUSED SQL compilation error: | duplicate property 'NETWORK_POLICY';",
                "ALTER USER nosuch_user_p1308 SET NETWORK_POLICY = x NETWORK_POLICY = y");
            assertAnswer(
                "REFUSED SQL compilation error: | duplicate property 'NETWORK_POLICY';",
                "ALTER USER nosuch_user_p1308 UNSET NETWORK_POLICY, NETWORK_POLICY");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'NETWORK_POLICY'",
                "ALTER USER nosuch_user_p1308 SET COMMENT = 'c' NETWORK_POLICY = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';",
                "ALTER USER nosuch_user_p1308 SET COMMENT = 'c', COMMENT = 'd'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 SET NETWORK_POLICY = 'x', COMMENT = 'c', FIRST_NAME = 'f'");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER USER IF EXISTS nosuch_user_p1308 SET COMMENT = 'c', NETWORK_POLICY = x");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER USER IF EXISTS nosuch_user_p1308 UNSET NETWORK_POLICY, COMMENT");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'NETWORK_POLICY'",
                "ALTER USER IF EXISTS nosuch_user_p1308 SET COMMENT = 'c' NETWORK_POLICY = 5");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid value [TRUE] for parameter 'NETWORK_POLICY'",
                "ALTER USER nosuch_user_p1308 SET NETWORK_POLICY = TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 SET NETWORK_POLICY = NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 SET COMMENT = 'c' NETWORK_POLICY = 'a b'");
            assertAnswer(
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_P1308' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE granted on ACCOUNT <ACCOUNT>.",
                "ALTER USER nosuch_user_p1308 SET NETWORK_POLICY = \"x y\" COMMENT = 'c'");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PROPERTY' for 'USER'",
                "ALTER USER nosuch_user_p1308 UNSET NETWORK_POLICY, NOSUCH_PROPERTY");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid property 'NOSUCH_PROPERTY' for 'USER'",
                "ALTER USER nosuch_user_p1308 UNSET NOSUCH_PROPERTY, NETWORK_POLICY");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
