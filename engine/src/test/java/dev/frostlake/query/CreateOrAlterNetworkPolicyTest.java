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
 * CREATE OR ALTER NETWORK POLICY tells the whole policy: over an existing one it leaves the lists and the
 * comment it writes and clears the ones it leaves out, and answers as an ALTER does; where there is none it
 * creates the policy. Every answer is live-verified.
 */
public class CreateOrAlterNetworkPolicyTest extends MeasuredCellsTestSupport {

    @Test
    public void whatTheStatementLeavesOutIsCleared() {
        try {
            engine.execute("DROP NETWORK POLICY IF EXISTS flq_np_a");
            assertAnswer(
                "Network policy FLQ_NP_A is created.",
                "CREATE NETWORK POLICY flq_np_a ALLOWED_IP_LIST = ('1.1.1.1') BLOCKED_IP_LIST = ('2.2.2.2') "
                + "COMMENT = 'first'");
            assertAnswer(
                "Statement executed successfully.",
                "CREATE OR ALTER NETWORK POLICY flq_np_a ALLOWED_IP_LIST = ('3.3.3.3', '4.4.4.4')");
            engine.execute("SHOW NETWORK POLICIES LIKE 'FLQ_NP_A'");
            assertAnswer(
                "|2|0",
                "SELECT \"comment\", \"entries_in_allowed_ip_list\", \"entries_in_blocked_ip_list\" FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Statement executed successfully.",
                "CREATE OR ALTER NETWORK POLICY flq_np_a COMMENT = 'second'");
            engine.execute("SHOW NETWORK POLICIES LIKE 'FLQ_NP_A'");
            assertAnswer(
                "second|0|0",
                "SELECT \"comment\", \"entries_in_allowed_ip_list\", \"entries_in_blocked_ip_list\" FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Statement executed successfully.",
                "CREATE OR ALTER NETWORK POLICY flq_np_a");
            engine.execute("SHOW NETWORK POLICIES LIKE 'FLQ_NP_A'");
            assertAnswer(
                "|0|0",
                "SELECT \"comment\", \"entries_in_allowed_ip_list\", \"entries_in_blocked_ip_list\" FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Network policy FLQ_NP_B is created.",
                "CREATE OR ALTER NETWORK POLICY flq_np_b COMMENT = 'made'");
            engine.execute("SHOW NETWORK POLICIES LIKE 'FLQ_NP_B'");
            assertAnswer(
                "made",
                "SELECT \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            engine.execute("DROP NETWORK POLICY IF EXISTS flq_np_a");
            engine.execute("DROP NETWORK POLICY IF EXISTS flq_np_b");
        }
    }
}
