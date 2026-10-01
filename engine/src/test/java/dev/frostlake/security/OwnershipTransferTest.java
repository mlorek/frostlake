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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * GRANT OWNERSHIP moves the object to the role: SHOW TABLES, SHOW VIEWS, SHOW GRANTS and INFORMATION_SCHEMA name the
 * new owner, and a later grant on the object is recorded in its name. A grant the object carries blocks the move,
 * named in live's sentence, unless the statement ends with COPY CURRENT GRANTS, which keeps the grants in the new
 * owner's name, or REVOKE CURRENT GRANTS, which drops them. Ownership is never revoked (live-verified).
 */
public class OwnershipTransferTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE TABLE T2 (a INT)");
        engine.execute("CREATE TABLE T3 (a INT)");
        engine.execute("CREATE VIEW V1 AS SELECT 1 AS x");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private static String dependent(final String privilege, final String securable, final String role) {
        return "SQL execution error: Dependent grant of privilege '" + privilege + "' on securable '" + securable
            + "' to role '" + role + "' exists.  It must be revoked first.  More than one dependent grant may exist: "
            + "use 'SHOW GRANTS' command to view them.  To revoke all dependent grants while transferring object "
            + "ownership, use convenience command 'GRANT OWNERSHIP ON <target_objects> TO <target_role> REVOKE "
            + "CURRENT GRANTS'.";
    }

    /** Each grant SHOW GRANTS ON lists, as privilege, grantee, grant option and grantor. */
    private List<String> grants(final String on) {
        final ResultSet shown = engine.executeQuery("SHOW GRANTS ON " + on);
        final List<String> grants = new ArrayList<>();
        for (final Row row : shown.getRows()) {
            grants.add(row.getValue(shown.getColumnIndex("privilege")) + " " + row.getValue(shown.getColumnIndex(
                "grantee_name")) + " " + row.getValue(shown.getColumnIndex("grant_option")) + " "
                + row.getValue(shown.getColumnIndex("granted_by")));
        }
        return grants;
    }

    /** The owner column of a SHOW listing's one row. */
    private String owner(final String show) {
        final ResultSet shown = engine.executeQuery(show);
        assertEquals(1, shown.getRowCount(), show);
        return String.valueOf(shown.getRows().get(0).getValue(shown.getColumnIndex("owner")));
    }

    private String tableOwner(final String schema, final String table) {
        return String.valueOf(engine.executeQuery("SELECT table_owner FROM information_schema.tables WHERE table_schema = '"
            + schema + "' AND table_name = '" + table + "'").getRows().get(0).getValue(0));
    }

    @Test
    public void aGrantTheObjectCarriesBlocksTheMove() {
        engine.execute("GRANT SELECT ON TABLE T3 TO ROLE PUBLIC");
        assertEquals(dependent("SELECT", "TEST_DB.TEST_SCHEMA.T3", "PUBLIC"),
            refusal("GRANT OWNERSHIP ON TABLE T3 TO ROLE SYSADMIN"));
        assertEquals(List.of("OWNERSHIP ACCOUNTADMIN true ACCOUNTADMIN", "SELECT PUBLIC false ACCOUNTADMIN"),
            grants("TABLE T3"));
        assertEquals("ACCOUNTADMIN", owner("SHOW TABLES LIKE 'T3'"));
        engine.execute("CREATE TABLE T6 (a INT)");
        engine.execute("GRANT INSERT, SELECT ON TABLE T6 TO ROLE PUBLIC");
        assertEquals(dependent("INSERT", "TEST_DB.TEST_SCHEMA.T6", "PUBLIC"),
            refusal("GRANT OWNERSHIP ON TABLE T6 TO ROLE SYSADMIN"));
        engine.execute("CREATE TABLE T7 (a INT)");
        engine.execute("GRANT SELECT ON TABLE T7 TO ROLE SYSADMIN");
        assertEquals(dependent("SELECT", "TEST_DB.TEST_SCHEMA.T7", "SYSADMIN"),
            refusal("GRANT OWNERSHIP ON TABLE T7 TO ROLE SYSADMIN"));
        engine.execute("GRANT SELECT ON VIEW V1 TO ROLE PUBLIC");
        assertEquals(dependent("SELECT", "TEST_DB.TEST_SCHEMA.V1", "PUBLIC"),
            refusal("GRANT OWNERSHIP ON VIEW V1 TO ROLE SYSADMIN"));
        engine.execute("CREATE SCHEMA S1");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("GRANT USAGE ON SCHEMA S1 TO ROLE PUBLIC");
        assertEquals(dependent("USAGE", "TEST_DB.S1", "PUBLIC"), refusal("GRANT OWNERSHIP ON SCHEMA S1 TO ROLE SYSADMIN"));
        // The role that already owns the object takes it again, grants and all.
        engine.execute("GRANT OWNERSHIP ON TABLE T3 TO ROLE ACCOUNTADMIN");
        assertEquals(List.of("OWNERSHIP ACCOUNTADMIN true ACCOUNTADMIN", "SELECT PUBLIC false ACCOUNTADMIN"),
            grants("TABLE T3"));
    }

    @Test
    public void copyCurrentGrantsKeepsThemInTheNewOwnersName() {
        engine.execute("GRANT SELECT ON TABLE T3 TO ROLE PUBLIC");
        engine.execute("GRANT OWNERSHIP ON TABLE T3 TO ROLE SYSADMIN COPY CURRENT GRANTS");
        assertEquals(List.of("SELECT PUBLIC false SYSADMIN", "OWNERSHIP SYSADMIN true SYSADMIN"), grants("TABLE T3"));
        assertEquals("SYSADMIN", owner("SHOW TABLES LIKE 'T3'"));
        assertEquals("SYSADMIN", tableOwner("TEST_SCHEMA", "T3"));
        engine.execute("GRANT SELECT ON VIEW V1 TO ROLE PUBLIC");
        engine.execute("GRANT OWNERSHIP ON VIEW V1 TO ROLE SYSADMIN COPY CURRENT GRANTS");
        assertEquals(List.of("SELECT PUBLIC false SYSADMIN", "OWNERSHIP SYSADMIN true SYSADMIN"), grants("VIEW V1"));
        assertEquals("SYSADMIN", owner("SHOW VIEWS LIKE 'V1'"));
        // Still blocked for the next move, now in the new owner's name.
        assertEquals(dependent("SELECT", "TEST_DB.TEST_SCHEMA.T3", "PUBLIC"),
            refusal("GRANT OWNERSHIP ON TABLE T3 TO ROLE ACCOUNTADMIN"));
    }

    @Test
    public void revokeCurrentGrantsDropsThem() {
        engine.execute("GRANT SELECT ON TABLE T3 TO ROLE PUBLIC");
        engine.execute("GRANT OWNERSHIP ON TABLE T3 TO ROLE SYSADMIN REVOKE CURRENT GRANTS");
        assertEquals(List.of("OWNERSHIP SYSADMIN true SYSADMIN"), grants("TABLE T3"));
        assertEquals("SYSADMIN", owner("SHOW TABLES LIKE 'T3'"));
    }

    @Test
    public void aMoveWithNoGrantsTakesTheOwnerAndLaterGrantsAreItsOwn() {
        engine.execute("GRANT OWNERSHIP ON TABLE T2 TO ROLE PUBLIC");
        assertEquals("PUBLIC", owner("SHOW TABLES LIKE 'T2'"));
        assertEquals("PUBLIC", tableOwner("TEST_SCHEMA", "T2"));
        assertEquals(List.of("OWNERSHIP PUBLIC true PUBLIC"), grants("TABLE T2"));
        assertEquals(0L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM T2").getRows().get(0).getValue(0))
            .longValue());
        engine.execute("GRANT SELECT ON TABLE T2 TO ROLE SYSADMIN");
        assertEquals(List.of("OWNERSHIP PUBLIC true PUBLIC", "SELECT SYSADMIN false PUBLIC"), grants("TABLE T2"));
        assertEquals(dependent("SELECT", "TEST_DB.TEST_SCHEMA.T2", "SYSADMIN"),
            refusal("GRANT OWNERSHIP ON TABLE T2 TO ROLE SYSADMIN"));
        engine.execute("GRANT OWNERSHIP ON VIEW V1 TO ROLE PUBLIC");
        assertEquals(List.of("OWNERSHIP PUBLIC true PUBLIC"), grants("VIEW V1"));
    }

    @Test
    public void everyTableOfASchemaMovesInNameOrderUntilAGrantBlocksOne() {
        engine.execute("CREATE SCHEMA S2");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TABLE test_db.S2.A1 (a INT)");
        engine.execute("CREATE TABLE test_db.S2.A2 (a INT)");
        engine.execute("CREATE TABLE test_db.S2.B1 (a INT)");
        engine.execute("GRANT SELECT ON TABLE test_db.S2.A2 TO ROLE PUBLIC");
        assertEquals(dependent("SELECT", "TEST_DB.S2.A2", "PUBLIC"),
            refusal("GRANT OWNERSHIP ON ALL TABLES IN SCHEMA test_db.S2 TO ROLE SYSADMIN"));
        assertEquals("SYSADMIN", tableOwner("S2", "A1"));
        assertEquals("ACCOUNTADMIN", tableOwner("S2", "A2"));
        assertEquals("ACCOUNTADMIN", tableOwner("S2", "B1"));
        engine.execute("GRANT OWNERSHIP ON ALL TABLES IN SCHEMA test_db.S2 TO ROLE SYSADMIN REVOKE CURRENT GRANTS");
        assertEquals("SYSADMIN", tableOwner("S2", "A2"));
        assertEquals("SYSADMIN", tableOwner("S2", "B1"));
        assertEquals(List.of("OWNERSHIP SYSADMIN true SYSADMIN"), grants("TABLE test_db.S2.A2"));
    }

    @Test
    public void ownershipIsTransferredNeverRevokedAndNeverListedBesideAnotherPrivilege() {
        assertEquals("SQL execution error: OWNERSHIP can only be transferred.",
            refusal("REVOKE OWNERSHIP ON TABLE T3 FROM ROLE ACCOUNTADMIN"));
        assertEquals(hinted("SQL compilation error:\nTable 'NOSUCH' does not exist or not authorized."),
            refusal("REVOKE OWNERSHIP ON TABLE nosuch FROM ROLE SYSADMIN"));
        assertEquals(hinted("SQL compilation error:\nRole 'NOSUCHROLE' does not exist or not authorized."),
            refusal("REVOKE OWNERSHIP ON TABLE T3 FROM ROLE nosuchrole"));
        assertEquals(hinted("SQL compilation error:\nUser 'NOSUCH_USER_XYZ' does not exist or not authorized."),
            refusal("GRANT OWNERSHIP ON TABLE T3 TO USER nosuch_user_xyz"));
        assertEquals("Cannot specify OWNERSHIP in privilege list.",
            refusal("GRANT SELECT, OWNERSHIP ON TABLE T3 TO ROLE PUBLIC"));
        assertEquals("SQL compilation error:\nOnly OWNERSHIP grant command may specify REVOKE CURRENT GRANTS option.",
            refusal("GRANT SELECT ON TABLE T3 TO ROLE PUBLIC COPY CURRENT GRANTS"));
        assertEquals("SQL compilation error:\nOnly OWNERSHIP grant command may specify REVOKE CURRENT GRANTS option.",
            refusal("GRANT SELECT ON TABLE T3 TO ROLE PUBLIC REVOKE CURRENT GRANTS"));
        assertEquals(hinted("SQL compilation error:\nTable 'NOSUCH' does not exist or not authorized."),
            refusal("GRANT SELECT ON TABLE nosuch TO ROLE PUBLIC COPY CURRENT GRANTS"));
        assertEquals(hinted("SQL compilation error:\nRole 'NOSUCHROLE' does not exist or not authorized."),
            refusal("GRANT SELECT ON TABLE T3 TO ROLE nosuchrole COPY CURRENT GRANTS"));
    }

    @Test
    public void anIncompleteTailIsASyntaxError() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 50 unexpected 'GRANTS'.",
            refusal("GRANT OWNERSHIP ON TABLE T5 TO ROLE SYSADMIN COPY GRANTS"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 48 unexpected '<EOF>'.",
            refusal("GRANT OWNERSHIP ON TABLE T10 TO ROLE PUBLIC COPY"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 56 unexpected '<EOF>'.",
            refusal("GRANT OWNERSHIP ON TABLE T10 TO ROLE PUBLIC COPY CURRENT"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 44 unexpected 'REVOKE'.",
            refusal("GRANT OWNERSHIP ON TABLE T10 TO ROLE PUBLIC REVOKE"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 65 unexpected 'REVOKE'.",
            refusal("GRANT OWNERSHIP ON TABLE T5 TO ROLE SYSADMIN COPY CURRENT GRANTS REVOKE CURRENT GRANTS"));
    }
}
