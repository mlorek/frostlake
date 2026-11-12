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

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Setting or unsetting a tag on a table, a column or any other object takes APPLY TAG on the account, or APPLY on the
 * tag beside the object's own privilege (documented, "Tag privileges"), and so does a tag clause on an object being
 * created. Owning the object was enough, so a role that cloned a table a tag-driven masking policy protects re-tagged
 * the clone's column and read it in clear. The refusal's wording is not measured. Engine only: the live account allows
 * no CREATE ROLE, CREATE USER or GRANT.
 */
public class TagApplyPrivilegeTest {

    private static final String NO_APPLY = "SQL access control error:\nInsufficient privileges to operate on tag"
        + " 'PII'. Your primary role R1 must have APPLY granted on TAG P.PUBLIC.PII.";

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.public");
        engine.execute("CREATE ROLE r1");
        // The user puts no secondary role in use, so a refusal names the primary role alone.
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE r1");
        engine.execute("GRANT USAGE, CREATE TABLE ON SCHEMA p.public TO ROLE r1");
        engine.execute("CREATE TAG pii");
        engine.execute("CREATE TABLE t (id INT, ssn STRING)");
        engine.execute("INSERT INTO t VALUES (1, '111')");
        engine.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> "
            + "CASE WHEN SYSTEM$GET_TAG_ON_CURRENT_COLUMN('pii') = 'none' THEN v ELSE '***' END");
        engine.execute("ALTER TABLE t MODIFY COLUMN ssn SET MASKING POLICY mp");
        engine.execute("ALTER TABLE t MODIFY COLUMN ssn SET TAG pii = 'high'");
        engine.execute("GRANT SELECT ON TABLE t TO ROLE r1");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void as(final String user, final String role) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(user);
        engine.setCurrentRole(role);
        engine.execute("USE SCHEMA p.public");
    }

    private void asSystem() {
        as("SYSTEM", systemRole);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String ssn(final String table) {
        return String.valueOf(engine.executeQuery("SELECT ssn FROM " + table).getRows().get(0).getValue(0));
    }

    @Test
    public void theOwnerOfACloneNeitherTagsNorUntagsWithoutApply() {
        as("U1", "R1");
        engine.execute("CREATE TABLE c2 CLONE t");
        assertEquals(NO_APPLY, refusal("ALTER TABLE c2 MODIFY COLUMN ssn SET TAG pii = 'none'"));
        assertEquals("***", ssn("c2"));
        assertEquals(NO_APPLY, refusal("ALTER TABLE c2 MODIFY COLUMN ssn UNSET TAG pii"));
        assertEquals(NO_APPLY, refusal("ALTER TABLE c2 SET TAG pii = 'x'"));
        assertEquals(NO_APPLY, refusal("ALTER TABLE c2 UNSET TAG pii"));
        // A tag clause on an object being created asks the same, before the object is made.
        assertEquals(NO_APPLY, refusal("CREATE TABLE c3 (a STRING WITH TAG (pii = 'z'))"));
        assertEquals(NO_APPLY, refusal("CREATE TABLE c4 (a STRING) WITH TAG (pii = 'z')"));
        assertEquals(0, engine.executeQuery("SHOW TABLES LIKE 'C_'").getRows().size() - 1);
    }

    @Test
    public void applyOnTheTagLetsTheOwnerTagItsObjects() {
        engine.execute("GRANT APPLY ON TAG pii TO ROLE r1");
        as("U1", "R1");
        engine.execute("CREATE TABLE c2 CLONE t");
        engine.execute("ALTER TABLE c2 MODIFY COLUMN ssn SET TAG pii = 'none'");
        assertEquals("111", ssn("c2"));
        engine.execute("ALTER TABLE c2 SET TAG pii = 'x'");
        engine.execute("ALTER TABLE c2 UNSET TAG pii");
        engine.execute("CREATE TABLE c3 (a STRING WITH TAG (pii = 'z'))");
        // APPLY on the tag does not make the role an owner of a table it does not own.
        asSystem();
        engine.execute("GRANT SELECT ON TABLE t TO ROLE r1");
        as("U1", "R1");
        assertEquals("SQL access control error:\nInsufficient privileges to operate on table 'T'.",
            refusal("ALTER TABLE t SET TAG pii = 'x'"));
    }

    @Test
    public void applyTagOnTheAccountTagsEveryObjectTheRoleOwns() {
        engine.execute("GRANT APPLY TAG ON ACCOUNT TO ROLE r1");
        as("U1", "R1");
        engine.execute("CREATE TABLE c2 CLONE t");
        engine.execute("ALTER TABLE c2 MODIFY COLUMN ssn UNSET TAG pii");
        engine.execute("ALTER TABLE c2 SET TAG pii = 'x'");
        engine.execute("CREATE TABLE c3 (a STRING) WITH TAG (pii = 'z')");
    }
}
