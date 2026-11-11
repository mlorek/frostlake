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
 * A user's policy bypasses - MINS_TO_BYPASS_NETWORK_POLICY, MINS_TO_BYPASS_SESSION_POLICY and
 * MINS_TO_BYPASS_INGRESS_POLICY - are the account administrators' to set: ALTER USER reads each, in a SET list and in
 * an UNSET list, and refuses it for want of privilege on the user it names once the user is found (live-verified).
 */
public class UserPolicyBypassTest extends MeasuredCellsTestSupport {

    @Test
    public void aBypassIsRefusedForWantOfPrivilege() {
        try {
            assertAnswer("User FLQ_BYPASS_U successfully created.", "CREATE OR REPLACE USER flq_bypass_u");
            final String refused = "REFUSED SQL access control error: | Insufficient privileges to operate on user "
                + "'FLQ_BYPASS_U'.";
            assertAnswer(refused, "ALTER USER flq_bypass_u SET MINS_TO_BYPASS_NETWORK_POLICY = 5");
            assertAnswer(refused, "ALTER USER flq_bypass_u SET MINS_TO_BYPASS_SESSION_POLICY = 5");
            assertAnswer(refused, "ALTER USER flq_bypass_u SET MINS_TO_BYPASS_INGRESS_POLICY = 5");
            assertAnswer(refused, "ALTER USER flq_bypass_u UNSET MINS_TO_BYPASS_NETWORK_POLICY");
        } finally {
            engine.execute("DROP USER IF EXISTS flq_bypass_u");
        }
    }
}
