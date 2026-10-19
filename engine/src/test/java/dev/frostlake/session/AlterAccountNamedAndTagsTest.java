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
 * ALTER ACCOUNT reads a name after ACCOUNT and the account's tags. A named account's list is judged before the
 * account is looked for, and a fault in it is refused at the SET or the UNSET after the name; the account's own
 * SET TAG and UNSET TAG look their tags up, and a fault in the tags names the word TAG. Every statement here is
 * one that can set nothing: an unknown parameter, a value its parameter refuses, a tag or an account that is
 * not there (live-verified).
 */
public class AlterAccountNamedAndTagsTest extends MeasuredCellsTestSupport {

    @Test
    public void aNamedAccountsListIsJudgedFirst() {
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
            "ALTER ACCOUNT nosuch_account_zq7 SET QUERY_TAG = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG_ZQ7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG_ZQ7.",
            "ALTER ACCOUNT SET TAG nosuch_tag_zq7 = 'v'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAM_ZQ7' for 'ACCOUNT'",
            "ALTER ACCOUNT nosuch_account_zq7 SET NOSUCH_PARAM_ZQ7 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SET'.",
            "ALTER ACCOUNT nosuch_account_zq7 SET QUERY_TAG = 5, x");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAM_ZQ7' for 'ACCOUNT'",
            "ALTER ACCOUNT nosuch_account_zq7 UNSET NOSUCH_PARAM_ZQ7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SET'.",
            "ALTER ACCOUNT nosuch_account_zq7 SET");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "ALTER ACCOUNT nosuch_account_zq7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SET'.",
            "ALTER ACCOUNT nosuch_account_zq7 SET QUERY_TAG");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SET'.",
            "ALTER ACCOUNT nosuch_account_zq7 SET QUERY_TAG = ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'UNSET'.",
            "ALTER ACCOUNT nosuch_account_zq7 UNSET");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
            "ALTER ACCOUNT \"nosuch account zq7\" SET QUERY_TAG = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
            "ALTER ACCOUNT nosuch_a_zq7.nosuch_b_zq7 SET QUERY_TAG = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
            "ALTER ACCOUNT IF EXISTS nosuch_account_zq7 SET QUERY_TAG = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAM_ZQ7' for 'ACCOUNT'",
            "ALTER ACCOUNT nosuch_account_zq7 SET QUERY_TAG = 5 NOSUCH_PARAM_ZQ7 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAM_ZQ7' for 'ACCOUNT'",
            "ALTER ACCOUNT nosuch_account_zq7 SET NOSUCH_PARAM_ZQ7 = 1, QUERY_TAG = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'UNSET'.",
            "ALTER ACCOUNT nosuch_account_zq7 UNSET NOSUCH_PARAM_ZQ7, QUERY_TAG x");
    }

    @Test
    public void theAccountsTags() {
        assertAnswer(
            "REFUSED SQL compilation error: | Account 'NOSUCH_ACCOUNT_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "ALTER ACCOUNT nosuch_account_zq7 SET TAG nosuch_tag_zq7 = 'v'");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG_ZQ7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG_ZQ7.",
            "ALTER ACCOUNT SET TAG nosuch_tag_zq7 = 'v', nosuch_tag2_zq7 = 'w'");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG_ZQ7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG_ZQ7.",
            "ALTER ACCOUNT UNSET TAG nosuch_tag_zq7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TAG'.",
            "ALTER ACCOUNT SET TAG");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TAG'.",
            "ALTER ACCOUNT SET TAG nosuch_tag_zq7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TAG'.",
            "ALTER ACCOUNT SET TAG nosuch_tag_zq7 = ");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'tagValue'",
            "ALTER ACCOUNT SET TAG nosuch_tag_zq7 = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'TAG' for 'ACCOUNT'",
            "ALTER ACCOUNT UNSET TAG");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "ALTER ACCOUNT SET TAG nosuch_db_zq7.s.t = 'v'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'TAG' for 'ACCOUNT'",
            "ALTER ACCOUNT SET TAG = 'v'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
            "ALTER ACCOUNT SET QUERY_TAG = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAM_ZQ7' for 'ACCOUNT'",
            "ALTER ACCOUNT SET NOSUCH_PARAM_ZQ7 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TAG'.",
            "ALTER ACCOUNT SET TAG nosuch_tag_zq7 = 'v' x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SET'.",
            "ALTER ACCOUNT nosuch_account_zq7 SET TAG");
        assertAnswer(
            "REFUSED SQL compilation error: | Account 'NOSUCH_ACCOUNT_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "ALTER ACCOUNT nosuch_account_zq7 UNSET TAG nosuch_tag_zq7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'.",
            "ALTER ACCOUNT nosuch_account_zq7 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TAG'.",
            "ALTER ACCOUNT SET TAG nosuch_tag_zq7 = 'v' QUERY_TAG = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'QUERY_TAG'.",
            "ALTER ACCOUNT SET QUERY_TAG = 5 TAG nosuch_tag_zq7 = 'v'");
    }

    @Test
    public void moreNamesTagsAndIfExists() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SET'.",
            "ALTER ACCOUNT nosuch_account_zq7 SET NOSUCH_PARAM_ZQ7 = 1 x");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
            "ALTER ACCOUNT nosuch_a_zq7.nosuch_b_zq7.nosuch_c_zq7 SET QUERY_TAG = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'tagValue'",
            "ALTER ACCOUNT nosuch_account_zq7 SET TAG nosuch_tag_zq7 = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'SET'.",
            "ALTER ACCOUNT IF EXISTS SET TAG nosuch_tag_zq7 = 'v'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAM_ZQ7' for 'ACCOUNT'",
            "ALTER ACCOUNT IF EXISTS nosuch_account_zq7 UNSET NOSUCH_PARAM_ZQ7");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'TAG' for 'ACCOUNT'",
            "ALTER ACCOUNT nosuch_account_zq7 UNSET TAG");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid property 'TAG' for 'ACCOUNT'",
            "ALTER ACCOUNT nosuch_account_zq7 SET TAG = 'v'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'tagValue'",
            "ALTER ACCOUNT SET TAG nosuch_tag_zq7 = 'v', QUERY_TAG = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG_ZQ7' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG_ZQ7.",
            "ALTER ACCOUNT UNSET TAG nosuch_tag_zq7, nosuch_tag2_zq7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'TAG'.",
            "ALTER ACCOUNT UNSET TAG nosuch_tag_zq7 x");
        assertAnswer(
            "REFUSED SQL compilation error: | Cannot specify IF EXISTS when no object name is specified",
            "ALTER ACCOUNT IF EXISTS SET NOSUCH_PARAM_ZQ7 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '<EOF>'.",
            "ALTER ACCOUNT IF EXISTS nosuch_account_zq7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'SET'.",
            "ALTER ACCOUNT IF EXISTS nosuch_account_zq7 SET");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'.",
            "ALTER ACCOUNT IF EXISTS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "ALTER ACCOUNT");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [5] for parameter 'QUERY_TAG'",
            "ALTER ACCOUNT nosuch_account_zq7 SET QUERY_TAG = 5;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SET'.",
            "ALTER ACCOUNT nosuch_account_zq7 SET TAG nosuch_tag_zq7 = 'v' x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'UNSET'.",
            "ALTER ACCOUNT nosuch_account_zq7 UNSET TAG nosuch_tag_zq7 x");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag '\"nosuch tag zq7\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TAG \"nosuch tag zq7\".",
            "ALTER ACCOUNT SET TAG \"nosuch tag zq7\" = 'v'");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "ALTER ACCOUNT UNSET TAG nosuch_db_zq7.s.t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '5'.",
            "ALTER ACCOUNT 5 SET QUERY_TAG = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ''x''.",
            "ALTER ACCOUNT 'x' SET QUERY_TAG = 5");
    }
}
