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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * GRANT OWNERSHIP … TO DATABASE ROLE moves the object to the database role (live-verified): the role that owned it
 * acts as its owner no more, so ACCOUNTADMIN is refused a DROP and a RENAME of it, and a session acts as the owner
 * once the database role is granted to its role — a RENAME then asks for CREATE TABLE on the schema, which a grant
 * to the database role answers.
 */
public class DatabaseRoleOwnershipTest extends BaseDatabaseTest {

    private static final String ACCOUNT_GRANTS =
        "grants a database role and the ownership of an object to it, which the account this suite is checked "
        + "against is not altered by";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_GRANTS);
        engine.execute("USE ROLE ACCOUNTADMIN");
        // Privileges come from the primary role alone, and the refusals name only it.
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE IF NOT EXISTS crw_other");
        engine.execute("CREATE SCHEMA crw_s");
        engine.execute("CREATE TABLE crw_s.seed (a INT)");
        engine.execute("CREATE DATABASE ROLE test_db.crw_dr");
        engine.execute("GRANT OWNERSHIP ON SCHEMA test_db.crw_s TO ROLE crw_other COPY CURRENT GRANTS");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void aDatabaseRoleOwnsWhatItIsGrantedTheOwnershipOf() {
        engine.execute("GRANT OWNERSHIP ON TABLE test_db.crw_s.seed TO DATABASE ROLE test_db.crw_dr");
        final String ownership = "SQL access control error:\nInsufficient privileges to operate on table 'SEED'. Your "
            + "primary role ACCOUNTADMIN must have OWNERSHIP granted on TABLE TEST_DB.CRW_S.SEED.";
        assertEquals(ownership, refusal("DROP TABLE test_db.crw_s.seed"));
        assertEquals(ownership, refusal("ALTER TABLE test_db.crw_s.seed RENAME TO test_db.crw_s.seed_r"));

        // Held through the database role, the session acts as the owner, and a rename asks for the schema's CREATE.
        engine.execute("GRANT DATABASE ROLE test_db.crw_dr TO ROLE ACCOUNTADMIN");
        assertEquals("SQL access control error:\nInsufficient privileges to operate on schema 'CRW_S'. Your primary "
            + "role ACCOUNTADMIN must have CREATE TABLE granted on SCHEMA TEST_DB.CRW_S.",
            refusal("ALTER TABLE test_db.crw_s.seed RENAME TO test_db.crw_s.seed_r"));
        engine.execute("GRANT CREATE TABLE ON SCHEMA test_db.crw_s TO DATABASE ROLE test_db.crw_dr");
        engine.execute("ALTER TABLE test_db.crw_s.seed RENAME TO test_db.crw_s.seed_r");
        engine.execute("DROP TABLE test_db.crw_s.seed_r");
    }
}
