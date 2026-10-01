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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE, ALTER, DROP, SHOW and DESCRIBE PASSWORD POLICY, and attaching a password policy to the account and to a
 * user, which is recorded and never enforced.
 */
public class PasswordPolicyTest extends BaseDatabaseTest {

    /** Runs a statement that must be refused and answers the refusal's message. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private String describedValue(final String policy, final String property, final String column) {
        final ResultSet described = engine.executeQuery("DESCRIBE PASSWORD POLICY " + policy);
        return cell(described, soleRowWhere(described, "property", property), column);
    }

    @Test
    public void aPolicyIsCreatedListedAndDescribed() {
        engine.execute("CREATE PASSWORD POLICY pp_a PASSWORD_MIN_LENGTH = 12 PASSWORD_HISTORY = 3 COMMENT = 'pw'");
        final ResultSet listed = engine.executeQuery("SHOW PASSWORD POLICIES LIKE 'pp_a'");
        final Row row = soleRowWhere(listed, "name", "PP_A");
        assertEquals("PASSWORD_POLICY", cell(listed, row, "kind"));
        assertEquals("pw", cell(listed, row, "comment"));
        assertEquals("TEST_SCHEMA", cell(listed, row, "schema_name"));
        assertEquals("12", describedValue("pp_a", "PASSWORD_MIN_LENGTH", "value"));
        assertEquals("14", describedValue("pp_a", "PASSWORD_MIN_LENGTH", "default"));
        assertEquals("3", describedValue("pp_a", "PASSWORD_HISTORY", "value"));
        assertEquals("90", describedValue("pp_a", "PASSWORD_MAX_AGE_DAYS", "value"), "an unset setting shows its default");
        assertEquals("pw", describedValue("pp_a", "COMMENT", "value"));
        assertEquals("PP_A", describedValue("pp_a", "NAME", "value"));
    }

    @Test
    public void settingsAreRangeChecked() {
        assertTrue(refusal("CREATE PASSWORD POLICY pp_b PASSWORD_MIN_LENGTH = 7").contains(
            "invalid value '7' for property 'PASSWORD_MIN_LENGTH'"));
        assertTrue(refusal("CREATE PASSWORD POLICY pp_b PASSWORD_MAX_RETRIES = 11").contains(
            "invalid value '11' for property 'PASSWORD_MAX_RETRIES'"));
        assertTrue(refusal("CREATE PASSWORD POLICY pp_b PASSWORD_MIN_LENGTH = 'ten'").contains(
            "invalid value ['ten'] for parameter 'PASSWORD_MIN_LENGTH'"));
        assertTrue(refusal("CREATE PASSWORD POLICY pp_b PASSWORD_COLOR = 1").contains(
            "invalid property 'PASSWORD_COLOR' for 'PASSWORD_POLICY'"));
        assertTrue(refusal("CREATE PASSWORD POLICY pp_b PASSWORD_HISTORY = 1 PASSWORD_HISTORY = 2").contains(
            "duplicate property 'PASSWORD_HISTORY'"));
    }

    @Test
    public void alterSetsUnsetsAndRenamesIntoAnotherSchema() {
        engine.execute("CREATE PASSWORD POLICY pp_c PASSWORD_MAX_AGE_DAYS = 30");
        engine.execute("ALTER PASSWORD POLICY pp_c SET PASSWORD_MAX_AGE_DAYS = 45, PASSWORD_MIN_SPECIAL_CHARS = 2");
        assertEquals("45", describedValue("pp_c", "PASSWORD_MAX_AGE_DAYS", "value"));
        engine.execute("ALTER PASSWORD POLICY pp_c UNSET PASSWORD_MAX_AGE_DAYS, PASSWORD_MIN_SPECIAL_CHARS");
        assertEquals("90", describedValue("pp_c", "PASSWORD_MAX_AGE_DAYS", "value"));
        assertEquals("0", describedValue("pp_c", "PASSWORD_MIN_SPECIAL_CHARS", "value"));
        engine.execute("CREATE SCHEMA pp_other");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("ALTER PASSWORD POLICY pp_c RENAME TO pp_other.pp_moved");
        assertEquals(0, engine.executeQuery("SHOW PASSWORD POLICIES LIKE 'PP_C'").getRows().size());
        assertEquals(1, engine.executeQuery("SHOW PASSWORD POLICIES LIKE 'PP_MOVED' IN SCHEMA pp_other")
            .getRows().size());
        assertTrue(refusal("ALTER PASSWORD POLICY pp_c SET PASSWORD_HISTORY = 2").contains(
            "Password policy 'TEST_DB.TEST_SCHEMA.PP_C' does not exist or not authorized."));
        engine.execute("ALTER PASSWORD POLICY IF EXISTS pp_c SET PASSWORD_HISTORY = 2");
    }

    @Test
    public void attachmentsAreRecordedAndShownOnTheirTarget() {
        engine.execute("CREATE PASSWORD POLICY pp_d");
        engine.execute("CREATE USER pp_user");
        engine.execute("ALTER ACCOUNT SET PASSWORD POLICY pp_d");
        ResultSet on = engine.executeQuery("SHOW PASSWORD POLICIES ON ACCOUNT");
        assertEquals("ACCOUNT", cell(on, soleRowWhere(on, "name", "PP_D"), "set_on"));
        engine.execute("ALTER USER pp_user SET PASSWORD POLICY test_db.test_schema.pp_d");
        on = engine.executeQuery("SHOW PASSWORD POLICIES ON USER pp_user");
        assertEquals("USER", cell(on, soleRowWhere(on, "name", "PP_D"), "set_on"));
        assertEquals("Object PP_USER already has a PASSWORD_POLICY. Only one PASSWORD_POLICY is allowed at a time.",
            refusal("ALTER USER pp_user SET PASSWORD POLICY pp_d"));
        engine.execute("ALTER USER pp_user SET PASSWORD POLICY pp_d FORCE");
        assertTrue(refusal("DROP PASSWORD POLICY pp_d").contains(
            "Policy PP_D cannot be dropped/replaced as it is associated with one or more entities."));
        engine.execute("ALTER USER pp_user UNSET PASSWORD POLICY");
        on = engine.executeQuery("SHOW PASSWORD POLICIES ON USER pp_user");
        assertEquals("ACCOUNT", cell(on, soleRowWhere(on, "name", "PP_D"), "set_on"), "the account's policy governs");
        engine.execute("DROP PASSWORD POLICY pp_d");
        on = engine.executeQuery("SHOW PASSWORD POLICIES ON ACCOUNT");
        assertEquals("SYSTEM", cell(on, soleRowWhere(on, "name", "BUILT-IN"), "set_on"));
        assertTrue(refusal("ALTER ACCOUNT SET PASSWORD POLICY pp_d").contains(
            "Password policy 'TEST_DB.TEST_SCHEMA.PP_D' does not exist or not authorized."));
    }

    @Test
    public void createModesTagsAndDrop() {
        engine.execute("CREATE PASSWORD POLICY pp_e COMMENT = 'first'");
        assertTrue(refusal("CREATE PASSWORD POLICY pp_e").contains("Object 'PP_E' already exists."));
        engine.execute("CREATE PASSWORD POLICY IF NOT EXISTS pp_e COMMENT = 'second'");
        engine.execute("CREATE OR REPLACE PASSWORD POLICY pp_e COMMENT = 'third'");
        assertEquals("third", describedValue("pp_e", "COMMENT", "value"));
        engine.execute("CREATE TAG pp_tag");
        engine.execute("ALTER PASSWORD POLICY pp_e SET TAG pp_tag = 'v'");
        final ResultSet tags = engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('pp_e', 'PASSWORD POLICY'))");
        assertEquals(1, tags.getRows().size());
        engine.execute("ALTER PASSWORD POLICY pp_e UNSET TAG pp_tag");
        engine.execute("DROP PASSWORD POLICY pp_e");
        engine.execute("DROP PASSWORD POLICY IF EXISTS pp_e");
        assertEquals(0, engine.executeQuery("SHOW PASSWORD POLICIES LIKE 'PP_E' IN ACCOUNT STARTS WITH 'PP' LIMIT 5")
            .getRows().size());
    }
}
