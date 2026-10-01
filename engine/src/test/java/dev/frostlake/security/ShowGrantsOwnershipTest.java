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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW GRANTS ON an object lists the owner's OWNERSHIP beside the privileges granted on it: granted to the
 * owning role with the grant option, by that role itself, and a role granted OWNERSHIP becomes that owner.
 * The rows come ordered by grantee and then by privilege, and each names the object by its own kind, a view
 * named with TABLE included, and by its full name, a routine's with its argument types. Every cell is
 * live-verified.
 */
public class ShowGrantsOwnershipTest extends BaseDatabaseTest {

    private String owner;

    @BeforeEach
    public void createObjects() {
        engine.execute("CREATE OR REPLACE DATABASE GRANTS_OWNERSHIP_DB");
        engine.execute("CREATE OR REPLACE TABLE T (a INT)");
        owner = String.valueOf(engine.executeQuery("SELECT CURRENT_ROLE()").getRows().get(0).getValue(0));
    }

    @AfterEach
    public void dropObjects() {
        engine.execute("DROP DATABASE IF EXISTS GRANTS_OWNERSHIP_DB");
    }

    /** Every row but its created_on, a comma between cells and a bar between rows. */
    private String grants(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 1; i < row.getValues().size(); i++) {
                if (i > 1) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** The owner's row for an object of this kind and name. */
    private String ownership(final String kind, final String name) {
        return "OWNERSHIP, " + kind + ", " + name + ", ROLE, " + owner + ", true, " + owner + ", ROLE";
    }

    /** A privilege granted by the owner to a role on the table. */
    private String granted(final String privilege, final String grantee) {
        return privilege + ", TABLE, GRANTS_OWNERSHIP_DB.PUBLIC.T, ROLE, " + grantee + ", false, " + owner + ", ROLE";
    }

    @Test
    public void theOwnerIsListedBesideTheGrantsInGranteeOrder() {
        assertEquals(ownership("TABLE", "GRANTS_OWNERSHIP_DB.PUBLIC.T"), grants("SHOW GRANTS ON TABLE T"));
        engine.execute("GRANT SELECT ON TABLE T TO ROLE PUBLIC");
        engine.execute("GRANT UPDATE, INSERT ON TABLE T TO ROLE PUBLIC");
        final String publicRows = granted("INSERT", "PUBLIC") + " | " + granted("SELECT", "PUBLIC") + " | "
            + granted("UPDATE", "PUBLIC");
        final String ownerRow = ownership("TABLE", "GRANTS_OWNERSHIP_DB.PUBLIC.T");
        final String expected = owner.compareTo("PUBLIC") < 0 ? ownerRow + " | " + publicRows : publicRows + " | " + ownerRow;
        assertEquals(expected, grants("SHOW GRANTS ON TABLE T"));
        assertEquals(expected, grants("SHOW GRANTS ON TABLE GRANTS_OWNERSHIP_DB.PUBLIC.T"));
        engine.execute("REVOKE SELECT ON TABLE T FROM ROLE PUBLIC");
        final String revoked = granted("INSERT", "PUBLIC") + " | " + granted("UPDATE", "PUBLIC");
        assertEquals(owner.compareTo("PUBLIC") < 0 ? ownerRow + " | " + revoked : revoked + " | " + ownerRow,
            grants("SHOW GRANTS ON TABLE T"));
    }

    @Test
    public void everyKindNamesItsOwnerAndItself() {
        engine.execute("CREATE OR REPLACE VIEW V AS SELECT 1 AS x");
        engine.execute("CREATE OR REPLACE SEQUENCE SQ");
        engine.execute("CREATE OR REPLACE STAGE STG");
        engine.execute("CREATE OR REPLACE FUNCTION F2(x INT, y VARCHAR) RETURNS INT AS 'x'");
        engine.execute("CREATE OR REPLACE PROCEDURE PR2(x INT) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE OR REPLACE STREAM ST ON TABLE T");
        engine.execute("CREATE OR REPLACE TRANSIENT TABLE T3 (a INT)");
        engine.execute("CREATE OR REPLACE SCHEMA S2");
        engine.execute("USE SCHEMA GRANTS_OWNERSHIP_DB.PUBLIC");
        assertEquals(ownership("VIEW", "GRANTS_OWNERSHIP_DB.PUBLIC.V"), grants("SHOW GRANTS ON VIEW V"));
        assertEquals(ownership("VIEW", "GRANTS_OWNERSHIP_DB.PUBLIC.V"), grants("SHOW GRANTS ON TABLE V"));
        assertEquals(ownership("TABLE", "GRANTS_OWNERSHIP_DB.PUBLIC.T"), grants("SHOW GRANTS ON VIEW T"));
        assertEquals(ownership("TABLE", "GRANTS_OWNERSHIP_DB.PUBLIC.T3"), grants("SHOW GRANTS ON TABLE T3"));
        assertEquals(ownership("SEQUENCE", "GRANTS_OWNERSHIP_DB.PUBLIC.SQ"), grants("SHOW GRANTS ON SEQUENCE SQ"));
        assertEquals(ownership("STAGE", "GRANTS_OWNERSHIP_DB.PUBLIC.STG"), grants("SHOW GRANTS ON STAGE STG"));
        assertEquals(ownership("FUNCTION", "GRANTS_OWNERSHIP_DB.PUBLIC.F2(NUMBER, VARCHAR)"),
            grants("SHOW GRANTS ON FUNCTION F2(INT, VARCHAR)"));
        assertEquals(ownership("PROCEDURE", "GRANTS_OWNERSHIP_DB.PUBLIC.PR2(NUMBER)"),
            grants("SHOW GRANTS ON PROCEDURE PR2(INT)"));
        assertEquals(ownership("STREAM", "GRANTS_OWNERSHIP_DB.PUBLIC.ST"), grants("SHOW GRANTS ON STREAM ST"));
        assertEquals(ownership("SCHEMA", "GRANTS_OWNERSHIP_DB.PUBLIC"), grants("SHOW GRANTS ON SCHEMA PUBLIC"));
        assertEquals(ownership("SCHEMA", "GRANTS_OWNERSHIP_DB.S2"), grants("SHOW GRANTS ON SCHEMA S2"));
        assertEquals(ownership("DATABASE", "GRANTS_OWNERSHIP_DB"), grants("SHOW GRANTS ON DATABASE GRANTS_OWNERSHIP_DB"));
        assertEquals("", grants("SHOW GRANTS ON SCHEMA INFORMATION_SCHEMA"));
    }

    /** A role granted OWNERSHIP holds it with the grant option, granted by itself. */
    @Test
    public void aRoleGrantedOwnershipIsTheOwner() {
        engine.execute("CREATE OR REPLACE TABLE T2 (a INT)");
        engine.execute("GRANT OWNERSHIP ON TABLE T2 TO ROLE SYSADMIN");
        assertEquals("OWNERSHIP, TABLE, GRANTS_OWNERSHIP_DB.PUBLIC.T2, ROLE, SYSADMIN, true, SYSADMIN, ROLE",
            grants("SHOW GRANTS ON TABLE T2"));
    }
}
