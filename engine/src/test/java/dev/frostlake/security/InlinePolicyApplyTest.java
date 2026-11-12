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
 * A masking policy attached in a column definition (CREATE TABLE, ALTER TABLE … ADD COLUMN) and a row access policy
 * attached by CREATE TABLE … WITH ROW ACCESS POLICY ask APPLY on the policy as the ALTER forms do, before anything is
 * created. They asked nothing, so a role with CREATE TABLE alone attached any policy of the account to its tables.
 * Engine only: the live account allows no CREATE ROLE, CREATE USER or GRANT.
 */
public class InlinePolicyApplyTest {

    private static final String NO_MASKING = "SQL access control error:\nInsufficient privileges to operate on "
        + "masking_policy 'MP'. Your primary role R1 must have APPLY granted on POLICY P.PUBLIC.MP.";
    private static final String NO_ROW_ACCESS = "SQL access control error:\nInsufficient privileges to operate on "
        + "row_access_policy 'RP'. Your primary role R1 must have APPLY granted on POLICY P.PUBLIC.RP.";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.public");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE r1");
        engine.execute("GRANT USAGE, CREATE TABLE ON SCHEMA p.public TO ROLE r1");
        engine.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE ROW ACCESS POLICY rp AS (r STRING) RETURNS BOOLEAN -> FALSE");
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

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void everyInlineFormAsksApply() {
        as("U1", "R1");
        engine.execute("CREATE TABLE own0 (a STRING)");
        assertEquals(NO_MASKING, refusal("CREATE TABLE own1 (a STRING WITH MASKING POLICY mp)"));
        assertEquals(NO_MASKING, refusal("ALTER TABLE own0 ADD COLUMN b STRING WITH MASKING POLICY mp"));
        assertEquals(NO_ROW_ACCESS, refusal("CREATE TABLE own2 (a STRING) WITH ROW ACCESS POLICY rp ON (a)"));
        assertEquals(1, engine.executeQuery("SHOW TABLES LIKE 'OWN%'").getRows().size());
        assertEquals(1, engine.executeQuery("SHOW COLUMNS IN TABLE own0").getRows().size());
    }

    @Test
    public void applyOnThePolicyAttachesIt() {
        engine.execute("GRANT APPLY ON MASKING POLICY mp TO ROLE r1");
        engine.execute("GRANT APPLY ON ROW ACCESS POLICY rp TO ROLE r1");
        as("U1", "R1");
        engine.execute("CREATE TABLE own1 (a STRING WITH MASKING POLICY mp)");
        engine.execute("CREATE TABLE own2 (a STRING) WITH ROW ACCESS POLICY rp ON (a)");
        engine.execute("ALTER TABLE own1 ADD COLUMN b STRING WITH MASKING POLICY mp");
        assertEquals(2, engine.executeQuery("SHOW TABLES LIKE 'OWN%'").getRows().size());
        assertEquals(2, engine.executeQuery("SHOW COLUMNS IN TABLE own1").getRows().size());
    }
}
