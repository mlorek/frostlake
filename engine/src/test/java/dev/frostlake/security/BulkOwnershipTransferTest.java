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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GRANT OWNERSHIP ON ALL <kind> IN SCHEMA | DATABASE} moves every object of that kind, not only
 * the tables and views it used to. A sequence, a stage, a stream, a function, a procedure and a schema
 * each move the same way, and the bulk form over a kind reaches exactly the objects a single-object
 * grant would name one at a time.
 *
 * <p>The move is NOT atomic: the objects go in name order, and a dependent grant on one stops the
 * statement there with everything before it already moved. {@code COPY CURRENT GRANTS} carries the
 * dependent grants over instead of refusing.
 *
 * <p>A bulk grant reaches a DATABASE or a SCHEMA and nothing wider — {@code IN ACCOUNT} is a syntax
 * error, for ON ALL and ON FUTURE alike, whichever privilege is named.
 */
public class BulkOwnershipTransferTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE OR REPLACE ROLE boa");
        engine.execute("CREATE OR REPLACE ROLE bob");
        engine.execute("CREATE OR REPLACE SCHEMA bs1");
    }

    /** Every row of a listing, as "c1, c2 | c1, c2". */
    private String rowsOf(final String sql, final String... columns) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int c = 0; c < columns.length; c++) {
                if (c > 0) {
                    out.append(", ");
                }
                out.append(String.valueOf(rs.getValue(columns[c])));
            }
        }
        return out.toString();
    }

    /** The refusal a statement raises, or "OK". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "OK";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Sequences, stages and streams move, as tables and views already did. */
    @Test
    public void theSchemaLevelKindsMove() {
        engine.execute("CREATE SEQUENCE bs1.q1");
        engine.execute("CREATE SEQUENCE bs1.q2");
        engine.execute("CREATE STAGE bs1.g1");
        engine.execute("CREATE TABLE bs1.t1 (a INT)");
        engine.execute("CREATE STREAM bs1.m1 ON TABLE bs1.t1");

        engine.execute("GRANT OWNERSHIP ON ALL SEQUENCES IN SCHEMA bs1 TO ROLE boa");
        assertEquals("Q1, BOA | Q2, BOA", rowsOf("SHOW SEQUENCES IN SCHEMA bs1", "name", "owner"));

        engine.execute("GRANT OWNERSHIP ON ALL STAGES IN SCHEMA bs1 TO ROLE boa");
        assertEquals("G1, BOA", rowsOf("SHOW STAGES IN SCHEMA bs1", "name", "owner"));

        engine.execute("GRANT OWNERSHIP ON ALL STREAMS IN SCHEMA bs1 TO ROLE boa");
        assertEquals("M1, BOA", rowsOf("SHOW STREAMS IN SCHEMA bs1", "name", "owner"));
    }

    /** A routine moves too, which SHOW GRANTS on it reads back as OWNERSHIP to the new role. */
    @Test
    public void theRoutinesMove() {
        engine.execute("CREATE FUNCTION bs1.f1() RETURNS INT AS $$ 1 $$");
        engine.execute("CREATE PROCEDURE bs1.p1() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");

        engine.execute("GRANT OWNERSHIP ON ALL FUNCTIONS IN SCHEMA bs1 TO ROLE boa");
        assertEquals("OWNERSHIP, BOA",
            rowsOf("SHOW GRANTS ON FUNCTION bs1.f1()", "privilege", "grantee_name"));

        engine.execute("GRANT OWNERSHIP ON ALL PROCEDURES IN SCHEMA bs1 TO ROLE boa");
        assertEquals("OWNERSHIP, BOA",
            rowsOf("SHOW GRANTS ON PROCEDURE bs1.p1()", "privilege", "grantee_name"));
    }

    /** A non-ownership bulk grant reaches the routines as well, beside the ownership row. */
    @Test
    public void aBulkPrivilegeReachesARoutine() {
        engine.execute("CREATE FUNCTION bs1.f2() RETURNS INT AS $$ 1 $$");
        engine.execute("GRANT USAGE ON ALL FUNCTIONS IN SCHEMA bs1 TO ROLE bob");
        assertTrue(rowsOf("SHOW GRANTS ON FUNCTION bs1.f2()", "privilege", "grantee_name")
            .contains("USAGE, BOB"),
            rowsOf("SHOW GRANTS ON FUNCTION bs1.f2()", "privilege", "grantee_name"));
    }

    /** SCHEMAS is scoped to a database and moves each schema — but never INFORMATION_SCHEMA. */
    @Test
    public void everySchemaOfADatabaseMoves() {
        engine.execute("CREATE OR REPLACE SCHEMA bs2");
        engine.execute("GRANT OWNERSHIP ON ALL SCHEMAS IN DATABASE test_db TO ROLE boa");
        final String owners = rowsOf("SHOW SCHEMAS IN DATABASE test_db", "name", "owner");
        assertTrue(owners.contains("BS1, BOA"), owners);
        assertTrue(owners.contains("BS2, BOA"), owners);
        assertTrue(owners.contains("PUBLIC, BOA"), owners);
        assertTrue(owners.contains("INFORMATION_SCHEMA, "), owners);
    }

    /** The move stops at the first dependent grant, with the objects before it already moved. */
    @Test
    public void aDependentGrantStopsTheMoveWhereItStands() {
        engine.execute("CREATE SEQUENCE bs1.d1");
        engine.execute("CREATE SEQUENCE bs1.d2");
        engine.execute("GRANT USAGE ON SEQUENCE bs1.d2 TO ROLE bob");
        assertEquals("SQL execution error: Dependent grant of privilege 'USAGE' on securable"
            + " 'TEST_DB.BS1.D2' to role 'BOB' exists.  It must be revoked first.  More than one"
            + " dependent grant may exist: use 'SHOW GRANTS' command to view them.  To revoke all"
            + " dependent grants while transferring object ownership, use convenience command 'GRANT"
            + " OWNERSHIP ON <target_objects> TO <target_role> REVOKE CURRENT GRANTS'.",
            outcome("GRANT OWNERSHIP ON ALL SEQUENCES IN SCHEMA bs1 TO ROLE boa"));
        assertEquals("D1, BOA | D2, ACCOUNTADMIN",
            rowsOf("SHOW SEQUENCES IN SCHEMA bs1", "name", "owner"),
            "the one before it moved, the blocked one did not");

        assertEquals("OK", outcome(
            "GRANT OWNERSHIP ON ALL SEQUENCES IN SCHEMA bs1 TO ROLE boa COPY CURRENT GRANTS"));
        assertEquals("D1, BOA | D2, BOA", rowsOf("SHOW SEQUENCES IN SCHEMA bs1", "name", "owner"));
    }

    /** A bulk grant has no ACCOUNT scope: the word itself is where the statement stops. */
    @Test
    public void thereIsNoAccountScope() {
        for (final String sql : new String[] {
            "GRANT OWNERSHIP ON ALL SEQUENCES IN ACCOUNT TO ROLE boa",
            "GRANT USAGE ON ALL SEQUENCES IN ACCOUNT TO ROLE boa",
            "GRANT USAGE ON FUTURE SEQUENCES IN ACCOUNT TO ROLE boa",
            "REVOKE USAGE ON ALL SEQUENCES IN ACCOUNT FROM ROLE boa"}) {
            final String answer = outcome(sql);
            assertTrue(answer.contains("unexpected 'ACCOUNT'"), sql + " => " + answer);
        }
    }
}
