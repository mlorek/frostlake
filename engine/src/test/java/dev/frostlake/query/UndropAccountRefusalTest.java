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
 * UNDROP ACCOUNT of an account that is not there is refused as an account the role may not operate on, by the
 * organization privilege it would need on the current account; a qualified name is refused for its qualifier,
 * and the statement's own faults where they stand. Every answer is live-verified.
 */
public class UndropAccountRefusalTest extends MeasuredCellsTestSupport {

    @Test
    public void anAccountThatIsNotThereIsNotTheRolesToRestore() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on account 'NOSUCH_ACCT_W7'. "
                + "Your primary role <ROLE> must have MANAGE ORGANIZATION ACCOUNTS granted on ACCOUNT <ACCOUNT>.",
            "UNDROP ACCOUNT nosuch_acct_w7");
        assertAnswer(
            "REFUSED SQL access control error: | Insufficient privileges to operate on account 'nosuch_acct_w7'. "
                + "Your primary role <ROLE> must have MANAGE ORGANIZATION ACCOUNTS granted on ACCOUNT <ACCOUNT>.",
            "UNDROP ACCOUNT \"nosuch_acct_w7\"");
        assertAnswer(
            "REFUSED Cannot specify qualifier for account.",
            "UNDROP ACCOUNT nosuch_s.nosuch_acct_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '<EOF>'.",
            "UNDROP ACCOUNT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
            "UNDROP ACCOUNT nosuch_acct_w7 x");
    }
}
