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

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * ALTER ACCOUNT over a named account, measured with statements that can set nothing and not sent to a real account by
 * this test: under IF EXISTS another account's tags are the organization's to change, refused before the account is
 * looked for; without it the account is the one missing; and of two unknown names in one list the first written is
 * refused (live-verified).
 */
public class AlterAccountTagAndNameTest extends MeasuredCellsTestSupport {

    @Test
    public void aNamedAccount() {
        // ALTER ACCOUNT is not sent to a real account from a test.
        assumeFalse(isLiveSnowflake());
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on account 'NOSUCH_ACCOUNT_ZQ7'. "
                + "Your primary role <ROLE> must have MANAGE ORGANIZATION ACCOUNTS granted on ACCOUNT <ACCOUNT>.",
            "ALTER ACCOUNT IF EXISTS nosuch_account_zq7 SET TAG nosuch_tag_zq7 = 'v'");
        assertAnswer(
            "REFUSED SQL compilation error: | Account 'NOSUCH_ACCOUNT_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "ALTER ACCOUNT nosuch_account_zq7 SET TAG nosuch_tag_zq7 = 'v'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAM_ZQ7' for 'ACCOUNT'",
            "ALTER ACCOUNT nosuch_account_zq7 UNSET NOSUCH_PARAM_ZQ7, NOSUCH_PARAM2_ZQ7");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAM_ZQ7' for 'ACCOUNT'",
            "ALTER ACCOUNT IF EXISTS nosuch_account_zq7 UNSET NOSUCH_PARAM_ZQ7");
    }
}
