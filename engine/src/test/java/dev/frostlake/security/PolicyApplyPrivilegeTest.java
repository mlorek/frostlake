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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Setting or unsetting a masking policy on a column, and adding or dropping a row access policy, takes APPLY MASKING
 * POLICY or APPLY ROW ACCESS POLICY on the account, or APPLY on the policy: owning the table is not enough
 * (documented). A role that may only SELECT from a protected table clones it and owns the clone, which keeps the
 * policies; without APPLY it cannot strip them and read the rows in clear (FL-429). The refusal's wording is not
 * measured. Engine only: the live account allows no CREATE ROLE or GRANT.
 */
public class PolicyApplyPrivilegeTest {

    private static final String MASKING = "SQL access control error:\nInsufficient privileges to operate on"
        + " masking_policy 'MP'. Your primary role R1 must have APPLY granted on POLICY P.PUBLIC.MP.";
    private static final String ROW_ACCESS = "SQL access control error:\nInsufficient privileges to operate on"
        + " row_access_policy 'RP'. Your primary role R1 must have APPLY granted on POLICY P.PUBLIC.RP.";

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.public");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE r1");
        engine.execute("GRANT USAGE, CREATE TABLE, CREATE VIEW ON SCHEMA p.public TO ROLE r1");
        engine.execute("CREATE TABLE t (id INT, ssn STRING, region STRING)");
        engine.execute("INSERT INTO t VALUES (1, '111', 'EU'), (2, '222', 'US')");
        engine.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE ROW ACCESS POLICY rp AS (r STRING) RETURNS BOOLEAN -> r = 'EU'");
        engine.execute("ALTER TABLE t MODIFY COLUMN ssn SET MASKING POLICY mp");
        engine.execute("ALTER TABLE t ADD ROW ACCESS POLICY rp ON (region)");
        engine.execute("GRANT SELECT ON TABLE t TO ROLE r1");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void asUser() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("u1");
        engine.setCurrentRole("r1");
    }

    private void asSystem() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("SYSTEM");
        engine.setCurrentRole(systemRole);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "; " : "").append(row.getValue(0)).append('|').append(row.getValue(1))
                .append('|').append(row.getValue(2));
        }
        return out.toString();
    }

    @Test
    public void aCloneOwnerCannotStripTheSourcesPoliciesWithoutApply() {
        asUser();
        engine.execute("CREATE TABLE c2 CLONE t");
        assertEquals("1|***|EU", rows("SELECT * FROM c2 ORDER BY id"));
        assertEquals(MASKING, refusal("ALTER TABLE c2 MODIFY COLUMN ssn UNSET MASKING POLICY"));
        assertEquals(MASKING, refusal("ALTER TABLE c2 ALTER COLUMN ssn UNSET MASKING POLICY"));
        assertEquals(ROW_ACCESS, refusal("ALTER TABLE c2 DROP ROW ACCESS POLICY rp"));
        assertEquals(ROW_ACCESS, refusal("ALTER TABLE c2 DROP ALL ROW ACCESS POLICIES"));
        assertEquals("1|***|EU", rows("SELECT * FROM c2 ORDER BY id"));
    }

    @Test
    public void aTableOwnerCannotAttachAPolicyWithoutApply() {
        asUser();
        engine.execute("CREATE TABLE own (a STRING)");
        assertEquals(MASKING, refusal("ALTER TABLE own MODIFY COLUMN a SET MASKING POLICY mp"));
        assertEquals(ROW_ACCESS, refusal("ALTER TABLE own ADD ROW ACCESS POLICY rp ON (a)"));
        engine.execute("CREATE VIEW v AS SELECT a FROM own");
        assertEquals(ROW_ACCESS, refusal("ALTER VIEW v ADD ROW ACCESS POLICY rp ON (a)"));
        assertEquals(MASKING, refusal("ALTER VIEW v MODIFY COLUMN a SET MASKING POLICY mp"));
    }

    @Test
    public void applyOnThePolicyLetsItsHolderSetAndUnsetIt() {
        asSystem();
        engine.execute("GRANT APPLY ON MASKING POLICY mp TO ROLE r1");
        engine.execute("GRANT APPLY ON ROW ACCESS POLICY rp TO ROLE r1");
        asUser();
        engine.execute("CREATE TABLE c2 CLONE t");
        engine.execute("ALTER TABLE c2 MODIFY COLUMN ssn UNSET MASKING POLICY");
        engine.execute("ALTER TABLE c2 DROP ROW ACCESS POLICY rp");
        assertEquals("1|111|EU; 2|222|US", rows("SELECT * FROM c2 ORDER BY id"));
        engine.execute("ALTER TABLE c2 MODIFY COLUMN ssn SET MASKING POLICY mp");
        engine.execute("ALTER TABLE c2 ADD ROW ACCESS POLICY rp ON (region)");
        assertEquals("1|***|EU", rows("SELECT * FROM c2 ORDER BY id"));
    }

    @Test
    public void applyOnTheAccountLetsItsHolderSetAndUnsetEveryPolicy() {
        asSystem();
        engine.execute("GRANT APPLY MASKING POLICY ON ACCOUNT TO ROLE r1");
        engine.execute("GRANT APPLY ROW ACCESS POLICY ON ACCOUNT TO ROLE r1");
        asUser();
        engine.execute("CREATE TABLE c2 CLONE t");
        engine.execute("ALTER TABLE c2 MODIFY COLUMN ssn UNSET MASKING POLICY");
        engine.execute("ALTER TABLE c2 DROP ALL ROW ACCESS POLICIES");
        assertEquals("1|111|EU; 2|222|US", rows("SELECT * FROM c2 ORDER BY id"));
    }

    @Test
    public void forceReplacingAPolicyTakesApplyOnTheOneItDetaches() {
        engine.execute("CREATE MASKING POLICY mp2 AS (v STRING) RETURNS STRING -> 'xxx'");
        engine.execute("GRANT APPLY ON MASKING POLICY mp2 TO ROLE r1");
        asUser();
        engine.execute("CREATE TABLE c2 CLONE t");
        assertEquals(MASKING, refusal("ALTER TABLE c2 MODIFY COLUMN ssn SET MASKING POLICY mp2 FORCE"));
        assertEquals("1|***|EU", rows("SELECT * FROM c2 ORDER BY id"));
    }
}
