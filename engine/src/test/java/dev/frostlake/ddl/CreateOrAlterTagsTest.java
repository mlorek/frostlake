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

/**
 * CREATE OR ALTER sets no tags: on a TASK, a TABLE or a VIEW a tag clause is refused once every tag it names is
 * found, ahead of the statement's properties, and a TASK takes no contacts there, so CONTACT is read as a
 * property's name (all live-verified).
 */
public class CreateOrAlterTagsTest extends MeasuredCellsTestSupport {

    @Test
    public void orAlterContacts() {
        engine.execute("CREATE OR REPLACE CONTACT c581 URL = 'https://example.com/help'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'CONTACT'. | "
                + "syntax error line 1 at position 37 unexpected '('.",
            "CREATE OR ALTER TASK tk WITH CONTACT (SUPPORT = c581) AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.TK' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.TK.",
            "SELECT * FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('tk', 'TASK'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('.",
            "CREATE OR ALTER TASK tk CONTACT (ACCESS_APPROVAL = c581) AS SELECT 2");
        assertAnswer("REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.TK' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.TK.",
            "SELECT * FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('tk', 'TASK'))");
        assertAnswer("Task TK successfully created.", "CREATE OR ALTER TASK tk AS SELECT 3");
        assertAnswer("", "SELECT * FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('tk', 'TASK'))");
        assertAnswer("REFUSED SQL compilation error: | Tag 'NOSUCHTAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCHTAG.",
            "CREATE OR ALTER TASK tk WITH TAG (nosuchtag = 'b') AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'CONTACT'. | "
                + "syntax error line 1 at position 37 unexpected '('.",
            "CREATE OR ALTER TASK tk WITH CONTACT (SUPPORT = nosuchcontact) AS SELECT 1");
    }

    @Test
    public void orAlterTaskTags() {
        engine.execute("CREATE OR REPLACE TAG t582");
        engine.execute("CREATE OR REPLACE CONTACT c582 URL = 'https://example.com/help'");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TASK tk WITH TAG (t582 = 'v1') AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | Task 'TK' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TASK TK.",
            "SELECT SYSTEM$GET_TAG('t582', 'tk', 'TASK')");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TASK tk WITH TAG (t582 = 'v2') AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | Task 'TK' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TASK TK.",
            "SELECT SYSTEM$GET_TAG('t582', 'tk', 'TASK')");
        assertAnswer("Task TK successfully created.", "CREATE OR ALTER TASK tk AS SELECT 1");
        assertAnswer("null", "SELECT SYSTEM$GET_TAG('t582', 'tk', 'TASK')");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TASK tk TAG (t582 = 'v3') AS SELECT 1");
        assertAnswer("null", "SELECT SYSTEM$GET_TAG('t582', 'tk', 'TASK')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('.",
            "CREATE OR ALTER TASK tk CONTACT (SUPPORT = c582) AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'CONTACT' for 'TASK'",
            "CREATE OR ALTER TASK tk CONTACT = 'x' AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'CONTACT'. | "
                + "syntax error line 1 at position 37 unexpected 'AS'.",
            "CREATE OR ALTER TASK tk WITH CONTACT AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'AS'.",
            "CREATE OR ALTER TASK tk WITH AS SELECT 1");
    }

    @Test
    public void orAlterTagsOnOtherKinds() {
        engine.execute("CREATE OR REPLACE TAG t583");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TABLE t583 (a INT) WITH TAG (t583 = 'v')");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER VIEW v583 WITH TAG (t583 = 'v') AS SELECT 1 AS a");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TASK tk583 WITH TAG (t583 = 'v') SCHEDULE = 'nonsense' AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected '<EOF>'.",
            "CREATE OR ALTER TASK tk583 WITH TAG (t583 = 'v') AS SELECT 1 FROM");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TASK tk583 WITH TAG (t583 = 'v', t583 = 'w') AS SELECT 1");
    }
}
