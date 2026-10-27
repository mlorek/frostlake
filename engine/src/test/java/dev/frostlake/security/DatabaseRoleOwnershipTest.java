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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * GRANT OWNERSHIP … TO DATABASE ROLE moves the object to the database role (live-verified): the role that owned it
 * acts as its owner no more, so ACCOUNTADMIN is refused a DROP and a RENAME of it, and a session acts as the owner
 * once the database role is granted to its role — a RENAME then asks for CREATE TABLE on the schema, which a grant
 * to the database role answers. SHOW GRANTS names the owning database role as its other grants name it, by its kind
 * and bare, and lists what it owns among its grants.
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

    /** The rows a statement answers, as "a|b ;; c|d". */
    private String rows(final String sql) {
        final StringBuilder text = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final StringBuilder cells = new StringBuilder();
            for (final Object value : row.getValues()) {
                cells.append(cells.length() > 0 ? "|" : "").append(value);
            }
            text.append(text.length() > 0 ? " ;; " : "").append(cells);
        }
        return text.toString();
    }

    @Test
    public void anOwningDatabaseRoleIsNamedAsItsOtherGrantsNameIt() {
        engine.execute("CREATE TABLE test_db.test_schema.crw_held (a INT)");
        engine.execute("CREATE TABLE test_db.test_schema.crw_read (a INT)");
        engine.execute("GRANT SELECT ON TABLE test_db.test_schema.crw_read TO DATABASE ROLE test_db.crw_dr");
        engine.execute("GRANT OWNERSHIP ON TABLE test_db.test_schema.crw_held TO DATABASE ROLE test_db.crw_dr");
        // The owner's row names the database role as its granted privileges do: by its kind, bare.
        assertEquals("OWNERSHIP|DATABASE_ROLE|CRW_DR|true|CRW_DR", rows("SHOW GRANTS ON TABLE "
            + "test_db.test_schema.crw_held ->> SELECT \"privilege\", \"granted_to\", \"grantee_name\", "
            + "\"grant_option\", \"granted_by\" FROM $1"));
        assertEquals("SELECT|DATABASE_ROLE|CRW_DR", rows("SHOW GRANTS ON TABLE test_db.test_schema.crw_read ->> "
            + "SELECT \"privilege\", \"granted_to\", \"grantee_name\" FROM $1 WHERE \"privilege\" = 'SELECT'"));
        // What it owns is listed among its grants, as a role's is.
        assertEquals("OWNERSHIP|TEST_DB.TEST_SCHEMA.CRW_HELD|CRW_DR|true|CRW_DR ;; "
            + "SELECT|TEST_DB.TEST_SCHEMA.CRW_READ|CRW_DR|false|ACCOUNTADMIN", rows("SHOW GRANTS TO DATABASE ROLE "
            + "test_db.crw_dr ->> SELECT \"privilege\", \"name\", \"grantee_name\", \"grant_option\", "
            + "\"granted_by\" FROM $1 WHERE \"granted_on\" = 'TABLE' ORDER BY 2"));
        assertEquals("0", rows("SHOW GRANTS TO ROLE ACCOUNTADMIN ->> SELECT COUNT(*) FROM $1 WHERE \"name\" = "
            + "'TEST_DB.TEST_SCHEMA.CRW_HELD'"));
    }
}
