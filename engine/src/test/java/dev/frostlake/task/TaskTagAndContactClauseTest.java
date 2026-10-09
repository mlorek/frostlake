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
package dev.frostlake.task;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tags and contacts of a task (live-verified). CREATE TASK takes {@code [WITH] TAG (…)} and then
 * {@code [WITH] CONTACT (purpose = contact, …)} right after the task's name, before every property; ALTER TASK
 * sets and unsets a contact as ALTER TABLE does; and the DDL writes both, each on a line of its own, the tags by
 * name and the contacts by purpose.
 */
public class TaskTagAndContactClauseTest extends BaseDatabaseTest {

    private static final String CREATE = "CREATE OR REPLACE TASK ttc_t";

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private String first(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String ddl() {
        return first("SELECT GET_DDL('TASK', 'ttc_t')");
    }

    private List<String> contacts() {
        final ResultSet rows = engine.executeQuery("SELECT * FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('ttc_t',"
            + " 'TASK'))");
        final List<String> told = new ArrayList<>();
        for (final Row row : rows.getRows()) {
            told.add(row.getValue(0) + "|" + row.getValue(1) + "|" + row.getValue(4));
        }
        return told;
    }

    private void objects() {
        engine.execute("CREATE OR REPLACE TAG ttc_tag");
        engine.execute("CREATE OR REPLACE TAG ttc_tag2");
        engine.execute("CREATE OR REPLACE CONTACT ttc_c EMAIL_DISTRIBUTION_LIST = 'a@example.com'");
        engine.execute("CREATE OR REPLACE CONTACT ttc_c2 EMAIL_DISTRIBUTION_LIST = 'b@example.com'");
    }

    @Test
    public void theTagsAreWrittenRightAfterTheName() {
        objects();
        engine.execute(CREATE + " WITH TAG (ttc_tag2 = 'b', ttc_tag = 'a') SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertEquals("a|b", first("SELECT SYSTEM$GET_TAG('ttc_tag', 'ttc_t', 'TASK') || '|' ||"
            + " SYSTEM$GET_TAG('ttc_tag2', 'ttc_t', 'TASK')"));
        assertEquals("create or replace task TTC_T\n WITH TAG (TEST_DB.TEST_SCHEMA.TTC_TAG='a',"
            + " TEST_DB.TEST_SCHEMA.TTC_TAG2='b')\n\tschedule='60 MINUTE'\n\tas SELECT 1;", ddl());
        engine.execute(CREATE + " TAG (ttc_tag = 'x') AS SELECT 1");
        assertEquals("x", first("SELECT SYSTEM$GET_TAG('ttc_tag', 'ttc_t', 'TASK')"), "WITH may be left out");
        assertTrue(refusal(CREATE + " SCHEDULE = '60 MINUTE' WITH TAG (ttc_tag = 'y') AS SELECT 1")
            .contains("syntax error line 1 at position 52 unexpected 'WITH'."), "a property may not come first");
        assertTrue(refusal(CREATE + " WITH TAG (ttc_notag = 'y') AS SELECT 1")
            .contains("Tag 'TTC_NOTAG' does not exist or not authorized."));
        assertEquals("Same tag TTC_TAG with multiple values provided, values = a, b",
            refusal(CREATE + " WITH TAG (ttc_tag = 'a', ttc_tag = 'b') AS SELECT 1"));
        assertEquals("x", first("SELECT SYSTEM$GET_TAG('ttc_tag', 'ttc_t', 'TASK')"),
            "a refused replacement leaves the task as it was");
        engine.execute("ALTER TASK ttc_t SET TAG ttc_tag2 = 'late'");
        assertTrue(ddl().contains(" WITH TAG (TEST_DB.TEST_SCHEMA.TTC_TAG='x', TEST_DB.TEST_SCHEMA.TTC_TAG2='late')\n"),
            "a tag set later is written the same way");
    }

    @Test
    public void theContactsFollowTheTags() {
        objects();
        engine.execute(CREATE + " WITH CONTACT (SUPPORT = ttc_c) AS SELECT 1");
        assertEquals("[SUPPORT|a@example.com|TASK]", contacts().toString());
        assertEquals("create or replace task TTC_T\n WITH CONTACT (SUPPORT=TEST_DB.TEST_SCHEMA.TTC_C)\n\tas SELECT 1;",
            ddl());
        engine.execute(CREATE + " WITH TAG (ttc_tag = 'v') CONTACT ('access_approval' = ttc_c, STEWARD = ttc_c2)"
            + " SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertEquals("create or replace task TTC_T\n WITH TAG (TEST_DB.TEST_SCHEMA.TTC_TAG='v')\n WITH CONTACT"
            + " (ACCESS_APPROVAL=TEST_DB.TEST_SCHEMA.TTC_C, STEWARD=TEST_DB.TEST_SCHEMA.TTC_C2)\n"
            + "\tschedule='60 MINUTE'\n\tas SELECT 1;", ddl());
        assertTrue(refusal(CREATE + " WITH CONTACT (SUPPORT = ttc_c) WITH TAG (ttc_tag = 'v') AS SELECT 1")
            .contains("syntax error line 1 at position 60 unexpected 'WITH'."), "the tags come first");
        assertEquals("Try to attach duplicate purposes to the object to create, domain TASK.",
            refusal(CREATE + " WITH CONTACT (SUPPORT = ttc_c, SUPPORT = ttc_c2) AS SELECT 1"));
        assertEquals("SQL compilation error: Invalid contact purpose. Valid purposes are: ACCESS_APPROVAL, SUPPORT,"
            + " STEWARD.", refusal(CREATE + " WITH CONTACT (BOGUS = ttc_c) AS SELECT 1"));
        assertTrue(refusal(CREATE + " WITH CONTACT (BOGUS = ttc_nocontact) AS SELECT 1")
            .contains("Contact 'TEST_DB.TEST_SCHEMA.TTC_NOCONTACT' does not exist or not authorized."),
            "a contact that is not there is told before its purpose is judged");
    }

    @Test
    public void alterSetsAndUnsetsAContact() {
        objects();
        engine.execute(CREATE + " AS SELECT 1");
        engine.execute("ALTER TASK ttc_t SET CONTACT SUPPORT = ttc_c, STEWARD = ttc_c2");
        assertEquals("[STEWARD|b@example.com|TASK, SUPPORT|a@example.com|TASK]", contacts().toString());
        assertTrue(ddl().contains(" WITH CONTACT (STEWARD=TEST_DB.TEST_SCHEMA.TTC_C2,"
            + " SUPPORT=TEST_DB.TEST_SCHEMA.TTC_C)\n"));
        final String purpose = "SQL compilation error: Purpose type BOGUS is not valid, please use access_approval,"
            + " steward and support.";
        assertEquals(purpose, refusal("ALTER TASK ttc_t SET CONTACT BOGUS = ttc_c"));
        assertEquals(purpose, refusal("ALTER TASK ttc_t UNSET CONTACT BOGUS"));
        assertTrue(refusal("ALTER TASK ttc_t SET CONTACT SUPPORT = ttc_nocontact")
            .contains("Contact 'TEST_DB.TEST_SCHEMA.TTC_NOCONTACT' does not exist or not authorized."));
        engine.execute("ALTER TASK ttc_t UNSET CONTACT SUPPORT, STEWARD");
        assertTrue(contacts().isEmpty());
        engine.execute("ALTER TASK ttc_t UNSET CONTACT ACCESS_APPROVAL");
    }
}
