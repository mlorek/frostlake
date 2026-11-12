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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GET_DDL spells an attached policy so its text reads back to that policy: a quoted lower-case join, masking or
 * aggregation policy keeps its quotes, where it was printed bare and a replay attached the upper-case policy of the
 * same letters instead.
 */
public class GetDdlQuotedPolicyNameTest extends BaseDatabaseTest {

    private String ddl(final String table) {
        return String.valueOf(engine.executeQuery("SELECT GET_DDL('TABLE', '" + table + "')").getRows().get(0)
            .getValue(0));
    }

    @Test
    public void aQuotedPolicyKeepsItsQuotes() {
        engine.execute("CREATE JOIN POLICY \"lq\" AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        engine.execute("CREATE JOIN POLICY LQ AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
        engine.execute("CREATE TABLE gqp_t3 (a INT) WITH JOIN POLICY \"lq\"");
        assertTrue(ddl("gqp_t3").contains("WITH JOIN POLICY TEST_DB.TEST_SCHEMA.\"lq\""), ddl("gqp_t3"));
        engine.execute("CREATE TABLE gqp_t4 (a INT) WITH JOIN POLICY LQ");
        assertTrue(ddl("gqp_t4").contains("WITH JOIN POLICY TEST_DB.TEST_SCHEMA.LQ"), ddl("gqp_t4"));
        engine.execute("CREATE MASKING POLICY \"mq\" AS (v STRING) RETURNS STRING -> v");
        engine.execute("CREATE AGGREGATION POLICY \"aq\" AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "NO_AGGREGATION_CONSTRAINT()");
        engine.execute("CREATE TABLE gqp_t7 (s STRING WITH MASKING POLICY \"mq\") WITH AGGREGATION POLICY \"aq\"");
        assertTrue(ddl("gqp_t7").contains("WITH MASKING POLICY TEST_DB.TEST_SCHEMA.\"mq\""), ddl("gqp_t7"));
        assertTrue(ddl("gqp_t7").contains("WITH AGGREGATION POLICY TEST_DB.TEST_SCHEMA.\"aq\""), ddl("gqp_t7"));
    }
}
