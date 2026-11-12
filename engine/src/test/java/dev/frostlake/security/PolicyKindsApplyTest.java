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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Setting or unsetting an aggregation, join or projection policy asks APPLY on the policy, or the account's APPLY
 * privilege for its kind, as a masking or a row access policy does: owning the table is not enough, so its owner can
 * neither strip a policy an administrator set nor attach one of its own choosing. They asked nothing. Engine only:
 * the live account allows no CREATE ROLE, CREATE USER or GRANT.
 */
public class PolicyKindsApplyTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.public");
        engine.execute("CREATE TABLE t (a INT, b VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'secret')");
        engine.execute("CREATE TABLE t2 (a INT)");
        engine.execute("CREATE ROLE ra");
        engine.execute("CREATE USER ua DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE ra TO USER ua");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE ra");
        engine.execute("GRANT USAGE, CREATE TABLE ON SCHEMA p.public TO ROLE ra");
        engine.execute("CREATE AGGREGATION POLICY ap AS () RETURNS AGGREGATION_CONSTRAINT "
            + "-> AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 5)");
        engine.execute("CREATE PROJECTION POLICY pp AS () RETURNS PROJECTION_CONSTRAINT "
            + "-> PROJECTION_CONSTRAINT(ALLOW => FALSE)");
        engine.execute("CREATE JOIN POLICY jp AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
        engine.execute("ALTER TABLE t SET AGGREGATION POLICY ap");
        engine.execute("ALTER TABLE t MODIFY COLUMN b SET PROJECTION POLICY pp");
        engine.execute("ALTER TABLE t2 SET JOIN POLICY jp");
        engine.execute("GRANT OWNERSHIP ON TABLE t TO ROLE ra COPY CURRENT GRANTS");
        engine.execute("GRANT OWNERSHIP ON TABLE t2 TO ROLE ra COPY CURRENT GRANTS");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void asRa() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("UA");
        engine.setCurrentRole("RA");
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

    private static void assertAsksApply(final String policy, final String refusal) {
        assertTrue(refusal.contains("must have APPLY granted on POLICY P.PUBLIC." + policy + "."), refusal);
    }

    @Test
    public void theOwnerMayNeitherStripNorAttachThem() {
        asRa();
        assertAsksApply("AP", refusal("ALTER TABLE t UNSET AGGREGATION POLICY"));
        assertAsksApply("PP", refusal("ALTER TABLE t MODIFY COLUMN b UNSET PROJECTION POLICY"));
        assertAsksApply("JP", refusal("ALTER TABLE t2 UNSET JOIN POLICY"));
        assertAsksApply("AP", refusal("ALTER TABLE t2 SET AGGREGATION POLICY ap"));
        assertAsksApply("AP", refusal("CREATE TABLE own1 (a INT) WITH AGGREGATION POLICY ap"));
        assertAsksApply("JP", refusal("CREATE TABLE own2 (a INT) WITH JOIN POLICY jp"));
        assertAsksApply("PP", refusal("CREATE TABLE own3 (a INT WITH PROJECTION POLICY pp)"));
        assertEquals(0, engine.executeQuery("SHOW TABLES LIKE 'OWN%'").getRows().size());
    }

    @Test
    public void theAccountsApplyPrivilegesLetItDoSo() {
        engine.execute("GRANT APPLY AGGREGATION POLICY ON ACCOUNT TO ROLE ra");
        engine.execute("GRANT APPLY JOIN POLICY ON ACCOUNT TO ROLE ra");
        engine.execute("GRANT APPLY PROJECTION POLICY ON ACCOUNT TO ROLE ra");
        asRa();
        engine.execute("ALTER TABLE t UNSET AGGREGATION POLICY");
        engine.execute("ALTER TABLE t MODIFY COLUMN b UNSET PROJECTION POLICY");
        engine.execute("ALTER TABLE t2 UNSET JOIN POLICY");
        engine.execute("ALTER TABLE t2 SET AGGREGATION POLICY ap");
        engine.execute("CREATE TABLE own1 (a INT) WITH AGGREGATION POLICY ap");
        assertEquals("secret", String.valueOf(engine.executeQuery("SELECT b FROM t").getRows().get(0).getValue(0)));
    }
}
